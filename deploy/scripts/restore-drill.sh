#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
# shellcheck source=backup-common.sh
source "$SCRIPT_DIR/backup-common.sh"

work_directory=""
drill_container=""
current_step="initialization"

report_unexpected_error() {
  local status=$?
  backup_warn "Restore drill failed during: $current_step (exit $status). The temporary container will be removed."
  return "$status"
}

cleanup() {
  local status=$?
  trap - EXIT
  trap - ERR
  set +e
  if [[ "$drill_container" =~ ^edu-admin-restore-drill-[0-9a-f]{12}$ ]]; then
    docker rm --force "$drill_container" >/dev/null 2>&1 || true
  fi
  remove_secure_work_directory "$work_directory"
  exit "$status"
}
trap cleanup EXIT
trap report_unexpected_error ERR
trap 'exit 130' INT
trap 'exit 143' TERM

[[ $# -eq 1 ]] || backup_die "Usage: $0 /absolute/path/to/edu-admin-TIMESTAMP.tar.enc"
for command_name in docker openssl tar gzip sha256sum realpath mktemp awk sort find; do
  require_command "$command_name"
done
require_encryption_key

MYSQL_DRILL_IMAGE="${MYSQL_DRILL_IMAGE:-mysql:8.0}"
[[ "$MYSQL_DRILL_IMAGE" != *$'\n'* && "$MYSQL_DRILL_IMAGE" != *$'\r'* ]] \
  || backup_die "MYSQL_DRILL_IMAGE must not contain line breaks"

artifact="$(resolve_existing_file "$1")"
work_directory="$(create_secure_work_directory edu-admin-drill)"
current_step="decrypting and verifying the selected artifact"
extract_verified_artifact "$artifact" "$work_directory"
MYSQL_DATABASE="$VERIFIED_DATABASE"

# Extract uploads only inside the temporary drill directory. This checks that
# every archived entry can be materialized without touching the live volume.
uploads_drill_directory="$work_directory/uploads-restored"
mkdir -m 700 "$uploads_drill_directory"
current_step="extracting uploads in the isolated work directory"
tar --no-same-owner --no-same-permissions -xzf "$VERIFIED_PAYLOAD_DIR/uploads.tar.gz" \
  -C "$uploads_drill_directory"
uploads_file_count="$(find "$uploads_drill_directory" -type f | wc -l | tr -d '[:space:]')"

attachment_check="archive-file-read"
attachment_file="$(find "$uploads_drill_directory" -type f -print -quit)"
if [[ -n "$attachment_file" ]]; then
  [[ -r "$attachment_file" ]] || backup_die "Restored attachment is not readable"
  head -c 1 "$attachment_file" >/dev/null || backup_die "Restored attachment could not be read"
else
  attachment_check="database-equivalence-query"
fi

drill_container="edu-admin-restore-drill-$(openssl rand -hex 6)"
drill_password="$(openssl rand -hex 32)"
drill_env_file="$work_directory/mysql-drill.env"
printf 'MYSQL_ROOT_PASSWORD=%s\n' "$drill_password" > "$drill_env_file"
chmod 600 "$drill_env_file"

current_step="starting isolated MySQL"
docker run --detach --rm --name "$drill_container" --network none \
  --env-file "$drill_env_file" "$MYSQL_DRILL_IMAGE" \
  --default-authentication-plugin=mysql_native_password >/dev/null

mysql_ready=0
for _ in $(seq 1 45); do
  if docker exec "$drill_container" sh -ceu \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping --user=root --silent' >/dev/null 2>&1; then
    mysql_ready=1
    break
  fi
  sleep 2
done
(( mysql_ready == 1 )) || backup_die "Temporary MySQL did not become ready within 90 seconds"

current_step="importing the SQL dump into isolated MySQL"
gzip -dc "$VERIFIED_PAYLOAD_DIR/database.sql.gz" | \
  docker exec -i "$drill_container" sh -ceu \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --binary-mode=1'

run_business_assertion() {
  local label="$1"
  local query="$2"
  local result

  result="$(docker exec "$drill_container" sh -ceu \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names --execute "$1"' \
    sh "$query")" || backup_die "$label query could not be executed"
  [[ "$result" == "1" ]] || backup_die "$label assertion failed; the configured query must return exactly 1"
}

if [[ -n "${RESTORE_DRILL_LOGIN_SQL:-}" ]]; then
  login_sql="$RESTORE_DRILL_LOGIN_SQL"
else
  login_sql="SELECT IF(COUNT(*) > 0 AND COALESCE(SUM(password NOT LIKE '\$2%'), 0) = 0, 1, 0)
             FROM \`$MYSQL_DATABASE\`.\`user\` WHERE status = 1 AND is_deleted = 0;"
fi
if [[ -n "${RESTORE_DRILL_LESSON_BALANCE_SQL:-}" ]]; then
  lesson_balance_sql="$RESTORE_DRILL_LESSON_BALANCE_SQL"
else
  lesson_balance_sql="SELECT IF(COUNT(*) > 0
                    AND COALESCE(SUM(remaining_lessons < 0 OR remaining_lessons > total_lessons), 0) = 0, 1, 0)
                    FROM \`$MYSQL_DATABASE\`.\`lesson_account\` WHERE is_deleted = 0;"
fi
if [[ -n "${RESTORE_DRILL_REFUND_SQL:-}" ]]; then
  refund_sql="$RESTORE_DRILL_REFUND_SQL"
else
  refund_sql="SELECT IF(COUNT(*) > 0
             AND COALESCE(SUM(r.amount <= 0 OR r.lesson_count <= 0
               OR p.id IS NULL OR e.id IS NULL), 0) = 0, 1, 0)
             FROM \`$MYSQL_DATABASE\`.\`refund_record\` r
             LEFT JOIN \`$MYSQL_DATABASE\`.\`payment_record\` p ON p.id = r.payment_record_id
             LEFT JOIN \`$MYSQL_DATABASE\`.\`enrollment\` e ON e.id = r.enrollment_id
             WHERE r.is_deleted = 0;"
fi

current_step="running isolated login, lesson-balance, and refund assertions"
run_business_assertion "login-account" "$login_sql"
run_business_assertion "lesson-balance" "$lesson_balance_sql"
run_business_assertion "refund-record" "$refund_sql"

if [[ "$attachment_check" == "database-equivalence-query" ]]; then
  if [[ -n "${RESTORE_DRILL_ATTACHMENT_SQL:-}" ]]; then
    attachment_sql="$RESTORE_DRILL_ATTACHMENT_SQL"
  else
    attachment_sql="SELECT IF(
      (SELECT COUNT(*) FROM \`$MYSQL_DATABASE\`.\`homework\`
       WHERE attachment_url IS NOT NULL AND attachment_url <> '')
      + (SELECT COUNT(*) FROM \`$MYSQL_DATABASE\`.\`exam_signup\`
         WHERE certificate_file_url IS NOT NULL AND certificate_file_url <> '') = 0,
      1, 0);"
  fi
  current_step="checking attachment equivalence because the uploads archive is empty"
  run_business_assertion "attachment-equivalence" "$attachment_sql"
fi

current_step="complete"
printf 'Restore drill passed for %s.\n' "$artifact"
printf 'Validated login data, lesson balances, refund links, and attachments (%s; %s files extracted).\n' \
  "$attachment_check" "$uploads_file_count"
printf 'Production Compose services, database, and uploads volume were not accessed.\n'
