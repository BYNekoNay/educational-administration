#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

BASE_URL="${1:-${JOURNEY_BASE_URL:-http://localhost}}"
BASE_URL="${BASE_URL%/}"
REQUEST_TIMEOUT_SECONDS="${JOURNEY_REQUEST_TIMEOUT_SECONDS:-30}"

for command_name in curl python mktemp rm sed cat; do
  command -v "$command_name" >/dev/null 2>&1 || {
    printf 'ERROR: required command is unavailable: %s\n' "$command_name" >&2
    exit 1
  }
done

if [[ -n "${JOURNEY_PASSWORD_FILE:-}" ]]; then
  [[ -f "$JOURNEY_PASSWORD_FILE" && ! -L "$JOURNEY_PASSWORD_FILE" ]] || {
    printf 'ERROR: JOURNEY_PASSWORD_FILE must be a regular non-symlinked file\n' >&2
    exit 1
  }
  resolved_journey_password="$(<"$JOURNEY_PASSWORD_FILE")"
else
  resolved_journey_password="${JOURNEY_PASSWORD:-${SMOKE_PASSWORD:-}}"
fi
[[ -n "$resolved_journey_password" ]] || {
  printf 'ERROR: set JOURNEY_PASSWORD, SMOKE_PASSWORD, or JOURNEY_PASSWORD_FILE\n' >&2
  exit 1
}

ADMIN_USERNAME="${JOURNEY_ADMIN_USERNAME:-admin}"
EDU_USERNAME="${JOURNEY_EDU_USERNAME:-edu}"
FINANCE_USERNAME="${JOURNEY_FINANCE_USERNAME:-finance}"
TEACHER_USERNAME="${JOURNEY_TEACHER_USERNAME:-teacher1}"
PARENT_USERNAME="${JOURNEY_PARENT_USERNAME:-parent1}"

temporary_directory="$(mktemp -d)"
journey_password_file="$temporary_directory/journey-password"
printf '%s' "$resolved_journey_password" > "$journey_password_file"
unset resolved_journey_password JOURNEY_PASSWORD SMOKE_PASSWORD
request_sequence=0
pass_count=0
admin_token=""
mutated_role_id=""
permissions_mutated=0
original_permissions_file="$temporary_directory/original-permissions.json"

api_json() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local body_file="${4:-}"
  local extra_header="${5:-}"
  local response_file http_status
  local -a curl_args

  request_sequence=$((request_sequence + 1))
  response_file="$temporary_directory/response-$request_sequence.json"
  curl_args=(
    --silent --show-error
    --connect-timeout 5
    --max-time "$REQUEST_TIMEOUT_SECONDS"
    --output "$response_file"
    --write-out '%{http_code}'
    --request "$method"
    --header 'Accept: application/json'
  )
  if [[ -n "$token" ]]; then
    curl_args+=(--header "Authorization: Bearer $token")
  fi
  if [[ -n "$body_file" ]]; then
    curl_args+=(--header 'Content-Type: application/json' --data-binary "@$body_file")
  fi
  if [[ -n "$extra_header" ]]; then
    curl_args+=(--header "$extra_header")
  fi

  if ! http_status="$(curl "${curl_args[@]}" "$BASE_URL$path")"; then
    printf 'ERROR: %s %s could not reach the service\n' "$method" "$path" >&2
    return 1
  fi
  if [[ "$http_status" != "200" ]]; then
    printf 'ERROR: %s %s returned HTTP %s\n' "$method" "$path" "$http_status" >&2
    sed -n '1,20p' "$response_file" >&2
    return 1
  fi
  if ! python - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)
if payload.get("code") != 0:
    print(f"ERROR: API business code {payload.get('code')}: {payload.get('message', '')}", file=sys.stderr)
    raise SystemExit(1)
PY
  then
    printf 'ERROR: %s %s failed its business contract\n' "$method" "$path" >&2
    return 1
  fi
  cat "$response_file"
}

login() {
  local username="$1"
  local password_file response
  password_file="$temporary_directory/login-${username}.json"
  python - "$username" "$journey_password_file" > "$password_file" <<'PY'
import json
import sys

with open(sys.argv[2], encoding="utf-8") as source:
    password = source.read()
json.dump({"username": sys.argv[1], "password": password}, sys.stdout)
PY
  response="$(api_json POST '/api/auth/login' '' "$password_file")"
  printf '%s' "$response" | python -c '
import json, sys
payload = json.load(sys.stdin)
token = (payload.get("data") or {}).get("token")
if not token:
    raise SystemExit("login response did not contain a token")
print(token)
'
}

