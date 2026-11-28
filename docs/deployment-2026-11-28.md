# 部署记录 · v1.3.0-20261128（第六轮验收修复）

> 部署时间：2026-11-28 15:00–15:15｜目标：云端测试端 101.35.239.218（测试/演示环境）

## 版本

- backend：`eduadmin-backend:v1.3.0-20261128`（jar md5 `1e0e04a1`，本地=服务器一致）
- web：`eduadmin-web:v1.3.0-20261128`（admin-dist + mobile-dist，mobile 构建已固化 `--base=/mobile/`）
- schema：14（无新迁移）
- 提交范围：`5cb91fd..80f6bea`（6 个语义化提交）

## 修复项与验证结果

| 报告编号 | 缺陷 | 修复 | 冒烟 |
|---|---|---|---|
| P0-1 | 移动端 H5 整站白屏（根因：本地构建未带 `VITE_BASE=/mobile/`，lite 镜像直 COPY 产物） | `build:h5` 固化 `--base=/mobile/`；manifest 清理无效旧键 | ✅ `/mobile/` 200、资源引用 `/mobile/assets/*`、静态资源 200 |
| P0-2 | 调课申请缺 expectTime 必然审核失败（根因：后端创建链路不校验） | `createAdjustRequest` 入口 400 拦截 | ✅ 缺时间 → 400「请选择期望上课时间」 |
| M13 | 退费驳回不填原因成功（后端无校验） | `FinanceController.auditRefund` 驳回 remark 必填 | ✅ 空原因 → 400 |
| M06 | 报名驳回不填备注成功 | `EnrollmentController.audit` 驳回 remark 必填 | ✅ 空备注 → 400 |
| M09/T01 | 迟到扣课时两端不一致（后端无口径，客户端可传 0~10） | 服务端硬口径：到课/迟到扣 1，请假/缺勤不扣，忽略客户端值 | 单测覆盖（云端口径复测留给测试） |
| M09 | 考勤按日期不按时刻（当天凌晨可点当晚课） | 时刻粒度校验：课次未开始 → 409 | ✅ 未来课次考勤 → 409 |
| P2 | 考勤 `?lessonId=` / 课时账户 `?studentId=` / 日志 `?module=` 筛选失效 | 三处 Controller→Service 全链路补参数 | ✅ 三处过滤均生效 |
| P2 | 风险名单分页回显 pageNum/pageSize=0 | `PageResult` 新增完整字段工厂并回显 | ✅ 回显 2/5 |
| P2 | EDU_ADMIN 能签发导出票据（下载才 403） | 签发按端点角色映射先行校验 | ✅ 签发即 403 |

## 验证

- 五角色验收脚本：**38/38 通过**
- 修复项冒烟脚本：**13/13 通过**（含版本核对）
- 后端单测 537 全绿（基线 532 + 新增/改造 6 例，其中修正了 `shouldNotDeductForLeave` 用例误用 status=2 的历史缺陷）
- 移动端 33 例全绿；管理端 121 例全绿

## 回滚点

- `.env.bak-20261128`（回滚 tag `v1.3.0-20261126o`）
- `artifacts/eduadmin.jar.bak-20261128`、`admin-dist.bak-20261128`、`mobile-dist.bak-20261128`

## 部署 SOP 增补（防复发）

- 移动端构建命令已固化 base（`npm run build:h5` 自带 `--base=/mobile/`），不再依赖环境变量
- 部署后冒烟**必须**包含：`/mobile/index.html` 资源引用以 `/mobile/` 开头 + 任一静态资源 200（本轮白屏即因旧流程无此断言而漏检）
