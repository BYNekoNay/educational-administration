# 第五轮多方位审核报告

> 审核日期：2026-10-05
> 审核基线：`dev/iteration-next@38389df`（工作区）
> 审核方式：MVP 开发专家团五切面并行审核 + 项目总监独立复核
> 性质：只读审计。本轮未修改任何代码、测试或配置。

---

## 零、结论先行

| 切面 | 负责人 | verdict | 一句话结论 |
|------|--------|---------|-----------|
| A 测试真实性与承重 | 严过关（QA） | **fail** | 502/118/25 全绿且证据可靠，但 3 条「钱/课时」不变量无任何回归保护 |
| B 核心业务逻辑 | 贝洛奇（后端） | **fail** | **3 条 P0**：调课致教师少拿 20% 薪资 / 已发放薪资可二次付款 / 删课次不回冲课时 |
| C 安全与架构一致性 | 高见远（架构师） | 条件 pass | 越权、SQL 注入、密钥均通过；唯一缺陷类发现为 FileController 下载缺角色约束（P2） |
| D 前端质量 | 贾思敏（前端） | **fail** | **1 处 emoji 残留（P0）**；tokens 体系未落地（748 处硬编码）；无反回归守护 |
| E 部署与交付基线 | 卜宕机（运维） | **fail（仅当基线为 master）** | 5 条阻断，全因基线错配；改指 dev 后 4 条自动消解 |

> **★ 修复分支声明**：本轮全部 P0/P1 修复**必须落在 `dev/iteration-next`**，禁止在 master 上直接修改。原因：master 是 dev 的严格祖先（可快进），在 master 上的改动会在后续 `master ← dev` 快进时被覆盖丢失。若最终基线定为 master，正确顺序是「先在 dev 修 → 再快进合入 master」。

**本轮最重要的发现不在代码层面，而在交付治理层面**：

> **两个远端的默认分支上都没有毕业论文和答辩 PPT。**
> Gitee 默认分支 `master@5cc3135` 与 GitHub 默认分支 `codex/fix-functional-gaps@5951d3a` 均缺失 `docs/thesis`、`docs/defence`、`docs/18-测试报告.md`，只有 `dev/iteration-next` 携带这些毕设核心交付物。详见 §6。

---

## 一、P0 缺陷清单（必须处理）

| # | 缺陷 | 位置 | 后果 | 复核状态 |
|---|------|------|------|----------|
| P0-1 | 调课生成的替换课次被薪资误判为「代课」 | `AdjustRequestServiceImpl:281`（master 上为 `ScheduleServiceImpl:862`）；判据 `SalaryServiceImpl:226/236/313` | 主讲教师该课次按 0.80 系数结算，**每次调课少拿 20%** | 总监已独立复核，链条闭合 |
| P0-2 | 已发放（status=3）薪资可被重新核算重置为待确认 | `SalaryServiceImpl:327-331`（仅拦 status=2）、`:346` | 存在**同月二次付款**路径：3→重算→1→确认→2→发放→3 | 总监已独立复核，路径成立 |
| P0-3 | 删除已有考勤的「待上课」课次不回冲课时 | `ScheduleServiceImpl:369/372` | 课时**永久丢失** | 总监已独立复核，注释暴露错误假设 |
| P0-4 | emoji 功能图标残留 1 处 | `mobile-uniapp/src/pages/parent/schedule.vue:46`（U+23F0） | 违反团队 P0-1 绝对规则 | 总监已字节级复核确认 |

### P0-1 完整因果链（总监独立复核）

代码自身注释即自相矛盾，这是最强证据：

| 环节 | 位置 | 事实 |
|------|------|------|
| 新课次继承原教师 | `AdjustRequestServiceImpl:249` | `setTeacherId(oldLesson.getTeacherId())` |
| 判定是否代课 | `:277-279` | `isSubstitute` 因两 TeacherId 必等而**恒为 false** |
| 却无条件写入 | `:281` | `setSourceLessonId(oldLesson.getId())` |
| 薪资判主讲 | `SalaryServiceImpl:226` | `.isNull(sourceLessonId)` → 调课新课次被**排除** |
| 薪资判代课 | `SalaryServiceImpl:236` | `.isNotNull(...)` → 调课新课次被**归入代课** |
| 代课打折 | `:313` | `unitPrice × substituteRate` |
| 系数实测 | `sql/data.sql:761+` | 全部教师全部课程 **0.80** |

