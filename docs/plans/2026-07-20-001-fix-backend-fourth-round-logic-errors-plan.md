---
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
execution: code
product_contract_source: ce-plan-bootstrap
created: 2026-07-20
---

# fix: 后端第四轮 49 个逻辑错误修复计划

## Summary

修复教务管理系统后端第四轮审查发现的 49 个逻辑错误，分 4 个阶段 12 个实施单元执行。第一阶段修复 5 个 Critical 问题（退费流程系统性失败、教师越权审批、统计 NPE/数据失真）；第二阶段修复 15 个 High 问题（并发竞态、数据错误、功能不可用）；第三阶段修复 25 个 Medium 问题（边界校验、数据一致性、安全加固）；第四阶段修复 4 个 Low 问题（防御性编程）。

## Problem Frame

前三轮修复（2026-07-16 ~ 07-17，累计 80+ Bug）后，第四轮深度审查发现 49 个残留逻辑错误。最严重的问题集中在三个区域：退费流程（自动计算金额未持久化 + 上限校验包含当前记录 → 合法退费被系统性拒绝）、考勤权限（scheduleId 审批前为 null → 任何教师可审批任意请假单）、统计模块（历史数据用当前状态过滤 + null teacherId 导致 NPE）。此外存在大量并发竞态（课时余额双扣、重复支付、重复账户）和边界校验缺失。

## Requirements

- R1: 5 个 Critical 问题必须全部修复，消除核心业务流程阻断
- R2: 15 个 High 问题必须修复，消除数据错误和功能不可用
- R3: 25 个 Medium 问题应当修复，提升边界安全性和数据一致性
- R4: 4 个 Low 问题尽量修复，提升防御性编程水平
- R5: 每个修复必须通过 `mvn test` 验证，不引入回归
- R6: 修复按模块聚合、按依赖排序，减少上下文切换

## Key Technical Decisions

- KTD1: **退费上限校验排除当前记录** — `sumApprovedByEnrollmentId` 查询增加 `WHERE id != currentId` 条件，或在审核前先计算上限再更新状态。确保合法全额退费不被自身金额阻断
- KTD2: **教师请假审批权限基于 classId 关联** — 由于 scheduleId 在审批前为 null，改为通过 leaveRequest → student → classStudent → class → scheduleLesson → teacherId 链路验证归属关系
- KTD3: **并发控制统一使用 CAS 原子更新** — 所有 read-modify-write 场景（课时余额、支付状态、退费申请）改为 `UPDATE ... WHERE status/amount = oldValue`，通过 affected rows 判断冲突，与项目已有的 Enrollment CAS 模式一致
- KTD4: **统计历史数据使用时间快照查询** — 流失趋势等历史统计不再依赖当前 status，改为基于 joinTime/leaveTime 时间区间判断"某月是否在班"
- KTD5: **分页搜索改为数据库层面 JOIN 过滤** — 将内存过滤改为 SQL LIKE + JOIN，确保分页总数和结果正确

---

## Phase 1: Critical — 核心业务流程阻断（5 个）

### U1. 修复退费审批流程系统性失败

**Goal:** 修复自动计算退费金额未持久化（#2）+ 上限校验包含当前记录导致合法退费被拒（#3）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/mapper/RefundRecordMapper.java`
- `backend/src/test/java/com/pzhu/eduadmin/FinanceServiceMockTest.java`

**Approach:**

**Issue #2 — 自动计算金额未持久化：** `auditRefund()` 中，当审核通过且未传入显式 refundAmount（controller 默认为 ZERO）时，`processRefundApproval` 内部自动计算了金额并设置到 record 对象，但 DB 持久化的 guard 条件 `refundAmount > 0` 为 false，导致金额从未写入。修复：在 `processRefundApproval` 返回最终计算金额后，无条件更新 `refund_record.amount`：

1. 将 `processRefundApproval` 改为返回 `BigDecimal finalAmount`
2. 审核通过后，用 `LambdaUpdateWrapper.set(RefundRecord::getAmount, finalAmount)` 无条件写入
3. 移除原来的 `if (refundAmount != null && refundAmount.compareTo(ZERO) > 0)` guard

