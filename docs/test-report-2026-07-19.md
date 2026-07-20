# 测试报告

> 版本：v1.0 | 最终验证日期：2026-07-20 | 环境：JDK 17, MySQL 8.0, Node.js 22

## 测试概览

| 层级 | 框架 | 测试文件 | 测试用例 | 失败 | 通过率 |
|------|------|---------|---------|------|--------|
| 后端单元测试 | JUnit 5 + Mockito | 24 | 352 | 0 | 100% |
| 前端组件测试 | Vitest | 21 | 70 | 0 | 100% |
| API 验收测试 | curl + bash | 1 | 26 | 0 | 100% |
| **合计** | | **46** | **445** | **0** | **100%** |

## 后端测试详情

### Service 层覆盖率

| 服务 | 测试文件 | 用例数 | 状态 |
|------|---------|--------|------|
| AuthService | AuthServiceMockTest | 10 | ✅ |
| UserService | UserServiceMockTest | 12 | ✅ |
| RoleService | RoleServiceMockTest | 8 | ✅ |
| PermissionService | PermissionServiceMockTest | 7 | ✅ |
| MenuService | MenuServiceMockTest | 7 | ✅ |
| StudentService | StudentServiceMockTest | 12 | ✅ |
| CourseService | CourseServiceMockTest | 10 | ✅ |
| EnrollmentService | EnrollmentServiceMockTest | 14 | ✅ |
| ScheduleService | ScheduleServiceMockTest | 15 | ✅ |
| AttendanceService | AttendanceServiceTest | 18 | ✅ |
| LeaveRequestService | (含于 AttendanceServiceTest) | — | ✅ |
| LearningService | (含于 AttendanceServiceTest) | — | ✅ |
| FinanceService | FinanceServiceMockTest | 28 | ✅ |
| SalaryService | SalaryServiceMockTest | 18 | ✅ |
| LessonAccountService | LessonAccountServiceTest | 6 | ✅ |
| TeacherStatisticsService | TeacherStatisticsServiceTest | 6 | ✅ |
| NotificationService | (含于 FinanceServiceMockTest) | 6 | ✅ |
| **总计** | **17/17 服务** | **352** | ✅ |

### 关键测试覆盖点

| 覆盖领域 | 测试点 |
|----------|--------|
| 退费流程 | 正常创建、重复申请拒绝、报名未缴费拒绝、审核通过/拒绝、金额上限校验、并发CAS、课时归零退班 |
| 薪资流程 | 核算、确认、作废、调整、状态转换防护 |
| 缴费流程 | 正常缴费、课时账户创建、班级容量检查、退费学员恢复 |
| 课时账户 | 乐观锁并发、余额不足拒绝、迟到扣课时 |
| JWT 认证 | 令牌解析、过期检测、版本吊销、角色校验 |
| SSE 通知 | Query param 令牌兼容、无权访问拒绝 |

## 前端测试详情

| 测试文件 | 用例数 | 状态 |
|---------|--------|------|
| Login.test.ts | 4 | ✅ |
| Dashboard.test.ts | 4 | ✅ |
| SalaryList.test.ts | 3 | ✅ |
| UserList.test.ts | 5 | ✅ |
| RoleList.test.ts | 1 | ✅ |
| MenuManagement.test.ts | 4 | ✅ |
| OperationLogs.test.ts | 2 | ✅ |
| Organization.test.ts | 3 | ✅ |
| CourseList.test.ts | 4 | ✅ |
| ClassList.test.ts | 4 | ✅ |
| StudentList.test.ts | 4 | ✅ |
| EnrollmentList.test.ts | 2 | ✅ |
| ScheduleList.test.ts | 4 | ✅ |
| AttendanceList.test.ts | 4 | ✅ |
| ExamList.test.ts | 3 | ✅ |
| ClassroomList.test.ts | 3 | ✅ |
| PaymentList.test.ts | 4 | ✅ |
| RefundList.test.ts | 4 | ✅ |
| LessonAccountList.test.ts | 2 | ✅ |
| LessonFlowList.test.ts | 2 | ✅ |
| NoticeList.test.ts | 4 | ✅ |
| **总计** | **70** | ✅ |

## API 验收测试详情

| 角色 | 授权端点 | 越权拒绝 |
|------|---------|---------|
| 超级管理员 (admin) | 6/6 | — |
| 教务管理员 (edu) | 6/6 | 1/1 |
| 财务管理员 (finance) | 3/3 | — |
| 教师 (teacher1) | 2/2 | 2/2 |
| 家长 (parent1) | 4/4 | 1/1 |
| **总计** | **21/21** | **5/5** |

详见 `acceptance-matrix-2026-07-19.md`

## 已知限制与风险

| 项目 | 状态 | 说明 |
|------|------|------|
| 模拟支付 | 已知限制 | payType=2，非真实支付网关 |
| JWT 密钥 | 环境变量 | JWT_SECRET 强制外部注入，无默认回退值 |
| 移动端 H5 构建 | 正常 | mp-weixin 子目录被外部工具锁定，H5 产物使用独立输出路径 |
| 并发压力测试 | 未执行 | 仅通过 Mock 验证了 CAS 乐观锁机制 |
| 第三方支付集成 | 延期 | 不在 v1.0 范围内 |

## 构建验证

| 构建项 | 命令 | 状态 |
|--------|------|------|
| 后端编译 | `mvn clean package -DskipTests` | ✅ |
| 后端测试 | `mvn test` | ✅ 352/352 |
| 管理前端编译 | `npm run build` | ✅ (vue-tsc + vite) |
| 管理前端测试 | `npx vitest --run` | ✅ 70/70 |
| 移动端 H5 | `npm run build:h5` | ✅ |

---

*报告生成时间：2026-07-20 11:15 GMT+8 | 最终验证：352 后端测试 + 70 前端测试 + 26 API 验收全部通过 | H5 产物：`mobile-uniapp/dist/build/h5-release/`*
