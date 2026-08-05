#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
# shellcheck source=backup-common.sh
source "$SCRIPT_DIR/backup-common.sh"

work_directory=""
backend_stopped=0
destructive_stage_started=0
current_step="initialization"

report_unexpected_error() {
  local status=$?
  backup_warn "Restore failed during: $current_step (exit $status). Follow the runbook before retrying."
  return "$status"
}

cleanup() {
  local status=$?
  trap - EXIT
  trap - ERR
  set +e
  if (( backend_stopped )); then
    if (( destructive_stage_started )); then
      backup_warn "Restore did not complete; backend remains stopped to avoid serving an inconsistent state"
    elif ! compose_cmd up -d --no-deps backend >/dev/null; then
      backup_warn "Backend restart failed after a pre-restore interruption"
    fi
  fi
  remove_secure_work_directory "$work_directory"
  exit "$status"
}
trap cleanup EXIT
trap report_unexpected_error ERR
trap 'exit 130' INT
trap 'exit 143' TERM

[[ $# -eq 1 ]] || backup_die "Usage: $0 /absolute/path/to/edu-admin-TIMESTAMP.tar.enc"
for command_name in docker openssl tar gzip sha256sum realpath mktemp awk sort; do
  require_command "$command_name"
done
require_encryption_key
[[ "${RESTORE_TARGET:-deny}" == "production" ]] \
  || backup_die "Production restore is disabled by default; set RESTORE_TARGET=production for this command only"

BACKUP_HELPER_IMAGE="${BACKUP_HELPER_IMAGE:-alpine:3.20.3}"
[[ "$BACKUP_HELPER_IMAGE" != *$'\n'* && "$BACKUP_HELPER_IMAGE" != *$'\r'* ]] \
  || backup_die "BACKUP_HELPER_IMAGE must not contain line breaks"

artifact="$(resolve_existing_file "$1")"
initialize_compose
load_mysql_database
work_directory="$(create_secure_work_directory edu-admin-restore)"

# All authenticity/integrity and archive-safety checks happen before the
# backend is stopped or any database/volume content is changed.
current_step="decrypting and verifying the selected artifact"
extract_verified_artifact "$artifact" "$work_directory" "$MYSQL_DATABASE"
current_step="checking the production database and uploads volume"
backend_container="$(find_backend_container)"
backend_is_running "$backend_container" || backup_die "Backend must be running before a production restore"
uploads_volume="$(find_uploads_volume "$backend_container")"
ensure_mysql_ready
docker run --rm "$BACKUP_HELPER_IMAGE" sh -ceu 'command -v tar >/dev/null && command -v find >/dev/null' >/dev/null \
  || backup_die "Restore helper image is unavailable or incomplete"

artifact_name="$(basename -- "$artifact")"
confirmation_phrase="RESTORE $MYSQL_DATABASE FROM $artifact_name"
backup_warn "This replaces the production database and every file in /app/uploads."
backup_warn "Application-image rollback and schema changes made outside this dump are not rolled back automatically."
printf 'Type exactly: %s\n' "$confirmation_phrase" >&2
[[ -t 0 ]] || backup_die "Production restore requires an interactive TTY"
IFS= read -r confirmation || backup_die "Confirmation was not received"
[[ "$confirmation" == "$confirmation_phrase" ]] || backup_die "Confirmation did not match; nothing was changed"

current_step="stopping the production backend"
backend_stopped=1
compose_cmd stop --timeout 30 backend >/dev/null
destructive_stage_started=1

current_step="replacing the production database"
gzip -dc "$VERIFIED_PAYLOAD_DIR/database.sql.gz" | \
  compose_cmd exec -T mysql sh -ceu \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --binary-mode=1'

current_step="replacing the production uploads volume"
docker run --rm \
  --volume "$uploads_volume:/target" \
  --volume "$VERIFIED_PAYLOAD_DIR/uploads.tar.gz:/backup/uploads.tar.gz:ro" \
  "$BACKUP_HELPER_IMAGE" sh -ceu '
    find /target -mindepth 1 -maxdepth 1 -exec rm -rf -- {} \;
    tar -xzf /backup/uploads.tar.gz -C /target
  '

current_step="restarting the production backend"
compose_cmd up -d --no-deps backend >/dev/null
backend_stopped=0
destructive_stage_started=0

current_step="complete"
printf 'Restore completed from: %s\n' "$artifact"
backup_warn "Verify the backend and user journeys before reopening traffic. Database schema rollback is not automatic."