**Issue #3 — 上限校验包含当前记录：** `processRefundApproval` 中 `sumApprovedByEnrollmentId` 查询 `WHERE status = 2`，但当前记录已被更新为 status=2，导致自身金额被计入 totalRefunded。修复方案：

1. 在 `auditRefund()` 中，先执行上限校验（此时当前记录仍为 status=1），再更新状态
2. 或者在 `sumApprovedByEnrollmentId` SQL 中增加 `AND id != #{excludeId}` 参数
3. 推荐方案 1（调整执行顺序），因为更简单且不改变 Mapper 接口

具体调整 `auditRefund()` 执行顺序：
```
原: 更新status=2 → processRefundApproval(含上限校验)
改: 计算refundAmount → 上限校验(排除当前) → 原子更新status=2 → 执行退费操作
```

**Patterns to follow:** 第三轮已建立的原子 CAS 模式（`UPDATE ... WHERE status = 1`）

**Test scenarios:**
- 全额退费：缴费 5000，申请退 5000 → 审批通过，refund_record.amount = 5000
- 自动计算退费：缴费 8000/40课时，剩余 20 课时 → 审批通过（不传金额），DB 中 amount = 4000
- 超额退费：缴费 5000，已退 3000，再申请退 3000 → 拒绝（上限 2000）
- 并发审核同一退费：仅一个成功
- 退费后 refund_record.amount 与 lesson_flow 金额一致

**Verification:** `mvn test` 通过；手动测试全额退费、自动计算退费、超额拒绝三条路径。

---

### U2. 修复教师请假审批权限绕过与队列为空

**Goal:** 修复任何教师可审批任意请假单（#1）+ 教师审批队列永远为空（#6）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/LeaveRequestServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/controller/TeacherAttendanceController.java`
- `backend/src/test/java/com/pzhu/eduadmin/LeaveRequestTest.java`（新建）

**Approach:**

**Issue #1 — 权限绕过：** `auditByTeacher()` 中通过 `scheduleId` 验证教师归属，但待审批记录的 scheduleId 始终为 null（该字段在审批通过后的 `createLeaveAttendance()` 中才赋值）。当 scheduleId 为 null 时检查被跳过。修复：改为通过学员-班级-教师链路验证：

1. 从 leaveRequest 获取 studentId
2. 查询 classStudent 获取该学员所在的 classId 列表
3. 查询 scheduleLesson 获取这些班级的 teacherId
4. 验证当前审批教师是否在这些 teacherId 中
5. 如果 leaveRequest 关联了具体 scheduleId（已有关联的情况），仍优先用 scheduleId 验证

**Issue #6 — 队列为空：** `pageByTeacher()` 按 `leaveRequest.scheduleId IN (教师的lessonIds)` 查询，但待审批记录 scheduleId 为 null，永远匹配不到。修复：改为通过学员归属关系查询：

1. 查询教师所教的所有班级（scheduleLesson.teacherId = teacherId → classId 集合）
2. 查询这些班级的所有学员（classStudent.classId IN classIds → studentId 集合）
3. 查询这些学员的请假记录（leaveRequest.studentId IN studentIds）
4. 支持 status 筛选（默认查待审批 status=1）

**Patterns to follow:** ParentController 中通过 parentStudent 关联查询学员数据的模式

**Test scenarios:**
- 教师 A 审批自己班级学员的请假：成功
- 教师 A 审批非自己班级学员的请假：`BusinessException(403)`
- 教师查询待审批列表：能看到自己班级学员的待审批请假
- 教师查询已审批列表：能看到历史审批记录
- 学员同时在两个班级（两个教师），两个教师都能看到该学员的请假

**Verification:** `mvn test` 通过；手动测试跨班级审批拒绝、正常审批通过。

---

### U3. 修复统计模块 NPE 与历史数据失真

**Goal:** 修复 teacherId 为 null 时 NPE（#5）+ 学员流失趋势历史数据错误（#4）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/statistics/service/StatisticsServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/StatisticsServiceTest.java`

**Approach:**

**Issue #5 — NPE：** `getTeacherWorkload()` 中 `Collectors.groupingBy(ScheduleLesson::getTeacherId)` 遇到 null teacherId 直接 NPE。修复：