pass_step() {
  pass_count=$((pass_count + 1))
  printf 'PASS %02d | %s\n' "$pass_count" "$1"
}

restore_permissions() {
  [[ "$permissions_mutated" -eq 1 ]] || return 0
  if ! api_json PUT "/api/admin/roles/$mutated_role_id/permissions" \
      "$admin_token" "$original_permissions_file" >/dev/null; then
    return 1
  fi
  permissions_mutated=0
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  set +e
  if [[ "$permissions_mutated" -eq 1 ]]; then
    if ! restore_permissions; then
      printf 'ERROR: failed to restore the mutated role permissions\n' >&2
      status=1
    fi
  fi
  rm -rf -- "$temporary_directory"
  exit "$status"
}
trap cleanup EXIT INT TERM

printf 'Critical journey target: %s\n' "$BASE_URL"

admin_token="$(login "$ADMIN_USERNAME")"
edu_token="$(login "$EDU_USERNAME")"
finance_token="$(login "$FINANCE_USERNAME")"
teacher_token="$(login "$TEACHER_USERNAME")"
parent_token="$(login "$PARENT_USERNAME")"
pass_step 'five role accounts authenticated'

students_response="$(api_json GET '/api/parent/students' "$parent_token")"
student_id="$(printf '%s' "$students_response" | python -c '
import json, sys
rows = json.load(sys.stdin).get("data") or []
if not rows:
    raise SystemExit("parent account has no bound student")
print(rows[0]["id"])
')"

snapshot_response="$(api_json GET "/api/parent/enrollments/snapshot?studentId=$student_id" "$parent_token")"
candidate="$(printf '%s' "$snapshot_response" | python -c '
import json, sys
payload = json.load(sys.stdin)
snapshot = payload["data"]
for decision in snapshot.get("courses") or []:
    if decision.get("enrolled"):
        continue
    conflicts = {item.get("classId") for item in decision.get("conflicts") or []}
    course = decision.get("course") or {}
    for class_group in decision.get("classes") or []:
        class_id = class_group.get("id")
        maximum = class_group.get("maxStudentCount") or 0
        current = class_group.get("currentStudentCount") or 0
        if class_group.get("status") == 1 and current < maximum and class_id not in conflicts:
            values = (
                snapshot["studentId"], course["id"], class_id,
                course.get("totalLessons") or 1, course.get("price") or 1,
                snapshot["versionToken"],
            )
            print(*values, sep="\t")
            raise SystemExit(0)
raise SystemExit("no conflict-free enrollment candidate is available")
')"
IFS=$'\t' read -r student_id course_id class_id lesson_count course_price snapshot_token <<< "$candidate"
pass_step 'parent enrollment snapshot selected a conflict-free class'

enrollment_body="$temporary_directory/enrollment.json"
python - "$student_id" "$course_id" "$class_id" > "$enrollment_body" <<'PY'
import json
import sys

json.dump({
    "studentId": int(sys.argv[1]),
    "courseId": int(sys.argv[2]),
    "classId": int(sys.argv[3]),
}, sys.stdout)
PY
enrollment_response="$(api_json POST '/api/parent/enrollments' "$parent_token" \
  "$enrollment_body" "If-Match: $snapshot_token")"
enrollment_id="$(printf '%s' "$enrollment_response" | python -c '
import json, sys
data = json.load(sys.stdin)["data"]
if data.get("status") != 1:
    raise SystemExit("new enrollment is not pending approval")
print(data["id"])
')"
pass_step 'parent submitted enrollment with the displayed snapshot version'

audit_response="$(api_json PUT "/api/edu/enrollments/$enrollment_id/audit?status=2&remark=ci-journey-approved" "$edu_token")"
printf '%s' "$audit_response" | python -c '
import json, sys
if json.load(sys.stdin)["data"].get("status") != 2:
    raise SystemExit("academic approval did not move enrollment to pending payment")
'
pass_step 'academic administrator approved the enrollment'

payment_body="$temporary_directory/payment.json"
python - "$enrollment_id" "$lesson_count" "$course_price" > "$payment_body" <<'PY'
import json
import sys

json.dump({
    "enrollmentId": int(sys.argv[1]),
    "lessonCount": float(sys.argv[2]),
    "amount": float(sys.argv[3]),
    "payType": 2,
    "remark": "ci-critical-journey",
}, sys.stdout)
PY
payment_response="$(api_json POST '/api/finance/payments' "$finance_token" "$payment_body")"
printf '%s' "$payment_response" | python -c '
import json, sys
data = json.load(sys.stdin)["data"]
if not data.get("id") or float(data.get("amount") or 0) <= 0:
    raise SystemExit("payment was not persisted")
