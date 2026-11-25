# 第三轮多方位审核报告（部署·安全·性能·文档）

> **审核对象**：《艺术培训机构全流程教务管理平台的设计与实现》（艺培通 v1.3.0）
> **审核日期**：2026-10-04（第三轮）
> **与前三轮分工**：一轮=是否达标（已整改）；二轮=代码质量/测试有效性/论文-代码一致性（已整改）；**本轮=部署运维、安全深度、性能与数据库、工程文档一致性**
> **方法**：四路独立审查（运维 / 安全 / 性能 / 文档）+ 项目总监**端到端实测**与关键结论二次复核

---

## 一、结论（TL;DR）

**功能真实性得到最硬的证据**：端到端核心链路在真实运行中跑通、账实一致。但本轮发现 **1 项 P0（生产"回滚能力"实际不成立）** 与若干 P1，集中在**部署运维**与**安全细节**。

| 维度 | 判定 | 代表发现 |
|------|:--:|------|
| **端到端功能** | 通过 | 报名→审核→缴费→课时到账；考勤→扣课时→流水，全部实测通过 |
| 部署运维 | **fail（P0）** | web 服务无 healthcheck → `rollback-images.sh` 必然失败；CI 亦无回滚步骤 |
| 安全深度 | 有条件通过 | 未发现可直接利用的漏洞；但 **SEC-02 生产初始化会创建 22 个默认口令账号**（P1） |
| 性能与数据库 | 通过（无 P0） | 零缓存、统计内存聚合、报名快照 N+1；当前数据量可接受，有拐点预警 |
| 工程文档一致性 | 基本通过 | 7 篇文档版本号陈旧（v1.0）；doc13 移动端组件清单失实 |

---

## 二、端到端实测（项目总监亲测，最强证据）

| 链路 | 实测结果 |
|------|------|
| 服务启动 | 后端 `1.3.0` 启动 7s、健康 UP；管理端静态服务 200 |
| 五角色登录 | parent1/teacher1/edu/finance/admin 全部获取 token |
| 家长端 | 查看学员（刘小小/刘子轩）、12 门课程、报名决策快照（含 versionToken/容量/冲突） |
| **链路一：报名→到账** | 提交报名（ID16，待审核）→ 教务审核通过（status=2，设置占位到期）→ 家长缴费（**3000 元 / 20 课时**）→ **课时账户自动到账（课程2：总20 剩余20）** |
| **链路二：考勤→扣课时** | 教师对课次 222 提交到课考勤 → **课时自动扣减（课程11：22→21）** + 生成流水（-1.00，before 22 / after 21） |
| 参数校验/状态机 | 错误调用被正确拒绝（"缺少必要参数：status"、"仅支持 2-通过 或 4-驳回"、"仅待缴费状态的报名可支付"） |

> 测试数据已**备份后回滚**（备份 `.workbuddy/tmp/audit-testdata-backup.txt`），演示库恢复基线（报名 15 / 缴费 15 / 考勤 30 / 流水 50）。

---

## 三、部署与运维（本轮唯一 P0）

### OPS-01【P0】"可回滚"实际不成立（已独立复核）

- `deploy/docker-compose.production.yml`：`backend` 定义了 healthcheck（L39），**`web` 服务没有 healthcheck**（L45-48 只有 image/restart/depends_on）。
- `deploy/scripts/rollback-images.sh:81-82`：要求 web 容器 `.State.Health.Status == "healthy"` 才判定回滚成功。
- **后果**：web 无 healthcheck → `Health.Status` 恒为空 → `healthy=0` → 脚本 `die` 退出，**回滚必然失败**。
- 叠加：`.github/workflows/deploy.yml` **没有任何回滚步骤**（`if: failure` 零命中，rollback-images.sh 只被 scp 上传而未调用）→ 所谓"发布可回滚"在 CI 中根本未接线。

**修复**：给 web 服务补 healthcheck（如 `wget -qO- http://127.0.0.1/ || exit 1`），或在回滚脚本中对 web 改用 running 态判定；并在 CI 加失败自动回滚步骤。