`AdjustRequestServiceImpl:270-275` 的注释白纸黑字写着「调课（reschedule）：newTeacherId == oldTeacherId」，却仍无条件写 `sourceLessonId`——**主讲被当成代课打折**。以 80 元/课计，每次调课少发 16 元。

排他性验证：全库 `sourceLessonId` 写入点仅此一处（`ScheduleController:124/145` 是剥离 `null`，非写入；无 XML mapper 旁路）。

### P0-2 完整非法跃迁链（总监独立复核）

`paySalary:490` 明确拦截 `status==3`，但前两道门未堵住：

```
3(已发放) --calculateSalary:328 只拦 2--> 1(待确认)
          --confirmSalary:432 只拦 2/4--> 2(已确认)
          --paySalary:490 拦 3，当前为 2--> 3(已发放)  ← 第二次付款
```

### P0-3 说明

`ScheduleServiceImpl:369` 仅允许删除 `status=1`（待上课）课次，其注释称「已完成（status=2）等状态的课次可能已产生考勤扣减，直接删除会导致课时账户永久不一致」。但 `AttendanceServiceImpl` 允许对 `status=1` 课次执行考勤并扣课时——**注释所依赖的前提「status=1 即无考勤」不成立**。先考勤再删课，课时永久丢失且无回冲流水。

### P0-4 说明

`schedule.vue:46` 为 `<text class="lesson-icon">⏰</text>`（字节 `e2 8f b0`）。同一文件内「班级/教师/教室/课程」四行已正确使用 `.ic .ic-sm .ic-*` 矢量图标类，唯独「时间」一行漏改；CSS 中 `.ic-clock` 早已存在，可直接替换。判定为上一轮全量替换（54 处）的漏网一行，非新引入。替换后 `.lesson-icon`（`schedule.vue:353`）即成无用样式，应一并删除。

---

## 二、P1 缺陷清单

### 2.1 后端（切面 B）

| # | 缺陷 | 位置 |
|---|------|------|
| P1-4 | 财务端建退费缺 `paymentRecordId`/`lessonCount` 兜底，落库 NOT NULL → 500 | `FinanceController:66-78`；`schema.sql:398/406` |
| P1-5 | 财务端审核强制 `refundAmount>0`，使「按比例自动计算」分支不可达；金额填少无拦截 | `FinanceController:102-104`；`FinanceServiceImpl:456-464` |
| P1-6 | 排课冲突检测为 check-then-insert，无事务、无 DB 约束 | `ScheduleServiceImpl:278-282/350-354` |
| P1-7 | `create()` 的 `DuplicateKeyException` 兜底为死代码（表无唯一键），且方法无 `@Transactional` | `EnrollmentServiceImpl:204-217` |
| P1-8 | 已发放薪资可回退/撤销：3→2、3→4 均放行 | `SalaryServiceImpl:433-434/462` |
| P1-9 | `createAdjustment` 明文拼接 SQL；调整额只加 `total_amount` 不加 `bonus_amount`，重算即被抹除 | `SalaryServiceImpl:544` |
| P1-10 | 手动 `setVersion` 违反项目铁律 1 | `FinanceServiceImpl:193`；`AttendanceServiceImpl:544/587`；`LeadeRequestServiceImpl:391` |
| P1-11 | 教师/教务可直接提交 `status=3`(请假) 绕过请假审批流程 | `AttendanceServiceImpl:206/248-250` |
| P1-12 | 缴费无幂等键 | `FinanceServiceImpl:147-288` |
| P1-13 | 请假审批自动建考勤记录内容零校验（测试缺口，非代码缺陷） | 见 §3 B3 |

### 2.2 测试（切面 A）——三条「钱/课时」不变量裸奔

| ID | 不变量 | 缺口 |
|----|--------|------|
| B1 | 薪资调整入账金额 | `SalaryServiceImpl:544` 金额拼进 SQL 字符串，测试只 `verify(update(any(),any()))` 验调用次数，**改成 ×10 无人发现** |
| B2 | 调薪为 0 须拒绝、总额不得为负 | `SalaryServiceImpl:521-528` 两道守卫**零用例覆盖** |
| B3 | 请假审批自动建考勤应为「请假 + 扣 0」 | `LeaveRequestServiceImpl:220-224`，全库无 `ArgumentCaptor<Attendance>`；若写成「到课 + 扣 1 课时」，每次批假静默偷扣学员课时 |

