# 后端代码系统检查计划

**创建日期**: 2026-07-17  
**原则**: 按模块依赖关系自底向上检查，每一层完成后再进入下一层；每层从四个维度（链路完整性、边界与异常、数据一致性、性能风险）交叉验证。

---

## 已完成的审计 (Round 1-3)

| 轮次 | 层次 | 覆盖范围 | 发现 | 状态 |
|------|------|----------|------|------|
| R1 | L2 ServiceImpl | 17 个 ServiceImpl 全量 | 9 项 | ✅ |
| R2 | Controller/Entity/Config | 22 Controller + 33 Entity | 30+ 项 | ✅ |
| R3 | Security/DTO/Scheduler | JWT/拦截器/DTO/异常 | 20 项 | ✅ |

---

## 待执行的四层检查计划

### L2-1: Student 模块 (学员管理) — 核心业务起点

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\student\`

**依赖**: User (L1), Course (L2, 同级)
**被依赖**: Enrollment, Finance, Attendance

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | Student → ParentStudent 绑定/解绑链路完整性 | StudentController, StudentServiceImpl |
| 链路 | transferStudent 跨班级状态流转 (源→目标) | StudentServiceImpl |
| 链路 | withdrawStudent → RefundRecord 退费自动关联 | StudentServiceImpl |
| 边界 | 删除学员前是否检查 enrollment/attendance/finance 关联 | StudentServiceImpl.deleteStudent |
| 边界 | bindParent 历史绑定关系清理与唯一键冲突 | StudentServiceImpl.bindParent |
| 边界 | parentBinding 家长禁用后应解除绑定 | parentStudent 生命周期 |
| 数据 | 转班时 class_student 源记录 status 与目标记录同时生效 | transferStudent 事务边界 |
| 数据 | 退班后 student.status=4 与 class_student.status=3 一致性 | withdrawStudent 事务 |
| 性能 | findStudentIdsByParentName 两表联查无索引 | StudentServiceImpl L79-93 |

### L2-2: Course 模块 (课程与班级) — 核心业务骨架

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\course\`

**依赖**: Student (L2)
**被依赖**: Schedule, Enrollment, Attendance

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | 删除班级前是否检查关联课次/学员 | CourseServiceImpl |
| 链路 | 删除课程前是否检查关联班级 | CourseServiceImpl |
| 边界 | addStudentToClass 唯一性检查(防重复入班) | CourseServiceImpl |
| 边界 | maxStudentCount=0 时班级不可入班的逻辑 | CourseServiceImpl (已修复 NPE) |
| 边界 | 班级状态变更对 ScheduleLesson 的影响 | 跨模块调用 |
| 数据 | class_student.status 状态码定义(1活跃/2转出/3退出/4?) | ClassStudent entity |
| 数据 | 教师-课程关联 teacher_course 物理删除后的数据完整性 | CourseServiceImpl |
| 性能 | ClassStudent 按 classId+status 查询无联合索引 | Mapper 查询模式 |

### L2-3: Schedule 模块 (排课与教室) — 冲突检测核心

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\schedule\`

**依赖**: Course (L2)
**被依赖**: Attendance, Finance (扣课时)

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | createLesson → checkConflict → insert 的完整路径 | ScheduleController, ScheduleServiceImpl |
| 链路 | batchCreate 批量排课的冲突检测(批次内+DB内) | ScheduleServiceImpl.batchCreate |
| 边界 | 排课时间跨天的处理(是否允许 23:00-01:00) | hasTimeOverlap |
| 边界 | 调课审核通过后原课次 status=4 与学生考勤的关系 | auditAdjustRequest |
| 边界 | 教室容量与班级人数的一致性校验 | createLesson 不校验 |
| 数据 | ScheduleLesson.status 状态码(1待上/2完成/3取消/4已调) | entity |
| 数据 | RoomBooking 与 ScheduleLesson 的教室占用数据一致性 | 跨表约束 |
| 性能 | studentConflicts 每次 O(N) 查询所有班级学员 | ScheduleConflictService |

### L2-4: Enrollment 模块 (报名与审核) — 业务流程入口

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\enrollment\`

**依赖**: Student, Course (L2)
**被依赖**: Finance (缴费)

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | 报名→审核→缴费→入班的完整状态流转 | 跨模块追踪 |
| 链路 | 定时释放超时留位(Enrollment.status=5)的实现 | expirePendingEnrollments |
| 边界 | 已拒绝(status=4)的报名能否重新提交 | EnrollmentServiceImpl.create |
| 边界 | 审核时若报名关联的班级已被删除 | audit 中未校验 classId |
| 边界 | 定时任务异常时的重试与幂等性 | @Scheduled 无异常处理 |
| 数据 | enrollment.status ↔ payment.status ↔ class_student.status 三表一致性 | 跨表验证 |
| 性能 | 定时任务每分钟扫描全表，holdExpireTime 应有索引 | expirePendingEnrollments |

### L2-5: Finance 模块 (缴费与课时) — 资金安全核心

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\finance\`

**依赖**: Student, Course, Enrollment (L2)
**被依赖**: Salary, Statistics

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | 缴费→入班→课时账户→扣减流程 | createPayment |
| 链路 | 退费审核→退费入账→课时返还→退班流程 | auditRefund |
| 链路 | 续费 createRenewal 是否复用 createPayment | FinanceController |
| 边界 | 缴费金额负数校验 | createPayment L93 |
| 边界 | 退费金额不应超过剩余课时价值 | auditRefund L303-307 |
| 边界 | 同一报名可否多次缴费(未校验paymentRecord唯一性) | createPayment |
| 数据 | lesson_account.balance 与 lesson_flow 流水一致性 | 对账逻辑 |
| 数据 | 课时扣减时 account 不存在如何兜底 | deductLessons findAccount |
| 性能 | 财务流水按时间范围查询需索引 | paymentRecord.pay_time |

### L2-6: Attendance 模块 (考勤与请假) — 课时消耗引擎

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\attendance\`

**依赖**: Schedule, Course, Student (L2)
**被依赖**: Salary, Statistics

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | 家长请假→教务审核→自动建考勤→教师提交覆盖逻辑 | 跨模块完整链 |
| 链路 | 教师提交考勤→去重→反扣旧课时→新扣课时 | submit |
| 边界 | 同一课次同一学员是否可以多次提交(已去重) | submit L166-168 |
| 边界 | 请假日期类型校验(LocalDate vs LocalDateTime) | submitLeaveRequest |
| 边界 | 考勤提交后课次被删除的情况 | checkTeacherLessonOwnership |
| 数据 | attendance.status 与 deductLessons 的一致性 | submit 全路径 |
| 数据 | leave_request 与 attendance 审批联动的一致性 | audit→createLeaveAttendance |
| 性能 | batchSubmit N次调用 submit(含查重/扣课) | batchSubmit |

### L2-7: Salary 模块 (薪资核算) — 财务结算终点

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\salary\`

**依赖**: Attendance, Schedule, Course, Finance (L2)
**被依赖**: Statistics

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | 薪资核算→确认→发放→调整状态流转 | SalaryServiceImpl |
| 链路 | SalaryRule 按课程→班级→全局默认的取值优先级 | calculateSalary L247-250 |
| 边界 | 薪资核算时教师当月无课次 | calculateSalary |
| 边界 | 同一月份重复核算(已用 uk_teacher_month 防重) | calculateSalary L283-285 |
| 边界 | 调整金额正负数范围 | createAdjustment |
| 数据 | 课次统计口径(仅已完成 status=2，剔除代课课次) | calculateSalary L190-193 |
| 性能 | 按月聚合课次+考勤的查询效率 | calculateSalary 多层查询 |

### L2-8: Exam 模块 (考级管理)

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\exam\`

**依赖**: Student (L2)
**被依赖**: Statistics

| 维度 | 检查项 | 文件 |
|------|--------|------|
| 链路 | 报名→缴费→成绩录入 | ExamController, ExamServiceImpl |
| 边界 | 删除考级项目时关联报名如何处理 | deleteExamLevel |
| 边界 | 同一学员同一考级重复报名 | createSignup 无去重 |
| 数据 | 证书编号唯一性 | exam_signup.certificate_no |

### L3-1: Notice 模块 (公告通知)

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\notice\`

| 维度 | 检查项 |
|------|--------|
| 链路 | 废弃旧路由 `/api/notices/*` 是否可安全移除 |
| 边界 | 公告发布者权限检查 |
| 数据 | 公告与用户可见范围的关系 |

### L3-2: Learning 模块 (作业与学情)

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\learning\`

| 维度 | 检查项 |
|------|--------|
| 链路 | 作业布置→提交→批改流程 |
| 边界 | 批量创建学习记录时的有效性校验 |
| 数据 | homework 与 lesson 关联一致性 |

### L3-3: Statistics 模块 (统计看板)

`D:\codegitee\educational-administration\backend\src\main\java\com\pzhu\eduadmin\modules\statistics\`

| 维度 | 检查项 |
|------|--------|
| 链路 | dashboard→cards+charts 聚合口径 |
| 边界 | 空数据集下的统计输出(除零保护) |
| 数据 | 营收 = 缴费 - 退费 的计算口径 |
| 性能 | attendanceTrend 全表扫描所有已完成课次 |

---

## L4: 跨层集成检查 (最后执行)

仅在所有单模块检查通过后执行，聚焦模块间交互：

| 检查项 | 涉及模块 |
|--------|----------|
| 学员生命周期: 报名→审核→缴费→入班→上课→考勤→退课→退费全链路 | Student→Enrollment→Finance→Course→Schedule→Attendance |
| 教师工作流: 排课→上课→考勤→核算薪资→确认→发放 | Schedule→Attendance→Salary |
| 数据字典一致性: 所有 status 枚举值定义与使用 | 全模块 entity 注释 |
| 事务边界: 跨 Service 调用时 @Transactional 传播行为 | 全模块 ServiceImpl |
| 级联删除: 删除父实体时子实体处理策略 | Course→Class→ScheduleLesson 等 |
| 权限穿透: Controller @RequireRole 与 Service 内部权限检查一致性 | Controller+Service |

---

## 执行顺序建议

```
L2-1 Student ──┐
L2-2 Course  ──┤
               ├── L2-3 Schedule ──┐
               ├── L2-4 Enrollment─┤
               ├── L2-5 Finance  ──┼── L2-7 Salary
               └── L2-6 Attendance┘
               └── L2-8 Exam

L3-1 Notice ──┐
L3-2 Learning─┼── 可并行
L3-3 Statistics┘

L4 跨层集成检查
```

**注意**: L2-5 Finance 和 L2-6 Attendance 互有交叉（扣课时两边都要调用 LessonAccount），应前后连续检查。L2-7 Salary 依赖这两个模块的数据，应最后检查。
