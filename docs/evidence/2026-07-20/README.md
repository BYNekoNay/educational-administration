# 五角色界面验收证据

> 验证日期：2026-07-20（Asia/Shanghai）

本目录保存最终交付轮次的界面证据。截图使用仓库演示账号和本地种子数据生成，不包含 JWT、真实个人信息或外部生产地址。

| 角色 | 账号 | 客户端 | 入口/关键页面 | 证据文件 |
|---|---|---|---|---|
| 超级管理员 | `admin` | 管理端 Web | 运营看板 | `super-admin-dashboard.png` |
| 教务管理员 | `edu` | 管理端 Web | 运营看板（教务权限菜单） | `edu-admin-dashboard.png` |
| 财务管理员 | `finance` | 管理端 Web | 运营看板（财务权限菜单） | `finance-admin-dashboard.png` |
| 教师 | `teacher1` | 移动端 H5 | 教师首页 | `teacher-home.png` |
| 家长 | `parent1` | 移动端 H5 | 家长首页、退费入口 | `parent-home.png`、`parent-refund.png` |

自动化交叉验证见 `../../acceptance-matrix-2026-07-19.md` 和 `../../test-report-2026-07-19.md`。五角色业务端点 32/32、未授权与越权拒绝 6/6 均通过。
