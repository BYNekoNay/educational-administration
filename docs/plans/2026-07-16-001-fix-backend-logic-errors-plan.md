---
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
execution: code
product_contract_source: ce-plan-bootstrap
created: 2026-07-16
---

# fix: 后端 42 个逻辑错误分批修复计划

## Summary

修复教务管理系统后端全部 42 个逻辑错误，分 3 个阶段 14 个实施单元执行。第一阶段修复 5 个 CRITICAL 级 Bug（涉及退费金额清零、薪资膨胀、删错数据行等核心功能缺陷）；第二阶段修复 12 个 HIGH 级 Bug（数据一致性、竞态条件、安全漏洞）；第三阶段修复 25 个 MEDIUM/LOW 级 Bug（健壮性、校验、防御性编程）。

## Problem Frame

后端代码审查发现 42 个逻辑错误，分布在 9 个业务模块中。其中最严重的问题直接影响资金计算正确性（退费金额被清零、代课薪资成倍膨胀）、核心功能可用性（从班级移除学员删错行）、以及数据安全（CORS 全开放、SQL 拼接注入）。这些 Bug 按严重程度分为 CRITICAL（5 个）、HIGH（12 个）、MEDIUM（21 个）、LOW（4 个），需要分批有序修复以避免引入回归。

## Requirements

- R1: 5 个 CRITICAL 级 Bug 必须全部修复，确保资金计算和数据操作的正确性
- R2: 12 个 HIGH 级 Bug 必须修复，确保数据一致性、事务安全和基本安全防护
- R3: 25 个 MEDIUM/LOW 级 Bug 应当修复，提升系统健壮性和防御性编程水平
- R4: 每个修复必须附带单元测试验证，不引入回归
- R5: 修复按模块聚合、按依赖排序，减少上下文切换和合并冲突

## Key Technical Decisions

- KTD1: **事务注解统一使用 `@Transactional(rollbackFor = Exception.class)`** — 与项目中 attendance/finance 模块的现有模式保持一致，避免 user 模块的裸 `@Transactional` 反模式
- KTD2: **重复检查统一用 selectCount + LambdaQueryWrapper** — 项目中已有此模式（如 FinanceServiceImpl 的退费校验），所有重复检查场景复用
- KTD3: **容量统计查询统一过滤 `status=1`** — 通过 QueryWrapper 条件过滤而非软删除，保持数据可追溯性
- KTD4: **Mapper SQL 拼接改为 MyBatis-Plus 的 `selectBatchIds` 或 XML foreach** — 优先使用框架内置方法，避免手写 SQL 的注入风险

---

## Phase 1: CRITICAL — 资金与数据正确性（Bug #1~#5）

### U1. 修复财务模块退费金额清零 + 缴费校验

**Goal:** 修复退费金额被静默清零（Bug #1）和缴费无输入校验（Bug #3）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/finance/FinanceServiceMockTest.java`

**Approach:**
Bug #1（退费金额清零）：在 `processRefundApproval` 方法中，自动计算的退费金额被入参对象的旧值覆盖。修复方式是确保计算结果直接赋值到入参对象，或在方法开头保存计算值、在后续流程中使用该保存值而非重新读取入参字段。需要仔细审查第 209 行附近的赋值顺序。

Bug #3（缴费无校验）：在 `createPayment` 方法入口处增加 `lessonCount` 的 null 检查和正数检查，以及 `amount` 的非负检查。抛出 `BusinessException(400, "...")` 与现有模式一致。

**Test scenarios:**
- 正常退费：传入退费记录，验证实际退费金额 = 按课时比例计算的金额（非 0）
- 退费金额为 0 时拒绝：`BusinessException(400)`
- 缴费 lessonCount 为 null 时：`BusinessException(400, "课时数不能为空")`
- 缴费 lessonCount 为负数时：`BusinessException(400, "课时数必须为正数")`
- 缴费 amount 为负数时：`BusinessException(400, "缴费金额不能为负数")`

**Verification:** 单元测试全部通过；手动通过 API 调用退费接口确认返回的退费金额与计算值一致。

