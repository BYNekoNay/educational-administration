"""
前端功能全面测试脚本 (Playwright)
管理后台 22 个页面逐一测试：登录、导航、截图、错误检测
"""
import os, sys, json, time
from playwright.sync_api import sync_playwright, Page

BASE_URL = "http://localhost:5173"
SCREENSHOT_DIR = "docs/screenshots"
REPORT_FILE = "docs/frontend_test_report.md"
ADMIN_USER = "admin"
ADMIN_PASS = "123456"

# 需要测试的所有页面（path-based 路由，createWebHistory）
PAGES = [
    ("登录页", "/login", False),
    ("运营看板", "/admin/dashboard", True),
    ("用户管理", "/admin/users", True),
    ("角色管理", "/admin/roles", True),
    ("菜单管理", "/admin/menus", True),
    ("机构配置", "/admin/organization", True),
    ("操作日志", "/admin/logs", True),
    ("公告管理", "/admin/notices", True),
    ("学员管理", "/edu/students", True),
    ("课程管理", "/edu/courses", True),
    ("班级管理", "/edu/classes", True),
    ("报名管理", "/edu/enrollments", True),
    ("大课表", "/edu/big-schedule", True),
    ("排课管理", "/edu/schedules", True),
    ("教室管理", "/edu/classrooms", True),
    ("考勤管理", "/edu/attendances", True),
    ("考级管理", "/edu/exams", True),
    ("收费管理", "/finance/payments", True),
    ("退费管理", "/finance/refunds", True),
    ("课时账户", "/finance/lesson-accounts", True),
    ("课时流水", "/finance/lesson-flows", True),
    ("薪资管理", "/finance/salaries", True),
]

results = []


def login_via_api(page: Page):
    """通过 API 登录并设置 token 到 localStorage（最可靠的 E2E 登录方式）"""
    print("\n>>> 通过 API 登录并注入 token...")
    try:
        # 先访问页面确保 origin 正确
        page.goto(f"{BASE_URL}/login", wait_until="domcontentloaded", timeout=15000)
        page.wait_for_timeout(1500)

        # 调用后端 API 获取 token
        api_result = page.evaluate("""async ({username, password}) => {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({username, password})
            });
            const json = await res.json();
            return json;
        }""", {"username": ADMIN_USER, "password": ADMIN_PASS})

        print(f"  API 响应: code={api_result.get('code')}, msg={api_result.get('message')}")
        if api_result.get("code") != 0:
            print(f"  ❌ 登录失败: {api_result.get('message')}")
            return False

        data = api_result["data"]
        token = data["token"]
        user_id = data["userId"]
        username = data["username"]
        real_name = data["realName"]
        role_code = data["roleCode"]
        permissions = data["permissions"]

        print(f"  ✓ 获取 token: {token[:30]}...")
        print(f"  ✓ 用户: {username} ({real_name}), 角色: {role_code}")
        print(f"  ✓ 权限数: {len(permissions)}")

        # 注入到 localStorage（与 Pinia store 结构一致）
        user_info = {
            "userId": user_id,
            "username": username,
            "realName": real_name,
            "roleCode": role_code,
        }
        # 查看 localStorage 实际 key（从 store 代码确认）
        page.evaluate("""({token, userInfo, permissions}) => {
            localStorage.setItem('token', token);
            localStorage.setItem('userInfo', JSON.stringify(userInfo));
            localStorage.setItem('permissions', JSON.stringify(permissions));
        }""", {"token": token, "userInfo": user_info, "permissions": permissions})

        print("  ✓ token 已写入 localStorage")
        return True

    except Exception as e:
        print(f"  ❌ 异常: {e}")
        return False


def check_page_content(page: Page, name: str):
    """检查页面是否有内容（不是空白页）"""
    issues = []
    body_text = page.inner_text("body").strip()

    if len(body_text) < 20:
        issues.append("页面内容极少，可能为空白页")

    error_texts = ["404", "Not Found", "Internal Server Error"]
    for et in error_texts:
        if et in body_text:
            issues.append(f"页面包含错误信息: {et}")

    # 检查 Vue 错误
    try:
        vue_errors = page.eval_on_selector_all(
            ".el-message--error, .el-alert--error",
            "els => els.map(e => e.textContent)"
        )
        if vue_errors:
            issues.append(f"页面有错误提示: {vue_errors}")
    except:
        pass

    return len(issues) == 0, "; ".join(issues)


def test_page(page: Page, name: str, path: str):
    """测试单个页面"""
    print(f"\n测试: {name} ({path})")

    result = {"name": name, "path": path, "status": "PASS", "issues": [], "screenshot": ""}

    try:
        # 导航
        page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded", timeout=15000)
        page.wait_for_timeout(2500)

        try:
            page.wait_for_load_state("networkidle", timeout=8000)
        except:
            pass

        ok, msg = check_page_content(page, name)
        if not ok:
            result["status"] = "FAIL"
            result["issues"].append(f"内容异常: {msg}")

        # 截图
        safe_name = name.replace("/", "_").replace(" ", "_")
        screenshot_path = f"{SCREENSHOT_DIR}/{safe_name}.png"
        page.screenshot(path=screenshot_path, full_page=False)
        result["screenshot"] = screenshot_path

        # 获取页面标题
        try:
            result["title"] = page.title()
        except:
            result["title"] = "N/A"

        # 统计可见元素
        try:
            visible_buttons = page.locator("button:visible").count()
            visible_tables = page.locator("table:visible").count()
            result["buttons"] = visible_buttons
            result["tables"] = visible_tables
        except:
            pass

        # 获取 body 文本长度
        try:
            body_text = page.inner_text("body")
            result["body_length"] = len(body_text)
        except:
            result["body_length"] = 0

        status_icon = "✅" if result["status"] == "PASS" else "❌"
        print(f"  {status_icon} 标题: {result['title']}, 按钮: {result.get('buttons', 0)}, 表格: {result.get('tables', 0)}")
        if result["issues"]:
            for issue in result["issues"]:
                print(f"  ⚠ {issue}")

    except Exception as e:
        result["status"] = "ERROR"
        result["issues"].append(f"异常: {str(e)[:150]}")
        print(f"  💥 异常: {str(e)[:150]}")

    results.append(result)
    return result


