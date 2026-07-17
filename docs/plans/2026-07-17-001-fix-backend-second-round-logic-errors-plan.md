---
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
execution: code
product_contract_source: ce-plan-bootstrap
created: 2026-07-17
---

# fix: 后端第二轮 34 个逻辑错误修复计划

## Summary

修复教务管理系统后端第二轮审查发现的 34 个逻辑错误，分 3 个阶段 11 个实施单元执行。第一阶段修复 12 个严重问题（状态码冲突、退费资金漏洞、安全基线）；第二阶段修复 14 个中等问题（数据一致性、校验完善）；第三阶段修复 8 个轻微问题（防御性编程、代码规范）。

## Problem Frame

第一轮修复（2026-07-16，42 个 Bug）后，第二轮审查又发现 34 个逻辑错误。最严重的问题包括：Enrollment 状态码语义冲突导致被驳回的报名可以缴费（直接财务损失）、退费流程多个环节存在资金漏洞（课时不归零、重复审核、金额按原价计算）、JWT Token 无吊销机制（禁用用户仍可使用旧 Token）。这些问题按严重程度分为严重（12 个）、中等（14 个）、轻微（8 个），需分批有序修复。

## Requirements

- R1: 12 个严重问题必须全部修复，消除资金漏洞和安全风险
- R2: 14 个中等问题应当修复，提升数据一致性和业务校验完整性
- R3: 8 个轻微问题尽量修复，提升防御性编程水平
- R4: 每个修复必须通过 `mvn test` 验证，不引入回归
- R5: 修复按模块聚合、按依赖排序，减少上下文切换

## Key Technical Decisions

- KTD1: **Enrollment 状态码统一定义** — 1=待审核, 2=审核通过(待缴费), 3=已缴费, 4=已驳回, 5=已失效。audit() 中"驳回"从 status=3 改为 status=4，与 create() 中排除 status=4 的逻辑对齐
- KTD2: **退费审核并发控制使用乐观锁** — RefundRecord 表增加 version 字段（或用 `WHERE status = 1` 的原子 UPDATE），通过 affected rows 判断是否抢到锁。与项目中 LessonAccount 已有的乐观锁模式一致
- KTD3: **退费金额基于实际缴费单价计算** — 用 `totalPaid / totalLessons * remainingLessons` 替代 `course.price / course.totalLessons * remainingLessons`，确保折扣场景下退费不超额
- KTD4: **安全加固采用最小侵入方式** — JWT 吊销用 User 表 version 字段对比（避免引入 Redis 依赖）；登录限流用内存 ConcurrentHashMap 计数（单机足够）

---

## Phase 1: 严重问题 — 资金安全与核心漏洞（12 个）

### U1. 统一 Enrollment 状态码语义

**Goal:** 修复状态码冲突（Issue #1）+ 退费驳回不再覆盖金额（Issue #16）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/service/EnrollmentServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/controller/EnrollmentController.java`
- `backend/src/test/java/com/pzhu/eduadmin/EnrollmentStatusTest.java`（新建）

**Approach:**

EnrollmentServiceImpl.audit() 中 `status=3` 的含义从"驳回"改为 status=4。具体修改：

1. `EnrollmentServiceImpl.audit()` 第 175 行：将 `status != 2 && status != 3` 改为 `status != 2 && status != 4`，即通过=2，驳回=4
2. `FinanceServiceImpl.createPayment()` 第 101 行的逻辑无需改动（status=2 审核通过、status=3 已缴费可续费，均正确）
3. `FinanceServiceImpl.auditRefund()` 第 226 行：将金额更新逻辑 `if (refundAmount != null && ...)` 移入 `if (status == 2)` 分支内，确保驳回时不修改金额
4. 更新 `EnrollmentController.audit()` 的 `@RequestParam` 注释，明确 status=2 通过、status=4 驳回
5. 数据库层面：如有已存在的 status=3 驳回记录需手动迁移为 status=4

**Patterns to follow:** EnrollmentServiceImpl.create() 第 134 行已经使用 `ne(status, 4)` 排除驳回记录，统一后语义一致。

**Test scenarios:**
- 报名审核驳回（status=4）后尝试缴费：`BusinessException(409, "仅审核通过或已缴费的报名可缴费")`
- 报名审核通过（status=2）后缴费：成功
- 已缴费（status=3）后再次缴费（续费）：成功
- 已驳回（status=4）后重新报名：成功（create() 中 ne(status, 4) 不阻止）
- 退费驳回时传入 refundAmount：金额字段不被修改

