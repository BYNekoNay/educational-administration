# codex/production-hardening 专项 Review 报告

> 日期：2026-09-07 ｜ 分支：`codex/production-hardening` = 8309bd5 ｜ 基线：715edc4（8/6）
> Review 方式：逐提交 + 逐主题抽查关键文件，与主线（master=7eb742d / dev/iteration-next=898eba2）三方对比

## 结论先行（TL;DR）

**这是一份高质量、覆盖 8/4 ideation 全 7 方向的"生产化完整实现"，价值极高，但绝不能整支合入。**

| 维度 | 结论 |
|------|------|
| 总体质量 | 🟢 高。脚本级工程素养（trap 清理、幂等迁移、证据收集、防盲目回滚），远超"毕设玩具"水平 |
| 危险项 | 🔴 `8309bd5`：push 到该分支即自动触发 production 发布 → **必须剥离，永不吸收** |
| 与主线关系 | 基线旧（715edc4 vs 当前 898eba2），JwtUtil 等与已合入 wip 版重复，直接 merge 冲突大 |
| 建议 | **按主题拆分吸收**，一次一个里程碑；8309bd5 与重复项剔除 |

## 变更主题分组（55 文件 / +5161 / -282）

| # | 主题 | 关键文件 | 对应 ideation | 质量 | 吸收建议 |
|---|------|---------|--------------|------|---------|
| T1 | 🔴 危险 CI 触发器 | deploy.yml push 触发 | — | 危险 | **剥离** |
| T2 | 安全配置校验（重复） | ProductionSecurityConfigValidator(+Test 170) | #1 | 🟢 | 与已合入 wip 版重叠；保留 wip 版，本版**弃用或二选一** |
| T3 | JwtUtil 弱密钥拒绝（重复） | JwtUtil(+10/-) | #1 | 🟢 | 与已合入 fb80769 **逐字一致** → 已覆盖，跳过 |
| T4 | 版本化 DB 迁移 | Flyway V6 索引 / V7 退款约束 / pom 引 flyway | #3 | 🟢 幂等+生成列 | **吸收（B3 落地）**，需先定基线策略 |
| T5 | 备份/恢复/演练 | backup-*.sh / restore-*.sh / backup.yml / BACKUP_AND_RESTORE.md | #2 | 🟢 trap+加密+演练 | **吸收（B4 落地）**，主线零冲突（全新文件） |
| T6 | 发布门禁+回退 | release-production.sh / rollback-images.sh / RELEASE_RUNBOOK.md / verify_high_value_invariants.sql | #4 | 🟢 证据收集+防盲目回滚 | **吸收**（脚本全新增，冲突小） |
| T7 | CI 多维门禁 | ci.yml（delivery-scripts/backend/admin-web/mobile/container 5 job） | #5 | 🟢 mobile 测试 0→有 | **吸收改造**（与已加 test job 合并） |
| T8 | 业务可观测性 | BusinessMetrics / RequestCorrelationFilter / logback-spring.xml / actuator+micrometer+logstash | #6 | 🟢 | **吸收（B5 增强）** |
| T9 | H5 报名决策快照 | ParentEnrollmentSnapshotVO / EnrollmentServiceImpl(+275) / enrollment.vue 重构 / snapshot.js(+Test) | #7 | 🟢 TTL5min+If-Match | **吸收**（前端后端配套，含 317 行测试） |
| T10 | 文件上传内容校验 | FileStorageService(+93/+Test 198) | #1 延伸 | 🟢 | **吸收**（独立模块） |
| T11 | 配套微调 | GlobalExceptionHandler / NotificationServiceImpl / nginx default.conf / acceptance_test.sh / schema.sql | 各方向 | 🟡 | 随 T 分批携带 |

## 关键文件质量摘录

### 🟢 T5 备份脚本（deploy/scripts/backup-production.sh）
- `set -euo pipefail` + `umask 077`；require_command 前置检查 docker/openssl/tar/gzip/sha256sum
- **完整 trap 网络**：EXIT 清理加密临时文件与工作目录、ERR 报告失败步骤、INT/TERM 优雅退出
- 备份前自动停 backend → 备份 → 自动拉起（backend_stopped 状态机 + 失败告警）
- 配套 restore-production.sh / restore-drill.sh（隔离环境真实恢复演练）/ run-scheduled-backup.sh + backup.yml workflow