'
paid_enrollment="$(api_json GET "/api/edu/enrollments/$enrollment_id" "$edu_token")"
printf '%s' "$paid_enrollment" | python -c '
import json, sys
if json.load(sys.stdin)["data"].get("status") != 3:
    raise SystemExit("payment did not complete the enrollment")
'
pass_step 'finance recorded payment and completed the enrollment'

refund_body="$temporary_directory/refund.json"
python - "$enrollment_id" > "$refund_body" <<'PY'
import json
import sys

json.dump({"enrollmentId": int(sys.argv[1])}, sys.stdout)
PY
refund_response="$(api_json POST '/api/parent/refunds' "$parent_token" "$refund_body")"
refund_candidate="$(printf '%s' "$refund_response" | python -c '
import json, sys
data = json.load(sys.stdin)["data"]
if data.get("status") != 1 or float(data.get("amount") or 0) <= 0:
    raise SystemExit("refund request was not created as pending")
print(data["id"], data["amount"], sep="\t")
')"
IFS=$'\t' read -r refund_id refund_amount <<< "$refund_candidate"

refund_audit_body="$temporary_directory/refund-audit.json"
python - "$refund_amount" > "$refund_audit_body" <<'PY'
import json
import sys

json.dump({"status": 2, "refundAmount": float(sys.argv[1])}, sys.stdout)
PY
refund_audit_response="$(api_json PUT "/api/finance/refunds/$refund_id/audit" \
  "$finance_token" "$refund_audit_body")"
printf '%s' "$refund_audit_response" | python -c '
import json, sys
if json.load(sys.stdin)["data"].get("status") != 2:
    raise SystemExit("finance did not approve the refund")
'
pass_step 'parent requested and finance approved a refund'

teacher_lessons="$(api_json GET '/api/teacher/schedules?pageNum=1&pageSize=100' "$teacher_token")"
teacher_adjustments="$(api_json GET '/api/teacher/adjust-requests?pageNum=1&pageSize=100' "$teacher_token")"
candidate_lesson_ids="$temporary_directory/candidate-lessons.txt"
printf '%s' "$teacher_lessons" > "$temporary_directory/teacher-lessons.json"
printf '%s' "$teacher_adjustments" > "$temporary_directory/teacher-adjustments.json"
python - "$candidate_lesson_ids" "$temporary_directory/teacher-lessons.json" \
  "$temporary_directory/teacher-adjustments.json" <<'PY'
import json
import sys

output_path, lessons_path, adjustments_path = sys.argv[1:]
with open(lessons_path, encoding="utf-8") as source:
    lessons_payload = json.load(source)
with open(adjustments_path, encoding="utf-8") as source:
    adjustments_payload = json.load(source)
pending = {
    row.get("lessonId")
    for row in (adjustments_payload.get("data") or {}).get("records") or []
    if row.get("status") == 1
}
ids = [
    row["id"]
    for row in (lessons_payload.get("data") or {}).get("records") or []
    if row.get("status") == 1 and row.get("id") not in pending
]
with open(output_path, "w", encoding="utf-8") as target:
    target.write("\n".join(str(value) for value in ids))
PY

teacher_lesson_id=""
attendance_student_id=""
while IFS= read -r lesson_id; do
  [[ -n "$lesson_id" ]] || continue
  lesson_students="$(api_json GET "/api/teacher/lessons/$lesson_id/students" "$teacher_token")"
  student_candidate="$(printf '%s' "$lesson_students" | python -c '
import json, sys
rows = json.load(sys.stdin).get("data") or []
active = next((row for row in rows if row.get("status") == 1 and row.get("studentId")), None)
print(active["studentId"] if active else "")
')"
  if [[ -n "$student_candidate" ]]; then
    teacher_lesson_id="$lesson_id"
    attendance_student_id="$student_candidate"
    break
  fi
done < "$candidate_lesson_ids"
[[ -n "$teacher_lesson_id" && -n "$attendance_student_id" ]] || {
  printf 'ERROR: no pending teacher lesson with an active student is available\n' >&2
  exit 1
}

attendance_body="$temporary_directory/attendance.json"
python - "$attendance_student_id" > "$attendance_body" <<'PY'
import json
import sys

json.dump([{
    "studentId": int(sys.argv[1]),
    "status": 1,
    "deductLessons": 1,
    "remark": "ci-critical-journey",
}], sys.stdout)
PY
attendance_response="$(api_json POST "/api/teacher/lessons/$teacher_lesson_id/attendances" \
  "$teacher_token" "$attendance_body")"