**Verification:** `mvn test` 全部通过；手动测试 报名→驳回→重新报名 和 报名→通过→缴费 两条链路。

---

### U2. 完善退费创建校验与审核流程

**Goal:** 修复 createRefund 无校验（#2）、退费金额按原价计算（#3）、课时 null 归零（#4）、重复审核（#5）、缴费插入顺序（#6）
**Requirements:** R1
**Dependencies:** U1（状态码统一后才能正确校验 enrollment 状态）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/FinanceServiceMockTest.java`

**Approach:**

**createRefund 校验（Issue #2）：** 在 insert 之前增加：
1. `enrollmentId` 非空且 enrollment 存在性校验
2. enrollment 状态为已缴费（status=3）
3. `lessonCount` 非空且为正数
4. 同一 enrollment 不存在待审核（status=1）的退费记录（防重复提交）
5. 添加 `@Transactional(rollbackFor = Exception.class)`

**退费金额按实际缴费计算（Issue #3）：** 在 `processRefundApproval` 第 246-261 行，将自动计算公式从 `remainingLessons * (course.price / course.totalLessons)` 改为：
```
totalPaid = paymentRecordMapper.sumByEnrollmentId(enrollmentId)
totalLessonsBought = account.getTotalLessons()
pricePerLesson = totalPaid / totalLessonsBought
autoRefundAmount = remainingLessons * pricePerLesson
```
当 autoRefundAmount 超过 maxRefundable 时，记录 log.warn 并在返回信息中告知操作者金额被调整。

**课时 null 强制处理（Issue #4）：** 在 `processRefundApproval` 第 282 行，将 `lessonCount` 为 null 的情况改为根据退费金额反算：
```
if (record.getLessonCount() == null) {
    refundLessonCount = refundAmount / pricePerLesson（向上取整）
}
```
如果无法反算（pricePerLesson 为 0 或 null），抛出 `BusinessException(400, "退费课时数不能为空")`。

**并发审核控制（Issue #5）：** 将 auditRefund 中的状态检查改为原子操作：
```
int rows = refundRecordMapper.update(null, 
    new LambdaUpdateWrapper<RefundRecord>()
        .eq(RefundRecord::getId, id)
        .eq(RefundRecord::getStatus, 1)
        .set(RefundRecord::getStatus, status)
        .set(RefundRecord::getAuditorId, auditorId));
if (rows == 0) throw new BusinessException(409, "该退费申请已被处理");
```
然后再执行 processRefundApproval（此时已通过原子更新确保只有一个请求能进入）。

**缴费插入顺序（Issue #6）：** 将 `createPayment` 第 97 行的 `paymentRecordMapper.insert(record)` 移到 enrollment 校验（第 99-103 行）之后、课时账户操作之前。

**Test scenarios:**
- createRefund 传入不存在的 enrollmentId：`BusinessException(404)`
- createRefund 传入未缴费的 enrollment：`BusinessException(409)`
- createRefund lessonCount 为 null：`BusinessException(400)`
- 同一 enrollment 重复提交退费：`BusinessException(409, "已有待审核的退费申请")`
- 退费金额自动计算：学员缴费 8000 元购买 40 课时（原价 250/课时），剩余 20 课时 → 退费 4000 元（非 5000 元）
- 退费 lessonCount 为 null 但能反算：自动计算课时数
- 退费 lessonCount 为 null 且无法反算：`BusinessException(400)`
- 并发审核同一退费：仅一个成功，另一个 `BusinessException(409)`
- 缴费记录在 enrollment 校验失败时不被插入

**Verification:** `mvn test` 全部通过；手动测试折扣场景退费金额、并发审核。

---

### U3. 安全基线加固

**Goal:** 修复 JWT Token 无吊销（#8）、登录暴力破解（#9）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/security/JwtInterceptor.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/auth/service/AuthService.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/entity/User.java`
- `backend/src/test/java/com/pzhu/eduadmin/SecurityTest.java`（新建）

**Approach:**

**JWT Token 吊销（Issue #8）：** 采用 User 表 version 字段方案：
1. User 实体增加 `version` 字段（Integer，默认 0）
2. AuthService.login() 生成 Token 时将 `version` 写入 JWT claims
3. JwtInterceptor.preHandle() 从 Token 解析 version，查询数据库比对 User.version。不一致则 401
4. 管理员禁用用户或变更角色时，递增 User.version（使旧 Token 自动失效）
5. 性能优化：可后续加 Redis 缓存 User.version，当前先直接查库（用户量小，性能可接受）

