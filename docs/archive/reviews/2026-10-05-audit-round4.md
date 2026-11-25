# 第四轮多方位审核报告（测试承重 · 业务正确性 · 前端质量 · 生产就绪度）

> **审核对象**：《艺术培训机构全流程教务管理平台的设计与实现》（艺培通 v1.3.0）
> **审核日期**：2026-10-05（第四轮）
> **基准 commit**：`a235ff5`（本轮起点；所有行号引证均锚定该版本）
> **与前几轮分工**：一轮=是否达标；二轮=代码质量/测试有效性/论文一致性；三轮=部署运维/安全/性能/文档；**本轮=测试承重、业务与数据一致性、前端与移动端质量、生产就绪度**
> **方法**：四路独立审查（QA / 后端 / 前端 / 运维）+ 项目总监对关键结论逐条独立复核

---

## 一、结论（TL;DR）

**四路全部判定 fail**，但性质不同：功能与业务正确性**扎实**（7/7 承重有效、金额精度全项 PASS、集成测试未被 mock 架空），fail 主要来自**此前未审到的维度**——测试里有恒绿占位、移动端有时区缺陷、缓存失效覆盖不全、生产缺告警。

| 维度 | 判定 | 代表发现 |
|------|:--:|------|
| 测试承重与有效性 | **fail → 已修** | 2 处零断言 + 1 处恒真断言（恒绿）；但 7 个关键类注入坏样后 **7/7 全红** |
| 业务正确性与数据一致性 | **fail → 已修** | 考级报名状态机是全库唯一无 CAS 的读-改-写；金额与课时精度全项 PASS |
| 前端与移动端质量 | **fail → 已修** | 4 处 `toISOString()` 时区缺陷；财务搜索框完全无效；小程序端不可运行 |
| 生产就绪度 | **fail（部分待决策）** | 就绪度 **Bronze**；无告警体系、CI 回滚曾自指、内存无余量 |

**整改后**：后端测试 **502 用例（490 单元 + 12 集成）全绿**；管理端 26 文件 / 118 用例全绿。

---

## 二、四路独立审查结论

### 2.1 QA（严过关）：测试承重与覆盖真实性

**承重验证 7/7 全红** —— 这是本轮最有价值的正面证据。逐个测试类注入"判据反方向"坏样，确认会变红后才还原：

| 测试类 | 注入方式 | 结果 |
|---|---|---|
| ScheduleConflictServiceTest | 断言期望改错 | 变红 |
| EnrollmentServiceMockTest | 异常消息期望改错 | 变红 |
| FinanceServiceMockTest | 期望金额改错 | 变红 |
| AttendanceServiceTest | 请假保护触发条件改掉 | 变红 |
| LoginAttemptServiceTest | 阈值循环次数 4→3 | 变红 |
| LessonAccountServiceTest | 乐观锁消息期望改错 | 变红 |
| RiskScoringEngineTest | 封顶值期望改错 | 变红 |

**集成测试真实性成立**：4 个集成类均 `@SpringBootTest` + H2 真实 SQL，`RefundFlowIntegrationTest` 绕过服务层直接 insert 断言 `DuplicateKeyException`，`AttendanceDeductionIntegrationTest` 断言回滚后计数为 0 —— 未被 mock 架空。
**跳过机制扫描干净**：无 `@Disabled` / `it.skip` / 条件跳过。

**发现的恒绿问题（已修）**：
- `ExamServiceMockTest.pageExamLevels_WithSort` 零断言（只验证不抛异常）
- `SmokeTest.contextLoads` 为 `assertThat(true).isTrue()` 占位
- `LoginAttemptServiceTest.missingRedisBean_DoesNotFailConstruction` 零断言（**由项目总监上一轮引入，已补**）

**未闭环（排期）**：28 个 Controller 零契约测试（B-04），其中金额/权限/状态机类建议补 MockMvc。

### 2.2 后端（贝洛奇）：业务正确性与数据一致性

**金额与课时精度全项 PASS**（质量高于多数商业项目）：
- 全链路 `BigDecimal`，**零 double 参与金额**（全库 6 处 double 均为展示用比率）
- 除法全部指定精度与舍入模式，比较一律 `compareTo`，未发现裸 `divide` 或 `equals` 比较
- 课时写入与缴费/退费/考勤同事务；DB 侧有 `CHECK (remaining_lessons >= 0)`
- 47/48 处 `@Transactional` 带 `rollbackFor = Exception.class`