---

### U2. 修复薪资模块代课费膨胀

**Goal:** 修复代课薪资计算逻辑，每位教师只计算自己授课的代课奖金（Bug #2）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/salary/service/SalaryServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/salary/SalaryServiceMockTest.java`

**Approach:**
第 237-244 行的代课费计算逻辑中，每位教师都被计入了所有教师的代课数量。修复方式是在代课统计查询中增加 `teacherId` 过滤条件，确保每位教师只统计自己代替他人授课的课次数量。需要审查代课查询的 SQL/QueryWrapper，加入 `.eq(ScheduleLesson::getTeacherId, currentTeacherId)` 条件。

**Test scenarios:**
- 教师 A 代课 3 节、教师 B 代课 2 节：A 的代课奖金基于 3 节，B 基于 2 节（非 5 节）
- 无代课记录时：代课奖金为 0
- 同一教师既是原授课人又是代课人时：不计代课奖金

**Verification:** 单元测试通过；手动创建含 3 位教师的薪资记录，验证各自的代课费独立计算。

---

### U3. 修复课程模块移除学员删错行

**Goal:** 修复 `removeStudentFromClass` 使用错误 ID 删除数据的问题（Bug #4）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/service/CourseServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/course/CourseServiceTest.java`（新建）

**Approach:**
当前代码将 URL 中的 `studentId` 直接传给 `classStudentMapper.deleteById(id)`，但 `deleteById` 期望的是 `class_student` 表的主键，不是 student 的 ID。修复方式：
1. 通过 `classId` 和 `studentId` 联合查询 `class_student` 记录：`new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, classId).eq(ClassStudent::getStudentId, studentId)`
2. 若记录不存在，抛出 `BusinessException(404, "该学员不在此班级中")`
3. 用查到的记录的 PK 执行 `deleteById`

同时需要修复 Controller 中 `classId` 路径变量被忽略的问题——Service 方法应同时接收 `classId` 和 `studentId` 参数。

**Test scenarios:**
- 正常移除：学员在班级中，调用后 class_student 记录被删除
- 学员不在班级中：抛出 `BusinessException(404)`
- 验证移除后班级容量计数减少 1
- 验证不影响同一班级中其他学员的记录

**Verification:** 单元测试通过；手动添加学员到班级后移除，验证数据库中正确的记录被删除。

---

### U4. 修复考勤模块 deductLessons 空指针

**Goal:** 修复更新已有考勤记录时 `deductLessons` 为 null 导致的 NPE（Bug #5）
**Requirements:** R1
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/AttendanceServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/attendance/AttendanceServiceTest.java`

**Approach:**
在 `submit()` 方法中，`deductLessons` 的默认值 `BigDecimal.ONE` 仅在"新建"分支赋值（第 175-177 行），"更新已有记录"分支（第 163-171 行）未设默认值。修复方式：将默认值赋值逻辑提到 if/else 分支之前，确保两个分支都能获得有效的 `deductLessons` 值：

```
if (attendance.getDeductLessons() == null) {
    attendance.setDeductLessons(BigDecimal.ONE);
}
```

同时修复 `reverseDeduct`（Bug #8，第 265 行）的关联 NPE 风险：增加 null guard。

**Test scenarios:**
- 更新已存在的出勤记录（status=1），`deductLessons` 为 null：不抛 NPE，使用默认值 1
- 更新已存在的出勤记录，`deductLessons` 为 0.5：使用传入值
- `reverseDeduct` 遇到 `deductLessons` 为 null 的记录：安全跳过不抛异常

**Verification:** 单元测试通过；手动对同一学员同一课次提交两次考勤，验证第二次更新不崩溃。

---

## Phase 2: HIGH — 数据一致性与安全（Bug #6~#17）

### U5. 修复退费学员占用班级名额

**Goal:** 容量统计查询过滤 `class_student.status`，退费学员不再占用名额（Bug #6）
**Requirements:** R2
**Dependencies:** U3（移除学员逻辑修复后才能正确验证容量）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/service/CourseServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/course/CourseServiceTest.java`

