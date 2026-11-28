# 部署记录 · v1.3.0-20261128a（第八轮复测修复）

> 部署时间：2026-11-28 19:10–19:35｜目标：云端测试端 101.35.239.218（测试/演示环境）｜仅 backend（无前端改动）

## 版本

- backend：`eduadmin-backend:v1.3.0-20261128a`（jar md5 `b7c98a18`）
- web：未重建（仍 `v1.3.0-20261128`，无前端改动）
- schema：**14 → 15**（V15 迁移），`EXPECTED_SCHEMA_VERSION` 已同步
- 提交范围：`5cb91fd..86654d9`（第八轮修复共 4+1 个提交）

## 修复项与验证结果

| 编号 | 缺陷 | 修复 | 验证 |
|---|---|---|---|
| P1 | 课时账目 remaining>total（刘小小 46/48，退费虚高） | **V15 迁移**：total = GREATEST(Σ(缴费+调账−退费), remaining) 重算 + `CHECK (remaining≤total)` 防线 | ✅ 全库 violations=0、刘小小账户 total=24 ≥ remaining=9、五角色 38/38 |
| P2 | 用户删除 405 / 考勤删除 405 / 停用提示 / IP 限流阈值 | 文档化（`docs/09-接口规范.md` §1.4/§2/§8.2 + 验收矩阵「已知限制」） | ✅ 文档落库 |
| P2 | 批量排课"无上限" | **误判澄清**：上限 200 存在（`ScheduleController` L114），101 条成功属预期 | 无需修复 |
| 澄清 | 筛选参数"未复测" | 上轮已修且冒烟 PASS | 无需修复 |
| 澄清 | 移动端首页单孩子 | StudentSwitcher 设计（10 页面全覆盖），非缺陷 | 无需修复 |

## V15 迁移试错记录（重要教训）

最终版本 v4：`total = GREATEST(Σflow(1,4,5), remaining)` 一步到位，幂等且恒满足目标 CHECK。

| 版本 | 方案 | 失败模式 |
|---|---|---|
| v1 | PREPARE 执行 SIGNAL 防御 | **MySQL 1295**：SIGNAL 不支持 prepared statement 协议（本地 @violations=0 走 SELECT 1 分支未暴露，服务器有违规账户才炸） |
| v2 | 补录差额流水 + 按流水和归位 | **3819**：部分原有账户流水汇总为负（种子设计），归位把 remaining 设成负数触发 inline chk_1(remaining≥0) |
| v3 | 重算 + 补齐两步 | **3819**：CHECK 已存在的库上互相打架——重算把补齐过的 total 打回流水值再次违规（且手动补数据后 Flyway 重跑仍炸） |
| v4 | **GREATEST 一步** | ✅ 幂等、自身恒满足 CHECK、重跑安全 |

其他教训：

- Flyway 迁移失败后须删除 `flyway_schema_history` 的 failed 记录才能重跑（本次删了 3 次）
- Flyway 失败会回滚 DML，但报错定位行号可能错位，真实错误以 `Message` 字段为准
- 本地 MySQL 5.7 对 CHECK 约束"解析但忽略"，本地验证通过 ≠ 服务器 8.0.46 行为一致；且本地库落后生产缺 V11-V15，本次已补齐（biz_no 列由此而来）

## 验证

- 五角色验收脚本：**38/38 通过**
- 全库违规账户：**0**
- Flyway：V15 success=1 正常登记
- `/mobile/` 静态资源抽查 200（web 未动）
- 后端单测 537 全绿（H2 同步 CHECK 后无破坏）

## 回滚点

- `.env.bak-20261128a` + `eduadmin.jar.bak-20261128a`（回滚 tag `v1.3.0-20261128`）
- **schema 回滚**：`ALTER TABLE lesson_account DROP CHECK chk_lesson_remaining_le_total;` + 删除 flyway V15 记录（total 修正不回滚——它们本来就是脏数据）
