# 项目长期记忆 · 教务管理平台

## 项目概况
- 本科毕设：艺术培训机构全流程教务管理平台（攀枝花学院）。v0.9。
- 后端 Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + JDK17 + MySQL8；前端 Vue3+Vite5+Pinia+Element Plus；移动端 uni-app。
- 包名 `com.pzhu.eduadmin`，库名 `edu_admin`，`Result<T>={code,message,data}`，JWT(HS256)。

## 启动方式
- **构建**：PowerShell `cd backend && mvn.cmd -o clean package -DskipTests`（Maven 3.9.4 `D:\bianyiheji\apache-maven-3.9.4`，离线 ~30s，产物 `target/eduadmin.jar`）
- **启动**：`cd backend && DB_PASSWORD=123456 java -jar target/eduadmin.jar --server.port=8080`
  ⚠️ 必须带 `--server.port=8080`，否则端口冲突（WorkBuddy.exe 占 62887）
- **前端**：`cd admin-web && npm run dev`（:5173，代理 `/api → :8080`）
- **MySQL**：3306，root/123456
- **登录**：`POST /api/auth/login`，admin/123456（SUPER_ADMIN）
- 财务账号：finance/finance2/finance3（无 finance1），密码重置 key=`newPassword`

## MyBatis-Plus 重要规范
1. **乐观锁**：启用 `@Version` 后**禁止手动 setVersion**，MP 自动 CAS；手动设会必现"更新冲突"
2. **绕过 @TableLogic 查询**：返回标量(Long/Integer)或非实体 DTO → 拦截器跳过。禁止用 `selectBatchIds` 查历史数据（已删学员名会丢）。`@InterceptorIgnore` 在 MP 3.5.7 无 `logicDelete` 键，不可靠
3. **关联表不用 @TableLogic**：`role_permission`、`teacher_course` 等有唯一键的关联表，逻辑删除行占用唯一键 → "先清后建"撞键。统一用 `@Delete` 物理删除（已修复 role_permission、teacher_course）
4. **历史流水取名**：`populateXxxNames` 全量改用 `loadStudentNamesIncludeDeleted` 绕过 @TableLogic（已全部修完）

## 已修复的关键缺陷
| 日期 | 问题 | 修复 |
|------|------|------|
| 07-14 | 薪资核算 500 | courseId→SalaryRule 映射，按课次→班级→课程取单价 |
| 07-14 | 退费审核两连 409/404 | 移除手动 setVersion + selectCourseIdById 标量绕过 |
| 07-14 | 机构配置无法保存 | 前后端字段名对齐 orgName/contactPhone |
| 07-14 | 前端 6 公共组件缺 | 补齐 DataTable/FormDialog/ScheduleCalendar/UploadFile/ChartPanel/ExportButton |
| 07-15 | 角色权限保存 409 | role_permission 改用物理删除 |
| 07-15 | 非超管登录空白 | 登录即返回 permissions + 前端去掉 roleApi.list() |
| 07-15 | 历史流水学员名空 | 全量 populateNames 绕过 @TableLogic |
| 07-16 | 17 项逻辑缺陷 | 详见 `docs/audit-backend-logic-2026-07-16.md` |

## 数据关系注意
- `schedule_lesson` 仅含 `class_id`，课程需经 `class_group.course_id`
- 考勤状态码：1-到课, 2-迟到, 3-请假, 4-缺勤（全端一致）
- `/api/edu/teachers` 返回列表(无分页包装)，classrooms/classes 返回分页对象
- docs/99 合规审查 6 项已全部闭环（2026-07-14）

## 单元测试（2026-07-17 全面补全）
- 测试文件：11 → 24，测试总数：~164 → 318，全量通过 0 失败
- Service 层覆盖率：53% (9/17) → 100% (17/17)
- 测试框架：JUnit 5 + Mockito + AssertJ，全部纯 Mock 单元测试
- H2 集成测试：`application-test.yml` + `SmokeTest`（上下文加载验证）
- **测试运行**：`cd backend && mvn.cmd -o test`
- **模式**：所有测试遵循 `@ExtendWith(MockitoExtension.class)` + `TableInfoHelper.initTableInfo` + `CurrentUserHolder.set/clear`
- **关键坑**：MyBatis-Plus `BaseMapper` 的 `insert(T)` 和 `updateById(T)` 有 `Collection<T>` 重载，`any()` 不明确需用 `any(Entity.class)`

## 审计修复新增规范（2026-07-17）
- **BlockAttackInnerInterceptor**：MybatisPlusConfig 已注册防全表更新/删除拦截器
- **并发操作标准模式**：状态变更类操作（审核/确认/作废）全部使用 `LambdaUpdateWrapper` CAS：
  `int updated = mapper.update(null, new LambdaUpdateWrapper<X>().eq(X::getId, id).eq(X::getStatus, expect).set(X::getStatus, target));`
  已覆盖：RefundRecord、TeacherSalary(confirm/void)、Enrollment(audit)、ScheduleAdjustRequest(audit)
- **@Valid 规则**：所有 POST/PUT 接收 @RequestBody 实体类的 Controller 方法都需要 @Valid
- **Controller 输入校验**：关键参数（status/password）需在 Controller 层做范围/空值校验
- **跨数据库兼容**：禁止 `.last("LIMIT 1")` 等方言 SQL，改用 MyBatis-Plus Page(1,1)
- **迟到扣课时**：考勤 status=2(迟到) 与 status=1(到课) 均扣
- **请假保护**：已有请假审批(status=3,deductLessons=0) 不允许教师覆盖为到课/迟到
- **maxStudentCount**：Integer nullable，比较前 null→0 兜底