> **总监复核**：B3 的**代码本身是正确的**（`:222-224` 明确设 `status=3` + `deductLessons=0`，并发冲突还有覆盖/回冲兜底）。它是测试缺口而非代码缺陷——不变量无人守护，改坏了 502 用例也不会红。

### 2.3 前端（切面 D）

| # | 缺陷 | 实测数字 |
|---|------|----------|
| D-02 | 设计 Token 体系未落地 | 硬编码颜色 748 处 / 50 文件（总监独立口径：仅 `.vue` 为 609 处 / 47 文件） |
| D-03 | 管理端窄屏不可用 | 全端 `@media` 仅 3 条 / 2 文件；最宽表格 3770px |
| D-04 | 无 emoji / token / iconMap 反回归守护测试 | 26 个 admin 测试命中 0 |
| D-05 | 交付文档仍断言「带 emoji 图标」 | `docs/ui-test-plan-browser-ai-2026-09-10.md:249/633`（被 README 引用，属活文档） |

---

## 三、已核实为「做对」的项（答辩可放心讲）

| 项 | 证据 |
|----|------|
| 金额全程 BigDecimal，divide 均带 scale + RoundingMode，无 double 中间计算 | `FinanceServiceImpl:450/460/481/519/530/544`；`SalaryServiceImpl:318-320` |
| 课时扣减为真实 CAS（version + remaining 双条件），并发不会超扣 | `AttendanceServiceImpl:581-588`；DB 另有 CHECK（`schema.sql:337`） |
| 考勤重复提交幂等：先回冲旧扣减再扣 | `AttendanceServiceImpl:252-266`；唯一键 `uk_lesson_student` |
| 退费超额三重拦截 + 并发 CAS + DB 部分唯一索引 | `FinanceServiceImpl:469-472/483-485/545-548`；`uk_refund_pending_enrollment` |
| 跨表写入均有 `@Transactional(rollbackFor=Exception.class)` | Finance/Attendance/Adjust/Leave 各 Service |
| 事务内不直发通知，延迟到 afterCommit | `ScheduleServiceImpl:299-316/677-699` |
| **越权防护**：17 个家长/教师端点全部做数据归属校验 | `ParentController:92-99/137-143/163-169/204-210`；`LeaveRequestServiceImpl:303-345`；`LearningController:270/76/142-153` |
| **SQL 注入 0 处** | 全局 `${}` 拼接 0；`.last()` 全为固定 `"LIMIT n"`；无 mapper XML |
| **密钥管理**：生产 100% 环境变量注入 + prod 启动强校验 | `ProductionConfigurationValidator:35-62` |
| 集成测试**未被 mock 架空**，真连 H2 | `RefundFlowIntegrationTest:101-112` 绕过服务层直接插库被 DB 唯一约束拒绝 |
| 前几轮恒绿断言清扫**真做完了** | `@Disabled`/`assertTrue(true)`/零断言方法均为 0 |
| 时区缺陷（第三轮）已修复，运行期 `toISOString()` 调用点 0 | 仅剩注释中的 9 处 |
| 财务列表搜索、考勤防重复、财务异常兜底（第二轮）均已到位 | 5 个列表全支持搜索 |

---

## 四、总监独立复核记录（不盲信专家结论）

本轮对所有 P0 及关键分歧均做了独立取证，并纠正了自己 3 处误判：

| 项 | 结论 |
|----|------|
| P0-1 / P0-2 / P0-3 | 逐条读码复核，**全部成立** |
| P0-4 emoji 残留 | 字节级复核（`od -c` 确认 `342 217 260`），**成立** |
| B3 请假考勤 | 读码确认代码正确，**是测试缺口非代码缺陷** |
| H2 测试库表数 | 实测 19 个 CREATE TABLE（生产 37），确认 QA 数字准确 |
| 架构师「退费缺上界」 | 已由架构师自行撤回（service 层实有三层防护） |
| 架构师「A8 mass assignment」 | **总监裁定降级 P3**：`ExamServiceImpl:186-190` 已剥离、`:198-217` 白名单 wrapper，客户端值进不了 SQL |

### 总监自身误判纠正（避免污染报告）