1. 在 groupingBy 前过滤掉 teacherId 为 null 的记录：`.filter(l -> l.getTeacherId() != null)`
2. 或者使用 `Collectors.groupingBy(l -> l.getTeacherId() != null ? l.getTeacherId() : 0L)` 将无教师的课归入"未分配"组
3. 推荐方案 1（过滤），因为教师工作量统计不应包含无教师的课次

**Issue #4 — 历史数据失真：** `getStudentLossTrend()` 用 `status = 1`（当前活跃）过滤历史月份数据，已离校学员被排除。修复：改为基于时间区间判断"某月是否在班"：

1. 月初在班人数 = joinTime < 月初 AND (leaveTime IS NULL OR leaveTime >= 月初) 的记录数
2. 本月新入班 = joinTime 在本月范围内的记录数（不限 status）
3. 本月离班 = leaveTime 在本月范围内的记录数
4. 流失率 = 本月离班 / (月初在班 + 本月新入班)
5. 需要 ClassStudent 表有 leaveTime 字段（如无需添加）

**Patterns to follow:** 第三轮修复中 attendance trend 已使用日期区间查询的模式

**Test scenarios:**
- 教师工作量：存在 teacherId=null 的课次 → 不崩溃，正确统计其他教师
- 教师工作量：所有课次都有教师 → 正常统计
- 流失趋势：1月有100人在班，2月20人离校 → 1月显示100（非80），2月流失率=20/100
- 流失趋势：某月0人在班 → 流失率为0，不除零
- 流失趋势：学员中途加入又离开 → 正确计入对应月份

**Verification:** `mvn test` 通过；验证 Dashboard 统计接口返回正确数据。

---

## Phase 2: High — 数据错误与功能不可用（15 个）

### U4. 考勤与课时账户并发安全

**Goal:** 修复 reverseDeduct 膨胀 totalLessons（#7）+ 课时余额并发双扣（#8）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/AttendanceServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/entity/LessonAccount.java`
- `backend/src/test/java/com/pzhu/eduadmin/AttendanceServiceTest.java`

**Approach:**

**Issue #7 — totalLessons 膨胀：** `deductLessons()` 只减 `remainingLessons`，不动 `totalLessons`。但 `reverseDeduct()` 同时加回 `remainingLessons` 和 `totalLessons`。修复：移除 `reverseDeduct()` 中对 `totalLessons` 的加回操作，只恢复 `remainingLessons`：

```
删除: account.setTotalLessons(account.getTotalLessons().add(old.getDeductLessons()));
保留: account.setRemainingLessons(account.getRemainingLessons().add(old.getDeductLessons()));
```

**Issue #8 — 并发双扣：** `deductLessons()` 的 read-modify-write 无乐观锁。修复：使用 CAS 原子更新：

1. 将 `lessonAccountMapper.updateById(account)` 改为：
```
int rows = lessonAccountMapper.update(null,
    new LambdaUpdateWrapper<LessonAccount>()
        .eq(LessonAccount::getId, account.getId())
        .eq(LessonAccount::getRemainingLessons, beforeBalance)  // CAS 条件
        .set(LessonAccount::getRemainingLessons, beforeBalance.subtract(actualDeduct)));
if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");
```
2. 同样修改 `reverseDeduct()` 中的余额恢复为 CAS 模式
3. 如果 LessonAccount 已有 `@Version` 字段，也可用 MyBatis-Plus 乐观锁插件（检查是否已配置）

**Patterns to follow:** 第三轮在 FinanceServiceImpl 中建立的 CAS 原子更新模式

**Test scenarios:**
- 扣减后 reverseDeduct：remainingLessons 恢复，totalLessons 不变
- 多次扣减+多次回冲：totalLessons 始终等于初始值
- 并发扣减同一账户（模拟）：仅一个成功，另一个抛 409
- 余额不足时扣减：拒绝，余额不变
- reverseDeduct 后 remainingLessons 不超过 totalLessons

**Verification:** `mvn test` 通过；验证课时流水中 totalLessons 一致性。

---

### U5. 排课搜索与支付并发修复

**Goal:** 修复关键字分页后过滤（#9）+ 家长支付竞态（#10）+ 重复课时账户（#11）+ enrollment CAS 未检查（#14）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/schedule/service/ScheduleServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/controller/ParentController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/ScheduleServiceTest.java`
- `backend/src/test/java/com/pzhu/eduadmin/FinanceServiceMockTest.java`

