# 第五轮后端代码审查报告

**日期:** 2026-07-20
**范围:** 全部后端模块（finance, attendance, schedule, course, user/auth, enrollment, student, salary, statistics, notification, learning, exam）
**方法:** 6 个并行审查代理，聚焦逻辑错误、竞态条件、数据完整性和安全漏洞

## 统计摘要

| 严重程度 | 数量 |
|---------|------|
| Critical | 7 |
| High | 15 |
| Medium | 31 |
| Low | 11 |
| **合计** | **64** |

---

## Critical（7个）

### C1. createRefund 未校验 studentId 与 enrollment 的归属关系

**文件:** `modules/finance/service/FinanceServiceImpl.java` ~L240-269

`createRefund` 中 `studentId` 直接来自请求体，未与 enrollment 的 studentId 比对。对比 `createPayment`（L107）正确执行了 `record.setStudentId(enrollment.getStudentId())`。

**后果:** 退费审批时按 record.studentId 查找课时账户并扣减，导致错误学员的课时被扣减。

**修复:** 在 createRefund 中强制 `record.setStudentId(enrollment.getStudentId())`。

---

### C2. @Version 旁路：考勤 CAS 与财务 updateById 之间的丢失更新

**文件:** `modules/attendance/service/AttendanceServiceImpl.java` ~L393, L433

考勤模块的 `deductLessons()`/`reverseDeduct()` 使用 `update(null, wrapper)` 手动 CAS 仅校验 remainingLessons，不触发 MyBatis-Plus @Version 插件（version 不递增）。财务模块使用 `updateById` 触发 @Version。

**后果:** 考勤扣减后 version 不变，财务并发 updateById 仍能通过 version 校验，覆盖考勤的扣减结果。学员课时余额静默丢失。

**修复:** 在 CAS wrapper 中增加 `.eq(LessonAccount::getVersion, account.getVersion()).set(LessonAccount::getVersion, account.getVersion() + 1)`，或统一改用 updateById。

---

### C3. 教室预约 TOCTOU：并发双重预约

**文件:** `modules/schedule/service/ScheduleServiceImpl.java` ~L434-445

`createRoomBooking()` 先 SELECT 冲突再 INSERT，无 @Transactional、无行锁、无唯一约束。两个并发请求均通过冲突检查后同时插入。

**后果:** 同一教室同一时段被双重预约。

**修复:** 添加 @Transactional + SELECT ... FOR UPDATE 锁定教室行，或添加数据库级约束。

---

### C4. 班级容量 TOCTOU：并发超员

**文件:** `modules/course/service/CourseServiceImpl.java` ~L278-302 (`addStudentToClass`)

容量检查为 count-then-insert 模式，默认 READ_COMMITTED 隔离级别无法阻止幻读。两个并发请求同时通过检查后均插入。

**后果:** 班级实际人数超过 maxStudentCount。

**修复:** 对 class_group 行加 SELECT ... FOR UPDATE，或使用乐观更新 `UPDATE SET current_count = current_count + 1 WHERE current_count < max_count`。

---

### C5. 非原子 version 递增导致 Token 失效机制被绕过

**文件:** `modules/user/service/UserServiceImpl.java` ~L135-138, L163-166, L183-185

version 递增在 Java 层执行（read-modify-write），两个并发操作均读到 version=N 并写入 N+1，丢失一次递增。

**后果:** 管理员重置密码/禁用用户后，若并发操作丢失了 version 递增，旧 Token 仍然有效。整个 Token 失效安全机制被架空。

**修复:** 改用原子 SQL：`.setSql("version = version + 1")`（RoleServiceImpl L144 已有正确示范）。

---

### C6. 登出不使 Token 失效

**文件:** `modules/auth/controller/AuthController.java` ~L45-49

`/api/auth/logout` 为空操作。JWT 在"登出"后仍有效最长 120 分钟。

**后果:** Token 被盗后用户无法通过登出来撤销，攻击者在剩余有效期内保持完整访问权限。