**Approach:**
在以下所有容量统计查询中增加 `.eq(ClassStudent::getStatus, 1)` 条件：
1. `CourseServiceImpl.addStudentToClass` — 第 189 行的 `selectCount`
2. `FinanceServiceImpl.createPayment` — 第 139 行的 `selectCount`

退费时将 `class_student.status` 设为 3（已有逻辑），但查询只统计 `status=1` 的活跃学员。这样退费学员不占名额，但数据保留可追溯。

同时需要在退费后再次报名的场景中，检查是否已存在 `status=3` 的记录，若有则更新为 `status=1` 而非新建记录。

**Test scenarios:**
- 班级容量 10 人，已有 8 个活跃 + 2 个退费：仍可添加 2 个新学员
- 退费学员重新报名：复用原记录（status 从 3 恢复为 1），不创建重复记录
- 正常满员后拒绝新学员：`BusinessException(409, "班级已满")`

**Verification:** 单元测试通过；手动模拟 报名→退费→再报名 全流程验证。

---

### U6. 修复考勤对未报名学员扣课时 + reverseDeduct 防护

**Goal:** 考勤提交前验证学员属于该课次所在班级（Bug #7）
**Requirements:** R2
**Dependencies:** U4（deductLessons NPE 修复后才能安全执行考勤流程）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/AttendanceServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/attendance/AttendanceServiceTest.java`

**Approach:**
在 `submit()` 方法的教师权限校验之后、考勤处理之前，增加学员-班级关联验证：
1. 查询 `class_student` 表验证 `studentId` + `lesson.getClassId()` 存在 `status=1` 的记录
2. 不存在则抛出 `BusinessException(409, "该学员未在此班级报名，无法考勤")`

**Test scenarios:**
- 学员在班级中且 status=1：考勤正常提交
- 学员不在该班级中：`BusinessException(409)`
- 学员在班级中但 status=3（已退费）：`BusinessException(409)`

**Verification:** 单元测试通过；手动对非班级学员提交考勤，验证被拒绝。

---

### U7. 修复报名重复检查 + 事务保护

**Goal:** 防止重复报名（Bug #9）+ 报名审核加事务保护（Bug #12）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/service/EnrollmentServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/enrollment/EnrollmentServiceTest.java`（新建）

**Approach:**
Bug #9（重复报名）：在 `create` 方法入口处查询是否已存在相同 `(studentId, courseId)` 且 `status != 4`（非拒绝）的报名记录：
```
selectCount(new LambdaQueryWrapper<Enrollment>()
    .eq(Enrollment::getStudentId, studentId)
    .eq(Enrollment::getCourseId, courseId)
    .ne(Enrollment::getStatus, 4))
```
若存在则抛出 `BusinessException(409, "该学员已报名此课程")`。

Bug #12（审核竞态）：在 `audit` 方法上增加 `@Transactional(rollbackFor = Exception.class)`，并将状态更新改为原子操作：`UPDATE enrollment SET status=? WHERE id=? AND status=1`，通过受影响行数判断是否成功。

**Test scenarios:**
- 同一学员重复报名同一课程：`BusinessException(409)`
- 报名被拒绝后重新报名：允许（status=4 的记录不阻止新报名）
- 并发审核同一条报名：只有一个成功，另一个抛出 `BusinessException(409, "该报名已被审核")`
- 审核无效状态值（如 status=5）：`BusinessException(400)`

**Verification:** 单元测试通过；手动快速连续点击两次审核按钮验证只有一个生效。

---

### U8. 修复班级添加学员重复检查 + 事务保护 + 级联删除

