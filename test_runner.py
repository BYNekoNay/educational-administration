#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""管理端功能实际测试：针对运行中的真实后端(8080)做接口级功能验证。"""
import urllib.request, urllib.error, json, datetime, sys

# 避免环境中的 HTTP 代理拦截对 localhost 的请求
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

BASE = "http://localhost:8080"
ADMIN = ("admin", "123456")

results = []  # (category, method, path, status, biz_code, ok, note)

def req(method, path, token=None, body=None, timeout=20):
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    r = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return -1, str(e)

def biz(raw):
    try:
        j = json.loads(raw)
        return j.get("code"), j.get("message"), j.get("data")
    except Exception:
        return None, None, raw

def extract_list(data):
    """兼容分页对象 {records/list/content} 或直接列表"""
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for k in ("records", "list", "content", "rows"):
            if isinstance(data.get(k), list):
                return data[k]
    return []

def record(category, method, path, status, raw, expect_ok=None, note=""):
    code, msg, data = biz(raw)
    has_data = data is not None and data != [] and data != {} and data != ""
    ok = (status == 200 and code in (0, None)) or (expect_ok is not None and status == expect_ok)
    results.append({
        "category": category, "method": method, "path": path,
        "status": status, "biz_code": code, "has_data": has_data,
        "ok": ok, "note": note or (str(msg)[:60] if msg else "")
    })

# ---------- Phase 1: 登录 ----------
st, raw = req("POST", "/api/auth/login", body={"username": ADMIN[0], "password": ADMIN[1]})
code, msg, data = biz(raw)
admin_token = data.get("token") if data else None
record("认证", "POST", "/api/auth/login", st, raw, note="SUPER_ADMIN" if data and data.get("roleCode")=="SUPER_ADMIN" else "登录失败")
assert admin_token, "管理员登录失败，无法继续"

# profile
st, raw = req("GET", "/api/auth/profile", token=admin_token)
record("认证", "GET", "/api/auth/profile", st, raw)

# ---------- Phase 2: 管理端 GET 清单 ----------
get_inventory = [
    ("用户/权限", "GET", "/api/admin/users"),
    ("用户/权限", "GET", "/api/admin/roles"),
    ("用户/权限", "GET", "/api/admin/permissions"),
    ("用户/权限", "GET", "/api/admin/menus/tree"),
    ("系统", "GET", "/api/admin/organization"),
    ("系统", "GET", "/api/admin/logs"),
    ("系统", "GET", "/api/admin/operation-logs"),
    ("系统", "GET", "/api/admin/dashboard"),
    ("课程", "GET", "/api/edu/courses"),
    ("课程", "GET", "/api/edu/classes"),
    ("学员", "GET", "/api/edu/students"),
    ("教室", "GET", "/api/edu/classrooms"),
    ("报名", "GET", "/api/edu/enrollments"),
    ("排课", "GET", "/api/edu/schedules"),
    ("排课", "GET", "/api/edu/teachers"),
    ("考勤", "GET", "/api/edu/attendances"),
    ("考勤", "GET", "/api/edu/leave-requests"),
    ("考级", "GET", "/api/edu/exams/levels"),
    ("考级", "GET", "/api/edu/exams/signups"),
    ("财务-缴费", "GET", "/api/finance/payments"),
    ("财务-退费", "GET", "/api/finance/refunds"),
    ("财务-课时账户", "GET", "/api/finance/lesson-accounts"),
    ("财务-课时流水", "GET", "/api/finance/lesson-flows"),
    ("财务-薪资规则", "GET", "/api/finance/salary-rules"),
    ("财务-营收统计", "GET", "/api/finance/statistics/revenue"),
    ("薪资", "GET", "/api/finance/salaries"),
    ("薪资", "GET", "/api/finance/salaries/rules"),
    ("通知", "GET", "/api/admin/notices"),
    ("通知", "GET", "/api/notices"),
    ("统计-总览", "GET", "/api/admin/statistics"),
    ("统计-报名", "GET", "/api/admin/statistics/enrollments"),
    ("统计-到课率", "GET", "/api/admin/statistics/attendance-rate"),
    ("统计-课时消耗", "GET", "/api/admin/statistics/lesson-consumption"),
    ("统计-营收", "GET", "/api/admin/statistics/revenue"),
    ("统计-教师工作量", "GET", "/api/admin/statistics/teacher-workload"),
    ("统计-学员流失", "GET", "/api/admin/statistics/student-loss"),
    ("作业", "GET", "/api/edu/homeworks"),
    ("学情", "GET", "/api/edu/learning-records"),
]
for cat, m, p in get_inventory:
    st, raw = req(m, p, token=admin_token)
    record(cat, m, p, st, raw)