**登录暴力破解防护（Issue #9）：**
1. AuthService 增加 `ConcurrentHashMap<String, AtomicInteger> loginAttempts` 字段
2. 登录失败时递增计数（key = username），连续 5 次失败后锁定 15 分钟
3. 登录成功时清除计数
4. 统一错误提示为"用户名或密码错误"，消除用户名枚举

**Test scenarios:**
- 用户被禁用后使用旧 Token 访问：返回 401
- 用户角色被降级后使用旧 Token 访问高级接口：返回 403
- 连续 5 次登录失败后第 6 次：返回 429 "账号已锁定，请 15 分钟后再试"
- 使用不存在的用户名登录：返回"用户名或密码错误"（与密码错误提示一致）
- 15 分钟后再次登录：锁定解除

**Verification:** `mvn test` 通过；手动测试禁用用户后旧 Token 失效、连续登录失败锁定。

---

### U4. 权限与用户管理修复

**Goal:** 修复公告无权限（#10）、角色删除无关联检查（#11）、重置密码无校验（#12）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/notice/controller/NoticeController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/service/RoleServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/service/UserServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/controller/UserController.java`

**Approach:**

**公告权限（Issue #10）：** NoticeController 的 `list()` 和 `get()` 方法添加 `@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})`。同时 `listLegacy()` 和 `getLegacy()` 也添加同样的注解。

**角色删除检查（Issue #11）：** RoleServiceImpl.deleteRole() 在删除前查询 `userMapper.selectCount(eq(User::getRoleCode, roleCode))`，若 > 0 则抛出 `BusinessException(409, "该角色下仍有用户，请先迁移用户后再删除")`。

**重置密码校验（Issue #12）：** UserServiceImpl.resetPassword() 增加：
```
if (newPassword == null || newPassword.isBlank()) throw new BusinessException(400, "新密码不能为空");
if (newPassword.length() < 6) throw new BusinessException(400, "新密码长度不能少于6位");
```
UserController 的 resetPassword 端点增加对 Map 中 newPassword 的非空检查。

**Test scenarios:**
- PARENT 角色访问 GET /api/admin/notices：返回 403
- EDU_ADMIN 访问 GET /api/admin/notices：成功
- 删除有用户的角色：`BusinessException(409)`
- 删除无用户的角色：成功
- resetPassword 传 newPassword=null：`BusinessException(400)`
- resetPassword 传 newPassword=""：`BusinessException(400)`
- resetPassword 传 newPassword="123"：`BusinessException(400, "长度不能少于6位")`

**Verification:** `mvn test` 通过。

---

## Phase 2: 中等问题 — 数据一致性与校验完善（14 个）

### U5. 报名模块完善

**Goal:** 修复重复报名检查遗漏（#13）、删除报名无关联检查（#14）
**Requirements:** R2
**Dependencies:** U1（状态码统一后检查逻辑才正确）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/service/EnrollmentServiceImpl.java`

**Approach:**

**重复报名检查（Issue #13）：** 将 create() 第 131-137 行的 `.ne(status, 4)` 改为 `.in(status, List.of(2, 3))`，仅检查"活跃"状态（审核通过和已缴费）的报名。已驳回(4)、已失效(5)的记录不再阻止重新报名。

**删除关联检查（Issue #14）：** delete() 方法在删除前增加：
1. `paymentRecordMapper.selectCount(eq(enrollmentId, id))` > 0 → `BusinessException(409, "该报名已有缴费记录，无法删除")`
2. `refundRecordMapper.selectCount(eq(enrollmentId, id))` > 0 → `BusinessException(409, "该报名已有退费记录，无法删除")`
3. 操作日志移到 deleteById 成功之后记录

**Test scenarios:**
- 已失效(5)的报名重新报名：成功
- 已退费(6)的报名重新报名：成功
- 审核通过(2)的报名重复报名：`BusinessException(409)`
- 已缴费(3)的报名重复报名：`BusinessException(409)`
- 删除有缴费记录的报名：`BusinessException(409)`
- 删除无关联记录的报名：成功

**Verification:** `mvn test` 通过。

---

### U6. 退费退班逻辑完善

**Goal:** 修复退费后过度退班（#15）
**Requirements:** R2
**Dependencies:** U2（退费流程核心修复后）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`

