# 后端全量接口测试报告

- 生成时间：2026-07-14 14:16:09
- 测试目标：运行中后端 `http://localhost:8080`（Maven 正规重建后的 eduadmin.jar）
- 测试策略：解析 22 个 Controller 枚举全部端点；按 URL 前缀判定所需角色并用对应账号 token 请求；GET 带分页参数、写接口发空体探活；检查 HTTP 状态与业务码 `code`。

## 一、总体结果

| 指标 | 数值 |
|---|---|
| 枚举端点总数 | 149 |
| ✅ 正常(200+code=0) | 86 |
| ❌ 失败(404/500/公开被拦) | 2 |
| ⚠️ 警告(400/鉴权/业务码) | 61 |

> 说明：写接口(POST/PUT/DELETE)仅发空体探活，返回 400 多为「缺必填参数」属正常，不计为缺陷；真正的缺陷是 **404(端点未注册)** 与 **500(服务端异常)**，以及**正确角色被 403 拒绝**。

## 二、失败项（需关注）

| 方法 | 路径 | 现象 | 来源文件 |
|---|---|---|---|
| GET | `/api/auth/profile` | 401 | AuthController.java |
| POST | `/api/auth/logout` | 401 | AuthController.java |

## 三、警告项（需复核）

| 方法 | 路径 | 现象 | 来源文件 |
|---|---|---|---|
| GET | `/api/edu/attendances` | 403 role=TEACHER | AdminAttendanceController.java |
| POST | `/api/edu/attendances` | 403 role=TEACHER | AdminAttendanceController.java |
| PUT | `/api/edu/leave-requests/{id}/audit` | code=400 | AdminLeaveRequestController.java |
| POST | `/api/parent/leave-requests` | code=400 | ParentLeaveRequestController.java |
| GET | `/api/teacher/lessons/{lessonId}/students` | code=404 | TeacherAttendanceController.java |
| POST | `/api/teacher/lessons/{lessonId}/attendances` | code=400 | TeacherAttendanceController.java |
| POST | `/api/auth/login` | code=400 | AuthController.java |
| POST | `/api/edu/courses` | code=400 | CourseController.java |
| POST | `/api/edu/classes` | code=409 | CourseController.java |
| POST | `/api/edu/classes/{id}/students` | code=404 | CourseController.java |
| PUT | `/api/edu/enrollments/{id}/audit` | code=400 | EnrollmentController.java |
| POST | `/api/parent/enrollments` | code=409 | ParentController.java |
| GET | `/api/edu/exams/levels` | 403 role=TEACHER | ExamController.java |
| GET | `/api/edu/exams/levels/{id}` | 403 role=TEACHER | ExamController.java |
| POST | `/api/edu/exams/levels` | 403 role=TEACHER | ExamController.java |
| PUT | `/api/edu/exams/levels/{id}` | 403 role=TEACHER | ExamController.java |
| DELETE | `/api/edu/exams/levels/{id}` | 403 role=TEACHER | ExamController.java |
| GET | `/api/edu/exams/signups` | 403 role=TEACHER | ExamController.java |
| POST | `/api/edu/exams/signups` | 403 role=TEACHER | ExamController.java |
| PUT | `/api/edu/exams/signups/{id}` | 403 role=TEACHER | ExamController.java |
| POST | `/api/finance/payments` | code=409 | FinanceController.java |
| POST | `/api/finance/refunds` | code=409 | FinanceController.java |
| PUT | `/api/finance/refunds/{id}/audit` | code=409 | FinanceController.java |
| POST | `/api/finance/renewals` | code=409 | FinanceController.java |
| POST | `/api/finance/salary-rules` | code=400 | FinanceController.java |
| GET | `/api/finance/parent/students/{studentId}/lesson-account` | code=403 | FinanceController.java |
| GET | `/api/finance/parent/students/{studentId}/lesson-flows` | code=403 | FinanceController.java |
| POST | `/api/edu/homeworks` | code=409 | LearningController.java |
| POST | `/api/teacher/lessons/{lessonId}/learning-records` | code=400 | LearningController.java |
| GET | `/api/parent/learning-records` | code=400 | LearningController.java |
| GET | `/api/parent/homeworks` | code=400 | LearningController.java |
| GET | `/api/parent/students/{studentId}/homeworks` | code=400 | LearningController.java |
| POST | `/api/admin/notices` | code=409 | NoticeController.java |
| POST | `/api/notices` | code=409 | NoticeController.java |
| POST | `/api/finance/salaries/rules` | code=400 | SalaryController.java |
| POST | `/api/finance/salaries/adjustments` | code=409 | SalaryController.java |
| POST | `/api/edu/schedules/check-conflict` | code=500 | ScheduleController.java |
| POST | `/api/edu/schedules/batch` | code=400 | ScheduleController.java |
| POST | `/api/edu/schedules` | code=500 | ScheduleController.java |
| POST | `/api/edu/classrooms` | code=400 | ScheduleController.java |
| POST | `/api/edu/room-bookings` | code=409 | ScheduleController.java |
| POST | `/api/edu/schedule-adjust-requests` | code=409 | ScheduleController.java |
| PUT | `/api/edu/schedule-adjust-requests/{id}/audit` | code=400 | ScheduleController.java |
| GET | `/api/export/payments` | 200 | ExportController.java |
| GET | `/api/export/lesson-flows` | 200 | ExportController.java |
| GET | `/api/export/salaries` | 200 | ExportController.java |
| POST | `/api/edu/students/bind-parent` | code=409 | StudentController.java |
| POST | `/api/edu/students/{id}/transfer` | code=400 | StudentController.java |
| POST | `/api/edu/students/{id}/withdraw` | code=404 | StudentController.java |
| GET | `/api/admin/menus/{id}` | code=404 | MenuController.java |
| PUT | `/api/admin/menus/{id}` | code=404 | MenuController.java |
| DELETE | `/api/admin/menus/{id}` | code=404 | MenuController.java |
| GET | `/api/admin/permissions/{id}` | code=404 | PermissionController.java |
| PUT | `/api/admin/permissions/{id}` | code=404 | PermissionController.java |
| DELETE | `/api/admin/permissions/{id}` | code=404 | PermissionController.java |
| PUT | `/api/admin/roles/{id}/permissions` | code=400 | RoleController.java |
| PUT | `/api/admin/roles/{id}` | code=400 | RoleController.java |
| DELETE | `/api/admin/roles/{id}` | code=400 | RoleController.java |
| PUT | `/api/admin/roles/code/{roleCode}/permissions` | code=400 | RoleController.java |
| PUT | `/api/admin/users/{id}/status` | code=400 | UserController.java |
| PUT | `/api/admin/users/{id}/password` | code=500 | UserController.java |

