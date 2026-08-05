#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
# shellcheck source=backup-common.sh
source "$SCRIPT_DIR/backup-common.sh"

work_directory=""
encrypted_temporary_file=""
backend_stopped=0
current_step="initialization"

report_unexpected_error() {
  local status=$?
  backup_warn "Backup failed during: $current_step (exit $status). Correct the reported problem and retry."
  return "$status"
}

cleanup() {
  local status=$?
  trap - EXIT
  trap - ERR
  set +e
  if (( backend_stopped )); then
    if ! compose_cmd up -d --no-deps backend >/dev/null; then
      backup_warn "Automatic backend restart failed; restart it manually"
      (( status == 0 )) && status=1
    fi
  fi
  [[ -z "$encrypted_temporary_file" || ! -e "$encrypted_temporary_file" ]] || rm -f -- "$encrypted_temporary_file"
  remove_secure_work_directory "$work_directory"
  exit "$status"
}
trap cleanup EXIT
trap report_unexpected_error ERR
trap 'exit 130' INT
trap 'exit 143' TERM

for command_name in docker openssl tar gzip sha256sum realpath mktemp find awk; do
  require_command "$command_name"
done
require_encryption_key
require_env BACKUP_DIR

BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
[[ "$BACKUP_RETENTION_DAYS" =~ ^[0-9]+$ ]] && (( BACKUP_RETENTION_DAYS >= 1 )) \
  || backup_die "BACKUP_RETENTION_DAYS must be an integer of at least 1"
BACKUP_HELPER_IMAGE="${BACKUP_HELPER_IMAGE:-alpine:3.20.3}"
[[ "$BACKUP_HELPER_IMAGE" != *$'\n'* && "$BACKUP_HELPER_IMAGE" != *$'\r'* ]] \
  || backup_die "BACKUP_HELPER_IMAGE must not contain line breaks"

backup_directory="$(resolve_backup_directory "$BACKUP_DIR")"
initialize_compose
load_mysql_database
work_directory="$(create_secure_work_directory edu-admin-backup)"
payload_directory="$work_directory/payload"
mkdir -m 700 "$payload_directory"

backend_container="$(find_backend_container)"
backend_is_running "$backend_container" || backup_die "Backend must be running before a production backup"
uploads_volume="$(find_uploads_volume "$backend_container")"
ensure_mysql_ready
current_step="preparing the uploads helper image"
docker run --rm "$BACKUP_HELPER_IMAGE" sh -ceu 'command -v tar >/dev/null' >/dev/null \
  || backup_die "Backup helper image is unavailable or does not provide tar"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
artifact_name="edu-admin-${timestamp}.tar.enc"
artifact_path="$backup_directory/$artifact_name"
[[ ! -e "$artifact_path" ]] || backup_die "Backup artifact already exists: $artifact_path"
encrypted_temporary_file="$(mktemp "$backup_directory/.${artifact_name}.XXXXXX.tmp")"

printf 'Stopping backend briefly to create a consistent database and uploads snapshot...\n'
current_step="stopping the backend for a consistent snapshot"
backend_stopped=1
compose_cmd stop --timeout 30 backend >/dev/null

current_step="dumping MySQL"
compose_cmd exec -T mysql sh -ceu '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump \
    --user=root --single-transaction --quick --routines --events --triggers \
    --hex-blob --set-gtid-purged=OFF --add-drop-database --databases "$MYSQL_DATABASE"
' \
  > "$work_directory/database.sql"
gzip -9 "$work_directory/database.sql"
mv "$work_directory/database.sql.gz" "$payload_directory/database.sql.gz"

current_step="archiving the uploads volume"
docker run --rm --volume "$uploads_volume:/source:ro" "$BACKUP_HELPER_IMAGE" \
  sh -ceu 'cd /source && tar -czf - .' > "$payload_directory/uploads.tar.gz"
validate_upload_archive "$payload_directory/uploads.tar.gz" "$work_directory"

current_step="building and encrypting the backup artifact"
compose_file_sha256="$(sha256sum "$COMPOSE_FILE_RESOLVED" | awk '{print $1}')"
cat > "$payload_directory/manifest.txt" <<EOF
format_version=1
created_at_utc=$timestamp
database=$MYSQL_DATABASE
compose_file_sha256=$compose_file_sha256
uploads_archive_root=/app/uploads
database_dump_mode=single-transaction
encryption=aes-256-cbc-pbkdf2-sha256-iter200000
EOF
(cd "$payload_directory" && sha256sum database.sql.gz manifest.txt uploads.tar.gz > SHA256SUMS)
tar -cf "$work_directory/payload.tar" -C "$payload_directory" \
  SHA256SUMS database.sql.gz manifest.txt uploads.tar.gz

openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 -md sha256 \
  -in "$work_directory/payload.tar" -out "$encrypted_temporary_file" \
  -pass env:BACKUP_ENCRYPTION_KEY
chmod 600 "$encrypted_temporary_file"
mv -- "$encrypted_temporary_file" "$artifact_path"
encrypted_temporary_file=""

current_step="restarting the backend"
compose_cmd up -d --no-deps backend >/dev/null
backend_stopped=0

if [[ -n "${BACKUP_OFFSITE_HOOK:-}" ]]; then
  current_step="copying the encrypted artifact off site"
  offsite_hook="$(resolve_existing_executable "$BACKUP_OFFSITE_HOOK")"
  if ! env -u BACKUP_ENCRYPTION_KEY -u MYSQL_ROOT_PASSWORD -u MYSQL_PASSWORD \
    -u JWT_SECRET -u ACR_PASSWORD \
    "$offsite_hook" "$artifact_path"; then
    backup_die "Off-site hook failed; the local encrypted artifact remains at $artifact_path"
  fi
else
  backup_warn "BACKUP_OFFSITE_HOOK is not configured; the artifact is local-only and does not meet the off-host RPO target"
fi

# Retention is intentionally constrained to immediate children of the resolved,
# explicitly configured backup directory and to this tool's artifact pattern.
current_step="applying local backup retention"
find "$backup_directory" -mindepth 1 -maxdepth 1 -type f \
  -name 'edu-admin-*.tar.enc' -mtime "+$BACKUP_RETENTION_DAYS" -delete

current_step="complete"
printf 'Encrypted backup created: %s\n' "$artifact_path"
