#!/bin/bash
# 五角色权限验收脚本 v1.0
# 用法: bash acceptance_test.sh [BASE_URL]
# 默认 BASE_URL=http://localhost:8080

BASE="${1:-http://localhost:8080}"
FAIL=0
PASS=0
TOTAL=0

# 动态登录获取 token
login() {
  curl -s "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python -c "import sys,json;d=json.load(sys.stdin);print(d['data']['token'])"
}

echo "========== 五角色权限验收矩阵 =========="
echo "BASE: $BASE"
echo ""

# 获取所有角色token
TA=$(login "admin" "123456")
TE=$(login "edu" "123456")
TF=$(login "finance" "123456")
TT=$(login "teacher1" "123456")
TP=$(login "parent1" "123456")

check() {
  local RESPONSE BODY CODE BUSINESS_CODE
  TOTAL=$((TOTAL + 1))
  RESPONSE=$(curl -s -w $'\n%{http_code}' -X "$1" "$BASE$2" -H "Authorization: Bearer $3")
  CODE=${RESPONSE##*$'\n'}
  BODY=${RESPONSE%$'\n'*}
  BUSINESS_CODE=$(printf '%s' "$BODY" | python -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || true)
  if [ "$CODE" = "200" ] && [ "$BUSINESS_CODE" = "0" ]; then
    PASS=$((PASS + 1))
    echo "  PASS  HTTP $CODE / code $BUSINESS_CODE | $4"
  else
    echo "  FAIL  HTTP $CODE / code ${BUSINESS_CODE:-invalid-json} (expected 200 / 0) | $4"
    FAIL=1
  fi
}

check_http() {
  local CODE
  TOTAL=$((TOTAL + 1))
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X "$1" "$BASE$2" -H "Authorization: Bearer $3")
  if [ "$CODE" = "200" ]; then
    PASS=$((PASS + 1))
    echo "  PASS  HTTP $CODE | $4"
  else
    echo "  FAIL  HTTP $CODE (expected 200) | $4"
    FAIL=1
  fi
}

check_status() {
  local CODE
  TOTAL=$((TOTAL + 1))
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X "$1" "$BASE$2" -H "Authorization: Bearer $3")
  if [ "$CODE" = "$4" ]; then
    PASS=$((PASS + 1))
    echo "  PASS  HTTP $CODE (expected $4) | $5"
  else
    echo "  FAIL  HTTP $CODE (expected $4) | $5"
    FAIL=1
  fi
}

check_status_no_auth() {
  local CODE
  TOTAL=$((TOTAL + 1))
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X "$1" "$BASE$2")
  if [ "$CODE" = "$3" ]; then
    PASS=$((PASS + 1))
    echo "  PASS  HTTP $CODE (expected $3) | $4"
  else
    echo "  FAIL  HTTP $CODE (expected $3) | $4"
    FAIL=1
  fi
}

echo "--- 超级管理员 (admin) ---"
check "GET" "/api/edu/students?pageNum=1" "$TA" "学员管理"
check "GET" "/api/admin/users?pageNum=1" "$TA" "用户管理"
check "GET" "/api/admin/roles" "$TA" "角色管理"
check "GET" "/api/admin/menus/tree" "$TA" "菜单管理"
check "GET" "/api/admin/permissions" "$TA" "权限管理"
check "GET" "/api/admin/dashboard" "$TA" "运营看板"
check "GET" "/api/admin/statistics/teacher-workload" "$TA" "教师工作量统计"
check "GET" "/api/admin/statistics/student-loss" "$TA" "学员流失率统计"
check "GET" "/api/admin/statistics/class-activity" "$TA" "班级活跃度统计"
check "GET" "/api/admin/statistics/course-profit" "$TA" "课程盈利统计"
check "GET" "/api/admin/statistics/payment-rate" "$TA" "收费率统计"
check_http "GET" "/api/export/payments" "$TA" "鉴权报表导出"

echo ""
echo "--- 教务管理员 (edu) ---"
check "GET" "/api/edu/students?pageNum=1" "$TE" "学员管理"
check "GET" "/api/edu/classes?pageNum=1" "$TE" "班级管理"
check "GET" "/api/edu/courses?pageNum=1" "$TE" "课程管理"
check "GET" "/api/edu/enrollments?pageNum=1" "$TE" "报名管理"
check "GET" "/api/edu/schedules?pageNum=1" "$TE" "排课管理"
check "GET" "/api/edu/classrooms?pageNum=1" "$TE" "教室管理"

echo ""
echo "--- 财务管理员 (finance) ---"
check "GET" "/api/finance/payments?pageNum=1" "$TF" "收费记录"
check "GET" "/api/finance/refunds?pageNum=1" "$TF" "退费记录"
check "GET" "/api/finance/salaries?pageNum=1" "$TF" "薪资列表"

echo ""
echo "--- 教师 (teacher1) ---"
check "GET" "/api/teacher/statistics" "$TT" "课时统计"
check "GET" "/api/teacher/schedules" "$TT" "我的课表"
check "GET" "/api/teacher/adjust-requests" "$TT" "调课申请列表"
check "GET" "/api/notifications?pageNum=1&pageSize=10" "$TT" "教师个人通知"

echo ""
echo "--- 家长 (parent1) ---"
check "GET" "/api/parent/students" "$TP" "我的孩子"
check "GET" "/api/parent/courses" "$TP" "浏览课程"
check "GET" "/api/parent/payments" "$TP" "缴费记录"
check "GET" "/api/parent/refunds/available" "$TP" "可退费(新)"
check "GET" "/api/parent/notices" "$TP" "家长机构公告"
check "GET" "/api/notifications?pageNum=1&pageSize=10" "$TP" "家长个人通知"
SP=$(curl -s "$BASE/api/parent/students" -H "Authorization: Bearer $TP" | python -c "import sys,json; d=json.load(sys.stdin); rows=d.get('data') or []; print(rows[0]['id'] if rows else '')")
if [ -n "$SP" ]; then
  check "GET" "/api/parent/students/$SP/homeworks" "$TP" "家长全部作业"
else
  TOTAL=$((TOTAL + 1))
  FAIL=1
  echo "  FAIL  parent1 未绑定学员，无法验收家长全部作业"
fi

echo ""
echo "--- 越权拒绝测试 ---"
check_status_no_auth "GET" "/api/files/00000000000000000000000000000000.pdf" "401" "匿名→附件读取"
check_status "GET" "/api/admin/users?pageNum=1" "$TT" "403" "教师→用户管理"
check_status "GET" "/api/admin/users?pageNum=1" "$TP" "403" "家长→用户管理"
check_status "GET" "/api/finance/salaries?pageNum=1" "$TP" "403" "家长→薪资管理"
check_status "GET" "/api/finance/salaries?pageNum=1" "$TT" "403" "教师→薪资管理"
check_status "GET" "/api/admin/roles" "$TE" "403" "教务→角色管理"

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "========== 全部 $PASS/$TOTAL 通过 =========="
  exit 0
else
  echo "========== 存在失败项 =========="
  exit 1
fi