1. 曾怀疑 `NoticeController` 零权限 → 实为我的 grep 漏了 `@RequireRole`，其 10 个端点全部有校验
2. 曾统计「79 个端点缺权限」→ 漏算类级注解兜底，精确统计后为 **10 个**
3. 曾报「管理端 29 页面」→ 实为 23 页面 + 6 个页面内组件

### 专家间交叉验证剔除的假条目（3 条）

| 条目 | 提出方 | 剔除依据 |
|------|--------|----------|
| 财务审核缺退费上界 | 架构师 | service 层三层防护齐全，架构师自行撤回 |
| A8 `ExamController` mass assignment | 架构师 | service 层已剥离 + 白名单 wrapper，不可利用，降 P3 |
| QA「缺 `@RequireRole` 端点 0 处」 | QA | 与总监实测（10 个）及架构师判定不符，已纠正 |

---

## 五、实测数字对账（对照项目记忆）

| 项目 | 记忆值 | 实测值 | 判定 |
|------|--------|--------|------|
| Controller 数 | 29 | **28** | 记忆有误。根因：`GlobalExceptionHandler` 的 `@RestControllerAdvice` 被 `@RestController` 子串误命中 |
| 方法级端点 | 208 | **208** | 一致 |
| 类级 `@RequestMapping` | - | 26 | 新增记录 |
| 数据表 | 37 | **37** | 一致 |
| 管理端页面 | 23 | **23**（+6 页面内组件 = 29 个 .vue） | 一致，口径差异 |
| 移动端页面 | 21 | **21**（路由 21/21 存在，dangling 0） | 一致 |
| 移动端图标类 | 21 类 | **22 个图标类 + 3 修饰类** | 以实测为准 |
| Flyway 版本门禁 | 9 | **9**（`deploy.yml:36` 与 `release-production.sh:12` 一致） | 一致 |
| 后端测试 | 502 | **502**（47 个 surefire XML 解析） | 一致 |
| 无角色保护端点 | - | **10 个** | 新增记录 |

**10 个无角色保护端点的逐条判定**：

- `AuthController` 5 个（login/register/logout/profile/menus）：公开或凭登录态，**合理**
- `NotificationController` 4 个：全部走 `CurrentUserHolder` 归属过滤，`markRead(id, userId)` 带用户维度，**安全**
- `FileController` 下载 1 个：无角色校验，但 `FileStorageService:40-41` 存储名为 UUID（不可枚举）+ `:149-158` 四道路径遍历防护 → **真实缺口为「登录即可下载」，判 P2**

### 5.1 文档数字陈旧值补充（切面 E 发现，为我早前扫描的遗漏）

早前扫描仅覆盖 `docs/*.md`，遗漏了仓库根目录 `README.md`。补扫结果：

| 位置 | 文档值 | 实测值 | 判定 |
|------|--------|--------|------|
| `README.md:135` | 后端 480 通过 | **502** | 陈旧值，需改 |
| `README.md:209` | 后端 480 通过 | **502** | 陈旧值，需改 |
| `README.md:146` | 管理端 118 通过 | **118** | 一致 |
| `README.md:156` | 移动端 25 通过 | **25** | 一致 |

`README.md` 是评审打开仓库第一眼看到的文档，这两处必须修正。

**成因已定位（QA）**：480 这个数于 `09082a0`（2026-10-04）写入，此后又有 5 个提交新增 22 个用例（`dc281ee`/`a71c194`/`39011c9`/`1ad9fdb`/`f6a0ca1`），而最后一次触碰 README 的 `806a942` 未同步。即 480 既不对应旧值、也不对应任何一次真实跑批口径——`:135` 原文自述"480 个（含 12 个 H2 集成测试）"，若含 12 集成则单元应为 468，而实测单元是 490，该行**内部都不自洽**。

> **★ 防坑提醒（运维提出，总监采纳）**：master 的 README 写 464、与 master 代码**自洽**（464/464）；dev 的 README 写 480、与 dev 代码**不自洽**（480/502）。这意味着 master 是"停留在旧版本且文档自洽"的快照，dev 是"代码前进了但文档回填漏一拍"的工作分支。**整改时应以 dev 实测 502 为准回填，绝不可把 master README 的 464 误当成正确基准抄回去。**

---

## 六、交付基线治理（本轮最高优先级）

### 6.1 事实