**修复:** 登出时原子递增 user.version，使该用户所有现有 Token 立即失效。

---

### C7. IDOR：家长可通过管理端点为任意学员创建报名

**文件:** `modules/enrollment/controller/EnrollmentController.java` ~L40-52

`POST /api/edu/enrollments` 的 @RequireRole 包含 PARENT，但仅设置 parentUserId，未校验家长-学员绑定关系。对比 `ParentController.createEnrollment()` 正确检查了绑定。

**后果:** 任何已认证家长可为系统中任意学员创建报名记录，绕过 ParentController 的授权检查。

**修复:** 从 @RequireRole 中移除 PARENT，或添加与 ParentController 相同的绑定校验。

---

## High（15个）

### H1. 续费 CAS 无效（状态 3→3）

**文件:** `modules/finance/service/FinanceServiceImpl.java` ~L110-118

续费时 enrollment 已为 status=3，CAS `WHERE status=3 SET status=3` 对并发请求均返回 rows=1，无保护作用。

**后果:** 并发续费双重扣款、双重课时充值。

---

### H2. 续费时重复插入 ClassStudent 记录

**文件:** `modules/finance/service/FinanceServiceImpl.java` ~L180-194

学员已在班级中（status=1）时，续费逻辑未检查现有活跃记录，直接插入新行。

**后果:** 班级花名册膨胀，容量统计失真，考勤查询异常。

---

### H3. 考勤并发提交未处理 DuplicateKeyException

**文件:** `modules/attendance/service/AttendanceServiceImpl.java` ~L185-213

SELECT-then-INSERT 去重在并发下失效，DuplicateKeyException 未捕获导致 500 错误。

**后果:** 教师双击提交按钮触发 500；batchSubmit 中一条失败回滚整批。

---

### H4. 请假审批不撤销已有"出勤"扣减

**文件:** `modules/attendance/service/LeaveRequestServiceImpl.java` ~L186-195

请假审批时若已存在出勤记录（status=1, deductLessons=1），代码直接 continue 跳过，不执行反向扣减。

**后果:** 学员被标记出勤并扣减课时后，即使请假获批，课时仍被扣除。

---

### H5. 请假申请去重竞态（无数据库约束）

**文件:** `modules/attendance/service/LeaveRequestServiceImpl.java` ~L79-85

去重检查为应用层 selectCount，leave_request 表无 (student_id, lesson_date) 唯一约束。

**后果:** 并发提交产生重复请假记录，审批后数据不一致。

---

### H6. 课次部分更新绕过冲突检测

**文件:** `modules/schedule/service/ScheduleServiceImpl.java` ~L242-251

请求体中未包含的字段为 null，冲突检测对 null 字段跳过检查。但 updateById 仅更新非 null 字段，DB 保留原值。

**后果:** 仅修改时间时不检测教师冲突，导致教师被安排重叠课次。

---

### H7. 调课审批跨午夜产生不可检测课次

**文件:** `modules/schedule/service/ScheduleServiceImpl.java` ~L500-502

`LocalTime.plus()` 在跨午夜时回绕（23:00 + 2h = 01:00），导致 endTime < startTime。

**后果:** 冲突检测公式对此课次永远返回 false，该时段可被无限叠加排课。

---

### H8. 调课审核状态参数未校验

**文件:** `modules/schedule/controller/ScheduleController.java` ~L209

status 参数为任意 Integer，可传入 99/0/-1 等非法值并持久化。

**后果:** 请求进入未定义状态，既不创建新课次也不记录驳回。

---

### H9. 账户锁定 DoS：攻击者可锁定任意账户

**文件:** `modules/auth/service/AuthService.java` ~L41-53

暴力破解防护仅按 username 计数，无 IP 维度限制。攻击者对已知用户名发送 5 次错误密码即可锁定 15 分钟。

**后果:** 管理员/教师在关键时期被恶意锁定，反复执行可形成持续 DoS。

---

### H10. 禁用用户检查在密码验证之后，泄露凭据有效性

