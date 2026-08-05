#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
COMPOSE_FILE="${COMPOSE_FILE:-$DEPLOY_DIR/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:?PUBLIC_BASE_URL is required}"
EXPECTED_IMAGE_TAG="${EXPECTED_IMAGE_TAG:?EXPECTED_IMAGE_TAG is required}"
EXPECTED_SCHEMA_VERSION="${EXPECTED_SCHEMA_VERSION:-7}"
EVIDENCE_ROOT="${RELEASE_EVIDENCE_DIR:-$DEPLOY_DIR/release-evidence}"
INVARIANTS_SQL="${INVARIANTS_SQL:-$DEPLOY_DIR/verify_high_value_invariants.sql}"
ACCEPTANCE_SCRIPT="${ACCEPTANCE_SCRIPT:-$DEPLOY_DIR/acceptance_test.sh}"

current_step="initialization"
evidence_directory=""

die() {
  printf 'ERROR: %s\n' "$*" >&2
  if [[ -n "$evidence_directory" && -d "$evidence_directory" ]]; then
    collect_evidence 1
  fi
  exit 1
}

compose() {
  docker compose --env-file "$ENV_FILE" --file "$COMPOSE_FILE" "$@"
}

container_id() {
  compose ps -q "$1"
}

container_image() {
  local id
  id="$(container_id "$1")"
  if [[ -z "$id" ]]; then
    printf 'none\n'
  else
    docker inspect --format '{{.Config.Image}}' "$id"
  fi
}

database_version() {
  compose exec -T mysql sh -ceu '
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names \
      "$MYSQL_DATABASE" --execute \
      "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)), 0) FROM flyway_schema_history WHERE success = 1"
  ' 2>/dev/null || printf '0\n'
}

collect_evidence() {
  local status="${1:-$?}"
  trap - ERR
  set +e
  if [[ -n "$evidence_directory" && -d "$evidence_directory" ]]; then
    printf '%s\n' "$current_step" > "$evidence_directory/failed-step.txt"
    compose ps --all > "$evidence_directory/compose-ps.txt" 2>&1
    compose logs --no-color --timestamps --tail 500 > "$evidence_directory/compose-logs.txt" 2>&1
    docker inspect "$(container_id backend)" > "$evidence_directory/backend-inspect.json" 2>&1
    docker inspect "$(container_id web)" > "$evidence_directory/web-inspect.json" 2>&1
    chmod -R go-rwx "$evidence_directory" 2>/dev/null || true
  fi
  printf 'Release failed during %s (exit %s). Evidence: %s\n' \
    "$current_step" "$status" "${evidence_directory:-unavailable}" >&2
  printf 'Do not roll back blindly. Use RELEASE_RUNBOOK.md to choose image rollback or database forward repair.\n' >&2
  exit "$status"
}
trap 'collect_evidence $?' ERR

for command_name in docker curl grep awk sed date mkdir chmod seq sleep tr tee bash; do
  command -v "$command_name" >/dev/null 2>&1 || die "Required command is unavailable: $command_name"
done
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
[[ -f "$COMPOSE_FILE" && ! -L "$COMPOSE_FILE" ]] || die "Compose file is missing or unsafe: $COMPOSE_FILE"
[[ -f "$ENV_FILE" && ! -L "$ENV_FILE" ]] || die "Compose dotenv file is missing or unsafe: $ENV_FILE"
[[ -f "$INVARIANTS_SQL" && ! -L "$INVARIANTS_SQL" ]] || die "Invariant SQL is missing or unsafe: $INVARIANTS_SQL"
[[ -f "$ACCEPTANCE_SCRIPT" && ! -L "$ACCEPTANCE_SCRIPT" ]] || die "Acceptance script is missing or unsafe: $ACCEPTANCE_SCRIPT"
[[ "$EXPECTED_IMAGE_TAG" =~ ^[A-Za-z0-9._-]{7,128}$ ]] || die "EXPECTED_IMAGE_TAG has an invalid format"
[[ "$EXPECTED_SCHEMA_VERSION" =~ ^[0-9]+$ ]] || die "EXPECTED_SCHEMA_VERSION must be numeric"

mkdir -p -- "$EVIDENCE_ROOT"
[[ -d "$EVIDENCE_ROOT" && "$EVIDENCE_ROOT" != "/" ]] || die "Unsafe evidence root"
release_id="$(date -u +%Y%m%dT%H%M%SZ)-${EXPECTED_IMAGE_TAG:0:12}"
evidence_directory="$EVIDENCE_ROOT/$release_id"
mkdir -m 700 -- "$evidence_directory"
: > "$evidence_directory/.edu-admin-release-evidence"

previous_backend_image="$(container_image backend)"
previous_web_image="$(container_image web)"

current_step="starting database for pre-release checks"
compose up -d mysql
mysql_healthy=0
for _ in $(seq 1 60); do
  mysql_id="$(container_id mysql)"
  if [[ -n "$mysql_id" ]] \
      && [[ "$(docker inspect --format '{{.State.Health.Status}}' "$mysql_id" 2>/dev/null)" == "healthy" ]]; then
    mysql_healthy=1
    break
  fi
  sleep 2
done
(( mysql_healthy == 1 )) || die "MySQL did not become healthy within 120 seconds"