| 远端 | 默认分支 | 提交 | 与 dev 关系 | 论文 | 答辩 PPT | 测试报告 |
|------|----------|------|-------------|------|----------|----------|
| Gitee | `master` | `5cc3135` | 落后 **52** 提交（严格祖先，可快进） | **缺** | **缺** | **缺** |
| GitHub | `codex/fix-functional-gaps` | `5951d3a` | 分叉：dev 独有 102、该分支独有 8 | **缺** | **缺** | **缺** |
| — | `dev/iteration-next` | `38389df` | 工作区与两远端一致 | 有 | 有 | 有 |

GitHub 上另有 5 个 `codex/*` 遗留实验分支。

### 6.2 影响

1. 评阅老师通过仓库链接打开，**默认看到的是没有论文、没有答辩 PPT 的分支**
2. GitHub 默认分支后端测试文件仅 29 个（dev 为 44 个），与论文声明的 490/502 基线冲突
3. master 上 SEC-03 登录锁定、nginx CSP、XFF 覆盖式改写三项安全修复未合入（`LoginAttemptService`、`CacheConfig` 在 master 上不存在）
4. **master 基线应判 P0 而非 P1**：架构师与运维独立得出同一结论——master 的 compose 仍挂载 `sql/data.sql`（`master:deploy/docker-compose.production.yml:15`），**会把 22 个弱口令演示账号导入生产库**（SEC-01 违规回归）。这不是"配置偏好"而是可被直接利用的安全缺陷，故 master 基线的 verdict 为 **fail（P0）**，dev 基线为 **pass**。

> 定级校准说明：架构师主动建议将自己原报告中的「master 无 JWT HS256 白名单」由 P0 降为 P1，理由是 `application-prod.yml:29` 用占位符语法、缺省即启动失败，而 `master:application-prod.yml:41` 直接硬编码 `secret: ${JWT_SECRET}`，**master 上该漏洞并不存在**。此项不阻断——建议采纳，避免 P0 清单注水。运维亦确认该项在其清单中本就不是独立 P0，只是佐证子项；按可利用性单独定级为 P1（纵深防御缺失，当前无直接利用路径）。

### 6.2.1 决定性量化证据：master 后端实测 464、集成测试 0 个

运维用 `git archive master backend | tar -x` 到临时目录后实跑（不污染工作树）：

| 分支 | 实测总数 | 单元测试 | **集成测试** | 报告类数 | 结果 |
|------|----------|----------|--------------|----------|------|
| **master** | **464** | 464 | **0** | 40 | BUILD SUCCESS |
| **dev/iteration-next** | **502** | 490 | **12** | 47 | BUILD SUCCESS |

master 的 `backend/src/test/java/com/pzhu/eduadmin/` 下**不存在 `integration/` 目录**（`git ls-tree -r master -- backend/src/test` 确认），即论文表 5.6 所载「12 个 H2 真实数据库集成测试」在 master 上**一个都不存在**。

由此，论文/答辩材料与 master 的口径冲突被量化：

| 材料 | 声称 | master 实测 | 冲突 |
|------|------|-------------|------|
| 论文摘要 / 第5章表5.6 / 答辩讲稿 / 问答预案 | 后端 502（490 单元 + **12 集成**） | **464，集成 = 0** | 差 **38** 个用例；**「12 个集成测试」在 master 上为 0/12** |
| master `README.md:118` | 后端 464 | 464 | 一致 |
| dev `README.md:135/:209` | 后端 480 | dev 实测 502 | 差 22（dev README 落后） |

**后果**：若答辩或评阅检出 master，「12 个真实数据库集成测试」这一整条答辩论据当场失效——不是数字偏差，而是论据归零。

**处置建议（运维提出，总监采纳）**：最省事的处置不是改论文，而是**锁死检出分支**——答辩前确认演示机/评阅检出的 SHA 为 `dev/iteration-next` 头（或合入后的 master 头），并在 README 首屏写明「论文与答辩数据对应此提交」。

### 6.2.2 测试文件数与版本号的分支漂移（总监补充实测）

| 项 | master | dev/iteration-next | 说明 |
|----|--------|---------------------|------|
| 后端测试文件 | **37 个** | **45 个** | master 缺 8 个（QA 实测） |
| 后端 `pom.xml` 版本 | **0.9.0** | **1.3.0** | **总监实测确认**：`master:backend/pom.xml:17` 为 `<version>0.9.0</version>` |

