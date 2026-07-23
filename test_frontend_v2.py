"""
前端功能全面测试脚本 (Playwright) - 错误检测增强版
管理后台 22 个页面逐一测试：登录、导航、截图、错误检测、网络监控
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
    ("运营看板", "/admin/dashboard"),
    ("用户管理", "/admin/users"),
    ("角色管理", "/admin/roles"),
    ("菜单管理", "/admin/menus"),
    ("机构配置", "/admin/organization"),
    ("操作日志", "/admin/logs"),
    ("公告管理", "/admin/notices"),
    ("学员管理", "/edu/students"),
    ("课程管理", "/edu/courses"),
    ("班级管理", "/edu/classes"),
    ("报名管理", "/edu/enrollments"),
    ("大课表", "/edu/big-schedule"),
    ("排课管理", "/edu/schedules"),
    ("教室管理", "/edu/classrooms"),
    ("考勤管理", "/edu/attendances"),
    ("考级管理", "/edu/exams"),
    ("收费管理", "/finance/payments"),
    ("退费管理", "/finance/refunds"),
    ("课时账户", "/finance/lesson-accounts"),
    ("课时流水", "/finance/lesson-flows"),
    ("薪资管理", "/finance/salaries"),
]

results = []
all_network_errors = []  # 收集所有网络错误


def login_via_api(page: Page):
    """通过 API 登录并设置 token"""
    print("\n>>> 通过 API 登录...")
    page.goto(f"{BASE_URL}/login", wait_until="domcontentloaded", timeout=15000)
    page.wait_for_timeout(1500)

    api_result = page.evaluate("""async ({username, password}) => {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({username, password})
        });
        return await res.json();
    }""", {"username": ADMIN_USER, "password": ADMIN_PASS})

    if api_result.get("code") != 0:
        print(f"  ❌ 登录失败: {api_result.get('message')}")
        return False

    data = api_result["data"]
    user_info = {
        "userId": data["userId"],
        "username": data["username"],
        "realName": data["realName"],
        "roleCode": data["roleCode"],
    }
    page.evaluate("""({token, userInfo, permissions}) => {
        localStorage.setItem('token', token);
        localStorage.setItem('userInfo', JSON.stringify(userInfo));
        localStorage.setItem('permissions', JSON.stringify(permissions));
    }""", {"token": data["token"], "userInfo": user_info, "permissions": data["permissions"]})

    print(f"  ✓ 登录成功: {data['realName']} ({data['roleCode']}), 权限 {len(data['permissions'])} 个")
    return True


def test_page(page: Page, name: str, path: str):
    """测试单个页面，捕获网络错误"""
    print(f"\n测试: {name} ({path})")

    result = {"name": name, "path": path, "status": "PASS", "issues": [], "screenshot": ""}
    page_errors = []
    api_errors = []

    # 监听网络响应
    def on_response(response):
        if '/api/' in response.url:
            if response.status >= 400:
                api_errors.append(f"{response.status} {response.url.split('/api/')[-1]}")

    def on_console(msg):
        if msg.type == "error":
            page_errors.append(msg.text[:100])

    page.on("response", on_response)
    page.on("console", on_console)

    try:
        # 导航
        page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded", timeout=15000)
        page.wait_for_timeout(3000)  # 等待所有 API 完成

        # 截图
        safe_name = name.replace("/", "_").replace(" ", "_")
        screenshot_path = f"{SCREENSHOT_DIR}/{safe_name}.png"
        page.screenshot(path=screenshot_path, full_page=False)
        result["screenshot"] = screenshot_path

        # 获取页面标题和内容
        result["title"] = page.title()
        body_text = page.inner_text("body")

        # 检查页面错误
        if api_errors:
            result["status"] = "FAIL"
            result["issues"].append(f"API 错误 {len(api_errors)} 个:")
            for err in api_errors[:5]:
                result["issues"].append(f"  - {err}")
            all_network_errors.extend([(name, err) for err in api_errors])

        # 检查 el-message 错误
        try:
            error_messages = page.eval_on_selector_all(
                ".el-message--error",
                "els => els.map(e => e.textContent).filter(t => t)"
            )
            if error_messages:
                if result["status"] == "PASS":
                    result["status"] = "WARN"
                result["issues"].append(f"页面错误提示: {error_messages[:3]}")
        except:
            pass

        # 元素统计
        try:
            result["buttons"] = page.locator("button:visible").count()
            result["tables"] = page.locator("table:visible").count()
            result["body_length"] = len(body_text)
        except:
            pass

        status_icon = {"PASS": "✅", "WARN": "⚠️", "FAIL": "❌"}.get(result["status"], "❓")
        api_count = len(api_errors)
        console_count = len(page_errors)
        print(f"  {status_icon} 标题={result.get('title','-')[:20]} 按钮={result.get('buttons',0)} 表格={result.get('tables',0)} API错误={api_count}")

        if result["issues"]:
            for issue in result["issues"][:3]:
                print(f"    ⚠ {issue}")

    except Exception as e:
        result["status"] = "ERROR"
        result["issues"].append(f"异常: {str(e)[:150]}")
        print(f"  💥 {str(e)[:150]}")

    finally:
        page.remove_listener("response", on_response)
        page.remove_listener("console", on_console)

    results.append(result)
    return result


def generate_report():
    """生成测试报告"""
    total = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    warned = sum(1 for r in results if r["status"] == "WARN")
    failed = sum(1 for r in results if r["status"] in ("FAIL", "ERROR"))

    report = f"""# 管理后台前端功能测试报告（错误检测版）