**Approach:**

**Issue #9 — 分页后过滤：** `pageScheduleLessons()` 在数据库分页后做内存 keyword 过滤。修复：将关键字搜索下推到 SQL 层：

1. 对 teacherName/classGroupName/classroomName 的搜索改为 JOIN + LIKE
2. 或者在 wrapper 中用子查询：`.in(ScheduleLesson::getTeacherId, selectTeacherIdsByName(kw))`
3. 移除分页后的内存过滤代码
4. 如果 JOIN 过于复杂，可先查出匹配的 teacherId/classId/roomId 集合，再用 IN 条件加入 wrapper

**Issue #10 — 支付竞态：** `mockPayment()` 的 status 检查和支付创建非原子。修复：

1. 在 `createPayment()` 内部（而非 controller）做 enrollment 状态 CAS：
```
int rows = enrollmentMapper.update(null,
    new LambdaUpdateWrapper<Enrollment>()
        .eq(Enrollment::getId, enrollmentId)
        .eq(Enrollment::getStatus, 2)  // 必须是待缴费
        .set(Enrollment::getStatus, 3));
if (rows == 0) throw new BusinessException(409, "该报名状态已变更，请刷新重试");
```
2. 将此 CAS 作为 createPayment 的第一步，失败则整个方法不执行

**Issue #11 — 重复课时账户：** `createPayment()` 中 selectOne→insert 非原子。修复：

1. 在 LessonAccount 表上添加唯一索引 `(student_id, course_id)`（如未有）
2. 将 insert 包裹在 try-catch 中，捕获 DuplicateKeyException 后改为 update
3. 或使用 `INSERT ... ON DUPLICATE KEY UPDATE` 语义（MyBatis-Plus saveOrUpdate）

**Issue #14 — CAS 结果未检查：** `createPayment()` 中 enrollment 状态更新未检查返回值。修复：检查 affected rows，为 0 时抛异常回滚。

**Test scenarios:**
- 搜索教师名：只返回匹配记录，total 正确，跨页无遗漏
- 搜索无结果：返回空列表，total=0
- 并发支付同一报名：仅一个成功创建支付记录
- 支付时报名已被取消：返回 409
- 并发为同一学员+课程创建账户：不产生重复记录
- enrollment CAS 失败时：支付记录和课时账户不创建（事务回滚）

**Verification:** `mvn test` 通过；手动测试搜索分页、快速双击支付按钮。

---

### U6. 财务计算与薪资修复

**Goal:** 修复家长退费只取最近一笔（#12）+ 薪资调整不更新 totalAmount（#13）
**Requirements:** R2
**Dependencies:** U1（退费上限逻辑修复后再处理退费金额计算）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/controller/ParentController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/mapper/PaymentRecordMapper.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/salary/service/SalaryServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/SalaryServiceTest.java`

**Approach:**

**Issue #12 — 退费只取最近一笔：** `ParentRefundController.create()` 用 `LIMIT 1` 取最近一笔 payment 计算退费金额。修复：改为取总缴费金额：

1. 使用 `paymentRecordMapper.sumByEnrollmentId(enrollmentId)` 获取总缴费
2. 退费公式改为：`totalPaid * remainLessons / totalLessons`
3. 如果 sumByEnrollmentId 已存在（FinanceServiceImpl 中用过），直接复用

**Issue #13 — 薪资调整无效：** `createAdjustment()` 插入调整记录后未更新 TeacherSalary.totalAmount。修复：

1. 插入 SalaryAdjustment 后，重新计算 totalAmount：
```
TeacherSalary salary = teacherSalaryMapper.selectById(salaryId);
BigDecimal newTotal = salary.getBaseAmount()
    .add(salary.getBonusAmount() != null ? salary.getBonusAmount() : ZERO)
    .add(getTotalAdjustments(salaryId));  // SUM(adjust_amount)