**文件:** `modules/auth/service/AuthService.java` ~L56-68

先验证密码再检查状态。攻击者对禁用账户暴力破解时，密码正确返回"账号已被禁用"，错误返回"用户名或密码错误"。

**后果:** 攻击者可确认禁用账户的正确密码，为账户重新启用后的攻击做准备。

---

### H11. isLocked() 竞态可清除刚设置的锁定

**文件:** `modules/auth/service/AuthService.java` ~L147-173

`isLocked()` 中 count.set(0) 和 lockTime=0 非原子操作，与 `increment()` 并发时可清除刚设置的锁。

**后果:** 自动化工具可绕过锁定机制，在 15 分钟窗口内超过 5 次尝试。

---

### H12. 并发薪资调整导致 totalAmount 丢失更新

**文件:** `modules/salary/service/SalaryServiceImpl.java` ~L366-392

两个并发 createAdjustment 在 REPEATABLE_READ 下各自 SUM 仅见自身插入，后提交者覆盖先提交者的 totalAmount。

**后果:** 薪资总额静默错误，需人工对账才能发现。

---

### H13. 教师可访问任意学员档案（无归属校验）

**文件:** `modules/learning/controller/LearningController.java` ~L64-68

`GET /api/teacher/students/{studentId}/archive` 仅要求 TEACHER 角色，未校验教师是否任教该学员的班级。

**后果:** 任何教师可枚举 studentId 查看全系统学员的考勤、作业、学习记录。

---

### H14. JWT Token 通过 URL 查询参数暴露（SSE 端点）

**文件:** `security/JwtInterceptor.java` ~L43-47; `modules/notification/controller/NotificationController.java` ~L23

SSE 端点通过 `?token=xxx` 传递 JWT，URL 被记录在服务器日志、代理日志、浏览器历史中。

**后果:** 有日志访问权限的人员可劫持任意用户会话。

---

### H15. 导出时 payType 为 null 触发 NPE 导致整个导出失败

**文件:** `modules/statistics/service/ExportService.java` ~L59

`r.getPayType() == 1` 对 null Integer 自动拆箱抛出 NullPointerException。

**后果:** 一条 payType 为 null 的记录导致整份报表导出失败（500 错误）。

---

## Medium（31个）

### M1. 家长退费金额使用缩小的 totalLessons 分母

**文件:** `modules/finance/controller/ParentRefundController.java` ~L166-177

家长端使用 `account.totalLessons`（退费后递减）作分母，审核端使用 `sumLessonCountByEnrollmentId`（稳定）。部分退费后家长看到的建议金额虚高。

---

### M2. 退费审核使用提交时的过期 lessonCount

**文件:** `modules/finance/service/FinanceServiceImpl.java` ~L407-409

提交退费时记录的 remainingLessons 在审核时可能已被考勤消耗，导致"剩余课时不足"错误或过度扣减。

---

### M3. 操作日志失败回滚整个业务事务

**文件:** `modules/finance/service/FinanceServiceImpl.java` ~L208, L317

`operationLogService.log()` 在 @Transactional 方法内调用，日志写入失败会回滚缴费/退费操作。

---

### M4. 班级容量后置检查在缴费插入之后

**文件:** `modules/finance/service/FinanceServiceImpl.java` ~L196-204

容量后置检查失败时回滚整个事务（含已插入的缴费记录），但现实中路费已收取。

---

### M5. 教师可审批非自己班级的请假

**文件:** `modules/attendance/service/LeaveRequestServiceImpl.java` ~L275-293

"通用路径"仅检查教师是否任教学员所在的任意班级，未限定到请假日期对应的具体课次。

---

### M6. 考勤状态为 null 时 NPE

**文件:** `modules/attendance/service/AttendanceServiceImpl.java` ~L196, L216

请求体未包含 status 字段时，Integer 自动拆箱抛出 NPE → 500。

---

### M7. 负数 deductLessons 导致反向扣减时窃取课时

**文件:** `modules/attendance/service/AttendanceServiceImpl.java` ~L381-383

