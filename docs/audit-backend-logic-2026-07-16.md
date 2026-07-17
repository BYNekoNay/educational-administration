# 后端代码逻辑缺陷审查报告

> 审查日期: 2026-07-16 | 审查范围: 全部 17 个 ServiceImpl + AuthService + 安全层 + 配置层

---

## 审查方法论

逐文件审读了 `backend/src/main/java/com/pzhu/eduadmin/` 下全部 158 个 Java 文件，重点检查:
- 空指针 (NPE) 风险
- 业务逻辑错误 (分支条件、数据一致性)
- 事务边界/数据完整性
- MyBatis-Plus @TableLogic 交互陷阱
- 参数校验缺失
- 竞态条件

---

## 发现汇总

| 级别 | 数量 | 说明 |
|------|------|------|
| 🔴 P0 · 严重 | 7 | 直接导致数据错误或运行时 500 |
| 🟡 P1 · 中等 | 7 | 边界条件下数据不一致或体验问题 |
| 🟢 P2 · 建议 | 3 | 长期维护隐患或安全建议 |

---

## 🔴 P0 · 严重缺陷

### P0-1: 薪资核算缺少月份过滤 → 统计区间错误

**文件**: `SalaryServiceImpl.java:162-175`

```java
// mainLessons (L162-167)
scheduleLessonMapper.selectList(
    new LambdaQueryWrapper<ScheduleLesson>()
        .eq(ScheduleLesson::getTeacherId, teacherId)
        .eq(ScheduleLesson::getStatus, 2)         // 已完成
        .le(ScheduleLesson::getCreateTime, calcSnapshot)  // ← 只过滤时间戳，不过滤月份!
        .orderByAsc(ScheduleLesson::getLessonDate));

// allCompletedLessons (L172-175) 同样缺月份过滤
```

**问题**: `salaryMonth` 参数传入后被忽略。查询只过滤了 `status=2` 和 `createTime ≤ now`，实际上查出了**该教师所有历史已完成课次**，毫无月份概念。

**影响**: 薪资核算结果严重偏高，核算 8 月薪资会把 7 月课次也算进去。

**修复**: 两个查询均补充 `ge/le lessonDate` 对应当月范围。

---

### P0-2: 退费时把所有班级都退出了 → 多班学员被误退

**文件**: `FinanceServiceImpl.java:288-291`

```java
// 4. 联动将学员退班（从所有班级退出）  ← 注释已暴露问题
classStudentMapper.update(null,
    new LambdaUpdateWrapper<ClassStudent>()
        .eq(ClassStudent::getStudentId, record.getStudentId())  // ← 缺少 classId 限制!
        .set(ClassStudent::getStatus, 3));
```

**问题**: 退费针对的是某个 enrollment → course，但退出班级时没有限制到该退费对应的课程/班级，而是把该学员**所有在读班级**都置为 status=3（已退出）。

**影响**: 学员报了 A 课程和 B 课程，退 A 课程的费用 → B 课程也被踢出。

**修复**: 退班条件应加上该 enrollment 对应的班级/课程限制。至少应有 `courseId` 过滤（通过 ClassGroup 关联查到对应 class_ids）。

---

### P0-3: 缴费创建允许"待审核"报名直接缴费

**文件**: `FinanceServiceImpl.java:93-97`

```java
if (enrollment.getStatus() != 2 && enrollment.getStatus() != 1) {
    throw new BusinessException(409, "当前报名状态不可缴费");
}
```

**问题**: 这段逻辑是"不是 1 也不是 2 就抛错"，意味着 status=1 (待审核) 或 status=2 (审核通过) 都可以缴费。但 status=1 是「待审核」状态，报名还没被教务审核通过。

**影响**: 家长可以在教务审核前就缴费占位，绕过审核流程。

**修复**: 改为 `if (enrollment.getStatus() != 2)` — 仅允许审核通过 (status=2) 后缴费。

---

### P0-4: 调课审核时原课次可能为 null → NPE

**文件**: `ScheduleServiceImpl.java:210-211`

```java
ScheduleLesson oldLesson = scheduleLessonMapper.selectById(request.getLessonId());
oldLesson.setStatus(4);  // ← NPE if oldLesson == null
```