### 其余运维问题

| 编号 | 严重度 | 问题 |
|:--:|:--:|------|
| OPS-02 | P1 | `release-production.sh`（发布门禁）未被 CI 调用，而文档声称门禁生效 |
| OPS-03 | P1 | 部署冒烟测试硬编码 `https://`，而当前生产为 http + IP 形态 |
| OPS-04 | P1 | `backup.yml` 的 known_hosts 硬编码 `8.145.58.241`，与文档所述 `101.35.239.218` 不一致 |
| OPS-05 | P1 | compose 无 CPU/内存资源限制、无日志轮转（log rotation） |
| OPS-06~ | P2 | 镜像未 pin digest、备份未显式异地、Actuator 暴露面等（详见运维审查原始清单） |

**亮点**：backend healthcheck 设计正确（TCP 探测 + start_period 60s）、数据卷持久化、restart 策略、加密备份脚本与恢复演练脚本齐备。

---

## 四、安全深度

**总评**：**8 个维度未发现可直接利用的 P0 漏洞**；注入面极干净（全库 100% 参数化、零 `${}`、零 `v-html`）、上传防护完整（白名单+魔数+路径穿越）、鉴权与行级隔离扎实。

### 实质问题

| 编号 | 严重度 | 问题（已独立复核） |
|:--:|:--:|------|
| **SEC-02** | **P1** | `docker-compose.production.yml:14-15` 把 `schema.sql`/`data.sql` 挂载到 `docker-entrypoint-initdb.d/`，而 `sql/data.sql:4` 注明"全部演示账号密码均为 **123456**" → **生产库首次初始化会创建 22 个弱口令账号**（含 admin）。建议生产不挂载 data.sql，或强制首次登录改密/仅导入必要种子 |
| SEC-03 | P1 | 登录爆破防护用**进程内** `ConcurrentHashMap`（`AuthService:45-46`）→ 多实例失效；且按用户名锁定可被用于**DoS 锁定合法账号**；无 IP 维度限流 |
| SEC-04 | P2 | SSE token 经 URL query 传递（会进反代日志） |
| SEC-05 | P2 | JWT 校验未显式限定算法（未显式 reject `none`/算法混淆） |
| SEC-06 | P2 | `ExamSignup` 等更新接口存在 mass-assignment（整体回传实体） |
| SEC-07 | P2 | 文件下载接口按 ID 读取，缺属主校验（IDOR 面，实测风险低） |
| SEC-08 | P2 | 班级报名容量校验存在窄窗口（无锁），极端并发下理论上可超卖 1 名 |
| SEC-09 | P2 | 无 CSP / X-Frame-Options 等响应头（点击劫持面） |
| SEC-10 | P2 | 依赖版本未逐条核 CVE（**未联网核验**，属提示性）；日志对 PII（手机号）未脱敏 |

> 注：安全审查将 **emoji 图标**（SEC-01）列为 blocking。项目总监复核后**维持既有定性**：emoji 属**专家团团队 P0 规则、非任务书条款**，用户已明确"不急"，故本报告按 **P2 工程质量项**记录，不作为"安全/达标"阻断项。

---

## 五、性能与数据库（verdict = pass，无 P0）

| 编号 | 严重度 | 问题 |
|:--:|:--:|------|
| **P-05** | P1 | **全应用零缓存**：pom 有 spring-boot-starter-data-redis，但 `application.yml:17-20` 显式 exclude 自动配置，且全库无 `@Cacheable`/CacheManager → 仪表盘/统计/风险/报名快照每次请求全量重算 |
| P-03 | P1 | 风险预警：全量加载 + 内存算分 + **内存分页**（每翻页全量重算），summary 再全量重算 |
| P-01/P-02 | P1 | 统计接口在 Java 内存做聚合（近 6 月考勤 join、payment 全行求和 ×7 次调用），未下推 SQL `SUM/GROUP BY` |
| P-04 | P1 | 家长报名快照 per 课程 × 班级 N+1（`detectTimeConflict` 每次 3+ SQL） |
| P-19 | — | `statistics_snapshot` 表设计为预聚合但**全库无写入**，形同虚设（P-01/P-06 的根因） |
| P-07~P-18 | P2 | 若干 N+1（退费取课程名、薪资取班级课程、批量排课冲突检测）、`LIKE '%kw%'` 前置通配 15+ 处、导出无行数上限 |