**Approach:**

**Issue #15：** processRefundApproval() 第 307-321 行，退费后退出班级的逻辑增加 remainingLessons 判断：
```
// 仅在课时归零时才将学员从班级退出
if (account.getRemainingLessons().compareTo(BigDecimal.ZERO) <= 0) {
    // 执行退班逻辑
}
```
部分退费（remainingLessons > 0）时学员保留在班级中，可继续上课。

**Test scenarios:**
- 全额退费（remainingLessons=0）：学员从班级退出
- 部分退费（remainingLessons > 0）：学员仍在班级中
- 退费后课时为 0.5（还有余额）：学员仍在班级中

**Verification:** `mvn test` 通过。

---

### U7. 薪资模块完善

**Goal:** 修复空 IN 子句（#17）、缺失事务（#18）、规则唯一性（#19）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/salary/service/SalaryServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/SalaryServiceMockTest.java`

**Approach:**

**空 IN 子句（Issue #17）：** calculateSalary() 中 allLessonIds 为空时直接跳过考勤查询：
```
Map<Long, Integer> lessonAttendanceMap = Collections.emptyMap();
if (!allLessonIds.isEmpty()) {
    // 原有的考勤查询逻辑
}
```

**缺失事务（Issue #18）：** calculateSalary() 方法添加 `@Transactional(rollbackFor = Exception.class)` 注解。

**规则唯一性（Issue #19）：** createSalaryRule() 在 insert 前查询：
```
Long existCount = salaryRuleMapper.selectCount(
    new LambdaQueryWrapper<SalaryRule>()
        .eq(SalaryRule::getTeacherId, rule.getTeacherId())
        .eq(SalaryRule::getCourseId, rule.getCourseId()));
if (existCount > 0) throw new BusinessException(409, "该教师在此课程已有薪资规则");
```

**Test scenarios:**
- 教师当月无课次：薪资计算不报错，基本薪资为 0
- 并发对同一教师同月核算薪资：仅一个成功
- 为已有规则的教师+课程创建规则：`BusinessException(409)`
- 正常创建新规则：成功

**Verification:** `mvn test` 通过。

---

### U8. 课程/班级模块完善

**Goal:** 修复更新无校验（#20）、删除未检查排课（#21）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/service/CourseServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/schedule/mapper/ScheduleLessonMapper.java`

**Approach:**

**更新校验（Issue #20）：** updateCourse() 改为白名单更新，仅允许修改 name、description、price、totalLessons 等非关键字段。如果已有关联的 payment_record 或 enrollment，则禁止修改 price 和 totalLessons：
```
Long paymentCount = paymentRecordMapper.selectCount(eq(PaymentRecord::getCourseId, course.getId()));
if (paymentCount > 0 && (course.getPrice() != null || course.getTotalLessons() != null)) {
    throw new BusinessException(409, "该课程已有缴费记录，不可修改价格和课时数");
}
```
updateClassGroup() 仅允许修改 className、maxStudentCount、description。

**删除检查排课（Issue #21）：** deleteCourse() 增加对 schedule_lesson 的检查：
```
Long futureLessonCount = scheduleLessonMapper.selectCount(
    new LambdaQueryWrapper<ScheduleLesson>()
        .eq(ScheduleLesson::getCourseId, id)  // 需确认字段
        .in(ScheduleLesson::getStatus, 1, 2)
        .ge(ScheduleLesson::getLessonDate, LocalDate.now()));
```
deleteClassroom() 增加对 schedule_lesson 中引用该教室的未来课次检查。

**Test scenarios:**
- 更新已有缴费课程的价格：`BusinessException(409)`
- 更新无缴费课程的价格：成功
- 删除有未来排课的课程：`BusinessException(409)`
- 删除有未来排课的教室：`BusinessException(409)`
- 删除无关联的教室：成功

**Verification:** `mvn test` 通过。

---

### U9. 学员模块完善

**Goal:** 修复转班仅处理首条记录（#22）、删除学员无财务检查（#26）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/student/service/StudentServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/student/controller/StudentController.java`

**Approach:**

**转班改进（Issue #22）：** transferStudent() 增加 fromClassId 参数：
1. Controller 层增加 `@RequestParam(required = false) Long fromClassId`
2. 若 fromClassId 非 null，精确查找源班级记录；若为 null 且 currentRecords.size() > 1，抛出 `BusinessException(400, "该学员在多个班级中，请指定 fromClassId")`
3. 仅处理指定的源班级记录