即：若评阅检出 master，看到的后端版本是 **0.9.0**，而论文、答辩 PPT、讲稿、README 全部声明 **1.3.0**。这不是小偏差——版本号是评审最先核对的元数据之一。

> 注（QA 纠正）：运维原报「master 缺 `ProductionConfigurationValidatorTest`」不成立——该文件在 master 上**存在**（`master:backend/src/test/java/com/pzhu/eduadmin/config/ProductionConfigurationValidatorTest.java`，3 个用例），运维当时是在工作区跑的、实际读的是 dev。此项已从阻断清单剔除。

### 6.3 建议

推荐基线：**`dev/iteration-next@38389df`**（唯一同时具备全部安全修复、V9 索引、Redis 缓存与毕设交付物的分支，且 master 是其严格祖先，合入方向应为 `master ← dev` 快进）。

需在网页端完成（本机无法修改远端默认分支）：
1. Gitee / GitHub 双端默认分支指向 `dev/iteration-next`，或将 dev 快进合入 master 并以 master 为基线
2. 清理 5 个 `codex/*` 遗留分支

### 6.4 切面 E（运维）阻断项清单

> 运维 verdict：**fail（仅当基线为 master）**。5 条阻断中 4 条的根因都是基线错配，改指 dev 后自动消解；仅 B6 在任意基线下都成立。

| ID | 级别 | 项 | 总监复核 |
|----|------|-----|----------|
| B2 | P1 | master 缺 SEC-03 登录锁定、SEC-04 CSP、SEC-05 XFF 覆盖式改写（`LoginAttemptService` / nginx CSP / XFF 三项在 master 上不存在） | 与架构师 C 切面一致 |
| B3 | **P0** | **master 生产 compose 仍挂载 `data.sql`**，SEC-02 违规回归：22 个弱口令演示账号会被导入生产库 | **已复核**：`master:deploy/docker-compose.production.yml:15` 挂载 `./data.sql`；dev 已改为只挂 `schema.sql`（`:41-44` 有 SEC-02 注释） |
| B4 | **P0** | master 回滚脚本仍带 `--remove-orphans` + 显式列举部分服务，`down` 时**会删除 mysql 服务及其数据卷** | 与第三轮已修项同源，master 未合入 |
| B5 | P1 | master web 无 `healthcheck`，但回滚脚本等待 `healthy` 条件 → **回滚必然超时失败**（已用 compose `config` 验证） | 运维已用配置解析验证 |
| B6 | **P0** | **GitHub 侧定时备份实际不会运行**：`schedule` 触发器只在默认分支存在该工作流时才创建，而 GitHub 默认分支 `codex/fix-functional-gaps` 只有 `ci.yml`/`deploy.yml`，**无 `backup.yml`**；`workflow_dispatch` 也因此不可手动触发 | **已复核**：`git ls-tree` 确认该分支 workflows 仅 2 个；dev 的 `backup.yml:5` 为 cron `15 19 * * *` |
| B8 | P2 | README 4 处测试数与实测不符：行 135 与 209 写「后端 480 通过」，实测 502 | **已复核确认**（本项为我早前扫描的遗漏——只扫 `docs/*.md` 未扫根目录 README） |
| B9 | P2 | `05-详细设计说明书.md:118` 仍写「V8」，实际 V9（compose 挂载最新 schema 自动漂移，无需重建库） | — |
| B10 | P2 | `BACKUP_AND_RESTORE.md:9` 写「四容器」+ `restore-drill.sh` grep 三容器，实际为 **mysql+backend+web+cache 五容器** | — |
| B11 | P2 | `BACKUP_AND_RESTORE.md:96` 称「Redis 未启用」，实际已启用（`spring.cache.type: redis` + compose `cache` 服务 + 128MB LRU） | — |
| B13 | P2 | `README.md:160` 指向 `ui-test-plan-browser-ai-2026-09-10.md`（AI 内部验收计划），应改为交付用的 `18-测试报告.md` | — |

**运维对 B1/B2/B4/B5/B7 的判据说明**：这些均以 master 为基线才成立，与架构师 C 切面独立得出同一结论（两人互不知情），互为佐证。若基线改指 `dev/iteration-next`：B1/B3/B4/B5/B7 自动消解，B11 需同步文档，B12 保留为提醒（dev 的 `docker-compose.yml:9` 未设 Redis 密码，依赖 Caddy 全量转 nginx 且 mysql/cache 不映射公网端口）。