**唯一 blocking（已修）**：`exam_signup` 状态机是全库唯一「读-改-写无 CAS」的路径。对照表显示其余 6 个状态机（enrollment/refund/leave/adjust/salary）都用 LambdaUpdateWrapper CAS + `updated==0 → 409`，唯独它用 `updateById` 裸更新。后果是**终态可被陈旧页面静默回滚**。

**重要更正（贝洛奇主动提出，已采纳）**：时区问题的根因**不在后端**——后端容器 `TZ: Asia/Shanghai`、JDBC URL 带 `serverTimezone`，缺 `spring.jackson.time-zone` 不构成缺陷（领域模型只用 `LocalDate/LocalDateTime`，Jackson 原样序列化）。根因 100% 在移动端。

### 2.3 前端（贾思敏）：前端与移动端质量

**blocking（已修）**：
- **时区**：移动端 3 处 + 管理后台 1 处用 `toISOString()` 取"今天/本月"。实测 UTC+8 下 10-05 07:30 → `10-04`；10-01 00:30 → `2026-09`。教师早 8 点前打开考勤页看不到今日课次；薪资一键结算会算错月份。
- **搜索失效**：收费/退费/课时账户/课时流水四个列表有搜索框但请求未传 keyword，后端也不支持 → 点了没反应也不报错（沉默失败）。
- **小程序端不可运行**：`#ifdef MP-WEIXIN` 硬编码 `http://localhost:8080`、`manifest.json` 的 appid 为空。

**P0 盘点（仅统计，用户明确"不急"）**：emoji 图标 54 处 / 16 文件；硬编码颜色 676 处 / 49 文件（两个前端均已建立 tokens.css 变量体系，属"有体系未收口"）；**紫粉渐变：无**（全为品牌青蓝）。

### 2.4 运维（卜宕机）：生产就绪度评分

**总档 Bronze**（记分卡取最低维）。评分：健康检查 2、优雅停机 2、密钥管理 2、备份演练 2、可观测性 **1**、容量与资源 **1**、依赖治理 **1**、发布与回滚 **1**、安全基线 **1**。

**Top3 风险**：
1. **出事没人知道** —— 8 条告警阈值写在 `deploy/OBSERVABILITY.md`，但全仓无 alertmanager/grafana/rules，指标只在发布时被 grep 一次。
2. **回滚是空的** —— CI 回滚自指（详见 §3.1）。
3. **并发尖峰 OOM + 事后查不到** —— backend limit 768m / `-Xmx512m`，堆外零预算，日志仅保留 30MB。

---

## 三、项目总监独立复核（不采信成员自述）

### 3.1 回滚自指 —— 我上一轮引入的回归

逐行复核确认成立：`deploy.yml:209`「Deploy containers」先 `up -d` 把新镜像跑起来 → `:255` 门禁脚本才执行，其 `:93` 采样 `previous_backend_image` 时容器已是新镜像 → 它写的证据目录 B 里 `PREVIOUS_*` 成了刚失败的镜像 → `:306` 回滚用 `ls -1dt | head -n1` 取最新目录命中 B → 回滚到失败镜像，健康检查照样过，打印 "rollback completed"。
**真出事那天：回滚一路绿灯、退出码 0，线上纹丝不动。**

### 3.2 Redis 降级不闭环 —— 我上一轮引入

用 `javap` 核实：`RedisSystemException extends UncategorizedDataAccessException`，与 `RedisConnectionFailureException extends DataAccessResourceFailureException` **互不为子类**。我原代码只捕获后者，因此 Redis 命令超时（我把 `timeout` 压到 1s）经 Spring 包装后会穿透 → 登录直接 500。**最该降级的路径最脆。**

### 3.3 缓存失效覆盖不全 —— 我上一轮引入

看板缓存 4 项（在册学员/本月课次/本月营收/考勤率），而 `@CacheEvict` 只覆盖 attendance/enrollment/finance/risk 四个类。实测定位需补 **3 个类 10 个方法**（薪资与考级不进这 4 项指标，无需失效）。

### 3.4 两处文档口径矛盾

- `docs/deployment-2026-09-10.md:73` 记「38 表」，而 `schema.sql` 定义 37、本地库实测 37。已用 `comm` 逐表比对确认**库 = 定义 = 37 且不含 flyway 表**，修正为 37 并加注说明。
- `README.md:14` 仍写移动端「+ uView Plus」，而 `package.json` 与源码均无该依赖（第二轮 A1 只改了论文，README 漏改）。

### 3.5 授权矩阵可自证性

