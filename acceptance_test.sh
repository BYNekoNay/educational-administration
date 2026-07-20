#!/bin/bash
# 五角色权限验收脚本 v1.0
# 用法: bash acceptance_test.sh [BASE_URL]
# 默认 BASE_URL=http://localhost:8080

BASE="${1:-http://localhost:8080}"
FAIL=0

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
  local CODE
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X "$1" "$BASE$2" -H "Authorization: Bearer $3")
  if [ "$CODE" = "200" ]; then
    echo "  PASS  HTTP $CODE | $4"
  else
    echo "  FAIL  HTTP $CODE (expected 200) | $4"
    FAIL=1
  fi
}

check_status() {
  local CODE
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X "$1" "$BASE$2" -H "Authorization: Bearer $3")
  if [ "$CODE" = "$4" ]; then
    echo "  PASS  HTTP $CODE (expected $4) | $5"
  else
    echo "  FAIL  HTTP $CODE (expected $4) | $5"
    FAIL=1
  fi
}

echo "--- 超级管理员 (admin) ---"
check "GET" "/api/edu/students?pageNum=1" "$TA" "学员管理"
check "GET" "/api/admin/users?pageNum=1" "$TA" "用户管理"
check "GET" "/api/admin/roles" "$TA" "角色管理"
check "GET" "/api/admin/menus" "$TA" "菜单管理"
check "GET" "/api/admin/permissions" "$TA" "权限管理"
check "GET" "/api/statistics/dashboard" "$TA" "运营看板"

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

echo ""
echo "--- 家长 (parent1) ---"
check "GET" "/api/parent/students" "$TP" "我的孩子"
check "GET" "/api/parent/courses" "$TP" "浏览课程"
check "GET" "/api/parent/payments" "$TP" "缴费记录"
check "GET" "/api/parent/refunds/available" "$TP" "可退费(新)"

echo ""
echo "--- 越权拒绝测试 ---"
check_status "GET" "/api/admin/users?pageNum=1" "$TT" "403" "教师→用户管理"
check_status "GET" "/api/admin/users?pageNum=1" "$TP" "403" "家长→用户管理"
check_status "GET" "/api/finance/salaries?pageNum=1" "$TP" "403" "家长→薪资管理"
check_status "GET" "/api/finance/salaries?pageNum=1" "$TT" "403" "教师→薪资管理"
check_status "GET" "/api/admin/roles" "$TE" "403" "教务→角色管理"

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "========== 全部 26/26 通过 =========="
  exit 0
else
  echo "========== 存在失败项 =========="
  exit 1
fi