**缺失索引清单（可直接执行）**：M1 `schedule_lesson(lesson_date,status)`、M2 `payment_record(course_id)`、M3 `class_student(status)`（均为 P1 级）；另有 M4~M7 为 P2。

**结论**：当前演示/单校区数据量下**可接受**；拐点预警——考勤 > 10^5 行、在册学员 > 2000 或课程 > 50 时，P-01/P-03/P-04/P-05 会率先成为瓶颈。

**亮点**：分页插件 + 乐观锁 + 防全表更新插件配置正确；16 个名称填充方法全部批量化（无逐行查询）；空集合 `in()` 前置判空；`afterCommit` 通知。

---

## 六、工程文档一致性

| 编号 | 严重度 | 问题 |
|:--:|:--:|------|
| **DOC-1** | **P1** | `docs/13-移动端与管理后台开发详细文档.md:84-90` 列出 6 个"移动端公共组件"（MobileCourseCard/MobileScheduleList/AttendanceQuickPanel/NoticeList/LessonBalanceCard/UploadAttachment），**实际移动端只有 1 个组件文件** `StudentSwitcher.vue`（已复核）→ 清单失实 |
| DOC-2 | P1 | 7 篇文档头部仍标「v1.0 正式版（2026-07-19）」（05/07/09/10/11/12/13），与 1.3.0 现状不符 |
| DOC-3 | P2 | `docs/08` 模块树只列 13 个包（实为 16）；`docs/10` 表清单缺 4 张表（leave_request/period/student_risk_followup/teacher_course） |

**已确认无问题的项**：docs/01–17 中**无** 464/468/480、"34 表"、"四维冲突"等废弃表述；`docs/16` 与 1.3.0 一致；数字口径（37 表/16 模块/497 测试）一致。

---

## 七、问题清单与建议（按优先级）

| 优先级 | 事项 | 建议动作 |
|:--:|------|------|
| **P0** | OPS-01 web 无 healthcheck 致回滚失效、CI 无回滚 | 补 web healthcheck + CI 接回滚；否则应把"可回滚"从文档/答辩话术中降级 |
| **P1** | SEC-02 生产初始化弱口令账号 | 生产不挂载 `data.sql`，或强制首登改密 |
| **P1** | SEC-03 进程内锁定 + 无 IP 限流 | 引入分布式限流或加 IP 维度 |
| **P1** | DOC-1/DOC-2 doc13 组件失实 + 7 篇版本号陈旧 | 改 doc13 组件表为真实 1 个组件；统一文档版本号 |
| **P1** | OPS-02~05 门禁/冒烟/IP/资源限制 | 按运维清单逐项修 |
| **P1** | P-05 零缓存、P-03 内存分页、P-01/02/04 | 先补 M1/M2/M3 索引；再按需引入缓存/预聚合 |
| P2 | 其余安全/性能/文档项 | 排期优化 |
| — | emoji 图标 | 用户已明确"不急"（专家团规则，非任务书条款） |

---

## 八、三轮审核总览

| 轮次 | 焦点 | 结论 |
|:--:|------|------|
| 第一轮 | 是否达标（功能/技术/成果/论文规范） | 达标；P0 参考文献与 P1 数字**已整改** |
| 第二轮 | 代码质量/安全/测试有效性/论文-代码一致性 | A1–A4、G1/G2、F-01/F-04 **已整改** |
| 第三轮 | 部署运维/安全深度/性能/工程文档 | **功能真实性实测通过**；发现 OPS-01（P0）等，待决策 |

**综合判断**：系统**功能真实、可运行、核心闭环正确**；工程质量在中上水平（注入面干净、并发控制扎实、分页与批量到位）。剩余问题**不改变"是否达标"**，但 **OPS-01（回滚）** 与 **SEC-02（默认口令）** 属上线前应处理的实质项。

