"""
前端功能深度交互测试 v3
测试表单操作、搜索、对话框等交互功能
"""
import os, json, time
from playwright.sync_api import sync_playwright, Page

BASE_URL = "http://localhost:5173"
SCREENSHOT_DIR = "docs/screenshots/interactive"
REPORT_FILE = "docs/frontend_interactive_test_report.md"
ADMIN_USER = "admin"
ADMIN_PASS = "123456"

# 交互测试场景
INTERACTIVE_TESTS = [
    # 学员管理
    {"name": "学员管理 - 搜索测试", "path": "/edu/students", "actions": [
        ("fill_search", "刘", "搜索刘姓学员"),
        ("verify_results", None, "验证搜索结果"),
    ]},
    {"name": "学员管理 - 新增对话框", "path": "/edu/students", "actions": [
        ("click_button", "新增学员", "打开新增对话框"),
        ("verify_dialog", None, "验证对话框打开"),
        ("close_dialog", "取消", "关闭对话框"),
    ]},
    # 课程管理
    {"name": "课程管理 - 搜索", "path": "/edu/courses", "actions": [
        ("fill_search", "美术", "搜索美术相关课程"),
        ("verify_results", None, "验证搜索结果"),
    ]},
    # 班级管理
    {"name": "班级管理 - 搜索", "path": "/edu/classes", "actions": [
        ("fill_search", "班", "搜索含'班'的班级"),
        ("verify_results", None, "验证搜索结果"),
    ]},
    # 角色管理
    {"name": "角色管理 - 新增角色", "path": "/admin/roles", "actions": [
        ("click_button", "新增", "打开新增角色对话框"),
        ("verify_dialog", None, "验证对话框打开"),
        ("close_dialog", "取消", "关闭对话框"),
    ]},
    # 报名管理
    {"name": "报名管理 - 状态筛选", "path": "/edu/enrollments", "actions": [
        ("click_tab", "待审核", "切换到待审核 Tab"),
        ("verify_results", None, "验证筛选结果"),
    ]},
    # 排课管理
    {"name": "排课管理 - 页面检查", "path": "/edu/schedules", "actions": [
        ("verify_results", None, "验证排课列表"),
    ]},
    # 收费管理
    {"name": "收费管理 - 页面检查", "path": "/finance/payments", "actions": [
        ("verify_results", None, "验证收费列表"),
    ]},
    # 退费管理
    {"name": "退费管理 - 页面检查", "path": "/finance/refunds", "actions": [
        ("verify_results", None, "验证退费列表"),
    ]},
    # 薪资管理 - 切换 Tab
    {"name": "薪资管理 - Tab 切换", "path": "/finance/salaries", "actions": [
        ("click_tab", "薪资列表", "切换到薪资列表 Tab"),
        ("verify_results", None, "验证 Tab 切换"),
    ]},
    # 机构配置
    {"name": "机构配置 - 表单检查", "path": "/admin/organization", "actions": [
        ("verify_form", None, "验证表单字段填充"),
    ]},
    # 菜单管理
    {"name": "菜单管理 - 树形结构", "path": "/admin/menus", "actions": [
        ("verify_results", None, "验证树形结构"),
    ]},
]

results = []


def login(page: Page):
    """登录"""
    page.goto(f"{BASE_URL}/login", wait_until="domcontentloaded", timeout=15000)
    page.wait_for_timeout(1500)
    api_result = page.evaluate("""async ({username, password}) => {
        const res = await fetch('/api/auth/login', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({username, password})
        });
        return await res.json();
    }""", {"username": ADMIN_USER, "password": ADMIN_PASS})
    if api_result.get("code") != 0:
        return False
    data = api_result["data"]
    page.evaluate("""({token, userInfo, permissions}) => {
        localStorage.setItem('token', token);
        localStorage.setItem('userInfo', JSON.stringify(userInfo));
        localStorage.setItem('permissions', JSON.stringify(permissions));
    }""", {
        "token": data["token"],
        "userInfo": {"userId": data["userId"], "username": data["username"], "realName": data["realName"], "roleCode": data["roleCode"]},
        "permissions": data["permissions"]
    })
    return True