**Goal:** 防止重复添加学员（Bug #10）、容量检查加事务（Bug #11）、删除课程/班级级联检查（Bug #13, #14）
**Requirements:** R2
**Dependencies:** U5（容量统计修复后才能正确验证）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/service/CourseServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/course/CourseServiceTest.java`

**Approach:**
Bug #10（重复添加）：在 `addStudentToClass` 方法中，容量检查之前增加重复检查。

Bug #11（事务）：在 `addStudentToClass` 方法上增加 `@Transactional(rollbackFor = Exception.class)`。

Bug #13（删除课程级联）：在 `deleteCourse` 方法中，删除前查询该课程下是否有活跃班级（`class_group` 表 `courseId=? AND status!=0`），有则拒绝删除。

Bug #14（删除班级级联）：在 `deleteClassGroup` 方法中，删除前查询该班级下是否有活跃学员（`class_student` 表 `classId=? AND status=1`），有则拒绝删除。

**Test scenarios:**
- 同一学员重复添加到同一班级：`BusinessException(409, "该学员已在此班级中")`
- 并发添加至满员：只有一个成功
- 删除有活跃班级的课程：`BusinessException(409, "该课程下存在活跃班级，无法删除")`
- 删除有活跃学员的班级：`BusinessException(409, "该班级中存在学员，无法删除")`
- 删除无关联数据的课程/班级：正常删除

**Verification:** 单元测试通过。

---

### U9. 修复考级报名重复检查 + 存在性验证 + 级联删除

**Goal:** 考级报名去重（Bug #15）、验证项目和学员存在性（Bug #26）、删除考级项目检查关联（Bug #27）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/exam/service/ExamServiceImpl.java`
- `backend/src/test/java/com/pzhu/eduadmin/modules/exam/ExamServiceTest.java`（新建）

**Approach:**
Bug #15：`createExamSignup` 入口查询 `(examId, studentId)` 是否已存在。
Bug #26：验证 `examId` 和 `studentId` 对应的记录存在。
Bug #27：`deleteExamLevel` 前查询是否有 `exam_signup` 关联记录。

**Test scenarios:**
- 重复报名同一考级：`BusinessException(409)`
- 报名不存在的考级项目：`BusinessException(404)`
- 报名不存在的学员：`BusinessException(404)`
- 删除有报名记录的考级项目：`BusinessException(409)`
- 删除无关联的考级项目：正常删除

**Verification:** 单元测试通过。

---

### U10. 修复 JWT roleCode NPE + CORS 安全

**Goal:** 修复 JWT 拦截器空指针（Bug #16）和 CORS 安全配置（Bug #17）
**Requirements:** R2
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/security/JwtInterceptor.java`
- `backend/src/main/java/com/pzhu/eduadmin/config/WebMvcConfig.java`

**Approach:**
Bug #16：在第 58 行 `roleCode::equals` 之前增加 null 检查。若 `roleCode` 为 null，直接返回 403。

Bug #17：将 `allowedOriginPatterns("*")` 替换为具体的前端域名列表。开发环境保留 `http://localhost:*`，生产环境从配置文件读取。

**Test scenarios:**
- JWT 无 roleCode claim：返回 403（非 500）
- JWT roleCode 为 "PARENT" 访问 SUPER_ADMIN 接口：返回 403
- 跨域请求来自 localhost:5173：允许
- 跨域请求来自恶意域名：被 CORS 拦截

**Verification:** 手动构造无 roleCode 的 JWT 验证返回 403；启动后端后用浏览器开发者工具验证 CORS 响应头。

---

## Phase 3: MEDIUM/LOW — 健壮性与规范（Bug #18~#42）

### U11. 修复安全与权限类 Bug