**问题**: 如果调课申请引用的原课次已被删除 (软删)，`selectById` 返回 null，直接 `.setStatus()` 触发 NPE → 500。

**修复**: 加 null 检查后抛 `BusinessException(404, "原课次不存在")`。

---

### P0-5: 调课审核时 expectTime 可能为 null → NPE

**文件**: `ScheduleServiceImpl.java:218-219`

```java
newLesson.setLessonDate(request.getExpectTime().toLocalDate());
newLesson.setStartTime(request.getExpectTime().toLocalTime());
```

**问题**: 前端若未传 `expectTime`，`getExpectTime()` 返回 null → `.toLocalDate()` NPE。

**修复**: 在 `auditAdjustRequest` 方法开头加 `expectTime` 非空校验。

---

### P0-6: 报名审核时 status 不校验合法值 → 数据污染

**文件**: `EnrollmentServiceImpl.java:165-166`

```java
enrollment.setStatus(status);
```

**问题**: 状态值完全信任前端传入。如果传入 99，会直接写入数据库，导致状态变成无意义值。

**影响**: 数据垃圾，后续依赖枚举值的业务逻辑（如定时过期任务 `eq(status,2)`）会漏掉这些记录。

**修复**: 添加白名单校验：`if (status != 2 && status != 3) throw ...`

---

### P0-7: 创建报名入口零参数校验

**文件**: `EnrollmentServiceImpl.java:129-132`

```java
public Enrollment create(Enrollment enrollment) {
    enrollmentMapper.insert(enrollment);  // ← studentId/courseId 全无校验
    return enrollment;
}
```

**问题**: `studentId`、`courseId`、`parentUserId` 等关键字段无 nil 检查，直接 insert。数据库层面缺乏 NOT NULL 约束的话会写入脏数据。

**修复**: 添加必要字段合法性校验。

---

## 🟡 P1 · 中等缺陷

### P1-1: 缴费创建 NPE 风险 — 班级被软删

**文件**: `FinanceServiceImpl.java:135-139`

```java
ClassGroup classGroup = classGroupMapper.selectById(enrollment.getClassId());
// classGroup 可能为 null（班级被软删）
long currentCount = classStudentMapper.selectCount(...);
if (currentCount >= classGroup.getMaxStudentCount())  // ← NPE
```

**修复**: `classGroup` 加 null 检查。

---

### P1-2: 退费审核同人隔离 NPE

**文件**: `FinanceServiceImpl.java:196`

```java
if (record.getApplicantId().equals(auditorId))
```

**问题**: `applicantId` 可能为 null（老数据或退班自动创建的退费记录），调用 `.equals()` NPE。

**修复**: 改为 `Objects.equals(record.getApplicantId(), auditorId)`。

---

### P1-3: 课时不足时静默扣到 0

**文件**: `AttendanceServiceImpl.java:300-301`

```java
BigDecimal actualDeduct = before.compareTo(deduct) >= 0 ? deduct : before;
```

**问题**: 余额不足时不报错，直接扣到 0。虽然流水备注写了"课时不足，扣至0"，但**前端不会主动提示**，管理员和教师都不知道学员课时已经耗尽。

**建议**: 至少返回业务警告（或改成先判断不足→抛异常提示续费，由管理员决定是否继续扣）。

---

### P1-4: 请假审核自动创建考勤时只取第一个课次

**文件**: `LeaveRequestServiceImpl.java:133-137`

```java
ScheduleLesson lesson = scheduleLessonMapper.selectOne(
    new LambdaQueryWrapper<ScheduleLesson>()
        .in(ScheduleLesson::getClassId, classIds)
        .eq(ScheduleLesson::getLessonDate, lr.getLessonDate())
        .last("LIMIT 1"));  // ← 多班多课次时只取第一个
```

**问题**: 学员同时在多个班级（A 班 9:00、B 班 14:00），请假一天 → 只创建了第一个课次的考勤记录，第二个课次漏掉。

**修复**: 用 `selectList` 替换 `selectOne`，为每个匹配的课次创建考勤。

---