---

---

## 九、第三轮整改闭环（当日完成，含复测证据）

> 用户指令："完成所有发现的吧，我开 redis，你重新试试"。以下每项均由本项目负责人亲自实施并复测，不采信执行者自述。

### 9.1 部署运维

| 编号 | 处置 | 验证方式与证据 |
|:--:|------|------|
| OPS-01 (P0) | web 补 healthcheck；CI 增加失败自动回滚；补齐回滚输入 `release-evidence/release-state.env` | compose YAML 解析通过，五服务健康检查齐备；`rollback-images.sh` 输入契约五字段与 CI 写入字段逐一对齐 |
| OPS-02 (P1) | **定位到门禁不可运行的真实根因**：`acceptance_test.sh` 在仓库中不存在，而 `release-production.sh:82` 硬校验该文件缺失即 `die`。补写只读五角色冒烟脚本并进 CI | 见 §9.5 承重验证 |
| OPS-03 (P1) | 冒烟 scheme 变量化 | 已由 `${DEPLOY_SMOKE_SCHEME:-https}` + `DEPLOY_SITE_ADDRESS` 组合推导 |
| OPS-04 (P1) | known_hosts 硬编码 → `secrets.DEPLOY_KNOWN_HOSTS` | — |
| OPS-05 (P1) | 四服务资源限制 + 日志轮转 + JVM `-Xmx512m` | compose 解析通过 |
| **新发现** | `rollback-images.sh:74` 原为 `up -d --remove-orphans backend web caddy`：仅列举三个服务却带 `--remove-orphans`，一旦 Compose 将未列举服务判为 orphan，会把 **mysql** 一并清除——回滚反而造成更大故障。已去掉该标志 | 人工复核 Compose 语义后修改并加注释说明 |
| **新发现** | `depends_on` 长短格式混用是非法 YAML，导致 compose 文件直接解析失败 | Python `yaml.safe_load` 实测：修复前 `ParserError`，修复后通过 |

### 9.2 安全

| 编号 | 处置 |
|:--:|------|
| SEC-02 (P1) | 生产 compose 移除 `data.sql` 挂载（22 个弱口令演示账号不再被导入） |
| SEC-03 (P1) | 新增 `LoginAttemptService`：Redis 为主 + 进程内降级，账号/IP 双维度；nginx 的 XFF 改为覆盖式 `$remote_addr`（原追加式会保留客户端伪造前缀，且容器内 `getRemoteAddr` 恒为 nginx 地址） |
| SEC-05 (P2) | `JwtUtil.parseToken` 增加算法白名单前置校验，显式拒绝非 HS256 与 `alg=none` |
| SEC-06 (P2) | **复核为已缓解**：`ExamServiceImpl.updateExamSignup` 已剥离 studentId/examId/createTime/updateTime，`isDeleted` 为 `@JsonIgnore`，无需改动 |
| SEC-07 (P2) | **复核为已缓解**：`FileStorageService` 采用 UUID 文件名（不可枚举）+ `STORED_NAME_PATTERN` 正则白名单 + `target.startsWith(uploadRoot)` 容器校验；鉴权由拦截器覆盖，无需改动 |
| SEC-09 (P2) | nginx 补充 CSP；并在静态资源 location 重复声明安全头（nginx 语义：子 location 一旦自行 `add_header`，父级全部失效） |

### 9.3 性能与数据库

| 编号 | 处置 |
|:--:|------|
| P-05 (P1) | 启用 `spring.cache.type: redis` + `@EnableCaching`，新增 `CacheConfig` 容错错误处理器（读失败回落真实查询、写/失效失败仅记日志）；测试 profile 降级为 `simple` |
| P-04 / N+1 | `ParentRefundController` 两处课程名 `selectById` 循环改为 `selectBatchIds` |
| 索引 M1/M2/M3 | 新增 Flyway **V9**，且按双轨约定同步进 `sql/schema.sql`；`EXPECTED_SCHEMA_VERSION` 8 → **9**（`deploy.yml` / `release-production.sh` / `RELEASE_RUNBOOK.md` / `docs/10`） |
| 未做（有理由） | P-01/P-02 统计内存聚合下推 SQL、P-03 风险内存分页：属重构级改动。报告结论为"当前数据量可接受"，且 V9 索引与缓存已是该报告推荐的**第一步**（§7 建议顺序） |