**测试时间**: {time.strftime('%Y-%m-%d %H:%M:%S')}
**测试环境**: Playwright + Chromium (headless)
**测试账号**: {ADMIN_USER}
**被测地址**: {BASE_URL}

## 概览

| 指标 | 数值 |
|------|------|
| 测试页面总数 | {total} |
| ✅ 通过 | {passed} |
| ⚠️ 警告 | {warned} |
| ❌ 失败/异常 | {failed} |
| 通过率 | {passed}/{total} ({100*passed//total if total > 0 else 0}%) |

## 详细结果

| # | 页面 | 路径 | 状态 | 标题 | 按钮 | 表格 | API错误 | 问题 |
|---|------|------|------|------|------|------|---------|------|
"""

    for i, r in enumerate(results, 1):
        status_icon = {"PASS": "✅", "WARN": "⚠️", "FAIL": "❌", "ERROR": "💥"}.get(r["status"], "❓")
        api_err_count = sum(1 for _, e in all_network_errors if _ == r["name"])
        issues_text = "<br>".join(r["issues"][:2]) if r["issues"] else "-"
        report += f"| {i} | {r['name']} | `{r['path']}` | {status_icon} | {r.get('title','-')[:25]} | {r.get('buttons',0)} | {r.get('tables',0)} | {api_err_count} | {issues_text} |\n"

    report += "\n## ❌ 失败/异常页面详情\n\n"
    failed_pages = [r for r in results if r["status"] in ("FAIL", "ERROR")]
    if failed_pages:
        for r in failed_pages:
            report += f"### {r['name']} (`{r['path']}`)\n\n"
            for issue in r["issues"]:
                report += f"- {issue}\n"
            report += f"\n截图: `{r['screenshot']}`\n\n"
    else:
        report += "**全部通过！** ✅\n"

    # API 错误汇总
    if all_network_errors:
        report += "\n## 🔌 API 错误汇总\n\n"
        report += "| 页面 | 错误 API |\n|------|----------|\n"
        seen = set()
        for page_name, err in all_network_errors:
            key = (page_name, err)
            if key in seen:
                continue
            seen.add(key)
            report += f"| {page_name} | `{err}` |\n"

    report += "\n## 截图清单\n\n"
    for r in results:
        report += f"- [{r['name']}]({r['screenshot']}) - {r.get('status', '?')}\n"

    return report


def main():
    os.makedirs(SCREENSHOT_DIR, exist_ok=True)

    print("=" * 70)
    print("  艺培通管理后台 - 前端功能全面测试 v2")
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

        try:
            if not login_via_api(page):
                print("\n❌ 登录失败")
                return

            # 验证登录态
            page.goto(f"{BASE_URL}/admin/dashboard", wait_until="domcontentloaded", timeout=15000)
            page.wait_for_timeout(2500)
            page.screenshot(path=f"{SCREENSHOT_DIR}/00_dashboard.png")
            print(f"\n登录验证: 当前 URL = {page.url}")

            # 逐个测试
            for name, path in PAGES:
                test_page(page, name, path)
                page.wait_for_timeout(300)

            # 报告
            report = generate_report()
            with open(REPORT_FILE, "w", encoding="utf-8") as f:
                f.write(report)
            print(f"\n📄 报告: {REPORT_FILE}")

            # 汇总
            total = len(results)
            passed = sum(1 for r in results if r["status"] == "PASS")
            warned = sum(1 for r in results if r["status"] == "WARN")
            failed = sum(1 for r in results if r["status"] in ("FAIL", "ERROR"))
            print(f"\n{'='*60}")
            print(f"  ✅ 通过: {passed} | ⚠️ 警告: {warned} | ❌ 失败: {failed} / {total}")
            print(f"{'='*60}")

        finally:
            browser.close()


if __name__ == "__main__":
    main()