### P1-5: 创建课次入口无冲突检测

**文件**: `ScheduleServiceImpl.java:91-93`

```java
public ScheduleLesson createLesson(ScheduleLesson lesson) {
    scheduleLessonMapper.insert(lesson);  // ← 未调用 checkConflict
    return lesson;
}
```

**问题**: 单条创建课次与批量创建 (`batchCreate`) 行为不一致 —— 批量有冲突检测，单条没有。

**修复**: 两种方案：(A) 单条也加冲突检测；(B) 废弃单条接口，统一用批量(传 1 条)。

---

### P1-6: 请假提交无重复去重

**文件**: `LeaveRequestServiceImpl.java:52-68`

```java
public LeaveRequest submitLeaveRequest(...) {
    // 无去重逻辑 —— 同一学员同一天可提交多次请假
    leaveRequestMapper.insert(lr);
}
```

**修复**: insert 前检查是否已有同一学员同一天待审核/已通过的请假记录。

---

### P1-7: 教师角色变更为非教师时 specialties 未清理

**文件**: `UserServiceImpl.java:107-109`

```java
if ("TEACHER".equals(user.getRoleCode()) && request.getSpecialtyCourseIds() != null) {
    saveSpecialties(id, request.getSpecialtyCourseIds());
}
```

**问题**: 教师改为 PARENT 后，`teacher_course` 表中的旧关联不会被删除，属于残留数据。

**修复**: 角色变更时主动清理旧 specialties。

---

## 🟢 P2 · 建议

### P2-1: JWT 密钥硬编码

**文件**: `application.yml`

```yaml
jwt:
  secret: change-me-eduadmin-secret-key-please-override-in-prod
```

毕业设计场景下可接受，但文档中应标注生产部署前必须替换。

---

### P2-2: logOperation 在非登录态调用会 NPE

多个 ServiceImpl 中的 `logOperation` 方法:
```java
log.setOperatorId(CurrentUserHolder.get().getUserId());  // ← get() 可能 null
```

`UserServiceImpl` 已经做了空安全处理（`operator != null ? ... : 0L`），但其余 11 个 ServiceImpl 没有。

**影响**: 定时任务（如 `EnrollmentServiceImpl.expirePendingEnrollments`）不写操作日志，但不会崩。如果 future 在非拦截器保护的路径调用会 NPE。

**修复**: 统一为空安全写法或抽取公共 `LogHelper` 工具类。

---

### P2-3: TeacherCourse 实体标记了 @TableLogic 但"先删后插"用逻辑删除

**文件**: `UserServiceImpl.java:153-154` + `TeacherCourse.java:29`

```java
// saveSpecialties:
teacherCourseMapper.delete(new LambdaQueryWrapper<TeacherCourse>()...);  // ← MP 逻辑删除
// 然后 insert 新行
```

`TeacherCourse` 实体带有 `@TableLogic`，`delete(LambdaQueryWrapper)` 走逻辑删除。

**影响**: 若表有唯一键（如 `uk_user_course`），多次变更特长后软删行会堆积，最终 insert 撞键。与之前 `role_permission` 的问题同根同源。

**修复**: 确认 `teacher_course` 是否有唯一键。如果有，改用物理删除 `@Delete`。

---

## 审查结论

| 维度 | 评分 | 说明 |
|------|------|------|
| 整体架构 | ⭐⭐⭐⭐ | 分层清晰，模块独立，异常体系完整 |
| 业务逻辑 | ⭐⭐⭐ | 7 个 P0 缺陷集中在**核算月份缺失**、**退费退班范围错误**等核心路径 |
| 空指针防护 | ⭐⭐⭐ | 多处遗漏 null 检查（调课、缴费、同人隔离） |
| 参数校验 | ⭐⭐ | 大部分 create 方法缺少输入校验，信任前端传参 |
| @TableLogic 使用 | ⭐⭐⭐ | 关联表仍存在隐患（TeacherCourse）；已修复的场景 (role_permission) 处理得当 |

**建议优先修复**: P0-1 (薪资核算)、P0-2 (退费退班)、P0-3 (缴费审核) — 这三项直接影响核心业务流程的正确性。
