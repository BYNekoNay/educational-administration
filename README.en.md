# Yipeitong — Full-Lifecycle Academic Administration Platform for Art Training Institutions

> Undergraduate graduation project | Panzhihua University | v1.3.0

## Project Overview

Yipeitong is a B/S full-lifecycle academic administration platform for art training institutions, covering the complete business loop of **enrollment → payment → scheduling → attendance → lesson-hour deduction → refund → salary settlement → operational statistics**. The system consists of an admin console (Web) and a mobile app (H5 / WeChat Mini Program), and supports five roles: super administrator, academic administrator, finance administrator, teacher, and parent.

## Tech Stack

| Layer | Technology |
|------|------|
| Admin console frontend | Vue 3 + Vite 5 + Pinia + Element Plus + TypeScript |
| Mobile | uni-app (Vue3+Vite) + uView Plus |
| Backend | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 |
| Language | JDK 17 |
| Database | MySQL 8.0 |
| Build tool | Maven 3.9 |
| Auth | JWT (HS256) |
| Testing | JUnit 5 + Mockito + Vitest |

## Requirements

- JDK 17+
- MySQL 8.0+ (port 3306)
- Node.js 18+ / npm 9+
- Maven 3.9+

## Quick Start

### 1. Initialize the database

```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p < sql/data.sql
```

Or use the one-click reset script:
```bash
sql/reset-all.ps1
```

- Database name: `edu_admin`
- Default account: root / 123456

### 2. Start the backend

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

The backend starts at http://localhost:8080

### 3. Start the admin frontend

```bash
cd admin-web
npm install
npm run dev
```

The admin console starts at http://localhost:5173

### 4. Build the mobile H5

```bash
cd mobile-uniapp
npm install
npm run build:h5
```

The output is in `dist/build/h5-release/`. For a released H5 build, configure a reverse proxy (e.g. nginx) to forward `/api` to the backend, or run it through the Vite dev proxy (`npm run dev:h5`).

## Demo Accounts

| Role | Username | Password | Description |
|------|--------|------|------|
| Super administrator | `admin` | `123456` | Full permissions |
| Academic administrator | `edu` | `123456` | Academic administration |
| Finance administrator | `finance` | `123456` | Finance management |
| Teacher | `teacher1` | `123456` | Attendance / learning records |
| Parent | `parent1` | `123456` | Enrollment / payment / leave requests |

> ⚠️ This system uses **simulated payment** (payType=2), not a real payment gateway. This design is intended for the graduation demo scenario.

## Database

37 tables in total, covering 8 business modules:

| Module | Core tables |
|------|--------|
| User & permissions | user, role, permission, sys_menu, role_permission |
| Students & courses | student, parent_student, course, class_group, class_student, enrollment |
| Scheduling & adjustment | classroom, room_booking, period, schedule_lesson, schedule_adjust_request, schedule_lock |
| Attendance & learning | attendance, leave_request, homework, learning_record |
| Lesson hours | lesson_account, lesson_flow |
| Finance & salary | payment_record, refund_record, teacher_course, salary_rule, teacher_salary, salary_adjustment |
| Exams & notices | exam_level, exam_signup, notice, notification |
| Operations | statistics_snapshot, student_risk_followup, operation_log, organization |

## Testing

### Backend tests

```bash
cd backend
mvn test
```

- Framework: JUnit 5 + Mockito + AssertJ
- Test count: 464, 0 failures
- Service layer coverage: 100% (20/20 core business services have dedicated tests)

### Frontend tests (admin console)

```bash
cd admin-web
npx vitest --run
```

- Framework: Vitest + @vue/test-utils
- Test count: 26 files, 118 tests, 0 failures

### Mobile tests (mobile-uniapp)

```bash
cd mobile-uniapp
npx vitest run
```

- Framework: Vitest
- Test count: 4 files, 25 tests (enrollment decision snapshot logic + pure functions for schedule summary / leave status / unread badge), 0 failures

### Acceptance tests

```bash
bash acceptance_test.sh
```

All five-role 32 authorized endpoints + 6 unauthorized-access rejections pass.

See `docs/acceptance-matrix-2026-07-19.md` and `docs/ui-test-report-final-2026-09-10.md` (69 browser-automation acceptance cases). Historical layered test reports are in `docs/archive/test-reports/`.

## Project Structure

```
educational-administration/
├── admin-web/          # Admin console frontend (Vue3+Vite)
│   ├── src/views/      # Page components
│   ├── src/api/        # API wrappers
│   ├── src/stores/     # Pinia state management
│   └── src/__tests__/  # Vitest tests
├── backend/            # Spring Boot backend
│   └── src/main/java/com/pzhu/eduadmin/
│       ├── modules/    # Business modules (auth/course/enrollment/schedule/...)
│       ├── security/   # JWT auth + interceptors
│       └── common/     # Common utilities
├── mobile-uniapp/      # uni-app mobile app
│   └── src/pages/
│       ├── parent/     # Parent pages
│       └── teacher/    # Teacher pages
├── sql/                # Database scripts
│   ├── schema.sql      # DDL table creation
│   ├── data.sql        # Demo data
│   └── reset.sql       # One-click reset
└── docs/               # Graduation project documents
    ├── 00-17 graduation design series documents
    ├── thesis/         # Graduation thesis (md + docx)
    ├── paper/          # Course design report + Mermaid figures
    ├── defence/        # Defence materials (script / demo flow / Q&A prep)
    ├── screenshots/    # UI screenshots
    ├── archive/        # Historical process records (fixes / audits / test reports / superseded drafts)
    ├── acceptance-matrix-2026-07-19.md
    └── ui-test-report-final-2026-09-10.md
```

## Delivery Version

- **Version**: v1.3.0
- **Build date**: 2026-09-08
- **Environment**: JDK 17 | MySQL 8.0 | Node.js 22 | Maven 3.9
- **Test statistics**: backend 464 passed | admin console 118 passed | mobile 25 passed
- **Production capabilities** (added in v1.1.0): Flyway versioned migrations (V6/V7/V8), encrypted backup/restore/drill, release gate + image rollback, business observability (actuator/Prometheus/correlation ID/JSON logs), H5 enrollment decision snapshot (If-Match consistency), upload content validation (Content-Type + magic number), CI test gate + login smoke test, production configuration startup validation
- **Added in v1.2.0**: visual drag-and-drop quick schedule adjustment (native DnD + CAS to prevent concurrent overwrite), five-factor student churn-warning engine + student-level to-do list (follow-up closed loop / Excel export), SSE long-connection hardening (heartbeat / nginx streaming) + outbound notification channel abstraction, dashboard isolation by role
- **Added in v1.3.0** (mobile completion): teacher-side student leave approval (class-pending list → approve/reject + remark, academic-admin fallback unchanged), main schedule tab made real (compact summary of today + the next 7 days, adaptive to parent/teacher roles), rich teacher schedule info (course / class / classroom name / time / status, grouped by day with today highlighted), message unread badge (tabBar red dot + "N unread"), parent learning context (attendance rows show "class date time" instead of a bare lesson ID); backend `/api/teacher/lessons` now backfills classroomName
- **Known limitations**: simulated payment / simulated SMS (not real gateways or SMS services), the JWT secret must be injected externally via the `JWT_SECRET` environment variable, and production DB password/JWT/CORS are validated at startup by `ProductionConfigurationValidator`

## License

This project is for academic graduation project purposes only.