# ---------- Phase 3: 角色隔离 ----------
st, raw = req("GET", "/api/admin/users")  # 无 token
record("权限隔离", "GET", "/api/admin/users (无token)", st, raw, expect_ok=401, note="期望401")

# 从用户列表动态取教师/家长账号
st, raw = req("GET", "/api/admin/users", token=admin_token)
_, _, udata = biz(raw)
users = extract_list(udata)
teacher_uname = next((u.get("username") for u in users if u.get("roleCode") == "TEACHER"), "teacher1")
parent_uname = next((u.get("username") for u in users if u.get("roleCode") == "PARENT"), "parent1")

def login_as(uname):
    st, raw = req("POST", "/api/auth/login", body={"username": uname, "password": "123456"})
    _, _, d = biz(raw)
    return (d.get("token") if d else None), raw

teacher_token, _ = login_as(teacher_uname)
if teacher_token:
    st, raw = req("GET", "/api/admin/users", token=teacher_token)
    record("权限隔离", "GET", "/api/admin/users (教师token)", st, raw, expect_ok=403, note="期望403")
    st, raw = req("GET", "/api/admin/dashboard", token=teacher_token)
    record("权限隔离", "GET", "/api/admin/dashboard (教师token)", st, raw, expect_ok=403, note="期望403")
    st, raw = req("GET", "/api/teacher/lessons", token=teacher_token)
    record("权限隔离", "GET", "/api/teacher/lessons (教师token)", st, raw, note="教师自有接口(应200)")
else:
    record("权限隔离", "GET", "/api/admin/users (教师token)", -1, "教师登录失败", note=teacher_uname)

parent_token, _ = login_as(parent_uname)
if parent_token:
    st, raw = req("GET", "/api/admin/users", token=parent_token)
    record("权限隔离", "GET", "/api/admin/users (家长token)", st, raw, expect_ok=403, note="期望403")
    st, raw = req("GET", "/api/parent/students", token=parent_token)
    record("权限隔离", "GET", "/api/parent/students (家长token)", st, raw, note="家长自有接口(应200)")
else:
    record("权限隔离", "GET", "/api/admin/users (家长token)", -1, "家长登录失败", note=parent_uname)

# 已知缺口演示：源码新增的请假模块控制器未打包进运行中的 jar
st, raw = req("GET", "/api/edu/leave-requests", token=admin_token)
record("请假模块(缺口)", "GET", "/api/edu/leave-requests", st, raw, note="源码已有，运行jar缺控制器")
if parent_token:
    st, raw = req("GET", "/api/parent/leave-requests", token=parent_token)
    record("请假模块(缺口)", "GET", "/api/parent/leave-requests", st, raw, note="源码已有，运行jar缺控制器")

