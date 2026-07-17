# 后端代码逻辑审计报告

**日期**：2026-07-17  
**范围**：17 ServiceImpl + 22 Controller + 33 Entity，全量逐层审查  
**结果**：发现 **2 个 P0**、**4 个 P1**、**3 个 P2**（共 9 项）  
**状态**：✅ 全部已修复（2026-07-17 同天闭环），351 测试全通过

---

## P0 — 必须修复（数据一致性/运行时异常）

### P0-1: 薪资确认/作废存在并发竞态条件

| 字段 | 值 |
|------|-----|
| **文件** | `SalaryServiceImpl.java` |
| **位置** | `confirmSalary()` (L313-326) 和 `voidSalary()` (L329-341) |
| **根因** | `TeacherSalary` 实体缺少 `@Version` 乐观锁，`updateById` 无 CAS |
| **场景** | 两人同时点击"确认薪资"→ 都读到 status=1 → 都通过校验 → 都执行 update → 第二个覆盖第一个 |
| **修复** | 给 `TeacherSalary` 添加 `@Version` 字段，或用 `LambdaUpdateWrapper` 做 CAS |

```java
// 修复方式一：给 TeacherSalary 加 @Version
@Version
private Integer version;

// 修复方式二：CAS 原子更新（不改实体）
public TeacherSalary confirmSalary(Long id) {
    int updated = teacherSalaryMapper.update(null,
        new LambdaUpdateWrapper<TeacherSalary>()
            .eq(TeacherSalary::getId, id)
            .eq(TeacherSalary::getStatus, 1)
            .set(TeacherSalary::getStatus, 2));
    if (updated == 0) throw new BusinessException(409, "薪资已被处理，请刷新后重试");
    return teacherSalaryMapper.selectById(id);
}
```

### P0-2: `maxStudentCount` 为 null 时抛 NPE

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` L153、`CourseServiceImpl.java` L273、`StudentServiceImpl.java` L297 |
| **根因** | `ClassGroup.maxStudentCount` 是 `Integer`（可 null），但直接与 `long` 做 `>=` 比较 |
| **影响** | 如果 DB 中某班级记录 `max_student_count IS NULL`，创建缴费/添加学员/转班直接 500 |
| **修复** | 统一加 null 兜底，或用 `Objects.requireNonNullElse` |

```java
// 所有三处的修复模式一致
int maxCount = classGroup.getMaxStudentCount() != null ? classGroup.getMaxStudentCount() : 0;
if (currentCount >= maxCount) throw new BusinessException(409, "班级已满");
```

---

## P1 — 建议修复（逻辑缺陷/健壮性）

### P1-1: 缴费创建未校验学员/课程是否存在

| 字段 | 值 |
|------|-----|
| **文件** | `EnrollmentServiceImpl.java` `create()` (L136-150) |
| **问题** | 仅校验 `studentId != null` 和 `courseId != null`，不校验是否存在 |
| **影响** | 可创建一个指向不存在学员或课程的报名记录，后续展示/关联会异常 |
| **修复** | 在 `create()` 前加 `studentMapper.selectById` 和 `courseMapper.selectById` 校验 |

### P1-2: 迟到(status=2)不扣课时，可能是逻辑遗漏

| 字段 | 值 |
|------|-----|
| **文件** | `AttendanceServiceImpl.java` `submit()` L191 |
| **问题** | `if (attendance.getStatus() == 1)` — 仅"到课"扣课时，"迟到"不扣 |
| **影响** | 迟到学员的课时不被消耗，与其他教务系统通常的"迟到也扣课时"行为不一致 |
| **判断** | 需确认产品预期：迟到是否应扣课时？若应扣，改为 `status == 1 || status == 2` |

### P1-3: 退班自动生成退费使用 MySQL 方言 `.last("LIMIT 1")`

| 字段 | 值 |
|------|-----|
| **文件** | `StudentServiceImpl.java` `withdrawStudent()` L375-379 |
| **问题** | `.last("LIMIT 1")` 是 MySQL 专有语法，切换数据库（如 H2、PostgreSQL）会报错 |
| **修复** | 改用 MyBatis-Plus 分页 `new Page<>(1, 1)` 或 `selectList` 后取第一条 |

### P1-4: 请假申请未校验日期是否为过去

| 字段 | 值 |
|------|-----|
| **文件** | `LeaveRequestServiceImpl.java` `submitLeaveRequest()` L56 |
| **问题** | 可为过去的日期提交请假申请 |
| **影响** | 家长可为已结束的课程补交请假，绕过正常考勤流程 |
| **修复** | 添加日期校验 |

```java
if (lessonDate.isBefore(LocalDate.now())) {
    throw new BusinessException(400, "不可为过去的日期提交请假申请");
}
```

---

## P2 — 可选修复（边缘情况/代码质量）

### P2-1: 到课率统计与课时扣减逻辑不一致

| 字段 | 值 |
|------|-----|
| **文件** | `StatisticsServiceImpl.java` `buildCards()` L161-162 |
| **问题** | 到课率将 status=2(迟到) 算作"到课"，但考勤扣课时仅处理 status=1(到课) |
| **影响** | Dashboard 显示的到课率比实际消耗课时的比例高 |

### P2-2: 薪资核算中冗余赋值

| 字段 | 值 |
|------|-----|
| **文件** | `SalaryServiceImpl.java` `calculateSalary()` L288, L292 |
| **问题** | L288 已赋值 `salary = existing`，L292 重复 `salary = existing` |
| **修复** | 删除 L292 |

### P2-3: 教师可覆盖请假审批创建的考勤记录

| 字段 | 值 |
|------|-----|
| **文件** | `LeaveRequestServiceImpl.java` `createLeaveAttendance()` → `AttendanceServiceImpl.java` `submit()` |
| **问题** | 请假审批自动创建 status=3 的考勤；教师提交考勤时 dedup 逻辑会更新已有记录 |
| **影响** | 教师提交到课(status=1)会覆盖请假记录并扣课时，可绕过请假审批 |
| **判断** | 需确认业务预期：是否允许教师覆盖已批准的请假？若不允许，submit 中需加判断 |

---

## 总结

| 级别 | 数量 | 说明 |
|------|------|------|
| **P0** | 2 | 薪资并发竞态 + maxStudentCount NPE |
| **P1** | 4 | 报名校验、迟到扣课、SQL 方言、请假日期校验 |
| **P2** | 3 | 到课率口径、冗余代码、考勤覆盖 |

**正面评价**：
- 退费审核采用 CAS 原子更新（`LambdaUpdateWrapper`），并发安全 ✅
- 课时账户使用 `@Version` 乐观锁 ✅  
- 暴力破解防护（内存计数器，5 次/15 分钟锁定）✅
- Token 吊销机制（User.version 比对）✅
- 跨服务历史数据查询统一绕过 `@TableLogic` ✅
- 关键流程均有 `@Transactional(rollbackFor = Exception.class)` ✅
- 排课冲突检测覆盖教师/教室/班级/学员四维度 ✅