def generate_report():
    """生成测试报告"""
    total = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] in ("FAIL", "ERROR"))

    report = f"""# 管理后台前端功能测试报告

**测试时间**: {time.strftime('%Y-%m-%d %H:%M:%S')}
**测试环境**: Playwright + Chromium (headless)
**测试账号**: {ADMIN_USER}
**被测地址**: {BASE_URL}

## 概览

| 指标 | 数值 |
|------|------|
| 测试页面总数 | {total} |
| ✅ 通过 | {passed} |
| ❌ 失败/异常 | {failed} |
| 通过率 | {passed}/{total} ({100*passed//total if total > 0 else 0}%) |

## 详细结果

| # | 页面 | 路径 | 状态 | 标题 | 按钮 | 表格 | 问题 |
|---|------|------|------|------|------|------|------|
"""

    for i, r in enumerate(results, 1):
        status_icon = {"PASS": "✅", "WARN": "⚠️", "FAIL": "❌", "ERROR": "💥"}.get(r["status"], "❓")
        issues_text = "<br>".join(r["issues"]) if r["issues"] else "-"
        report += f"| {i} | {r['name']} | `{r['path']}` | {status_icon} | {r.get('title','-')[:30]} | {r.get('buttons',0)} | {r.get('tables',0)} | {issues_text} |\n"

    report += "\n## 失败的页面详情\n\n"
    failed_pages = [r for r in results if r["status"] in ("FAIL", "ERROR")]
    if failed_pages:
        for r in failed_pages:
            report += f"### ❌ {r['name']} (`{r['path']}`)\n\n"
            for issue in r["issues"]:
                report += f"- {issue}\n"
            report += f"\n截图: `{r['screenshot']}`\n\n"
    else:
        report += "**全部通过！** ✅\n"

    report += "\n## 截图清单\n\n"
    for r in results:
        report += f"- [{r['name']}]({r['screenshot']}) - {r.get('status', '?')}\n"

    return report


def main():
    os.makedirs(SCREENSHOT_DIR, exist_ok=True)

    print("=" * 70)
    print("  艺培通管理后台 - 前端功能全面测试")
    print("=" * 70)
    print(f"  Base URL: {BASE_URL}")
    print(f"  截图目录: {SCREENSHOT_DIR}")
    print(f"  测试页面: {len(PAGES)} 个")
    print("=" * 70)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": 1920, "height": 1080},
            locale="zh-CN"
        )
        page = context.new_page()

        # 收集控制台错误
        console_errors = []
        page.on("console", lambda msg: console_errors.append(f"{msg.type}: {msg.text}") if msg.type == "error" else None)

        try:
            # 截图登录页（初始状态）
            page.goto(f"{BASE_URL}/login", wait_until="domcontentloaded", timeout=15000)
            page.wait_for_timeout(2000)
            page.screenshot(path=f"{SCREENSHOT_DIR}/00_login.png")
            print("截图: 登录页")

            # Step 1: 通过 API 登录并注入 token
            if not login_via_api(page):
                print("\n❌ 登录失败，无法继续测试")
                browser.close()
                return

            # 验证登录态生效
            page.goto(f"{BASE_URL}/admin/dashboard", wait_until="domcontentloaded", timeout=15000)
            page.wait_for_timeout(3000)
            page.screenshot(path=f"{SCREENSHOT_DIR}/00_dashboard_login_validated.png")
            current_url = page.url
            print(f"\n登录验证 URL: {current_url}")
            if "login" in current_url:
                print("  ⚠ token 注入后仍跳转登录页，权限验证可能有问题")

            # Step 2: 逐个测试页面
            for name, path, _ in PAGES:
                test_page(page, name, path)
                page.wait_for_timeout(400)

            # Step 3: 生成报告
            report = generate_report()
            with open(REPORT_FILE, "w", encoding="utf-8") as f:
                f.write(report)
            print(f"\n📄 测试报告已生成: {REPORT_FILE}")

            # Step 4: 控制台错误汇总
            if console_errors:
                print(f"\n⚠ 控制台错误 ({len(console_errors)} 条):")
                unique_errors = list(set(console_errors))[:10]
                for err in unique_errors:
                    print(f"  - {err[:150]}")

            # Step 5: 打印汇总
            total = len(results)
            passed = sum(1 for r in results if r["status"] == "PASS")
            failed = sum(1 for r in results if r["status"] in ("FAIL", "ERROR"))
            print(f"\n{'='*60}")
            print(f"  测试完成: {passed}/{total} 通过, {failed} 失败")
            print(f"{'='*60}")

        finally:
            browser.close()


if __name__ == "__main__":
    main()
