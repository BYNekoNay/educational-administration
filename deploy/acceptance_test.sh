#!/usr/bin/env bash
#
# Five-role API smoke matrix for the release gate (read-only).
#
# Invoked by deploy/scripts/release-production.sh as the final gate step:
#   bash acceptance_test.sh https://example.org
#
# Contract (documented in deploy/RELEASE_RUNBOOK.md):
#   $1                      base URL of the deployment (no trailing slash)
#   SMOKE_PASSWORD_FILE     mode-600 file holding the smoke account password
#                           (preferred; the password never appears in argv/ps)
#   SMOKE_PASSWORD          fallback when no password file is provided
#   SMOKE_*_USERNAME        per-role username overrides
#
# Design constraints:
#   * READ-ONLY. The gate runs against production, so this script must never
#     create data. deploy/scripts/critical-journey-ci.sh performs a full
#     enrollment -> payment -> refund journey and mutates role permissions;
#     it belongs to throwaway CI environments, never to a release gate.
#   * fails loudly: any unexpected status or business code exits non-zero,
#     which makes the invoking gate fail too.
#
set -euo pipefail
umask 077

BASE_URL="${1:-${SMOKE_BASE_URL:-}}"
BASE_URL="${BASE_URL%/}"
REQUEST_TIMEOUT_SECONDS="${SMOKE_REQUEST_TIMEOUT_SECONDS:-20}"

PYTHON_BIN="$(command -v python3 || command -v python || true)"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

[[ -n "$BASE_URL" ]] || fail "usage: $0 https://example.org"
[[ -n "$PYTHON_BIN" ]] || fail "python is required but was not found on PATH"

if [[ -n "${SMOKE_PASSWORD_FILE:-}" ]]; then
  [[ -f "$SMOKE_PASSWORD_FILE" && ! -L "$SMOKE_PASSWORD_FILE" ]] \
    || fail "SMOKE_PASSWORD_FILE must be an existing regular file: ${SMOKE_PASSWORD_FILE}"
  smoke_password="$(<"$SMOKE_PASSWORD_FILE")"
else
  smoke_password="${SMOKE_PASSWORD:-}"
fi
[[ -n "$smoke_password" ]] \
  || fail "set SMOKE_PASSWORD_FILE or SMOKE_PASSWORD"

ADMIN_USERNAME="${SMOKE_ADMIN_USERNAME:-admin}"
EDU_USERNAME="${SMOKE_EDU_USERNAME:-edu}"
FINANCE_USERNAME="${SMOKE_FINANCE_USERNAME:-finance}"
TEACHER_USERNAME="${SMOKE_TEACHER_USERNAME:-teacher1}"
PARENT_USERNAME="${SMOKE_PARENT_USERNAME:-parent1}"

temporary_directory="$(mktemp -d)"
# 清理临时目录绝不能改变脚本结果：结算所以先记下退出码再清理，并吞掉 rm 的错误。
# （曾出现 rm 因环境策略被拦截导致门禁整体判定失败的情况——临时文件没删干净
#  属于卫生问题，不是发布失败，两者不能混为一谈。）
cleanup() {
  local exit_code=$?
  rm -rf -- "$temporary_directory" >/dev/null 2>&1 || true
  exit "$exit_code"
}
trap cleanup EXIT

password_file="$temporary_directory/smoke-password"
printf '%s' "$smoke_password" > "$password_file"
unset smoke_password SMOKE_PASSWORD

pass_count=0

pass_step() {
  printf '  ok   %s\n' "$1"
  pass_count=$((pass_count + 1))
}

# Prints "<http_status>\n<body>" on stdout for a JSON request.
request() {
  local method="$1" path="$2" token="${3:-}"
  local body_file status
  body_file="$(mktemp "$temporary_directory/body.XXXXXX")"
  local -a auth_header=()
  if [[ -n "$token" ]]; then
    auth_header=(-H "Authorization: Bearer $token")
  fi
  status="$(curl -sS -o "$body_file" -w '%{http_code}' \
    --max-time "$REQUEST_TIMEOUT_SECONDS" \
    -X "$method" "$BASE_URL$path" \
    -H 'Content-Type: application/json' \
    "${auth_header[@]}" \
    ${4:+-d "$4"} || printf '000')"
  printf '%s\n' "$status"
  cat "$body_file"
}

login() {
  local username="$1"
  local response status body token
  response="$(request POST '/api/auth/login' '' "{\"username\":\"$(json_escape "$username")\",\"password\":\"$(json_escape "$(<"$password_file")")\"}")"
  status="$(printf '%s' "$response" | head -n1)"
  body="$(printf '%s' "$response" | tail -n +2)"
  [[ "$status" == "200" ]] || fail "login failed for $username (HTTP $status)"
  token="$(printf '%s' "$body" | "$PYTHON_BIN" -c '
import json, sys
payload = json.load(sys.stdin)
token = ((payload.get("data") or {}).get("token") or "")
if not token:
    sys.exit("login response carried no token")
print(token)
')" || fail "login response for $username was not a successful envelope"
  printf '%s' "$token"
}

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

expect_allowed() {
  local role="$1" token="$2" path="$3"
  local response status body
  response="$(request GET "$path" "$token")"
  status="$(printf '%s' "$response" | head -n1)"
  body="$(printf '%s' "$response" | tail -n +2)"
  [[ "$status" == "200" ]] \
    || fail "$role GET $path returned HTTP $status (expected 200)"
  printf '%s' "$body" | "$PYTHON_BIN" -c '
import json, sys
payload = json.load(sys.stdin)
if payload.get("code") != 0:
    sys.exit("business code %s: %s" % (payload.get("code"), payload.get("message")))
' || fail "$role GET $path returned a non-zero business code"
  pass_step "$role may read $path"
}

expect_forbidden() {
  local role="$1" token="$2" path="$3"
  local response status
  response="$(request GET "$path" "$token")"
  status="$(printf '%s' "$response" | head -n1)"
  [[ "$status" == "403" ]] \
    || fail "$role GET $path returned HTTP $status (expected 403): role isolation is broken"
  pass_step "$role is refused $path"
}

printf 'Five-role smoke matrix against %s\n' "$BASE_URL"

admin_token="$(login "$ADMIN_USERNAME")"
edu_token="$(login "$EDU_USERNAME")"
finance_token="$(login "$FINANCE_USERNAME")"
teacher_token="$(login "$TEACHER_USERNAME")"
parent_token="$(login "$PARENT_USERNAME")"
pass_step 'all five roles obtained a token'

# Positive checks: one representative read per role. Routes and required roles
# mirror the controller annotations.
expect_allowed 'SUPER_ADMIN' "$admin_token"   '/api/admin/statistics'
expect_allowed 'EDU_ADMIN'   "$edu_token"     '/api/edu/enrollments'
expect_allowed 'FINANCE'     "$finance_token" '/api/finance/payments'
expect_allowed 'TEACHER'     "$teacher_token" '/api/teacher/lessons'
expect_allowed 'PARENT'      "$parent_token"  '/api/parent/enrollments'

# Negative checks: the valuable half of a role matrix. A low-privilege role must
# be refused by the interceptor (HTTP 403), not merely receive an empty payload.
expect_forbidden 'PARENT'  "$parent_token"  '/api/finance/payments'
expect_forbidden 'PARENT'  "$parent_token"  '/api/edu/enrollments'
expect_forbidden 'TEACHER' "$teacher_token" '/api/admin/statistics'
expect_forbidden 'TEACHER' "$teacher_token" '/api/finance/payments'

printf 'Acceptance matrix complete: %d checks passed\n' "$pass_count"