guard 仅跳过 null 和 0，负数 deductLessons 在 reverseDeduct 中执行 `balance.add(负数)` = 减少余额。

---

### M8. 考勤状态值无校验，可存入任意值

**文件:** `modules/attendance/service/AttendanceServiceImpl.java` ~L163-224

无 status IN (1,2,3,4) 校验，非法值产生"幽灵"记录阻断后续正常提交。

---

### M9. 冲突检测包含非活跃学员

**文件:** `modules/schedule/service/ScheduleConflictServiceImpl.java` ~L99-110

查询 class_student 未过滤 status=1，已退班学员仍触发冲突警告。

---

### M10. 更新不存在的课次时 NPE

**文件:** `modules/schedule/service/ScheduleServiceImpl.java` ~L247-249

`selectById` 返回 null 后 `notifyLessonTeacher(null, ...)` 抛出 NPE。

---

### M11. deleteCourse/deleteClassGroup 无 @Transactional

**文件:** `modules/course/service/CourseServiceImpl.java` ~L113-140, L221-243

多步检查+删除无事务保护，并发创建可产生孤立引用。

---

### M12. 教室预约缺少输入校验

**文件:** `modules/schedule/service/ScheduleServiceImpl.java` ~L434-445

未校验 classroomId/startTime/endTime 非空及 startTime < endTime，null 值使冲突检查失效。

---

### M13. createRole 缺少 DuplicateKeyException 捕获

**文件:** `modules/user/service/RoleServiceImpl.java` ~L50-59

唯一性检查为应用层 selectCount，并发创建时未捕获 DuplicateKeyException → 500。

---

### M14. 废弃端点跳过角色存在性校验

**文件:** `modules/user/controller/RoleController.java` ~L82-85

`/code/{roleCode}/permissions` 直接传入 pathVariable，不校验角色是否存在。

---

### M15. JWT 短密钥零填充降低安全强度

**文件:** `security/JwtUtil.java` ~L27-34

少于 32 字节的密钥被零填充，攻击者知道填充逻辑后可仅暴力破解前 N 字节。

---

### M16. 无最后一个 SUPER_ADMIN 保护

**文件:** `modules/user/service/UserServiceImpl.java` ~L126-137

可降级/禁用唯一的 SUPER_ADMIN，导致系统永久不可管理。

---

### M17. updateUserStatus/resetPassword 缺少 @Transactional

**文件:** `modules/user/service/UserServiceImpl.java` ~L158, L172

读-改-写无事务保护，部分失败时审计日志与实际状态不一致。

---

### M18. 报名创建 TOCTOU：DuplicateKeyException 捕获可能为死代码

**文件:** `modules/enrollment/service/EnrollmentServiceImpl.java` ~L150-163

条件唯一性（排除 status 4,5）无法用标准 MySQL 唯一索引表达，若无对应约束则 catch 永不触发。

---

### M19. 报名创建/更新无 classId↔courseId 归属校验

**文件:** `modules/enrollment/service/EnrollmentServiceImpl.java` ~L137

可将 Course B 的班级分配给 Course A 的报名，破坏数据完整性。

---

### M20. 并发薪资计算产生重复记录

**文件:** `modules/salary/service/SalaryServiceImpl.java` ~L286-312

selectOne 检查后 insert，无唯一约束或 DuplicateKeyException 处理。

---

### M21. bindParent 竞态：未处理 DuplicateKeyException

**文件:** `modules/student/service/StudentServiceImpl.java` ~L226-241

并发绑定请求在 selectCount 和 insert 之间竞态，DuplicateKeyException 未捕获 → 500。

---

### M22. deleteStudent 无 @Transactional

**文件:** `modules/student/service/StudentServiceImpl.java` ~L155-193

多步前置检查+删除无事务，并发写入可产生孤立财务/报名记录。

---

### M23. substituteRate 接受零或负值

**文件:** `modules/salary/service/SalaryServiceImpl.java` ~L100-114