答辩反复引用的「38 项（32 授权 + 6 越权）」经查有明细支撑：`docs/acceptance-matrix-2026-07-19.md` 逐条列出，总计 32 授权端点 + 6 项越权拒绝。另实测权限注解 **133 处**（SUPER_ADMIN 121 / EDU_ADMIN 82 / FINANCE 41 / TEACHER 21 / PARENT 12）。**可自证**。

---

## 四、整改闭环（当日完成）

| 项 | 处置 | 承重验证 |
|---|---|---|
| CI 回滚自指（P0） | 回滚改用发布前快照的 `steps.snapshot.outputs`；production 部署只由门禁脚本执行；门禁缺凭据时前置 `exit 1` | YAML 解析 + 步骤顺序实测 |
| 移动端/后台时区（P0） | 抽 `utils/date.ts` / `date.js`，4 处改用本地时间 | 实测 07:30 → 10-05（旧：10-04）；10-01 00:30 → 2026-10（旧：2026-09） |
| 恒绿断言 ×3 | 改为断言真实证据（排序映射、Bean 存在性、降级语义） | 各注入反方向坏样确认变红 |
| 考级状态机 CAS | 改 `LambdaUpdateWrapper` + `updated==0 → 409` + `@Transactional` | 新建 `ExamSignupConcurrencyTest`（3 例）；**去掉 CAS 后 1 例变红**，还原后字节一致 |
| Redis 降级捕获面 | 放宽到 `DataAccessException` | javap 确认两类异常互不为子类 |
| 缓存失效缺口 | 3 个类 10 个方法补 `@CacheEvict` | 编译通过 |
| 财务搜索失效 | 后端 4 端点补 keyword（含新增 2 个 `selectIdsByNameLikeIncludeDeleted` 避开软删陷阱）+ 前端 4 列表传参 | 编译 + 全量测试通过 |
| 小程序端 | BASE_URL 外置为占位、README 明示未纳入交付 | 声明性修复 |
| 管理端组件未注册 | `setup.ts` 补 `el-link`/`el-divider`/`el-tag` 与 `loading` 指令 | 118 用例仍全绿，此前被跳过的断言现会执行 |
| Dockerfile 时区 | 加 `ENV TZ=Asia/Shanghai` | 与 compose 的 TZ 一致 |
| 文档口径 | 38 表→37、uView Plus 描述、README 已知限制、测试数字 502 | 脚本统计 + grep 残留为 0 |

**测试数字回填**：502（490 单元 + 12 集成）已同步至论文中英摘要、第 5 章表 5.6 与小结、答辩讲稿、答辩问答预案，以及 `md2docx.py` 的硬编码摘要，并重新生成论文 docx。

---

## 五、遗留与待决策

| 项 | 严重度 | 说明 |
|---|:--:|---|
| 无告警体系 | P0（运维口径） | 需独立决策：最小闭环=外部拨测 + 通知渠道；完整=引入 prometheus + alertmanager + rules。未做，因属架构级新增 |
| 生产明文 HTTP | P1 | 需域名与备案，属现实约束；已在文档明示 |
| 生产库 22 个弱口令账号 | P1 | 改 compose 对**已初始化**的库无效，需线上实测并强制改密 |
| backend 内存档位 | P1 | 需压测数据后定档（建议 `-Xmx384m` + limit 896m + MaxMetaspaceSize） |
| Controller 契约测试 | P1 | 28 个 Controller 零测试，建议优先补金额/权限/状态机类 |
| emoji 图标 / 硬编码颜色 | P2 | 用户明确"不急"，维持既有定性 |

---

## 六、达标判定

| 面向 | 判定 | 依据 |
|---|:--:|---|
| **毕业设计答辩交付** | **达标** | 功能真实可运行、核心闭环正确、502 后端 + 118 管理端用例全绿、7/7 承重有效、论文与代码口径一致、五角色授权矩阵 38 项可自证 |
| **工程质量（毕设尺度）** | **达标** | 注入面干净、并发控制扎实（整改后状态机无漏网）、金额精度严谨、分层测试真实 |
| **生产长期运行** | **有条件** | 功能无虞，但缺告警、明文 HTTP、内存无余量属上线后运维短板；建议答辩演示场景可接受，正式商用前先补告警 |

**一句话**：这套系统在"毕业设计是否达标"这个尺度上是站得住的，多数硬指标（测试承重、金额正确性、集成测试真实性）经得起追问；剩余问题不改变达标结论，但"生产可观测性为零"这条若被追问，应如实回答为已知短板而非声称已具备。

---

> 本报告由项目总监统筹，四路独立审查 + 关键结论亲自复现。
> 所有实测证据（承重验证、时区对比、javap 继承链、表数比对、脚本统计用例数）均在报告正文给出可复现命令或结果。
