# 艺培通 — 艺术培训机构全流程教务管理平台

> 本科毕业设计项目 | 攀枝花学院 | v1.3.0

## 项目简介

艺培通是一个面向艺术培训机构的 B/S 架构全流程教务管理平台，覆盖**招生报名→缴费→排课→考勤→课时扣减→退费→薪资核算→运营统计**的全链路业务闭环。系统包含管理后台（Web）、移动端（H5/微信小程序），支持超级管理员、教务管理员、财务管理员、教师、家长五种角色。

## 技术栈

| 层级 | 技术 |
|------|------|
| 管理后台前端 | Vue 3 + Vite 5 + Pinia + Element Plus + TypeScript |
| 移动端 | uni-app (Vue3+Vite) + uView Plus |
| 后端 | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 |
| 语言 | JDK 17 |
| 数据库 | MySQL 8.0 |
| 构建工具 | Maven 3.9 |
| 认证 | JWT (HS256) |
| 测试 | JUnit 5 + Mockito + Vitest |

## 环境要求

- JDK 17+
- MySQL 8.0+（端口 3306）
- Node.js 18+ / npm 9+
- Maven 3.9+

## 快速启动

### 1. 初始化数据库

```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p < sql/data.sql
```

或者使用一键重置脚本：
```bash
sql/reset-all.ps1
```

- 数据库名：`edu_admin`
- 默认账号：root / 123456

### 2. 启动后端

```bash
cd backend
mvn clean package -DskipTests
# Windows PowerShell:
$env:JWT_SECRET="demo-secret-key-change-in-prod"
$env:DB_PASSWORD="123456"
java -jar target/eduadmin.jar --server.port=8080
# Linux/macOS:
JWT_SECRET=demo-secret-key-change-in-prod DB_PASSWORD=123456 java -jar target/eduadmin.jar --server.port=8080
```

后端启动在 http://localhost:8080

### 3. 启动管理前端

```bash
cd admin-web
npm install
npm run dev
```

管理端启动在 http://localhost:5173

### 4. 构建移动端 H5

```bash
cd mobile-uniapp
npm install
npm run build:h5
```

产物在 `dist/build/h5-release/`。H5 发布态需配置反向代理（如 nginx）将 `/api` 转发到后端，或通过 Vite 开发代理运行（`npm run dev:h5`）。

## 演示账号

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 超级管理员 | `admin` | `123456` | 全部权限 |
| 教务管理员 | `edu` | `123456` | 教务管理 |
| 财务管理员 | `finance` | `123456` | 财务管理 |
| 教师 | `teacher1` | `123456` | 考勤/学情 |
| 家长 | `parent1` | `123456` | 报名/缴费/请假 |

> ⚠️ 本系统使用**模拟支付**（payType=2），非真实支付网关接入。该设计适用于毕业演示场景。

## 数据库

共 37 张表，覆盖 8 个业务模块：

| 模块 | 核心表 |
|------|--------|
| 用户权限 | user, role, permission, sys_menu, role_permission |
| 学员课程 | student, parent_student, course, class_group, class_student, enrollment |
| 排课调课 | classroom, room_booking, period, schedule_lesson, schedule_adjust_request, schedule_lock |
| 考勤学情 | attendance, leave_request, homework, learning_record |
| 课时流水 | lesson_account, lesson_flow |
| 财务薪资 | payment_record, refund_record, teacher_course, salary_rule, teacher_salary, salary_adjustment |
| 考级通知 | exam_level, exam_signup, notice, notification |
| 运营管理 | statistics_snapshot, student_risk_followup, operation_log, organization |

## 测试

### 后端测试

```bash
cd backend
mvn test
```

- 测试框架：JUnit 5 + Mockito + AssertJ
- 测试数量：464 个，0 失败
- Service 层覆盖率：100%（20/20 核心业务 Service 有专属测试）

### 前端测试（管理后台）

```bash
cd admin-web
npx vitest --run
```

- 测试框架：Vitest + @vue/test-utils
- 测试数量：26 文件 116 测试，0 失败

### 移动端测试（mobile-uniapp）

```bash
cd mobile-uniapp
npx vitest run
```

- 测试框架：Vitest
- 测试数量：4 文件 25 测试（报名快照决策逻辑 + 课表摘要/请假状态/未读角标纯函数），0 失败

### 验收测试

```bash
bash acceptance_test.sh
```

五角色 21 个授权端点 + 5 个越权拒绝测试全部通过。

详见 `docs/acceptance-matrix-2026-07-19.md` 和 `docs/test-report-2026-07-19.md`。

## 项目结构

```
educational-administration/
├── admin-web/          # 管理后台前端 (Vue3+Vite)
│   ├── src/views/      # 页面组件
│   ├── src/api/        # API 封装
│   ├── src/stores/     # Pinia 状态管理
│   └── src/__tests__/  # Vitest 测试
├── backend/            # Spring Boot 后端
│   └── src/main/java/com/pzhu/eduadmin/
│       ├── modules/    # 业务模块 (auth/course/enrollment/schedule/...)
│       ├── security/   # JWT 鉴权 + 拦截器
│       └── common/     # 通用工具
├── mobile-uniapp/      # uni-app 移动端
│   └── src/pages/
│       ├── parent/     # 家长端页面
│       └── teacher/    # 教师端页面
├── sql/                # 数据库脚本
│   ├── schema.sql      # DDL 建表
│   ├── data.sql        # 演示数据
│   └── reset.sql       # 一键重置
└── docs/               # 毕业设计文档
    ├── 01-09 毕业设计系列文档
    ├── acceptance-matrix-2026-07-19.md
    └── test-report-2026-07-19.md
```

## 交付版本

- **版本号**：v1.3.0
- **构建日期**：2026-09-08
- **环境**：JDK 17 | MySQL 8.0 | Node.js 22 | Maven 3.9
- **测试统计**：后端 464 通过 | 管理端 116 通过 | 移动端 25 通过
- **生产化能力**（v1.1.0 新增）：Flyway 版本化迁移（V6/V7）、加密备份/恢复/演练、发布门禁 + 镜像回退、业务可观测性（actuator/Prometheus/关联 ID/JSON 日志）、H5 报名决策快照（If-Match 一致性）、上传内容校验（Content-Type + 魔数）、CI 测试门禁 + 登录冒烟、生产配置启动校验
- **v1.2.0 新增**：排课可视化拖拽快速调课（原生 DnD + CAS 防并发覆盖）、流失预警五因子引擎 + 学员级待办清单（跟进闭环/Excel 导出）、SSE 长连加固（心跳/nginx 流式）+ 通知外发通道抽象、运营看板按角色隔离
- **v1.3.0 新增**（移动端补齐）：教师端学员请假审批（本班待审列表 → 通过/驳回+备注，教务兜底不变）、课表主 Tab 真实化（今日+未来 7 天紧凑摘要，家长/教师角色自适应）、教师课表富信息（课程/班级/教室名/时间/状态，按日分组+今日高亮）、消息未读角标（tabBar 红点 + 未读 N 条）、家长学情上下文（考勤行展示"班级 日期 时间"替代裸课次 ID）；后端 `/api/teacher/lessons` 补 classroomName 回填
- **已知限制**：模拟支付/模拟短信（非真实网关与短信服务），JWT 密钥必须通过 `JWT_SECRET` 环境变量外部注入，生产 DB 密码/JWT/CORS 由 `ProductionConfigurationValidator` 启动校验

## 许可证

本项目仅用于毕业设计学术目的。