### 9.4 Redis 缺失导致的新增问题（自发现并修复）

生产 compose **原本没有 Redis 服务**，而 `application.yml` 已启用 redis 缓存：上线后每次缓存访问都会去连不存在端点，Lettuce 默认命令超时甚至可能将接口拖至分钟级。处置：

1. compose 增加 `cache` 服务（redis:7-alpine，128MB 内存上限 + LRU，仅启动顺序依赖、**不用** `service_healthy`，保证 Redis 挂掉后端仍能启动）；
2. 压短超时为 1s，使"降级"真正快速生效。

### 9.5 承重验证（注入坏样确认会变红，而非只看通过）

| 验证 | 命令/方式 | 结果 |
|------|------|------|
| 后端全量测试 | `mvn -o test` | **497 通过 / 0 失败 / 0 错误 / 0 跳过**（脚本统计 surefire：485 单元 + 12 集成），较基线 484 净增 13 |
| 双维度计数（真 Redis） | 连续 6 次错误密码 | 第 6 次返回 429；Redis 出现 `auth:login-fail:user:probe-user` 与 `auth:login-fail:ip:127.0.0.1:ip` **两个键**；TTL=849s（≈15min） |
| 成功后清除 | admin 正常登录 | IP 维度键消失（账号维度键保留），符合同 IP 多账号设计 |
| 缓存效果实测 | `/api/admin/dashboard` 连续两次 | 首次 **147ms** → 二次 **16.6ms**（约 9 倍）；Redis 键 `dashboard::SimpleKey []` 存在，值 1849 字节 |
| 验收脚本正向 | `SMOKE_PASSWORD=123456 bash acceptance_test.sh http://127.0.0.1:8080` | 10/10 通过，EXIT=0 |
| 验收脚本负向 | 坏密码 / 不可达地址 | EXIT=1 且给出明确失败原因（承重通过） |
| 验收脚本 python 可移植约定 | `test-acceptance-python-runtime.sh` | 通过 |
| compose 语法 | `yaml.safe_load` | 通过 |

### 9.6 数字口径同步（脚本实测，非心算）

新增 13 个用例使后端口径由 **484（472 单元 + 12 集成）** 变为 **497（485 单元 + 12 集成）**。已同步：

- `docs/thesis/00-封面与摘要.md`（中英文摘要）、`docs/thesis/06-第5章-系统测试.md`（表 5.6 + 小结）
- `docs/defence/答辩讲稿.md`、`docs/defence/答辩问答预案.md`
- `.workbuddy/tmp/md2docx.py`（该脚本**硬编码摘要**，不同步会造成 md 与 docx 分叉——历史上已踩过此坑）
- 论文 docx 已重新生成，并复核摘要与表 5.6 均已为 485/12
- 历史快照（round2 报告、2026-07-19 验收矩阵、2026-09-25 交接索引、00 文档说明版本行）**按约定冻结，不改**

### 9.7 遗留

| 项 | 状态 |
|------|------|
| 论文封面占位符（学号/院系/导师/日期） | 待用户提供 |
| emoji 图标（16 文件）/ 硬编码颜色 | 用户明确"不急"，维持 P2 工程质量项定性 |
| OPS-02 的 CI 门禁 | 已接线，但需 GitHub Environment 配置 `DEPLOY_SMOKE_PASSWORD` 才实际执行；未配置时 CI 会**显式告警并跳过**（不静默假装通过） |
| SEC-10 依赖 CVE | 未联网核验，属提示性 |

---

> 本报告由 MVP 开发专家团项目总监统筹，运维/安全/性能/文档四路独立审查 + 端到端实测交叉验证。
> 关键结论（OPS-01、OPS-02、SEC-03、SEC-05、P-05、V9、数字口径）均经项目总监亲自复现与实测。