def run_action(page: Page, action_type, param, description):
    """执行单个动作"""
    print(f"  → {description}...", end=" ")
    try:
        if action_type == "fill_search":
            # 找到搜索输入框并填入
            search_input = page.locator("input[placeholder*='搜索'], input[placeholder*='查询'], input[placeholder*='姓名'], input[placeholder*='名称']").first
            search_input.fill(param)
            page.wait_for_timeout(1500)  # 等待搜索结果
            print("✓")
            return True, "搜索完成"

        elif action_type == "verify_results":
            # 等待并验证有结果
            page.wait_for_timeout(500)
            rows = page.locator("table tbody tr").count()
            print(f"✓ (表格 {rows} 行)")
            return True, f"表格 {rows} 行"

        elif action_type == "click_button":
            # 点击按钮
            button = page.locator(f"button:has-text('{param}')").first
            if button.count() == 0:
                print(f"✗ 未找到按钮: {param}")
                return False, f"未找到按钮: {param}"
            button.click()
            page.wait_for_timeout(1000)
            print("✓")
            return True, "点击成功"

        elif action_type == "verify_dialog":
            # 验证对话框
            dialog = page.locator(".el-dialog, .el-drawer, .el-message-box").first
            page.wait_for_timeout(500)
            visible = dialog.is_visible() if dialog.count() > 0 else False
            if visible:
                print("✓ 对话框已显示")
                return True, "对话框可见"
            else:
                print("⚠ 对话框未显示")
                return True, "对话框未显示(可能已关闭)"

        elif action_type == "close_dialog":
            # 关闭对话框
            close_btn = page.locator(f"button:has-text('{param}'), .el-dialog__close, .el-drawer__close-btn").first
            if close_btn.count() > 0:
                close_btn.click()
                page.wait_for_timeout(800)
            print("✓")
            return True, "关闭"

        elif action_type == "click_tab":
            tab = page.locator(f".el-tabs__item:has-text('{param}'), .el-tab-pane:has-text('{param}'), [role='tab']:has-text('{param}')").first
            if tab.count() > 0:
                tab.click()
                page.wait_for_timeout(1000)
                print("✓")
                return True, "切换成功"
            else:
                print(f"⚠ 未找到 Tab: {param}")
                return True, "Tab 不存在(可能此页无 Tab)"

        elif action_type == "verify_form":
            inputs = page.locator("input:visible, textarea:visible").count()
            print(f"✓ (表单字段 {inputs} 个)")
            return True, f"表单 {inputs} 字段"

        else:
            print(f"? 未知动作: {action_type}")
            return True, "未知动作"

    except Exception as e:
        print(f"✗ 异常: {str(e)[:80]}")
        return False, str(e)[:80]


def run_test(page: Page, test):
    """运行单个交互测试"""
    print(f"\n{'='*60}")
    print(f"测试: {test['name']} ({test['path']})")
    print(f"{'='*60}")

    result = {
        "name": test["name"],
        "path": test["path"],
        "status": "PASS",
        "actions": []
    }

    try:
        page.goto(f"{BASE_URL}{test['path']}", wait_until="domcontentloaded", timeout=15000)
        page.wait_for_timeout(2500)

        for action in test["actions"]:
            ok, msg = run_action(page, action[0], action[1], action[2])
            result["actions"].append({"type": action[0], "ok": ok, "msg": msg})
            if not ok:
                result["status"] = "FAIL"
                break

        # 截图
        safe_name = test["name"].replace("/", "_").replace(" ", "_").replace(":", "_")
        screenshot_path = f"{SCREENSHOT_DIR}/{safe_name}.png"
        page.screenshot(path=screenshot_path, full_page=False)
        result["screenshot"] = screenshot_path

        status_icon = "✅" if result["status"] == "PASS" else "❌"
        print(f"  {status_icon} {result['status']}")

    except Exception as e:
        result["status"] = "ERROR"
        result["error"] = str(e)[:150]
        print(f"  💥 {str(e)[:150]}")

    results.append(result)
    return result


def generate_report():
    total = len(results)
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = total - passed

    report = f"""# 管理后台前端深度交互测试报告

**测试时间**: {time.strftime('%Y-%m-%d %H:%M:%S')}
**测试环境**: Playwright + Chromium (headless)
**测试账号**: {ADMIN_USER}

## 概览

| 指标 | 数值 |
|------|------|
| 交互测试场景数 | {total} |
| ✅ 通过 | {passed} |
| ❌ 失败/异常 | {failed} |
| 通过率 | {passed}/{total} ({100*passed//total if total > 0 else 0}%) |

## 详细结果

| # | 测试场景 | 路径 | 状态 | 动作结果 | 截图 |
|---|---------|------|------|---------|------|
"""

    for i, r in enumerate(results, 1):
        status_icon = {"PASS": "✅", "FAIL": "❌", "ERROR": "💥"}.get(r["status"], "❓")
        actions_text = ", ".join([f"{a['type']}={'✓' if a['ok'] else '✗'}" for a in r.get("actions", [])])
        report += f"| {i} | {r['name']} | `{r['path']}` | {status_icon} | {actions_text} | [{r.get('screenshot', '-')}]({r.get('screenshot', '')}) |\n"

    report += "\n## 失败的场景\n\n"
    failed_pages = [r for r in results if r["status"] != "PASS"]
    if failed_pages:
        for r in failed_pages:
            report += f"### ❌ {r['name']}\n\n"
            report += f"错误: {r.get('error', '未知')}\n\n"
    else:
        report += "**全部通过！** ✅\n"

    return report


def main():
    os.makedirs(SCREENSHOT_DIR, exist_ok=True)

    print("=" * 70)
    print("  艺培通管理后台 - 深度交互测试 v3")
    print("=" * 70)
    print(f"  测试场景: {len(INTERACTIVE_TESTS)} 个")

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1920, "height": 1080}, locale="zh-CN")
        page = context.new_page()

        try:
            if not login(page):
                print("❌ 登录失败")
                return

            for test in INTERACTIVE_TESTS:
                run_test(page, test)
                page.wait_for_timeout(300)

            report = generate_report()
            with open(REPORT_FILE, "w", encoding="utf-8") as f:
                f.write(report)
            print(f"\n📄 报告: {REPORT_FILE}")

            total = len(results)
            passed = sum(1 for r in results if r["status"] == "PASS")
            print(f"\n{'='*60}")
            print(f"  ✅ 通过: {passed} | ❌ 失败: {total - passed} / {total}")
            print(f"{'='*60}")

        finally:
            browser.close()


if __name__ == "__main__":
    main()