---

## 七、待决策项

| 项 | 级别 | 说明 |
|----|------|------|
| 交付基线统一 | **P0（治理）** | 需用户在网页端操作，见 §6。此项一决定，E 切面 5 条阻断中 4 条自动消解 |
| 定时备份从未真正运行 | **P0** | GitHub 侧 `backup.yml` 因默认分支错配从未触发（B6）。基线修正后需确认定时任务确实创建 |
| README 测试数 480 → 502 | P2 | 2 处，评审第一眼可见，5 分钟工作量 |
| 3 条业务 P0 是否修复 | P0 | 涉及算错钱与课时丢失，建议修复；均已有明确定位 |
| emoji 残留 1 处 | P0 | 5 分钟工作量 |
| 硬编码颜色 748 处 | P1 | 毕设尺度下建议「不修、只说明」；若修需 1 天 |
| 管理端窄屏 | P1 | 毕设不演示窄屏，建议不修 |
| Spring Boot 3.2.5 → 3.2.14+ | P2 | 8 个已披露 CVE，实际可达性低（推断） |
| 无告警体系 / 明文 HTTP / 生产弱口令 | P1 | 前轮已记录，用户明确不做 |

---

## 八、本轮方法论沉淀

1. **排他性断言必须穷举验证后才能写入报告**（架构师提出）。反面样例：架构师用不含 `insert` 的 grep 模式得出「唯一写库路径」；后端专家用大小写敏感模式差点漏检 `setSourceLessonId`。
2. **「controller 侧缺失校验」的判定落点应是「客户端可控值能否进入最终 SQL」，而非「哪一层写了剥离」**（架构师终版）。本项目业务规则多下沉在 service 层，只看 controller 表象会系统性高估缺陷。
3. **缺陷清单不放复现不出来的项**——QA 去验验不出来，会稀释整份报告的置信度。
4. 数字入报告前一律脚本实测，禁止沿用旧值。

---

## 九、治本建议：把「声明-实现一致性」做成发布门禁（架构师提出）

本轮发现的问题有一个共同形态：**文档/声明与代码实测值脱节**。它已重复出现四次，说明靠人工记忆无法根治：

| # | 形态 | 实例 |
|---|------|------|
| 1 | README 测试数 | 480 vs 实测 502 |
| 2 | README 版本号 | 声称 v1.3.0，master 三端实际 0.9.0 |
| 3 | 文档 schema 版本 | `05-详细设计说明书.md:118` 写 V8，实际 V9 |
| 4 | 文档容器数 | `BACKUP_AND_RESTORE.md:9` 写四容器，实际五容器 |

前三次均为人工回填时漏一拍。**建议在 deploy 门禁里固化三项可机检断言**，任何一项不符即阻断发布：

1. `README.md` 中的后端用例数 == 实跑 surefire 汇总数
2. `README.md` / 答辩材料中的版本号 == `backend/pom.xml` + 两端 `package.json` + `manifest.json` 实读值
3. 文档中的 schema 版本 == `EXPECTED_SCHEMA_VERSION`

**同时建议引入单一真源**：把版本号、测试数、schema 版本集中到一个 `VERSIONS.md` 或 CI 生成的制品里，README、论文、答辩材料统一引用，杜绝多份手写字面量。

> 毕设尺度下的现实取舍：以上属工程洁癖，**若时间紧张可只做第 1、2 项**（成本极低，收益最高——版本号和测试数恰是评审最先核对的两项元数据）。

---

## 十、审核过程统计

| 项 | 数值 |
|----|------|
| 并行切面 | 5（测试承重 / 业务逻辑 / 安全架构 / 前端质量 / 部署基线） |
| P0 缺陷 | 4（3 业务 + 1 前端） |
| P1 缺陷 | 16 |
| P2 建议 | 20+ |
| 总监独立复核项 | 11（含 4 条 P0 全部逐行读码复核） |
| 交叉验证剔除的假条目 | 3 |
| 总监自身误判纠正 | 3 |
| 专家主动撤回/降级的条目 | 3（架构师 2、含 1 条 P0→P1 定级校准） |
| 本轮修改的代码/测试/配置 | **0**（纯只读审计） |
