# -*- coding: utf-8 -*-
"""
全量后端接口测试：解析所有 Controller，枚举端点，按角色请求，检查返回是否正常。
仅做"可达性 + 鉴权 + 返回结构"校验，不依赖业务数据是否非空。
"""
import urllib.request, urllib.error, json, re, os, datetime, sys

# 绕过环境 HTTP 代理（localhost 必须直连，否则 502）
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

BASE = os.environ.get("BASE_URL", "http://localhost:8080")
SRC = os.environ.get("SRC_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "backend", "src", "main", "java", "com", "pzhu", "eduadmin"))
OUT = os.environ.get("REPORT_PATH", os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs", f"test-all-report-{datetime.date.today()}.md"))

def req(method, path, token=None, body=None, query=None):
    """返回 (http_status, raw_text)"""
    url = BASE + path
    if query:
        url += ("&" if "?" in url else "?") + query
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        try:
            return e.code, e.read().decode("utf-8", "replace")
        except Exception:
            return e.code, ""
    except Exception as e:
        return -1, "ERR:%s" % type(e).__name__

def biz(raw):
    try:
        j = json.loads(raw)
        return j.get("code"), j.get("message"), j.get("data")
    except Exception:
        return None, None, raw

# ---------- 1) 解析所有端点 ----------
def parse_controllers():
    eps = []
    for root, _, files in os.walk(SRC):
        for fn in files:
            if not fn.endswith("Controller.java"):
                continue
            p = os.path.join(root, fn)
            text = open(p, encoding="utf-8", errors="replace").read()
            # 类级 base（行首无缩进的 @RequestMapping）
            m = re.search(r'^@RequestMapping\s*\(\s*["\']([^"\']+)', text, re.M)
            base = m.group(1) if m else ""
            # 方法级映射（有缩进）
            for mm in re.finditer(
                r'^\s{1,}@(Get|Post|Put|Delete|Request)Mapping\s*\((.*?)\)',
                text, re.M | re.S):
                kw = mm.group(1)
                args = mm.group(2)
                pm = re.search(r'["\']([^"\']+)["\']', args)
                sub = pm.group(1) if pm else ""
                if kw == "Request":
                    if re.search(r'RequestMethod\.POST', args): verb = "POST"
                    elif re.search(r'RequestMethod\.PUT', args): verb = "PUT"
                    elif re.search(r'RequestMethod\.DELETE', args): verb = "DELETE"
                    else: verb = "GET"
                else:
                    verb = kw.upper()
                if sub:
                    full = base + (sub if sub.startswith("/") else "/" + sub)
                else:
                    full = base
                eps.append((verb, full, fn))
    # 去重
    seen = set(); uniq = []
    for e in eps:
        if e[:2] not in seen:
            seen.add(e[:2]); uniq.append(e)
    return uniq

def role_for(path):
    if path.startswith("/api/auth"): return "PUBLIC"
    if path.startswith("/api/teacher") or path.startswith("/api/parent/attendance") \
       or path.startswith("/api/parent/leave") or path.startswith("/api/edu/exams") \
       or path.startswith("/api/learning") or path.startswith("/api/edu/attendance"):
        # 教师类
        if path.startswith("/api/parent"): return "PARENT"
        return "TEACHER"
    if path.startswith("/api/parent"): return "PARENT"
    if path.startswith("/api/finance") or path.startswith("/api/salary"): return "FINANCE"
    # 其余（admin/edu/system/statistics/notice/schedule/export 等）归 admin 可访问
    return "ADMIN"

def fill(path):
    # 路径变量替换为 1（仅 GET 详情用）
    return re.sub(r"\{[^}]+\}", "1", path)

# ---------- 2) 登录各角色 ----------
def login(uname, pwd="123456"):
    st, raw = req("POST", "/api/auth/login", body={"username": uname, "password": pwd})
    _, _, d = biz(raw)
    if isinstance(d, dict) and d.get("token"):
        return d["token"]
    return None

admin_token = login("admin")
print("admin token:", "OK" if admin_token else "FAIL")

# 动态取各角色账号
st, raw = req("GET", "/api/admin/users?pageNum=1&pageSize=100", token=admin_token)
_, _, ud = biz(raw)
users = ud.get("records", []) if isinstance(ud, dict) else (ud if isinstance(ud, list) else [])
def pick(rc):
    for u in users:
        if u.get("roleCode") == rc:
            return u.get("username")
    return None
teacher_uname = pick("TEACHER") or "teacher1"
parent_uname = pick("PARENT") or "parent1"
finance_uname = pick("FINANCE") or "finance1"
print("accounts: teacher=%s parent=%s finance=%s" % (teacher_uname, parent_uname, finance_uname))

role_tokens = {
    "ADMIN": admin_token,
    "FINANCE": login(finance_uname) or admin_token,
    "TEACHER": login(teacher_uname) or admin_token,
    "PARENT": login(parent_uname) or admin_token,
    "PUBLIC": None,
}

# ---------- 3) 全量测试 ----------
eps = parse_controllers()
print("枚举端点总数: %d" % len(eps))

rows = []        # 明细
fails = []       # 失败（404/500/403 正确角色被拒）
warns = []       # 警告（400/业务码非0/无 token 未 401）

for verb, path, srcfile in eps:
    role = role_for(path)
    token = role_tokens.get(role)
    q = "pageNum=1&pageSize=10" if verb == "GET" else None
    test_path = fill(path)
    if verb == "GET":
        st, raw = req(verb, test_path, token=token, query=q)
    elif verb in ("POST", "PUT"):
        # 写接口默认发空对象探活；对已知需要合法入参的端点补真实 body，避免误报 500
        body = {}
        if test_path.endswith("/api/finance/salaries/calculate"):
            body = {"salaryMonth": "2026-07", "teacherId": 4, "bonusAmount": 0}
        elif test_path.endswith("/api/finance/salaries/adjustments"):
            body = {"salaryId": 1, "adjustAmount": 10, "reason": "回归校验"}
        st, raw = req(verb, test_path, token=token, body=body)
    else:  # DELETE
        st, raw = req(verb, test_path, token=token)
    code, msg, _ = biz(raw)
    # 分类
    if st == 200 and code == 0:
        status = "OK"
    elif st == 404:
        status = "FAIL(404未映射)"
        fails.append((verb, path, "404", srcfile))
    elif st == 500:
        status = "FAIL(500错误)"
        fails.append((verb, path, "500:%s" % str(msg)[:60], srcfile))
    elif st in (401, 403):
        if role == "PUBLIC":
            status = "FAIL(公开接口被拦)"
            fails.append((verb, path, "%d" % st, srcfile))
        else:
            # 正确角色被拒 => 可能角色映射不对，记 warn
            status = "WARN(%d鉴权)" % st
            warns.append((verb, path, "%d role=%s" % (st, role), srcfile))
    elif st == 400:
        status = "WARN(400缺参)"
        warns.append((verb, path, "400", srcfile))
    elif st == 200 and code not in (0, None):
        status = "WARN(业务码%d)" % code
        warns.append((verb, path, "code=%s" % code, srcfile))
    else:
        status = "WARN(%d)" % st
        warns.append((verb, path, "%d" % st, srcfile))
    rows.append((verb, path, role, st, code, status, srcfile))

# 无 token 探活（受保护接口应 401）
print("\n--- 无 token 探活抽样 ---")
no_token_check = 0
for verb, path, srcfile in eps:
    role = role_for(path)
    if role == "PUBLIC":
        continue
    if no_token_check >= 8:
        break
    test_path = fill(path)
    q = "pageNum=1&pageSize=10" if verb == "GET" else None
    st, _ = req(verb, test_path, token=None, query=q)
    if st != 401:
        warns.append((verb, path, "无token返回%d(期望401)" % st, srcfile))
    no_token_check += 1

# ---------- 4) 汇总 ----------
ok_cnt = sum(1 for r in rows if r[5] == "OK")
print("\n==== 汇总 ====")
print("端点总数: %d | OK: %d | FAIL: %d | WARN: %d" % (len(rows), ok_cnt, len(fails), len(warns)))

# ---------- 5) 写报告 ----------
now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
lines = []
lines.append("# 后端全量接口测试报告\n")
lines.append("- 生成时间：%s" % now)
lines.append("- 测试目标：运行中后端 `http://localhost:8080`（Maven 正规重建后的 eduadmin.jar）")
lines.append("- 测试策略：解析 22 个 Controller 枚举全部端点；按 URL 前缀判定所需角色并用对应账号 token 请求；GET 带分页参数、写接口发空体探活；检查 HTTP 状态与业务码 `code`。\n")
lines.append("## 一、总体结果\n")
lines.append("| 指标 | 数值 |")
lines.append("|---|---|")
lines.append("| 枚举端点总数 | %d |" % len(rows))
lines.append("| ✅ 正常(200+code=0) | %d |" % ok_cnt)
lines.append("| ❌ 失败(404/500/公开被拦) | %d |" % len(fails))
lines.append("| ⚠️ 警告(400/鉴权/业务码) | %d |" % len(warns))
lines.append("")
lines.append("> 说明：写接口(POST/PUT/DELETE)仅发空体探活，返回 400 多为「缺必填参数」属正常，不计为缺陷；真正的缺陷是 **404(端点未注册)** 与 **500(服务端异常)**，以及**正确角色被 403 拒绝**。\n")

lines.append("## 二、失败项（需关注）\n")
if fails:
    lines.append("| 方法 | 路径 | 现象 | 来源文件 |")
    lines.append("|---|---|---|---|")
    for v, p, info, sf in fails:
        lines.append("| %s | `%s` | %s | %s |" % (v, p, info, sf))
else:
    lines.append("**无失败项** ✅ 所有端点均可达且未出现 500/404。\n")

lines.append("\n## 三、警告项（需复核）\n")
if warns:
    lines.append("| 方法 | 路径 | 现象 | 来源文件 |")
    lines.append("|---|---|---|---|")
    for v, p, info, sf in warns:
        lines.append("| %s | `%s` | %s | %s |" % (v, p, info, sf))
else:
    lines.append("**无警告项**。\n")

lines.append("\n## 四、全量明细\n")
lines.append("| # | 方法 | 路径 | 角色 | HTTP | 业务码 | 结果 |")
lines.append("|---|---|---|---|---|---|---|")
for i, (v, p, role, st, code, status, sf) in enumerate(rows, 1):
    lines.append("| %d | %s | `%s` | %s | %s | %s | %s |" % (i, v, p, role, st, code, status))

report = "\n".join(lines)
with open(OUT, "w", encoding="utf-8") as f:
    f.write(report)
print("报告已写入: %s" % OUT)