**Goal:** 修复家长伪造 parentUserId（#18）、报名状态绕过（#20）、考勤权限缺失（#36）、家长查看非本班数据（#30）
**Requirements:** R3
**Dependencies:** U7（报名模块修复后再加固）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/controller/EnrollmentController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/controller/AdminAttendanceController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/learning/controller/LearningController.java`

**Approach:**
Bug #18：当当前用户角色为 PARENT 时，无论请求体是否传入 `parentUserId`，都强制从登录上下文覆盖。
Bug #20：报名更新接口限制可更新字段（只允许 `classId`、`remark`），或增加状态转换校验。
Bug #36：为考勤单条查询接口增加 `@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})`。
Bug #30：家长查看作业/学情时验证 `lessonId` 对应的课次属于该学员所在班级。

**Test scenarios:**
- 家长传入其他 parentUserId：被覆盖为自身 ID
- 报名 PUT 请求传入 status=3：被忽略或拒绝
- 无权限用户访问考勤详情：403
- 家长查看非本班课次的作业：`BusinessException(403)`

**Verification:** 单元测试通过。

---

### U12. 修复校验与数据完整性类 Bug

**Goal:** 修复 @Valid 缺失（#19, #39）、课程状态验证（#21）、请假状态校验（#24, #25）、学情 studentId 校验（#28）、parentIds null 过滤（#37）、audit 校验顺序（#38）
**Requirements:** R3
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/controller/CourseController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/notice/controller/NoticeController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/controller/ParentController.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/LeaveRequestServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/learning/service/LearningServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/service/EnrollmentServiceImpl.java`

**Approach:**
逐个修复：
- #19: `CourseController.updateCourse` 增加 `@Valid`
- #39: `NoticeController.update` 和 `updateLegacy` 增加 `@Valid`
- #21: `ParentController.createEnrollment` 增加课程存在性和状态检查
- #24: `LeaveRequestServiceImpl.audit` 增加 `status` 值范围校验（只允许 2 或 3）
- #25: 请假考勤创建时过滤课次状态 `in(ScheduleLesson::getStatus, 1, 2)`
- #28: `LearningServiceImpl.batchCreateRecords` 增加 `studentId` 非 null 校验
- #37: `EnrollmentServiceImpl.populateNames` 为 `parentIds` 增加 `.filter(Objects::nonNull)`
- #38: `audit()` 方法将状态值校验提到 setter 调用之前

**Test scenarios:**
- 每个修复点对应至少 1 个正向 + 1 个反向测试用例
- 重点关注：课程更新传空名称被拒绝、请假审核传 status=1 被拒绝、学情批量创建含 null studentId 被拒绝

**Verification:** 单元测试通过。

---

### U13. 修复数据展示与通用工具类 Bug