### 🟢 T4 Flyway 迁移（V6/V7）
- 全部**幂等**（information_schema 探测 + PREPARE/EXECUTE 动态 DDL），历史生产数据安全
- V7 用**生成列**（status=1 且未删 → enrollment_id，否则 NULL）绕开 MySQL 无部分索引的限制，实现"单 pending 退款"唯一约束——MySQL 高级技巧
- 注释明确"Existing installations are baselined at version 5"

### 🟢 T6 发布门禁（release-production.sh）
- 明确输出 `Do not roll back blindly. Use RELEASE_RUNBOOK.md`——有状态迁移下拒绝盲滚的工程判断
- mysql/backend/web 三容器 health 检查（docker inspect）+ 120s 超时 + 失败 die
- collect_evidence 证据收集（发布现场留痕）

### 🟢 T9 报名快照（EnrollmentServiceImpl）
- ConcurrentMap + `PARENT_SNAPSHOT_TTL=5min` + versionToken + 过期条目惰性清理
- ParentController 用 `If-Match` 头携带快照版本提交校验——与 ideation #7"快照版本供提交时校验"逐点对应
- 配套 317 行测试 + mobile 端 snapshot.js/重构 enrollment.vue

### 🟡 需注意的整合点
- application-prod.yml 在本分支 +53 行（actuator/logstash/flyway 配置），与已合入的 wip 版 +11 行需**三方合并**（主线版本为基准）
- pom.xml 引 4 个新依赖（actuator/micrometer-prometheus/logstash-encoder/flyway×2），本地离线仓库需确认可用
- schema.sql 改动与 Flyway 迁移需统一（建议：schema.sql 冻结为"全新建库基线"，增量全走 Flyway）

## 拆分合入执行方案（建议顺序，一次一个里程碑）

> 每批独立验证（后端 mvn test + 前端 vitest + 冒烟），独立提交，沿用 packed-refs 固化链路。

| 批次 | 内容 | 风险 | 预估 |
|------|------|------|------|
| M1 | T5 备份恢复全套（纯新增脚本+md，零冲突） | 🟢 极低 | 小 |
| M2 | T4 Flyway 基线 + V6/V7 + pom + schema 策略 | 🟡 中（需定基线） | 中 |
| M3 | T6 发布门禁脚本 + verify SQL + RELEASE_RUNBOOK | 🟡 中（与 deploy.yml 整合） | 中 |
| M4 | T8 可观测性（actuator/metrics/correlation/logback） | 🟡 中（yml 三方合并） | 中 |
| M5 | T9 报名快照（后端 VO/Service + 前端 vue/js + 测试） | 🟡 中（EnrollmentServiceImpl 冲突） | 大 |
| M6 | T10 文件内容校验 + T7 ci.yml 吸收合并 | 🟡 中 | 中 |
| — | T1 8309bd5 / T2 重复 validator / T3 已覆盖 JwtUtil | 直接剔除或冻结 | — |

## 风险清单

1. 🔴 **T1 绝不整支 merge**：8309bd5 让 push 即发生产，任何误 push 到该分支 = 线上事故
2. 🟡 分支基线落后主线 ~6 提交（含 vitest 降级、权限码修复），EnrollmentServiceImpl / ParentController / application-prod.yml 三方合并是主战场
3. 🟡 Flyway 首次启用需**确认线上实际 schema** 与 V5 基线假设一致，否则迁移会失败
4. 🟡 本地 maven 离线仓库无 flyway/actuator 依赖时需先联网拉取一次

## 参考
- 8/4 ideation：`docs/ideation/2026-08-04-educational-administration-optimization-ideation.html`
- 已合入最小安全集：dev/iteration-next `fb80769`（wip-prod-hardening 3855cb3 内容）
- CI 测试门禁：dev/iteration-next `898eba2`