# ---------- Phase 4: 教室 CRUD 闭环 ----------
st, raw = req("GET", "/api/edu/classrooms", token=admin_token)
_, _, cdata = biz(raw)
rooms = extract_list(cdata)
created_id = None
if rooms:
    sample = dict(rooms[0])
    for k in ("id", "createTime", "updateTime", "isDeleted", "create_time", "update_time",
              "teacherName", "className"):
        sample.pop(k, None)
    sample["name"] = "AutoTest_Room_" + datetime.datetime.now().strftime("%H%M%S")
    st, raw = req("POST", "/api/edu/classrooms", token=admin_token, body=sample)
    _, _, cpost = biz(raw)
    if isinstance(cpost, dict) and cpost.get("id"):
        created_id = cpost["id"]
        record("教室CRUD", "POST", "/api/edu/classrooms", st, raw, note="创建 id=%s" % created_id)
        st, raw = req("GET", "/api/edu/classrooms/%s" % created_id, token=admin_token)
        record("教室CRUD", "GET", "/api/edu/classrooms/{id}", st, raw, note="读取新建")
        st, raw = req("DELETE", "/api/edu/classrooms/%s" % created_id, token=admin_token)
        record("教室CRUD", "DELETE", "/api/edu/classrooms/{id}", st, raw, note="清理删除")
    else:
        record("教室CRUD", "POST", "/api/edu/classrooms", st, raw, note="创建返回异常: %s" % str(cpost)[:80])
else:
    record("教室CRUD", "POST", "/api/edu/classrooms", -1, "无样本数据", note="classrooms 为空")

# ---------- Phase 5: 导出端点 ----------
for p in ["/api/export/payments", "/api/export/lesson-flows", "/api/export/salaries"]:
    st, raw = req("GET", p, token=admin_token)
    record("导出", "GET", p, st, raw, note="文件/数据流" if st == 200 else "")

# ---------- 汇总 ----------
total = len(results)
passed = sum(1 for r in results if r["ok"])
failed = [r for r in results if not r["ok"]]

print("==== 管理端功能实际测试汇总 ====")
print("测试时间:", datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
print("总计: %d  通过(可达且业务正常): %d  异常: %d" % (total, passed, len(failed)))
print("\n-- 异常项 --")
for r in failed:
    print("  [%s] %s %s -> HTTP %s biz=%s | %s" % (r["category"], r["method"], r["path"], r["status"], r["biz_code"], r["note"]))

# 写 markdown 报告
lines = []
lines.append("# 管理端功能实际测试报告")
lines.append("")
lines.append("> 测试时间：%s  " % datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
lines.append("> 测试对象：运行中真实后端 `http://localhost:8080`（与管理后台调用同一套接口）")
lines.append("> 测试账号：admin / 123456（SUPER_ADMIN）")
lines.append("")
lines.append("## 一、总体结果")
lines.append("")
lines.append("| 指标 | 数值 |")
lines.append("|------|------|")
lines.append("| 用例总数 | %d |" % total)
lines.append("| 通过（接口可达且业务正常） | %d |" % passed)
lines.append("| 异常 | %d |" % len(failed))
lines.append("")
lines.append("## 二、详细结果（按类别）")
lines.append("")
cats = {}
for r in results:
    cats.setdefault(r["category"], []).append(r)
for cat, items in cats.items():
    lines.append("### %s" % cat)
    lines.append("")
    lines.append("| 方法 | 路径 | HTTP | 业务码 | 数据 | 结果 | 说明 |")
    lines.append("|------|------|------|--------|------|------|------|")
    for r in items:
        lines.append("| %s | `%s` | %s | %s | %s | %s | %s |" % (
            r["method"], r["path"], r["status"], r["biz_code"],
            "✅" if r["has_data"] else "—", "✅" if r["ok"] else "❌", r["note"]))
    lines.append("")
if failed:
    lines.append("## 三、异常项清单")
    lines.append("")
    for r in failed:
        lines.append("- [%s] `%s %s` → HTTP %s，业务码 %s，%s" % (r["category"], r["method"], r["path"], r["status"], r["biz_code"], r["note"]))
else:
    lines.append("## 三、异常项清单")
    lines.append("")
    lines.append("无异常，全部接口可达且业务正常。")
lines.append("")
lines.append("*本报告由自动化测试脚本对运行中的真实后端采集生成。*")

with open("test-report-2026-07-14.md", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("\n报告已写入 test-report-2026-07-14.md")