salary.setTotalAmount(newTotal);
teacherSalaryMapper.updateById(salary);
```
2. 添加 `@Transactional` 确保调整记录和总额更新原子性
3. 考虑是否需要支持负调整（扣款），adjustAmount 允许为负数

**Test scenarios:**
- 续费后退费：两笔各 3000（共 6000），50% 剩余 → 退费 3000（非 1500）
- 单笔缴费退费：5000 元 40 课时，剩余 20 → 退费 2500
- 创建薪资调整（+500）：totalAmount 增加 500
- 创建薪资调整（-200）：totalAmount 减少 200
- 多次调整：totalAmount = base + bonus + SUM(adjustments)
- 调整不存在的 salaryId：404

**Verification:** `mvn test` 通过；验证薪资导出中 totalAmount 与明细一致。

---

### U7. 用户/课程/学习/通知 High 修复

**Goal:** 修复用户名重复（#15）、课程删除 NPE（#16）、操作日志顺序（#17）、作业伪造（#18）、通知中断（#19）、出勤率不一致（#20）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/service/UserServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/service/CourseServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/learning/controller/LearningController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/notification/service/NotificationServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/learning/service/LearningServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/statistics/service/StatisticsServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/UserServiceTest.java`

**Approach:**

**Issue #15 — 用户名重复：** `updateUser()` 无唯一性校验。修复：在设置 username 前检查：
```
if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
    User existing = userMapper.selectOne(eq(User::getUsername, request.getUsername()));
    if (existing != null && !existing.getId().equals(id)) {
        throw new BusinessException(409, "用户名已存在");
    }
    user.setUsername(request.getUsername());
}
```

**Issue #16 — 删除 NPE：** `deleteCourse/deleteClassGroup` 中 selectById 可能为 null。修复：添加 null guard：
```
Course course = courseMapper.selectById(id);
if (course == null) throw new BusinessException(404, "课程不存在");
```

**Issue #17 — 日志顺序 + selectOne 多条：** 修复：
1. 将 operationLogService.log() 移到操作成功之后
2. selectOne 改为 selectList + 取第一条（或加 status 过滤避免多条）

**Issue #18 — 作业伪造：** 修复：对 TEACHER 角色强制覆盖 teacherId：
```
if (CurrentUserHolder.get().getRoleCode().equals("TEACHER")) {
    homework.setTeacherId(CurrentUserHolder.get().getUserId());  // 强制，忽略传入值
}
```

**Issue #19 — 通知中断：** 修复：在 sendToUsers 循环内加 try-catch：
```
for (Long userId : userIds) {
    try {
        send(userId, copy);
    } catch (Exception e) {
        log.error("通知发送失败 userId={}", userId, e);
    }
}
```

**Issue #20 — 出勤率分母不一致：** 修复：统一出勤率计算口径。在 LearningServiceImpl.getStudentArchive() 中，分母改为仅包含 status IN (1,2,3)（出勤、迟到、请假），与 Dashboard 一致。排除 status=4（缺勤）和 null。

**Test scenarios:**
- 更新用户名为已存在的用户名：409
- 更新用户名为自身当前用户名：成功（不报错）
- 删除不存在的课程：404（非 NPE）
- 移除不在班级的学员：404，操作日志不记录
- 教师创建作业传入他人 teacherId：实际保存为自己的 ID
- 管理员创建作业传入指定 teacherId：保存为指定值
- 通知发送中某用户失败：其他用户仍收到
- 出勤率：学员有 10 条记录（7出勤+1迟到+1请假+1缺勤）→ 出勤率 = 9/9 = 100%（或 8/9 视口径）

**Verification:** `mvn test` 通过；验证用户管理、课程删除、作业创建、通知功能。

---

## Phase 3: Medium — 边界校验与数据一致性（25 个）

### U8. 排课与报名边界修复

