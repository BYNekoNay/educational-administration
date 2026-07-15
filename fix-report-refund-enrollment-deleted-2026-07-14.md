# 修复报告：退费审核「报名记录不存在」（2026-07-14 15:23）

## 1. 现象

管理后台「退费管理」点击「审核通过」后，前端控制台报错：

```
[Vue warn]: Unhandled error during execution of component event handler
request.ts:35 Uncaught (in promise) Error: 报名记录不存在
    at async doAudit (RefundList.vue:161:3)
    at async confirmAudit (RefundList.vue:157:9)
```

这是上一轮「乐观锁冲突」修复之后出现的新报错，发生于审核 **唯一一条待审核退费记录（refund_record id=8）**。

## 2. 根因分析

### 2.1 为什么报「报名记录不存在」

`FinanceServiceImpl.processRefundApproval` 在上一轮修复中改为：

```java
Enrollment enrollment = enrollmentMapper.selectById(record.getEnrollmentId());
if (enrollment == null) throw new BusinessException(404, "报名记录不存在");
```

这条待审核退费 `refund_record id=8` 关联 `enrollment_id = 1`，而该报名在测试数据中 **`is_deleted = 1`（已被逻辑删除）**。

项目对 `Enrollment` 启用了 MyBatis-Plus `@TableLogic`，`LogicDeleteInnerInterceptor` 会**自动给所有实体查询追加 `is_deleted = 0`**。于是 `selectById(1)` 在数据库里被变成 `WHERE id = 1 AND is_deleted = 0`，自然查不到那条已删记录 → 返回 `null` → 抛 404。

### 2.2 为什么必须拿到 courseId（不能只按 studentId 退课时）

退费需从学员的**指定课程**课时账户扣减课时。`refund_record.id=8` 关联 course 1，但学员 `student_id=1` 名下有 **两个** 课时账户（course 1、course 5）。若只按 `studentId` 查课时账户，会选错账户、误扣其它课程的课时。因此**必须解析出正确的 courseId=1**，这也是上一轮改成「studentId + courseId 精确匹配」的原因。

## 3. 修复方案

### 3.1 后端：绕过逻辑删除读取 courseId（核心改动）

在 `EnrollmentMapper` 新增一个**返回标量**的查询方法：

```java
@Select("SELECT course_id FROM enrollment WHERE id = #{id}")
Long selectCourseIdById(@Param("id") Long id);
```

**关键技术点**：MyBatis-Plus 的 `LogicDeleteInnerInterceptor` 依据**返回实体类型**解析 `TableInfo` 并追加 `is_deleted = 0`。当返回类型是**标量（`Long`/`Integer` 等）或非实体 DTO** 时，`TableInfo` 为 `null`，拦截器直接跳过，**不会**追加逻辑删除条件 → 即使报名已逻辑删除，仍能读到其 `course_id`。

> 注意：`@InterceptorIgnore` 在 MyBatis-Plus 3.5.7 **没有** `logicDelete` 键，无法用它绕过；返回 `Map` 仍会被拦截。返回标量是可靠方案。

`FinanceServiceImpl.processRefundApproval` 改为：

```java
// 0. 取报名对应的课程ID（绕过逻辑删除：报名被删时 course_id 仍有效，退费需据此定位课时账户）
Long courseId = enrollmentMapper.selectCourseIdById(record.getEnrollmentId());
if (courseId == null) throw new BusinessException(404, "报名记录不存在");
```

后续金额自动计算与课时账户查询中的 `enrollment.getCourseId()` 全部替换为 `courseId`。

> 说明：`createPayment` 中「按报名状态校验是否允许缴费」的 `selectById` **保留**——缴费不应针对已逻辑删除的报名，此处逻辑删除过滤是合理的。

### 3.2 前端：消除「Unhandled error」

`admin-web/src/views/finance/RefundList.vue` 的 `doAudit` 原先**没有任何 try/catch**，任何审核失败都会变成未捕获异常（`Unhandled error during execution of component event handler`）。补充：

- `confirmAudit`（审核通过弹窗）：`try/catch`，捕获后用 `showError(e, '审核失败')` 友好提示；
- 拒绝路径：`.catch` 只吞掉消息框取消（`cancel`/`close`），其余错误同样用 `showError` 提示。

## 4. 构建与验证

| 步骤 | 命令 / 操作 | 结果 |
|---|---|---|
| 停旧进程 | `taskkill /F /PID 15400` | 已终止旧 jar |
| 后端构建 | `mvn -o clean package -DskipTests` | **BUILD SUCCESS**（29.7s） |
| 启动后端 | `DB_PASSWORD=123456 java -jar target/eduadmin.jar --server.port=8080` | `Started EduAdminApplication` |
| 健康检查 | `GET /api/auth/login` | 200 |
| 前端构建 | `npm run build` | ✅ built（仅预存 chunk>500kB 警告） |
| 后端单测 | `mvn -o test` | 30 用例 / 0 失败 / 0 错误 |

**真实接口复测（admin 审核 refund_record id=8）**：

```
PUT /api/finance/refunds/8/audit  {status:2, refundAmount:200}
→ code=0, success

课时账户 student1/course1:
  BEFORE  remaining=22.0  version=0
  AFTER   remaining=20.0  version=1      （正确回退 2 课时，乐观锁 version 自动 +1）

refund_record id=8:
  status 1 → 2（已通过），amount=200.0，auditorId=1
```

关联报名 `enrollment 1` 虽已逻辑删除，但 `course_id` 仍被正确解析，课时从**正确的 course 1 账户**扣减，未误伤 course 5 账户。

## 5. 改动文件清单

- `backend/src/main/java/com/pzhu/eduadmin/modules/enrollment/mapper/EnrollmentMapper.java`（新增 `selectCourseIdById`）
- `backend/src/main/java/com/pzhu/eduadmin/modules/finance/service/FinanceServiceImpl.java`（processRefundApproval 改用 courseId）
- `admin-web/src/views/finance/RefundList.vue`（doAudit 增加错误捕获）

## 6. 结论

退费审核的两处报错（乐观锁冲突 → 报名已逻辑删除）均已闭环修复。后端审核逻辑对「关联报名已被逻辑删除」这一边缘场景已健壮处理；前端也不再裸抛未捕获异常，改为友好提示。后端 `:8080` 与前端 `:5173` 运行正常，可直接在管理后台「退费管理」点击「通过」验证。
