#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
DEPLOY_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

[[ $# -eq 1 ]] || die "Usage: $0 $DEPLOY_DIR/.backup-key-RUN_ID"
key_file="$1"
[[ "$key_file" =~ ^${DEPLOY_DIR//./\.}/\.backup-key-[0-9]+$ ]] \
  || die "Backup key file must be a run-scoped file inside the deployment directory"
[[ -f "$key_file" && ! -L "$key_file" ]] || die "Backup key file is missing or unsafe"

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  unset BACKUP_ENCRYPTION_KEY
  rm -f -- "$key_file"
  exit "$status"
}
trap cleanup EXIT INT TERM

BACKUP_ENCRYPTION_KEY="$(<"$key_file")"
export BACKUP_ENCRYPTION_KEY
[[ -n "$BACKUP_ENCRYPTION_KEY" ]] || die "Backup encryption key is empty"

BACKUP_DIR="${BACKUP_DIR:-$DEPLOY_DIR/backups}"
[[ "$BACKUP_DIR" == "$DEPLOY_DIR/backups" ]] \
  || die "Scheduled backups are restricted to $DEPLOY_DIR/backups"
if [[ ! -e "$BACKUP_DIR" ]]; then
  mkdir -m 700 -- "$BACKUP_DIR"
fi
[[ -d "$BACKUP_DIR" && ! -L "$BACKUP_DIR" ]] || die "Backup directory is unsafe"
chmod 700 "$BACKUP_DIR"

export BACKUP_DIR
export BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
export COMPOSE_FILE="${COMPOSE_FILE:-$DEPLOY_DIR/docker-compose.yml}"
unset BACKUP_OFFSITE_HOOK

backup_output="$(bash "$SCRIPT_DIR/backup-production.sh")"
printf '%s\n' "$backup_output" >&2
artifact_path="$(printf '%s\n' "$backup_output" \
  | sed -n 's/^Encrypted backup created: //p' \
  | tail -n 1)"
[[ "$artifact_path" == "$BACKUP_DIR/"* && -f "$artifact_path" && ! -L "$artifact_path" ]] \
  || die "Backup command did not return a valid encrypted artifact"
artifact_name="${artifact_path##*/}"
[[ "$artifact_name" =~ ^edu-admin-[0-9]{8}T[0-9]{6}Z\.tar\.enc$ ]] \
  || die "Backup artifact name is invalid"

if [[ "${RUN_RESTORE_DRILL:-0}" == "1" ]]; then
  printf 'Running the monthly isolated restore drill for %s\n' "$artifact_name" >&2
  bash "$SCRIPT_DIR/restore-drill.sh" "$artifact_path" >&2
fi

printf '%s\n' "$artifact_path"