previous_schema_version="$(database_version | tr -d '[:space:]')"
cat > "$evidence_directory/release-state.env" <<EOF
PREVIOUS_BACKEND_IMAGE=$previous_backend_image
PREVIOUS_WEB_IMAGE=$previous_web_image
PREVIOUS_SCHEMA_VERSION=$previous_schema_version
TARGET_IMAGE_TAG=$EXPECTED_IMAGE_TAG
TARGET_SCHEMA_VERSION=$EXPECTED_SCHEMA_VERSION
EOF

current_step="validating high-value data invariants"
violations="$(compose exec -T mysql sh -ceu '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names "$MYSQL_DATABASE"
' < "$INVARIANTS_SQL")"
if [[ -n "${violations//[[:space:]]/}" ]]; then
  printf '%s\n' "$violations" > "$evidence_directory/invariant-violations.txt"
  die "Database invariants failed; inspect $evidence_directory/invariant-violations.txt"
fi

current_step="pulling immutable images"
compose pull

current_step="starting the release"
compose up -d --remove-orphans

current_step="waiting for service health"
healthy=0
for _ in $(seq 1 60); do
  mysql_id="$(container_id mysql)"
  backend_id="$(container_id backend)"
  web_id="$(container_id web)"
  caddy_id="$(container_id caddy)"
  if [[ -n "$mysql_id" && -n "$backend_id" && -n "$web_id" && -n "$caddy_id" ]] \
      && [[ "$(docker inspect --format '{{.State.Health.Status}}' "$mysql_id" 2>/dev/null)" == "healthy" ]] \
      && [[ "$(docker inspect --format '{{.State.Health.Status}}' "$backend_id" 2>/dev/null)" == "healthy" ]] \
      && [[ "$(docker inspect --format '{{.State.Health.Status}}' "$web_id" 2>/dev/null)" == "healthy" ]] \
      && [[ "$(docker inspect --format '{{.State.Running}}' "$caddy_id" 2>/dev/null)" == "true" ]]; then
    healthy=1
    break
  fi
  sleep 2
done
(( healthy == 1 )) || die "Services did not become healthy within 120 seconds"

current_step="verifying immutable image references"
actual_backend_image="$(container_image backend)"
actual_web_image="$(container_image web)"
[[ "$actual_backend_image" == *":$EXPECTED_IMAGE_TAG" ]] \
  || die "Backend is not running the expected immutable tag"
[[ "$actual_web_image" == *":$EXPECTED_IMAGE_TAG" ]] \
  || die "Web is not running the expected immutable tag"

current_step="verifying Flyway schema version"
actual_schema_version="$(database_version | tr -d '[:space:]')"
[[ "$actual_schema_version" == "$EXPECTED_SCHEMA_VERSION" ]] \
  || die "Expected Flyway version $EXPECTED_SCHEMA_VERSION, got $actual_schema_version"

current_step="verifying backend readiness and build information"
compose exec -T backend curl --fail --silent --show-error \
  http://localhost:8080/actuator/health/readiness > "$evidence_directory/readiness.json"
compose exec -T backend curl --fail --silent --show-error \
  http://localhost:8080/actuator/info > "$evidence_directory/build-info.json"
grep -Fq "$EXPECTED_IMAGE_TAG" "$evidence_directory/build-info.json" \
  || die "Actuator build information does not contain the expected image tag"

current_step="verifying Prometheus business metrics"
compose exec -T backend curl --fail --silent --show-error \
  http://localhost:8080/actuator/prometheus > "$evidence_directory/prometheus.txt"
for metric in \
  eduadmin_login_failures_total \
  eduadmin_enrollment_failures_total \
  eduadmin_payment_failures_total \
  eduadmin_lesson_account_cas_failures_total \
  eduadmin_schedule_conflicts_total \
  eduadmin_notification_failures_total \
  eduadmin_refunds_pending
do
  grep -Fq "$metric" "$evidence_directory/prometheus.txt" \
    || die "Prometheus output is missing required metric: $metric"
done

current_step="verifying admin and H5 entry points"
base_url="${PUBLIC_BASE_URL%/}"
curl --fail --silent --show-error --location "$base_url/" > "$evidence_directory/admin-index.html"
curl --fail --silent --show-error --location "$base_url/mobile/" > "$evidence_directory/mobile-index.html"
grep -Eiq '<!doctype html|<html' "$evidence_directory/admin-index.html" \
  || die "Admin entry point did not return HTML"
grep -Eiq '<!doctype html|<html' "$evidence_directory/mobile-index.html" \
  || die "H5 entry point did not return HTML"

current_step="running five-role API smoke checks"
bash "$ACCEPTANCE_SCRIPT" "$base_url" | tee "$evidence_directory/five-role-smoke.txt"

current_step="complete"
compose ps > "$evidence_directory/compose-ps.txt"
cat > "$evidence_directory/release-summary.txt" <<EOF
release_id=$release_id
backend_image=$actual_backend_image
web_image=$actual_web_image
schema_version=$actual_schema_version
public_base_url=$base_url
completed_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
chmod -R go-rwx "$evidence_directory"
printf 'Release verified. Evidence: %s\n' "$evidence_directory"