仅默认 null→1，不校验 >0。负值使代课费为负，减少教师总薪资。

---

### M24. updateSalaryRule 允许修改 teacherId/courseId

**文件:** `modules/salary/service/SalaryServiceImpl.java` ~L118-128

修改规则关联的教师/课程后，历史薪资重算使用错误费率。

---

### M25. 到期提醒去重键阻止重复通知

**文件:** `modules/notification/service/ReminderService.java` ~L106

去重键不含日期，7 天到期窗口内家长仅收到一次提醒。

---

### M26. 学习记录/考试报名并发重复

**文件:** `modules/learning/service/LearningServiceImpl.java` ~L120-127; `modules/exam/service/ExamServiceImpl.java` ~L134-139

SELECT-then-INSERT 去重无数据库约束保护。

---

### M27. 统计全表加载：OOM 风险

**文件:** `modules/statistics/service/StatisticsServiceImpl.java` ~L445, L500

`getCourseProfit`/`getPaymentRate` 加载整张 payment_record 表到内存。

---

### M28. 流失趋势使用 updateTime 而非实际离开时间

**文件:** `modules/statistics/service/StatisticsServiceImpl.java` ~L330-350

任何编辑都更新 updateTime，导致流失趋势数据不可靠。

---

### M29. 教师可向任意课次写入学习记录

**文件:** `modules/learning/controller/LearningController.java` ~L87-101

未校验教师是否为该课次的授课教师。

---

### M30. 过期且低余额账户无任何通知

**文件:** `modules/notification/service/ReminderService.java` ~L70-88

过期账户（expireDate < today）被 continue 跳过，即使 remaining > 0 也不通知。

---

### M31. 出勤率分母不一致

**文件:** `modules/statistics/service/StatisticsServiceImpl.java` ~L160 vs `modules/learning/service/LearningServiceImpl.java` ~L137

仪表盘用 status IN (1,2,3)，学员档案用排除 null 和 4。存在其他状态时两处结果不同。

---

## Low（11个）

### L1. 零元退费金额与"未提供"不可区分

**文件:** `modules/finance/controller/FinanceController.java` ~L88

---

### L2. 用户上下文为 null 时安全检查静默跳过

**文件:** `modules/attendance/service/AttendanceServiceImpl.java` ~L370-371

---

### L3. 批量考勤无大小限制

**文件:** TeacherAttendanceController ~L62

---

### L4. 自动排课锁不覆盖手动创建

**文件:** `modules/schedule/service/ScheduleServiceImpl.java` ~L310-382

---

### L5. 注册端点无 IP 限流

**文件:** `modules/auth/controller/AuthController.java` ~L34

---

### L6. 登录用户名无最大长度限制

**文件:** LoginRequest ~L9

---

### L7. 权限码未校验是否存在于 permission 表

**文件:** `modules/user/service/RoleServiceImpl.java` ~L136-140

---

### L8. 报名删除不检查报名状态

**文件:** `modules/enrollment/service/EnrollmentServiceImpl.java` ~L174-199

---

### L9. 退学时无缴费记录仍创建退费记录

**文件:** `modules/student/service/StudentServiceImpl.java` ~L396-416

---

### L10. @Async 自调用绕过 Spring 代理

**文件:** `modules/notification/service/NotificationServiceImpl.java` ~L83-98

---

### L11. SSE 每用户单连接：多标签页丢失通知

**文件:** `modules/notification/service/NotificationServiceImpl.java` ~L25

---

## 修复优先级建议

**Phase 1（Critical + 安全相关 High）：** C1-C7, H9-H11, H13-H14 — 安全漏洞和数据完整性核心问题

**Phase 2（数据完整性 High）：** H1-H8, H12, H15 — 并发竞态和业务逻辑错误

**Phase 3（Medium 批次 1）：** M1-M12 — 财务/考勤/排课模块

**Phase 4（Medium 批次 2）：** M13-M31 — 用户/报名/薪资/统计模块

**Phase 5（Low）：** L1-L11 — 防御性加固