**删除学员检查（Issue #26）：** deleteStudent() 增加：
```
Long paymentCount = paymentRecordMapper.selectCount(eq(PaymentRecord::getStudentId, id));
Long refundCount = refundRecordMapper.selectCount(eq(RefundRecord::getStudentId, id));
if (paymentCount > 0 || refundCount > 0) {
    throw new BusinessException(409, "该学员已有财务记录，无法删除");
}
```

**Test scenarios:**
- 学员在 2 个班级中，不指定 fromClassId 转班：`BusinessException(400)`
- 指定 fromClassId 转班：仅处理指定班级
- 删除有缴费记录的学员：`BusinessException(409)`
- 删除无财务记录的学员：成功

**Verification:** `mvn test` 通过。

---

### U10. 统计与配置完善

**Goal:** 修复到课率分母（#23）、权限加载异常（#24）、CORS 配置（#25）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/statistics/service/StatisticsServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/auth/service/AuthService.java`
- `backend/src/main/java/com/pzhu/eduadmin/config/WebMvcConfig.java`
- `backend/src/main/resources/application.yml`

**Approach:**

**到课率分母（Issue #23）：** buildCards() 中 totalAttendance 查询增加 `in(Attendance::getStatus, 1, 2, 3)`（到场、迟到、请假），排除"待确认"和"已取消"等无效状态。

**权限加载异常（Issue #24）：** AuthService.loadPermissions() 的 catch 块增加 `log.error("权限加载失败", e)`，并在异常时抛出 `BusinessException(500, "系统异常，请联系管理员")` 而非静默返回空列表。

**CORS 配置（Issue #25）：**
1. application.yml 增加 `app.cors.allowed-origins` 配置项，开发环境默认 `http://localhost:*`
2. WebMvcConfig 从配置注入读取：`@Value("${app.cors.allowed-origins:http://localhost:*}")` 
3. 生产部署时通过环境变量或 profile 覆盖

**Test scenarios:**
- 到课率计算排除"待确认"状态的记录
- 权限加载失败时返回 500 而非空权限
- CORS 从配置读取，非硬编码

**Verification:** `mvn test` 通过；检查 application.yml 配置项。

---

## Phase 3: 轻微问题 — 防御性编程（8 个）

### U11. 杂项改进