**Goal:** 修复调课时长硬编码（#21）、报名删除无事务（#22）、报名重复 TOCTOU（#23）、leaveStatus 语义复用（#24）、course.totalLessons NPE（#25）、教室预约无冲突检测（#26）、教室删除无引用检查（#27）、null 时间绕过冲突检测（#28）
**Requirements:** R3
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/schedule/service/ScheduleServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/schedule/service/ScheduleConflictServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/service/EnrollmentServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/controller/ParentController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/AttendanceServiceImpl.java`

**Approach:**

**#21 调课时长：** `auditAdjustRequest()` 中 `endTime = expectTime + 60min` 改为从原课次计算实际时长：
```
Duration dur = Duration.between(oldLesson.getStartTime(), oldLesson.getEndTime());
newLesson.setEndTime(request.getExpectTime().toLocalTime().plus(dur));
```

**#22 报名删除无事务：** 添加 `@Transactional(rollbackFor = Exception.class)`

**#23 报名重复 TOCTOU：** 在 enrollment 表添加唯一索引 `(student_id, course_id, status)` 或在 insert 时 catch DuplicateKeyException 转为 BusinessException(409)

**#24 leaveStatus 语义复用：** 新增一个独立字段（如 `timeStatus`）表示"已开始/迟到"，不复用 leaveStatus。或者在返回 VO 中增加 `isStarted` boolean 字段

**#25 totalLessons NPE：** 在 mockPayment 中添加 null 检查：
```
if (course.getTotalLessons() == null || course.getPrice() == null) {
    throw new BusinessException(400, "课程信息不完整，无法支付");
}
```

**#26 教室预约冲突：** `createRoomBooking()` 中调用 `checkConflict()` 或自定义重叠检测：
```
// 检查同一教室、同一天、时间段重叠的已有预约和课次
```

**#27 教室删除：** 删除前检查是否有未来课次引用：
```
long futureLessons = scheduleLessonMapper.selectCount(
    eq(classroomId, id).ge(lessonDate, LocalDate.now()));
if (futureLessons > 0) throw new BusinessException(409, "该教室有未来排课，无法删除");
```

**#28 null 时间绕过：** 在 `createLesson()` 入口处校验 startTime/endTime/lessonDate 非空，而非依赖冲突检测的 null 短路

**Test scenarios:**
- 调课：原课 90 分钟 → 新课也是 90 分钟
- 报名删除失败时 ClassStudent 不被删除（事务回滚）
- 并发创建同一学员+课程报名：仅一个成功
- 教室预约与已有课次时间重叠：拒绝
- 删除有未来排课的教室：409
- 创建课次 startTime 为 null：400

**Verification:** `mvn test` 通过。

---

### U9. 财务与薪资 Medium 修复

**Goal:** 修复 pricePerLesson 膨胀（#29）、重复退费 TOCTOU（#30）、班级容量非原子（#31）、substituteAmount 未持久化（#32）、零元缴费（#33）
**Requirements:** R3
**Dependencies:** U1（退费流程修复后）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/salary/service/SalaryServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/salary/entity/TeacherSalary.java`

**Approach:**

**#29 pricePerLesson 膨胀：** 退费后 totalLessons 减少但 totalPaid 不变，导致单价虚高。修复：在 processRefundApproval 中，pricePerLesson 始终用原始 totalLessons 计算（即 totalPaid / 原始购买课时数），或在 LessonAccount 中保存 `originalTotalLessons` 字段不被退费修改。推荐后者更清晰。

**#30 重复退费 TOCTOU：** 与 U5 中 #11 类似，在 refund_record 表添加条件唯一索引 `(enrollment_id, status)` 的 partial index（MySQL 不支持 partial index，改为在 insert 前用 `SELECT ... FOR UPDATE` 或 catch DuplicateKeyException）

**#31 班级容量非原子：** 改为在 ClassStudent insert 后检查 count，超过则回滚。或用 `SELECT COUNT(*) ... FOR UPDATE` 锁定行再判断。

**#32 substituteAmount 未持久化：** TeacherSalary 实体添加 `substituteAmount` 字段，calculateSalary 中设置：
```
salary.setSubstituteAmount(substituteAmount);
salary.setTotalAmount(baseAmount.add(substituteAmount).add(bonusAmount));
```
需要数据库 ALTER TABLE 添加列。

**#33 零元缴费：** 将校验从 `< 0` 改为 `<= 0`：
```
if (record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
    throw new BusinessException(400, "缴费金额必须大于零");
}
```

**Test scenarios:**
- 部分退费后再退费：pricePerLesson 不膨胀（10000/100=100，退50课后仍为100/课时）
- 并发提交退费申请：仅一个成功
- 班级满员后并发支付：不超额
- 薪资计算含代课费：totalAmount = base + substitute + bonus，各字段独立可查
- 零元缴费：400 拒绝

**Verification:** `mvn test` 通过；验证退费后再次退费金额正确。

---

### U10. 用户认证与安全 Medium 修复