**Goal:** 修复 populateNames NPE（#22）、enrichClassGroupNames 软删除遗漏（#23）、审计日志顺序（#31）、PageQuery 上限（#32）、LIKE 转义（#33）、getById 404（#34）、异常泄露（#35）、Mapper SQL 拼接（#29）
**Requirements:** R3
**Dependencies:** —
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/service/EnrollmentServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/service/CourseServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/notice/service/NoticeServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/common/PageQuery.java`
- `backend/src/main/java/com/pzhu/eduadmin/common/QueryHelper.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/notice/controller/NoticeController.java`
- `backend/src/main/java/com/pzhu/eduadmin/common/GlobalExceptionHandler.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/schedule/mapper/ScheduleLessonMapper.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/student/mapper/StudentMapper.java`（路径需确认）
- `backend/src/main/java/com/pzhu/eduadmin/modules/course/mapper/ClassGroupMapper.java`（路径需确认）

**Approach:**
- #22: `Collectors.toMap` 增加 null 值 fallback：`u -> u.getRealName() != null ? u.getRealName() : u.getUsername()`
- #23: `enrichClassGroupNames` 改用 `selectNamesByIdsIncludeDeleted`
- #31: 审计日志移到 `deleteById` 之后，仅在删除成功时记录
- #32: `PageQuery` 增加 `@Max(200)` 注解或在 service 层 clamp
- #33: `QueryHelper` 对 keyword 转义 `%` 和 `_` 字符
- #34: `NoticeController.get` 增加 null 检查返回 404
- #35: `GlobalExceptionHandler` 对 500 错误返回通用消息 `"服务器内部错误，请稍后再试"`
- #29: 三个 Mapper 的 `${ids}` 改为 MyBatis-Plus `selectBatchIds(Collection)` 或 XML `<foreach>`

**Test scenarios:**
- populateNames 含 realName=null 的用户：不抛 NPE，使用 username 替代
- 搜索关键字含 `%`：只匹配字面 `%`，非全表
- pageSize=999999：被限制为 200
- 查询不存在的公告 ID：返回 code=404
- 500 错误响应：不含表名/列名等内部信息

**Verification:** 单元测试通过；手动搜索 `%` 验证不返回全表。

---

### U14. 修复 LOW 级杂项 Bug

**Goal:** 修复 batchSubmit 返回值问题（#42）、toMap merge 函数（#41）、操作日志 IP（#40）
**Requirements:** R3
**Dependencies:** U4, U6（考勤模块修复后）
**Files:**
- `backend/src/main/java/com/pzhu/eduadmin/modules/attendance/service/AttendanceServiceImpl.java`
- `backend/src/main/java/com/pzhu/eduadmin/modules/notice/service/NoticeServiceImpl.java`

**Approach:**
- #42: `batchSubmit` 在每次 `submit(a)` 后用返回值或查询结果同步入参对象的字段
- #41: `Collectors.toMap` 增加 merge 函数 `(a, b) -> a`
- #40: `NoticeServiceImpl.logOperation` 通过 `RequestContextHolder` 获取实际请求 IP

**Test scenarios:**
- batchSubmit 返回列表中每个对象的 ID 和 checkTime 与数据库一致
- toMap 遇到重复 key 不抛异常

**Verification:** 单元测试通过。

---

## Scope Boundaries

### In Scope
- 42 个已识别的后端逻辑错误修复
- 每个修复对应的单元测试
- 保持现有 API 接口契约不变（不改变前端调用方式）

### Out of Scope
- 前端代码修改
- 数据库 schema 变更（所有修复在应用层完成）
- 性能优化
- user 模块裸 `@Transactional` 升级（低风险，留作后续代码规范化）

### Deferred to Follow-Up Work
- 集成测试基础设施搭建（H2 schema + application-test.yml）
- Controller 层测试覆盖（当前为零）
- user 模块 `@Transactional` 统一升级

---

## Risks

- **RISK-1（中）：修复引入回归** — 多个 Bug 位于同一文件（如 `FinanceServiceImpl` 涉及 Bug #1, #3, #6），修复时可能互相影响。缓解：按 U-ID 顺序逐个修复，每个修复后立即运行该模块所有测试。
- **RISK-2（低）：API 行为变更影响前端** — Bug #20（报名状态绕过）的修复可能影响现有管理后台的前端逻辑。缓解：修复前确认前端是否有依赖该行为的代码路径。
- **RISK-3（低）：Mapper SQL 改写影响查询结果** — Bug #29 的 Mapper 改写需确保返回结果与原来一致（包括软删除记录）。缓解：改写后对比新旧查询的返回结果。

---

## Implementation Unit Dependency Graph

```
Phase 1 (CRITICAL):
  U1 ──┐
  U2   ├──→ Phase 2 (HIGH):
  U3 ──┤      U5 (←U3)
  U4 ──┘      U6 (←U4)
              U7
              U8 (←U5)
              U9
              U10

Phase 3 (MEDIUM/LOW):
  U11 (←U7)
  U12
  U13
  U14 (←U4, U6)
```

Phase 1 的 4 个单元可并行修复。Phase 2 中 U7/U9/U10 无依赖可并行，U5/U6 需等 Phase 1 对应前置完成。Phase 3 中 U12/U13 无依赖可并行。

---

## Verification Contract

每个 Phase 完成后执行：

1. **单元测试**：`mvn test -pl backend` 确保所有新增和既有测试通过
2. **编译检查**：`mvn compile -pl backend` 无编译错误
3. **Phase 1 专项验证**：启动后端服务，手动测试退费、薪资计算、移除学员、考勤更新四个核心流程
4. **Phase 2 专项验证**：手动测试并发场景（快速双击审核/报名）、CORS 响应头
5. **全量回归**：Phase 3 完成后运行 `mvn test` 确认无回归

---

## Definition of Done

- 全部 42 个 Bug 修复完毕
- 每个 Bug 有对应的单元测试覆盖（至少正向 + 反向各 1 个用例）
- `mvn test` 全部通过
- `mvn compile` 无错误
- API 接口契约未发生变更（前端无需修改）