**Goal:** 修复 SQL 拼接（#28）、请假家长绑定校验（#30）、考勤扣课时日志（#32）、JSON 注入（#33）、404 处理（#34）以及代课费存储（#27）、退学关联退费（#29）、排课删除检查（#31）
**Requirements:** R3
**Dependencies:** U4（考勤和排课核心修复后）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/LeaveRequestServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/AttendanceServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/security/JwtInterceptor.java`
- `backend/src/main/java/com/pzhu/eduadmin/common/GlobalExceptionHandler.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/schedule/service/ScheduleServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/student/service/StudentServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/salary/service/SalaryServiceImpl.java`

**Approach:**

逐个修复：

- **#28 SQL 拼接：** FinanceServiceImpl.getFlowsByStudentId() 的 `.last("LIMIT " + limit)` 改为使用 Page 分页：`selectPage(new Page<>(1, limit), wrapper)`
- **#30 请假家长绑定：** LeaveRequestServiceImpl.submitLeaveRequest() 入口增加 parentStudentMapper.selectCount 校验 parentUserId 与 studentId 的绑定关系
- **#32 扣课时日志：** AttendanceServiceImpl.deductLessons() 余额不足时，在 warn 日志中记录实际扣减量和差异量，方便对账
- **#33 JSON 注入：** JwtInterceptor.writeJson() 改用 Jackson ObjectMapper 序列化响应，或对 message 做转义
- **#34 404 处理：** GlobalExceptionHandler 增加 `@ExceptionHandler(NoHandlerFoundException.class)` 返回 404，application.yml 增加 `spring.mvc.throw-exception-if-no-handler-found=true`
- **#27 代课费存储：** TeacherSalary 实体增加 `substituteAmount` 字段（BigDecimal），SalaryServiceImpl.calculateSalary() 中设置该字段。需执行 DDL：`ALTER TABLE teacher_salary ADD COLUMN substitute_amount DECIMAL(10,2) DEFAULT 0`
- **#29 退学关联退费：** StudentServiceImpl.withdrawStudent() 改为查询所有 enrollment 对应的 payment 记录，生成一条汇总退费申请（而非仅关联最近一条）
- **#31 排课删除：** ScheduleServiceImpl.deleteLesson() 增加 status 检查（status > 2 的已完成/已取消课次才可删除），deleteClassroom() 增加未来排课引用检查（与 U8 的 #21 合并处理）

**Test scenarios:**
- getFlowsByStudentId 使用 Page 分页：返回正确数量
- 家长为非绑定向学员请假：`BusinessException(403)`
- 访问不存在 API：返回 code=404
- TeacherSalary 含 substituteAmount 字段：可查询代课费明细
- 退学学员有多次缴费：退费申请关联所有 enrollment

**Verification:** `mvn test` 通过。

---

## Scope Boundaries

### In Scope
- 34 个已识别的后端逻辑错误修复
- 每个修复对应 `mvn test` 验证
- TeacherSalary 实体新增 substituteAmount 字段（需 DDL）

### Out of Scope
- 前端代码修改
- 性能优化（如 Redis 缓存、连接池调优）
- 数据库索引优化（唯一索引建议记录在文档中，不在本次修复中执行 DDL）
- 班级容量并发竞态的分布式锁方案（Issue #7，需引入分布式锁中间件，留作后续）

### Deferred to Follow-Up Work
- Issue #7 班级容量并发竞态：需引入 Redis 分布式锁或数据库行锁，当前仅在应用层校验
- JWT 吊销的 Redis 缓存优化（U3 方案先用数据库查询，后续可升级为 Redis 缓存）
- 唯一索引 DDL 执行：lesson_account(student_id, course_id)、teacher_salary(teacher_id, salary_month)、salary_rule(teacher_id, course_id)

---

## Risks

- **RISK-1（中）：状态码迁移影响前端** — U1 将"驳回"从 status=3 改为 status=4，前端如有硬编码 status=3 为驳回的逻辑需同步修改。缓解：修复前搜索前端代码中所有 enrollment.status 的判断逻辑。
- **RISK-2（低）：DDL 变更** — U11 中 TeacherSalary 增加 substitute_amount 字段需执行 ALTER TABLE。缓解：在开发环境先执行验证。
- **RISK-3（低）：并发控制改动的正确性** — U2 退费审核的原子 UPDATE 需要验证 MyBatis-Plus LambdaUpdateWrapper 的行为与预期一致。缓解：编写专门的并发测试用例。

---

## Implementation Unit Dependency Graph

```
Phase 1 (严重):
  U1 ──────────┐
  U2 (←U1)     ├──→ Phase 2 (中等):
  U3           │      U5 (←U1)
  U4           │      U6 (←U2)
               │      U7
               │      U8
               │      U9
               │      U10
               │
               └──→ Phase 3 (轻微):
                      U11 (←U4)
```

U1 和 U3、U4 可并行。U2 依赖 U1（状态码统一后才能正确校验）。Phase 2 中 U5 依赖 U1，U6 依赖 U2，U7/U8/U9/U10 无依赖可并行。Phase 3 的 U11 建议在 Phase 2 完成后统一处理。

---

## Verification Contract

每个 Phase 完成后执行：

1. **编译检查**：`mvn compile -pl backend` 无编译错误
2. **单元测试**：`mvn test -pl backend` 确保所有新增和既有测试通过
3. **Phase 1 专项验证**：
   - 报名→驳回(status=4)→重新报名 链路正常
   - 报名→通过(status=2)→缴费(status=3)→续费 链路正常
   - 退费审核：并发请求仅一个成功
   - 禁用用户后旧 Token 返回 401
   - 连续 5 次登录失败后锁定
4. **Phase 2 专项验证**：
   - 已失效报名重新报名成功
   - 部分退费后学员仍在班级中
   - 教师无课次时薪资计算不报错
5. **全量回归**：Phase 3 完成后运行 `mvn test` 确认无回归

---

## Definition of Done

- 全部 34 个 Bug 修复完毕
- 每个 Bug 有对应的测试验证（mvn test 通过）
- `mvn compile` 无错误
- API 接口契约未发生破坏性变更（前端无需修改，状态码语义变更除外——需同步通知前端）
- TeacherSalary 新增 substitute_amount 字段的 DDL 已在开发环境执行