printf '%s' "$attendance_response" | python -c '
import json, sys
rows = json.load(sys.stdin).get("data") or []
if len(rows) != 1 or rows[0].get("status") != 1:
    raise SystemExit("teacher attendance was not persisted")
'
pass_step 'teacher recorded attendance through the owned lesson boundary'

adjustment_body="$temporary_directory/adjustment.json"
python > "$adjustment_body" <<'PY'
import json
import sys

json.dump({"reason": "ci-critical-journey"}, sys.stdout)
PY
adjustment_response="$(api_json POST "/api/teacher/lessons/$teacher_lesson_id/adjust-requests" \
  "$teacher_token" "$adjustment_body")"
adjustment_id="$(printf '%s' "$adjustment_response" | python -c '
import json, sys
data = json.load(sys.stdin)["data"]
if data.get("status") != 1:
    raise SystemExit("adjustment request was not created as pending")
print(data["id"])
')"
adjustment_audit_response="$(api_json PUT "/api/edu/schedule-adjust-requests/$adjustment_id/audit?status=3&remark=ci-journey-rejected" "$edu_token")"
printf '%s' "$adjustment_audit_response" | python -c '
import json, sys
if json.load(sys.stdin)["data"].get("status") != 3:
    raise SystemExit("academic administrator did not reject the adjustment request")
'
pass_step 'teacher requested a schedule change and academic administration audited it'

roles_response="$(api_json GET '/api/admin/roles' "$admin_token")"
mutated_role_id="$(printf '%s' "$roles_response" | python -c '
import json, sys
roles = json.load(sys.stdin).get("data") or []
role = next((item for item in roles if item.get("roleCode") == "PARENT"), None)
if role is None:
    raise SystemExit("PARENT role was not found")
print(role["id"])
')"
permissions_response="$(api_json GET "/api/admin/roles/$mutated_role_id/permissions" "$admin_token")"
printf '%s' "$permissions_response" | python -c '
import json, sys
json.dump((json.load(sys.stdin).get("data") or {}).get("permissionCodes") or [], sys.stdout)
' > "$original_permissions_file"

all_permissions_response="$(api_json GET '/api/admin/permissions' "$admin_token")"
printf '%s' "$all_permissions_response" > "$temporary_directory/all-permissions-response.json"
mutated_permissions_file="$temporary_directory/mutated-permissions.json"
python - "$original_permissions_file" "$temporary_directory/all-permissions-response.json" \
  "$mutated_permissions_file" <<'PY'
import json
import sys

original_path, available_path, output_path = sys.argv[1:]
with open(original_path, encoding="utf-8") as source:
    original = json.load(source)
with open(available_path, encoding="utf-8") as source:
    available_payload = json.load(source)
available = [
    row.get("permissionCode")
    for row in available_payload.get("data") or []
    if row.get("permissionCode")
]
if original:
    mutated = original[1:]
else:
    candidate = next((code for code in available if code not in original), None)
    if candidate is None:
        raise SystemExit("no permission is available for a reversible mutation")
    mutated = [candidate]
if mutated == original:
    raise SystemExit("permission mutation did not change the role")
with open(output_path, "w", encoding="utf-8") as target:
    json.dump(mutated, target)
PY

api_json PUT "/api/admin/roles/$mutated_role_id/permissions" \
  "$admin_token" "$mutated_permissions_file" >/dev/null
permissions_mutated=1
mutated_permissions_response="$(api_json GET "/api/admin/roles/$mutated_role_id/permissions" "$admin_token")"
printf '%s' "$mutated_permissions_response" > "$temporary_directory/mutated-permissions-response.json"
python - "$mutated_permissions_file" "$temporary_directory/mutated-permissions-response.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    expected = set(json.load(source))
with open(sys.argv[2], encoding="utf-8") as source:
    actual = set((json.load(source).get("data") or {}).get("permissionCodes") or [])
if actual != expected:
    raise SystemExit("role permission mutation was not persisted")
PY

restore_permissions
restored_permissions_response="$(api_json GET "/api/admin/roles/$mutated_role_id/permissions" "$admin_token")"
printf '%s' "$restored_permissions_response" > "$temporary_directory/restored-permissions-response.json"
python - "$original_permissions_file" "$temporary_directory/restored-permissions-response.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    expected = set(json.load(source))
with open(sys.argv[2], encoding="utf-8") as source:
    actual = set((json.load(source).get("data") or {}).get("permissionCodes") or [])
if actual != expected:
    raise SystemExit("role permissions were not restored")
PY
pass_step 'administrator changed and restored role permissions'

printf 'Critical journey complete: %d/%d steps passed\n' "$pass_count" "$pass_count"