**Goal:** 修复 roleCode 无校验（#34）、bindParent 不校验角色（#35）、用户名 TOCTOU（#36）、登录锁定 DoS（#37）、loginAttempts 内存泄漏（#38）
**Requirements:** R3
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/service/UserServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/student/service/StudentServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/auth/service/AuthService.java`

**Approach:**

**#34 roleCode 无校验：** createUser/updateUser 中验证 roleCode 存在于 role 表：
```
if (roleMapper.selectOne(eq(Role::getRoleCode, roleCode)) == null) {
    throw new BusinessException(400, "角色编码不存在: " + roleCode);
}
```

**#35 bindParent 不校验角色：** 添加：
```
User parentUser = userMapper.selectById(parentStudent.getParentUserId());
if (!"PARENT".equals(parentUser.getRoleCode())) {
    throw new BusinessException(400, "只能绑定家长角色的用户");
}
if (parentUser.getStatus() != 1) {
    throw new BusinessException(400, "该用户已被禁用");
}
```

**#36 用户名 TOCTOU：** 在 user 表 username 列添加唯一索引（如未有），insert 时 catch DuplicateKeyException 转为 BusinessException(409)。

**#37 登录锁定 DoS：** 改为 username + IP 双维度计数。同一 IP 连续失败 20 次锁定 IP；同一 username 连续失败 5 次锁定 username 但增加验证码提示（或改为渐进延迟）。

**#38 loginAttempts 内存泄漏：** 将 ConcurrentHashMap 替换为有 TTL 和容量上限的结构：
1. 使用 Caffeine Cache（如项目已引入）或自定义定时清理
2. 或简单方案：在 recordFailedAttempt 中，每次写入时清理超过 15 分钟的旧条目
3. 添加最大容量限制（如 10000 条），超出时移除最旧条目

**Test scenarios:**
- 创建用户传入不存在的 roleCode：400
- bindParent 绑定教师账号：400
- bindParent 绑定禁用家长：400
- 并发注册同一用户名：一个成功，另一个 409（非 500）
- 同一 IP 大量不同用户名失败登录：不 OOM，超限后拒绝
- 15 分钟后锁定自动解除

**Verification:** `mvn test` 通过。

---

### U11. 统计/考试/通知 Medium 修复

**Goal:** 修复缴费率用当前价格（#39）、导出日期精度（#40）、考试状态转换（#41）、null status NPE（#42）、考勤趋势 OOM（#43）、到期提醒逻辑（#44）
**Requirements:** R3
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/statistics/service/StatisticsServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/statistics/service/ExportService.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/exam/service/ExamServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/learning/service/LearningServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/notification/service/ReminderService.java`

**Approach:**

**#39 缴费率用当前价格：** 在 Enrollment 创建时快照 course.price 到 enrollment 表（添加 `snapshot_price` 字段），统计时用快照价格。或者退而求其次：在 getPaymentRate 中用实际 payment 记录的金额总和作为"应收"。

**#40 导出日期精度：** 将 `<= endDate + " 23:59:59"` 改为 `< nextDay + " 00:00:00"`：
```
.lt(PaymentRecord::getPayTime, endDate.plusDays(1).atStartOfDay())
```

**#41 考试状态转换：** updateExamSignup 添加状态转换校验：
```
// 合法转换：1→2, 1→3（报名→通过/不通过）
// 非法：2→1, 3→1, 2→3, 3→2
```
同时添加存在性检查。

**#42 null status NPE：** 所有 `a.getStatus() == 1` 改为 `Integer.valueOf(1).equals(a.getStatus())`，或在查询时过滤 `isNotNull(Attendance::getStatus)`。

**#43 考勤趋势 OOM：** 改为按月分组聚合查询（SQL GROUP BY），而非全量加载到内存：
1. 只查最近 6 个月的数据（添加 lessonDate >= 6个月前 条件）
2. 使用 SQL 聚合：`SELECT DATE_FORMAT(lesson_date, '%Y-%m'), COUNT(*) ... GROUP BY ...`