## 四、全量明细

| # | 方法 | 路径 | 角色 | HTTP | 业务码 | 结果 |
|---|---|---|---|---|---|---|
| 1 | GET | `/api/edu/attendances` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 2 | GET | `/api/edu/attendances/{id}` | TEACHER | 200 | 0 | OK |
| 3 | POST | `/api/edu/attendances` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 4 | GET | `/api/edu/leave-requests` | ADMIN | 200 | 0 | OK |
| 5 | PUT | `/api/edu/leave-requests/{id}/audit` | ADMIN | 200 | 400 | WARN(业务码400) |
| 6 | GET | `/api/parent/attendances` | PARENT | 200 | 0 | OK |
| 7 | GET | `/api/parent/students/{studentId}/attendance` | PARENT | 200 | 0 | OK |
| 8 | GET | `/api/parent/students/{studentId}/schedule` | PARENT | 200 | 0 | OK |
| 9 | POST | `/api/parent/leave-requests` | PARENT | 200 | 400 | WARN(业务码400) |
| 10 | GET | `/api/parent/leave-requests` | PARENT | 200 | 0 | OK |
| 11 | GET | `/api/teacher/lessons` | TEACHER | 200 | 0 | OK |
| 12 | GET | `/api/teacher/lessons/{lessonId}/students` | TEACHER | 200 | 404 | WARN(业务码404) |
| 13 | GET | `/api/teacher/lessons/{lessonId}/attendances` | TEACHER | 200 | 0 | OK |
| 14 | POST | `/api/teacher/lessons/{lessonId}/attendances` | TEACHER | 200 | 400 | WARN(业务码400) |
| 15 | GET | `/api/teacher/adjust-requests` | TEACHER | 200 | 0 | OK |
| 16 | POST | `/api/teacher/schedules/{lessonId}/adjust-requests` | TEACHER | 200 | 0 | OK |
| 17 | POST | `/api/auth/login` | PUBLIC | 200 | 400 | WARN(业务码400) |
| 18 | GET | `/api/auth/profile` | PUBLIC | 401 | 401 | FAIL(公开接口被拦) |
| 19 | POST | `/api/auth/logout` | PUBLIC | 401 | 401 | FAIL(公开接口被拦) |
| 20 | GET | `/api/edu/courses` | ADMIN | 200 | 0 | OK |
| 21 | GET | `/api/edu/courses/{id}` | ADMIN | 200 | 0 | OK |
| 22 | POST | `/api/edu/courses` | ADMIN | 200 | 400 | WARN(业务码400) |
| 23 | PUT | `/api/edu/courses/{id}` | ADMIN | 200 | 0 | OK |
| 24 | DELETE | `/api/edu/courses/{id}` | ADMIN | 200 | 0 | OK |
| 25 | GET | `/api/edu/classes` | ADMIN | 200 | 0 | OK |
| 26 | GET | `/api/edu/classes/{id}` | ADMIN | 200 | 0 | OK |
| 27 | POST | `/api/edu/classes` | ADMIN | 200 | 409 | WARN(业务码409) |
| 28 | PUT | `/api/edu/classes/{id}` | ADMIN | 200 | 0 | OK |
| 29 | DELETE | `/api/edu/classes/{id}` | ADMIN | 200 | 0 | OK |
| 30 | GET | `/api/edu/classes/{id}/students` | ADMIN | 200 | 0 | OK |
| 31 | POST | `/api/edu/classes/{id}/students` | ADMIN | 200 | 404 | WARN(业务码404) |
| 32 | DELETE | `/api/edu/classes/{id}/students/{studentId}` | ADMIN | 200 | 0 | OK |
| 33 | GET | `/api/edu/enrollments/{id}` | ADMIN | 200 | 0 | OK |
| 34 | PUT | `/api/edu/enrollments/{id}` | ADMIN | 200 | 0 | OK |
| 35 | DELETE | `/api/edu/enrollments/{id}` | ADMIN | 200 | 0 | OK |
| 36 | PUT | `/api/edu/enrollments/{id}/audit` | ADMIN | 200 | 400 | WARN(业务码400) |
| 37 | GET | `/api/parent/courses` | PARENT | 200 | 0 | OK |
| 38 | POST | `/api/parent/enrollments` | PARENT | 200 | 409 | WARN(业务码409) |
| 39 | GET | `/api/parent/enrollments` | PARENT | 200 | 0 | OK |
| 40 | GET | `/api/parent/students` | PARENT | 200 | 0 | OK |
| 41 | GET | `/api/parent/notices` | PARENT | 200 | 0 | OK |
| 42 | GET | `/api/parent/payments` | PARENT | 200 | 0 | OK |
| 43 | GET | `/api/edu/exams/levels` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 44 | GET | `/api/edu/exams/levels/{id}` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 45 | POST | `/api/edu/exams/levels` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 46 | PUT | `/api/edu/exams/levels/{id}` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 47 | DELETE | `/api/edu/exams/levels/{id}` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 48 | GET | `/api/edu/exams/signups` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 49 | POST | `/api/edu/exams/signups` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 50 | PUT | `/api/edu/exams/signups/{id}` | TEACHER | 403 | 403 | WARN(403鉴权) |
| 51 | GET | `/api/finance/payments` | FINANCE | 200 | 0 | OK |
| 52 | POST | `/api/finance/payments` | FINANCE | 200 | 409 | WARN(业务码409) |
| 53 | GET | `/api/finance/refunds` | FINANCE | 200 | 0 | OK |
| 54 | POST | `/api/finance/refunds` | FINANCE | 200 | 409 | WARN(业务码409) |
| 55 | PUT | `/api/finance/refunds/{id}/audit` | FINANCE | 200 | 409 | WARN(业务码409) |
| 56 | GET | `/api/finance/lesson-accounts` | FINANCE | 200 | 0 | OK |
| 57 | GET | `/api/finance/lesson-accounts/{id}` | FINANCE | 200 | 0 | OK |
| 58 | GET | `/api/finance/lesson-flows` | FINANCE | 200 | 0 | OK |
| 59 | POST | `/api/finance/renewals` | FINANCE | 200 | 409 | WARN(业务码409) |
| 60 | GET | `/api/finance/salary-rules` | FINANCE | 200 | 0 | OK |
| 61 | POST | `/api/finance/salary-rules` | FINANCE | 200 | 400 | WARN(业务码400) |
| 62 | GET | `/api/finance/statistics/revenue` | FINANCE | 200 | 0 | OK |
| 63 | GET | `/api/finance/parent/students/{studentId}/lesson-account` | FINANCE | 200 | 403 | WARN(业务码403) |
| 64 | GET | `/api/finance/parent/students/{studentId}/lesson-flows` | FINANCE | 200 | 403 | WARN(业务码403) |
| 65 | GET | `/api/edu/homeworks` | ADMIN | 200 | 0 | OK |
| 66 | POST | `/api/edu/homeworks` | ADMIN | 200 | 409 | WARN(业务码409) |
| 67 | GET | `/api/edu/learning-records` | ADMIN | 200 | 0 | OK |
| 68 | GET | `/api/teacher/homeworks/{lessonId}` | TEACHER | 200 | 0 | OK |
| 69 | POST | `/api/teacher/lessons/{lessonId}/homeworks` | TEACHER | 200 | 0 | OK |
| 70 | GET | `/api/teacher/lessons/{lessonId}/learning-records` | TEACHER | 200 | 0 | OK |
| 71 | POST | `/api/teacher/lessons/{lessonId}/learning-records` | TEACHER | 200 | 400 | WARN(业务码400) |
| 72 | GET | `/api/parent/learning-records` | PARENT | 200 | 400 | WARN(业务码400) |
| 73 | GET | `/api/parent/students/{studentId}/learning-records` | PARENT | 200 | 0 | OK |
| 74 | GET | `/api/parent/homeworks` | PARENT | 200 | 400 | WARN(业务码400) |
| 75 | GET | `/api/parent/students/{studentId}/homeworks` | PARENT | 200 | 400 | WARN(业务码400) |
| 76 | GET | `/api/admin/notices` | ADMIN | 200 | 0 | OK |
| 77 | GET | `/api/admin/notices/{id}` | ADMIN | 200 | 0 | OK |
| 78 | POST | `/api/admin/notices` | ADMIN | 200 | 409 | WARN(业务码409) |
| 79 | PUT | `/api/admin/notices/{id}` | ADMIN | 200 | 0 | OK |
| 80 | DELETE | `/api/admin/notices/{id}` | ADMIN | 200 | 0 | OK |
| 81 | GET | `/api/notices` | ADMIN | 200 | 0 | OK |
| 82 | GET | `/api/notices/{id}` | ADMIN | 200 | 0 | OK |
| 83 | POST | `/api/notices` | ADMIN | 200 | 409 | WARN(业务码409) |
| 84 | PUT | `/api/notices/{id}` | ADMIN | 200 | 0 | OK |
| 85 | DELETE | `/api/notices/{id}` | ADMIN | 200 | 0 | OK |
| 86 | GET | `/api/finance/salaries/rules` | FINANCE | 200 | 0 | OK |
| 87 | POST | `/api/finance/salaries/rules` | FINANCE | 200 | 400 | WARN(业务码400) |
| 88 | PUT | `/api/finance/salaries/rules/{id}` | FINANCE | 200 | 0 | OK |
| 89 | DELETE | `/api/finance/salaries/rules/{id}` | FINANCE | 200 | 0 | OK |
| 90 | POST | `/api/finance/salaries/calculate` | FINANCE | 200 | 0 | OK |
| 91 | PUT | `/api/finance/salaries/{id}/confirm` | FINANCE | 200 | 0 | OK |
| 92 | PUT | `/api/finance/salaries/{id}/void` | FINANCE | 200 | 0 | OK |
| 93 | GET | `/api/finance/salaries/adjustments` | FINANCE | 200 | 0 | OK |
| 94 | POST | `/api/finance/salaries/adjustments` | FINANCE | 200 | 409 | WARN(业务码409) |
| 95 | GET | `/api/edu/teachers` | ADMIN | 200 | 0 | OK |
| 96 | GET | `/api/edu/schedules` | ADMIN | 200 | 0 | OK |
| 97 | GET | `/api/edu/schedules/{id}` | ADMIN | 200 | 0 | OK |
| 98 | POST | `/api/edu/schedules/check-conflict` | ADMIN | 200 | 500 | WARN(业务码500) |
| 99 | POST | `/api/edu/schedules/batch` | ADMIN | 200 | 400 | WARN(业务码400) |
| 100 | POST | `/api/edu/schedules` | ADMIN | 200 | 500 | WARN(业务码500) |
| 101 | PUT | `/api/edu/schedules/{id}` | ADMIN | 200 | 0 | OK |
| 102 | DELETE | `/api/edu/schedules/{id}` | ADMIN | 200 | 0 | OK |
| 103 | GET | `/api/edu/classrooms` | ADMIN | 200 | 0 | OK |
| 104 | GET | `/api/edu/classrooms/{id}` | ADMIN | 200 | 0 | OK |
| 105 | POST | `/api/edu/classrooms` | ADMIN | 200 | 400 | WARN(业务码400) |
| 106 | PUT | `/api/edu/classrooms/{id}` | ADMIN | 200 | 0 | OK |
| 107 | DELETE | `/api/edu/classrooms/{id}` | ADMIN | 200 | 0 | OK |
| 108 | GET | `/api/edu/room-bookings` | ADMIN | 200 | 0 | OK |
| 109 | POST | `/api/edu/room-bookings` | ADMIN | 200 | 409 | WARN(业务码409) |
| 110 | GET | `/api/edu/schedule-adjust-requests` | ADMIN | 200 | 0 | OK |
| 111 | POST | `/api/edu/schedule-adjust-requests` | ADMIN | 200 | 409 | WARN(业务码409) |
| 112 | PUT | `/api/edu/schedule-adjust-requests/{id}/audit` | ADMIN | 200 | 400 | WARN(业务码400) |
| 113 | GET | `/api/export/payments` | ADMIN | 200 | None | WARN(200) |
| 114 | GET | `/api/export/lesson-flows` | ADMIN | 200 | None | WARN(200) |
| 115 | GET | `/api/export/salaries` | ADMIN | 200 | None | WARN(200) |
| 116 | GET | `/api/admin/statistics` | ADMIN | 200 | 0 | OK |
| 117 | GET | `/api/admin/organization` | ADMIN | 200 | 0 | OK |
| 118 | PUT | `/api/admin/organization` | ADMIN | 200 | 0 | OK |
| 119 | GET | `/api/admin/logs` | ADMIN | 200 | 0 | OK |
| 120 | GET | `/api/admin/operation-logs` | ADMIN | 200 | 0 | OK |
| 121 | GET | `/api/admin/dashboard` | ADMIN | 200 | 0 | OK |
| 122 | GET | `/api/admin/statistics/enrollments` | ADMIN | 200 | 0 | OK |
| 123 | GET | `/api/admin/statistics/attendance-rate` | ADMIN | 200 | 0 | OK |
| 124 | GET | `/api/admin/statistics/lesson-consumption` | ADMIN | 200 | 0 | OK |
| 125 | GET | `/api/admin/statistics/revenue` | ADMIN | 200 | 0 | OK |
| 126 | GET | `/api/admin/statistics/teacher-workload` | ADMIN | 200 | 0 | OK |
| 127 | GET | `/api/admin/statistics/student-loss` | ADMIN | 200 | 0 | OK |
| 128 | GET | `/api/edu/students/{id}` | ADMIN | 200 | 0 | OK |
| 129 | PUT | `/api/edu/students/{id}` | ADMIN | 200 | 0 | OK |
| 130 | DELETE | `/api/edu/students/{id}` | ADMIN | 200 | 0 | OK |
| 131 | POST | `/api/edu/students/bind-parent` | ADMIN | 200 | 409 | WARN(业务码409) |
| 132 | POST | `/api/edu/students/{id}/transfer` | ADMIN | 200 | 400 | WARN(业务码400) |
| 133 | POST | `/api/edu/students/{id}/withdraw` | ADMIN | 200 | 404 | WARN(业务码404) |
| 134 | GET | `/api/admin/menus/tree` | ADMIN | 200 | 0 | OK |
| 135 | GET | `/api/admin/menus/{id}` | ADMIN | 200 | 404 | WARN(业务码404) |
| 136 | PUT | `/api/admin/menus/{id}` | ADMIN | 200 | 404 | WARN(业务码404) |
| 137 | DELETE | `/api/admin/menus/{id}` | ADMIN | 200 | 404 | WARN(业务码404) |
| 138 | GET | `/api/admin/permissions/{id}` | ADMIN | 200 | 404 | WARN(业务码404) |
| 139 | PUT | `/api/admin/permissions/{id}` | ADMIN | 200 | 404 | WARN(业务码404) |
| 140 | DELETE | `/api/admin/permissions/{id}` | ADMIN | 200 | 404 | WARN(业务码404) |
| 141 | GET | `/api/admin/roles/{id}/permissions` | ADMIN | 200 | 0 | OK |
| 142 | PUT | `/api/admin/roles/{id}/permissions` | ADMIN | 200 | 400 | WARN(业务码400) |
| 143 | PUT | `/api/admin/roles/{id}` | ADMIN | 200 | 400 | WARN(业务码400) |
| 144 | DELETE | `/api/admin/roles/{id}` | ADMIN | 200 | 400 | WARN(业务码400) |
| 145 | GET | `/api/admin/roles/code/{roleCode}/permissions` | ADMIN | 200 | 0 | OK |
| 146 | PUT | `/api/admin/roles/code/{roleCode}/permissions` | ADMIN | 200 | 400 | WARN(业务码400) |
| 147 | PUT | `/api/admin/users/{id}/status` | ADMIN | 200 | 400 | WARN(业务码400) |
| 148 | PUT | `/api/admin/users/{id}` | ADMIN | 200 | 0 | OK |
| 149 | PUT | `/api/admin/users/{id}/password` | ADMIN | 200 | 500 | WARN(业务码500) |