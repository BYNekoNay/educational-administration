# 五角色权限验收矩阵

> 日期: 2026-07-19 | 版本: v1.0 | 环境: localhost:8080

---

## 测试环境

| 项目 | 值 |
|------|-----|
| 后端 | Spring Boot 3.2.5, JDK17, MySQL8 |
| 管理前端 | Vue3 + Vite5, port 5173 |
| 移动端H5 | uni-app H5 build, port 5176 |
| 数据库 | edu_admin, 从 sql/data.sql 初始化 |
| 测试工具 | curl + bash |

---

## 验收结果总览

| 角色 | 账号 | 授权端点 | 越权拒绝 | 状态 |
|------|------|---------|---------|------|
| 超级管理员 | admin/123456 | 12/12 ✅ | — | PASS |
| 教务管理员 | edu/123456 | 6/6 ✅ | 1/1 ✅ | PASS |
| 财务管理员 | finance/123456 | 3/3 ✅ | — | PASS |
| 教师 | teacher1/123456 | 4/4 ✅ | 2/2 ✅ | PASS |
| 家长 | parent1/123456 | 7/7 ✅ | 2/2 ✅ | PASS |

**总计**: 32 个授权端点 ✅ | 6 个未授权/越权拒绝 ✅ | 0 失败

---

## 逐角色详细

### 超级管理员 (SUPER_ADMIN)

| 端点 | 方法 | HTTP | 结果 |
|------|------|------|------|
| `/api/edu/students` | GET | 200 | ✅ |
| `/api/admin/users` | GET | 200 | ✅ |
| `/api/admin/roles` | GET | 200 | ✅ |
| `/api/admin/menus/tree` | GET | 200 | ✅ |
| `/api/admin/permissions` | GET | 200 | ✅ |
| `/api/admin/dashboard` | GET | 200 | ✅ |
| `/api/admin/statistics/teacher-workload` | GET | 200 | ✅ |
| `/api/admin/statistics/student-loss` | GET | 200 | ✅ |
| `/api/admin/statistics/class-activity` | GET | 200 | ✅ |
| `/api/admin/statistics/course-profit` | GET | 200 | ✅ |
| `/api/admin/statistics/payment-rate` | GET | 200 | ✅ |
| `/api/export/payments` | GET | 200 | ✅ |

### 教务管理员 (EDU_ADMIN)

| 端点 | 方法 | HTTP | 结果 |
|------|------|------|------|
| `/api/edu/students` | GET | 200 | ✅ |
| `/api/edu/classes` | GET | 200 | ✅ |
| `/api/edu/courses` | GET | 200 | ✅ |
| `/api/edu/enrollments` | GET | 200 | ✅ |
| `/api/edu/schedules` | GET | 200 | ✅ |
| `/api/edu/classrooms` | GET | 200 | ✅ |

### 财务管理员 (FINANCE)

| 端点 | 方法 | HTTP | 结果 |
|------|------|------|------|
| `/api/finance/payments` | GET | 200 | ✅ |
| `/api/finance/refunds` | GET | 200 | ✅ |
| `/api/finance/salaries` | GET | 200 | ✅ |

### 教师 (TEACHER)

| 端点 | 方法 | HTTP | 结果 |
|------|------|------|------|
| `/api/teacher/statistics` | GET | 200 | ✅ |
| `/api/teacher/schedules` | GET | 200 | ✅ |
| `/api/teacher/adjust-requests` | GET | 200 | ✅ |
| `/api/notifications` | GET | 200 | ✅ |

### 家长 (PARENT)

| 端点 | 方法 | HTTP | 结果 |
|------|------|------|------|
| `/api/parent/students` | GET | 200 | ✅ |
| `/api/parent/courses` | GET | 200 | ✅ |
| `/api/parent/payments` | GET | 200 | ✅ |
| `/api/parent/refunds/available` | GET | 200 | ✅ (v0.9 新增) |
| `/api/parent/notices` | GET | 200 | ✅ |
| `/api/notifications` | GET | 200 | ✅ |
| `/api/parent/students/{id}/homeworks` | GET | 200 | ✅ |

---

## 越权拒绝矩阵

| 攻击者 | 目标端点 | HTTP | 结果 |
|--------|---------|------|------|
| 匿名用户 | `/api/files/{fileName}` (附件读取) | 401 | ✅ 正确拒绝 |
| 教师 | `/api/admin/users` (用户管理) | 403 | ✅ 正确拒绝 |
| 家长 | `/api/admin/users` (用户管理) | 403 | ✅ 正确拒绝 |
| 家长 | `/api/finance/salaries` (薪资) | 403 | ✅ 正确拒绝 |
| 教师 | `/api/finance/salaries` (薪资) | 403 | ✅ 正确拒绝 |
| 教务 | `/api/admin/roles` (角色管理) | 403 | ✅ 正确拒绝 |

---

## 前端页面可访问性

| 页面 | URL | 状态 |
|------|-----|------|
| 管理端登录 | http://localhost:5173 | ✅ |
| 移动端H5 | http://localhost:5176 | ✅ |
| 管理端编译 | `npm run build` | ✅ (vue-tsc + vite 通过) |
| 移动端编译 | `npm run build:h5` | ✅ (产物 dist/build/h5-release) |

---

## 已知限制

1. 移动端 H5 构建产物位于 `dist/build/h5-release`，因原 `mp-weixin` 目录被外部进程锁定
2. 支付为模拟支付（payType=2），非真实支付网关
3. JWT 密钥通过环境变量 `JWT_SECRET` 强制外部注入，无默认回退值
4. 家长退费功能已纳入本轮 H5 构建和 API 验收；页面截图证据见 `docs/evidence/2026-07-20/`

---

## 附件清单

- 后端测试: 362 通过, 0 失败 (`mvn test`)
- 前端测试: 22 文件 74 测试通过 (`vitest --run`)
- 验收脚本: `acceptance_test.sh`