**#44 到期提醒逻辑：** 修正查询条件，将两个 .and() 改为 .or() 逻辑：
```
// 意图：(即将到期 OR 课时不足) AND 仍有剩余
.gt(remainingLessons, 0)
.and(w -> w
    .le(expireDate, today.plusDays(7))      // 7天内到期
    .or()
    .le(remainingLessons, new BigDecimal("3"))  // 剩余≤3课时
)
.isNotNull(expireDate)  // 无到期日的不需要到期提醒
```
对于无到期日但课时不足的情况，单独处理或移除 isNull 条件。

**Test scenarios:**
- 缴费率：课程改价后历史报名仍用原价计算
- 导出：23:59:59.500 的记录包含在当天导出中
- 考试状态：passed→registered 转换被拒绝
- null status 考勤记录：不导致 NPE
- 考勤趋势：数据量大时不全量加载
- 到期提醒：7天内到期 + 剩余>3 → 提醒；无到期日 + 剩余≤3 → 提醒

**Verification:** `mvn test` 通过；验证 Dashboard 和导出功能。

---

## Phase 4: Low — 防御性加固（4 个）

### U12. Low 优先级防御性修复

**Goal:** 修复权限变更不刷新 token（#45）、roleCode 信任 token（#46）、用户名变更不递增 version（#47）、教师工时整数除法（#48）
**Requirements:** R4
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/service/RoleServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/security/JwtInterceptor.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/service/UserServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/schedule/statistics/TeacherStatisticsServiceImpl.java`

**Approach:**

**#45 权限变更不刷新：** `updateRolePermissions()` 中，更新权限后递增该角色所有用户的 version：
```
userMapper.update(null, new LambdaUpdateWrapper<User>()
    .eq(User::getRoleCode, roleCode)
    .setSql("version = version + 1"));
```

**#46 roleCode 信任 token：** JwtInterceptor 中 select 时增加 roleCode 字段，用 DB 值而非 token 值：
```
.select(User::getVersion, User::getStatus, User::getRoleCode)
// ...
CurrentUserHolder.set(new LoginUser(userId, username, currentUser.getRoleCode()));
```

**#47 用户名变更不递增 version：** updateUser 中，username 变更时也递增 version（使旧 token 的 username 失效）。

**#48 整数除法：** 将 `totalMinutes / 60` 改为 `totalMinutes / 60.0` 返回 double，或返回分钟数让前端格式化。

**Test scenarios:**
- 更新角色权限后：该角色用户旧 token 失效（401）
- 用户角色被直接修改（绕过 service）：拦截器用 DB roleCode 鉴权
- 修改用户名后旧 token：401
- 教师工时：3 节 45 分钟课 → 显示 2.25 小时（非 2）

**Verification:** `mvn test` 通过。

---

## Scope Boundaries

### Deferred to Follow-Up Work

- 数据库 migration 脚本（唯一索引、新字段）统一在修复完成后编写
- 前端适配（如 leaveStatus 新字段、出勤率口径变更后的展示调整）
- 性能压测（并发场景的实际多线程验证）
- Redis 缓存 User.version 优化（当前直接查库，用户量小可接受）

## Risks & Dependencies

- **数据库变更风险：** U9（substituteAmount 字段）、U10（唯一索引）、U11（snapshot_price 字段）需要 ALTER TABLE，应在开发环境验证后再应用
- **退费流程依赖链：** U1 → U6 → U9 有顺序依赖，退费相关修复必须按此顺序
- **测试覆盖：** 现有 362 个测试可能需要适配（如 totalLessons 行为变更后旧测试断言需更新）
- **并发修复的测试局限：** 单元测试难以真正验证并发安全，CAS 逻辑正确性依赖代码审查 + 手动并发测试

## Verification Contract

1. 每个 Unit 修复后运行 `mvn test`，确保全部通过
2. Phase 1 完成后手动验证：退费审批全流程、教师请假审批、Dashboard 统计
3. Phase 2 完成后手动验证：搜索分页、快速双击支付、薪资调整
4. 全部完成后运行完整测试套件 + acceptance_test.sh
5. 启动后端服务，验证关键 API 无 500 错误

## Definition of Done

- 49 个问题全部修复（或明确标注为"设计如此/不修复"并说明理由）
- `mvn test` 全部通过（含新增测试）
- 无新增 Critical/High 级别问题
- 关键业务流程手动验证通过：报名→缴费→排课→考勤→退费→薪资
