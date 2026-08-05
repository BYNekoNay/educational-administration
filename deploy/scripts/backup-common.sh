#!/usr/bin/env bash
# shellcheck shell=bash

# Shared, source-only helpers for the production backup tools.

backup_die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

backup_warn() {
  printf 'WARNING: %s\n' "$*" >&2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || backup_die "Required command is not available: $1"
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || backup_die "Required environment variable is not set: $name"
  [[ "${!name}" != *$'\n'* && "${!name}" != *$'\r'* ]] || backup_die "$name must not contain line breaks"
}

require_encryption_key() {
  require_env BACKUP_ENCRYPTION_KEY
  (( ${#BACKUP_ENCRYPTION_KEY} >= 32 )) || backup_die "BACKUP_ENCRYPTION_KEY must contain at least 32 characters"
}

validate_database_name() {
  [[ "$1" =~ ^[A-Za-z0-9_]+$ ]] || backup_die "MYSQL_DATABASE may contain only letters, digits, and underscores"
}

resolve_existing_file() {
  local requested="$1"
  local resolved

  [[ "$requested" == /* ]] || backup_die "Path must be absolute: $requested"
  [[ ! -L "$requested" ]] || backup_die "Symbolic-link paths are not accepted: $requested"
  resolved="$(realpath -e -- "$requested")" || backup_die "File does not exist: $requested"
  [[ -f "$resolved" && ! -L "$resolved" ]] || backup_die "Path is not a regular file: $resolved"
  printf '%s\n' "$resolved"
}

resolve_existing_executable() {
  local requested="$1"
  local resolved

  resolved="$(resolve_existing_file "$requested")"
  [[ -x "$resolved" ]] || backup_die "File is not executable: $resolved"
  printf '%s\n' "$resolved"
}

resolve_backup_directory() {
  local requested="$1"
  local resolved

  [[ "$requested" == /* ]] || backup_die "BACKUP_DIR must be an absolute path"
  [[ "$requested" != "/" ]] || backup_die "BACKUP_DIR must not be the filesystem root"
  [[ "$requested" != *$'\n'* && "$requested" != *$'\r'* ]] || backup_die "BACKUP_DIR must not contain line breaks"
  mkdir -p -- "$requested"
  resolved="$(realpath -e -- "$requested")" || backup_die "Cannot resolve BACKUP_DIR"
  [[ -d "$resolved" && "$resolved" != "/" ]] || backup_die "Resolved BACKUP_DIR is unsafe: $resolved"
  printf '%s\n' "$resolved"
}

create_secure_work_directory() {
  local prefix="$1"
  local temp_root="${TMPDIR:-/tmp}"
  local resolved_root
  local directory

  resolved_root="$(realpath -e -- "$temp_root")" || backup_die "Cannot resolve temporary directory root: $temp_root"
  [[ -d "$resolved_root" ]] || backup_die "Temporary directory root is not a directory: $resolved_root"
  directory="$(mktemp -d "${resolved_root%/}/${prefix}.XXXXXX")" || backup_die "Cannot create temporary work directory"
  chmod 700 "$directory"
  : > "$directory/.edu-admin-tool-workdir"
  printf '%s\n' "$directory"
}

remove_secure_work_directory() {
  local directory="${1:-}"
  local resolved
  local temp_root

  [[ -n "$directory" && -d "$directory" ]] || return 0
  resolved="$(realpath -e -- "$directory")" || return 0
  temp_root="$(realpath -e -- "${TMPDIR:-/tmp}")" || return 0
  case "$resolved" in
    "$temp_root"/edu-admin-backup.*|"$temp_root"/edu-admin-restore.*|"$temp_root"/edu-admin-drill.*) ;;
    *) backup_warn "Refusing to remove unexpected work directory: $resolved"; return 1 ;;
  esac
  [[ -f "$resolved/.edu-admin-tool-workdir" ]] || {
    backup_warn "Refusing to remove unmarked work directory: $resolved"
    return 1
  }
  rm -rf -- "$resolved"
}

initialize_compose() {
  local candidate
  local env_candidate

  if [[ -n "${COMPOSE_FILE:-}" ]]; then
    candidate="$COMPOSE_FILE"
  elif [[ -f "$DEPLOY_DIR/docker-compose.yml" ]]; then
    candidate="$DEPLOY_DIR/docker-compose.yml"
  else
    candidate="$DEPLOY_DIR/docker-compose.production.yml"
  fi
  COMPOSE_FILE_RESOLVED="$(resolve_existing_file "$candidate")"
  env_candidate="${COMPOSE_ENV_FILE:-$DEPLOY_DIR/.env}"
  if [[ -e "$env_candidate" ]]; then
    COMPOSE_ENV_FILE_RESOLVED="$(resolve_existing_file "$env_candidate")"
  else
    COMPOSE_ENV_FILE_RESOLVED=""
  fi
  docker compose version >/dev/null 2>&1 || backup_die "Docker Compose v2 is required"
}

compose_cmd() {
  local -a command=(docker compose)
  if [[ -n "${COMPOSE_ENV_FILE_RESOLVED:-}" ]]; then
    command+=(--env-file "$COMPOSE_ENV_FILE_RESOLVED")
  fi
  command+=(--file "$COMPOSE_FILE_RESOLVED")
  "${command[@]}" "$@"
}

load_mysql_database() {
  local database

  database="$(compose_cmd exec -T mysql sh -ceu 'printf "%s" "$MYSQL_DATABASE"')" \
    || backup_die "Cannot read MYSQL_DATABASE from the running MySQL service"
  database="${database//$'\r'/}"
  [[ -n "$database" ]] || backup_die "The running MySQL service has an empty MYSQL_DATABASE"
  validate_database_name "$database"
  MYSQL_DATABASE="$database"
}

find_backend_container() {
  local output
  local -a containers

  output="$(compose_cmd ps -aq backend)" || backup_die "Cannot query the backend Compose service"
  mapfile -t containers <<< "$output"
  [[ -n "${containers[0]:-}" && ${#containers[@]} -eq 1 ]] || backup_die "Expected exactly one existing backend container"
  printf '%s\n' "${containers[0]}"
}

find_uploads_volume() {
  local backend_container="$1"
  local mount
  local mount_type
  local volume_name

  mount="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/uploads"}}{{printf "%s|%s" .Type .Name}}{{end}}{{end}}' "$backend_container")" \
    || backup_die "Cannot inspect backend uploads mount"
  IFS='|' read -r mount_type volume_name <<< "$mount"
  [[ "$mount_type" == "volume" ]] || backup_die "The backend /app/uploads mount is not a named Docker volume"
  [[ "$volume_name" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || backup_die "Resolved uploads volume name is invalid"
  printf '%s\n' "$volume_name"
}

backend_is_running() {
  [[ "$(docker inspect --format '{{.State.Running}}' "$1")" == "true" ]]
}

ensure_mysql_ready() {
  compose_cmd exec -T mysql sh -ceu \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqladmin ping --user=root --silent' >/dev/null \
    || backup_die "MySQL is not ready or its container credentials are invalid"
}

validate_upload_archive() {
  local archive="$1"
  local work_directory="$2"
  local member
  local normalized
  local listing="$work_directory/uploads-members.txt"
  local verbose_listing="$work_directory/uploads-members-verbose.txt"

  gzip -t "$archive" || backup_die "Uploads archive is not valid gzip data"
  tar -tzf "$archive" > "$listing" || backup_die "Uploads archive cannot be listed"
  tar -tvzf "$archive" > "$verbose_listing" || backup_die "Uploads archive metadata cannot be listed"

  while IFS= read -r member; do
    normalized="${member#./}"
    case "$normalized" in
      "") ;;
      /*|..|../*|*/../*|*/..) backup_die "Uploads archive contains an unsafe path" ;;
    esac
  done < "$listing"

  awk 'NF && substr($1, 1, 1) != "-" && substr($1, 1, 1) != "d" { exit 1 }' "$verbose_listing" \
    || backup_die "Uploads archive contains links or special files"
}

manifest_value() {
  local manifest="$1"
  local key="$2"
  awk -F= -v wanted="$key" '
    $1 == wanted { count += 1; value = substr($0, index($0, "=") + 1) }
    END { if (count != 1) exit 1; print value }
  ' "$manifest"
}

extract_verified_artifact() {
  local artifact="$1"
  local work_directory="$2"
  local expected_database="${3:-}"
  local package="$work_directory/payload.tar"
  local payload="$work_directory/payload"
  local actual_members
  local checksum_members
  local expected_members=$'SHA256SUMS\ndatabase.sql.gz\nmanifest.txt\nuploads.tar.gz'
  local format_version
  local database

  openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -md sha256 \
    -in "$artifact" -out "$package" -pass env:BACKUP_ENCRYPTION_KEY \
    || backup_die "Artifact decryption failed"

  actual_members="$(tar -tf "$package" | LC_ALL=C sort)" || backup_die "Decrypted payload is not a valid tar archive"
  [[ "$actual_members" == "$expected_members" ]] || backup_die "Artifact contains unexpected or missing payload members"

  mkdir -m 700 "$payload"
  tar --no-same-owner --no-same-permissions -xf "$package" -C "$payload" \
    || backup_die "Cannot extract decrypted payload"
  for required in manifest.txt SHA256SUMS database.sql.gz uploads.tar.gz; do
    [[ -f "$payload/$required" && ! -L "$payload/$required" ]] || backup_die "Invalid payload member: $required"
  done

  checksum_members="$(awk '
    NF != 2 || $1 !~ /^[0-9a-fA-F]{64}$/ { exit 1 }
    { print $2 }
  ' "$payload/SHA256SUMS" | LC_ALL=C sort)" || backup_die "Checksum manifest has an invalid format"
  [[ "$checksum_members" == $'database.sql.gz\nmanifest.txt\nuploads.tar.gz' ]] \
    || backup_die "Checksum manifest does not cover exactly the required payload files"
  (cd "$payload" && sha256sum --check --strict --status SHA256SUMS) \
    || backup_die "Artifact checksum verification failed"

  format_version="$(manifest_value "$payload/manifest.txt" format_version)" \
    || backup_die "Manifest format_version is missing or duplicated"
  [[ "$format_version" == "1" ]] || backup_die "Unsupported backup format version: $format_version"
  database="$(manifest_value "$payload/manifest.txt" database)" \
    || backup_die "Manifest database is missing or duplicated"
  validate_database_name "$database"
  if [[ -n "$expected_database" && "$database" != "$expected_database" ]]; then
    backup_die "Artifact database does not match MYSQL_DATABASE"
  fi

  gzip -t "$payload/database.sql.gz" || backup_die "Database dump is not valid gzip data"
  validate_upload_archive "$payload/uploads.tar.gz" "$work_directory"

  VERIFIED_PAYLOAD_DIR="$payload"
  VERIFIED_DATABASE="$database"
}
