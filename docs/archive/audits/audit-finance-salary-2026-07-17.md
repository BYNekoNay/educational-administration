# L2-5/L2-7: Finance + Salary 模块深度检查报告

**日期**: 2026-07-17  
**计划**：按四维度（链路完整性/边界异常/数据一致性/性能风险）检查  
**状态**：发现 3 P0 + 3 P1 + 3 P2（共 9 项）

---

## Finance 模块 (L2-5)

### P0-1: 退费后 totalLessons 未扣减，账户数据不一致

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `processRefundApproval()` L342-343 |
| **根因** | 退费时只减 `remainingLessons`，不减 `totalLessons` |
| **影响** | `totalLessons` 持续递增（每次缴费累加），退费不回调，与 `remainingLessons` 逐渐偏离 |
| **修复** | 退费时将 `totalLessons` 同步扣减 |

```java
// 当前：
account.setRemainingLessons(before.subtract(refundLessonCount));
// 修复：
account.setRemainingLessons(before.subtract(refundLessonCount));
account.setTotalLessons(account.getTotalLessons().subtract(refundLessonCount));
```

### P0-2: 退费后 enrollment 状态未更新，可重复退费

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `processRefundApproval()` |
| **根因** | 全额退费后 enrollment 仍为 status=3（已缴费），createRefund 只检查 status=3 |
| **影响** | 退费后再次提交退费申请 → 通过 → 课时账户再次被扣 → 负数余额 |
| **修复** | 全额退费后将 enrollment.status 置为特定状态（如 6-已退费），createRefund 排除此状态 |

### P0-3: 薪资核算未统计迟到考勤

| 字段 | 值 |
|------|-----|
| **文件** | `SalaryServiceImpl.java` `calculateSalary()` L216 |
| **根因** | `lessonAttendanceMap` 仅筛选 `.eq(Attendance::getStatus, 1)`（到课），遗漏 status=2（迟到） |
| **影响** | 迟到学员不计入教师授课量 → 薪资偏低；且与 Attendance 扣课时（已修复含迟到）口径不一致 |
| **修复** | 改为 `.in(Attendance::getStatus, 1, 2)` |

---

### P1-1: createPayment 中 enrollment.status 更新无 CAS

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `createPayment()` L143-144 |
| **代码** | `enrollment.setStatus(3); enrollmentMapper.updateById(enrollment);` |
| **影响** | 与 audit 方法并发时可能丢失更新；缴费和审核同时操作同一报名 |
| **修复** | 使用 LambdaUpdateWrapper CAS 更新 |

### P1-2: 退费审核中 amount 设置与 maxRefundable 检查顺序错误

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `auditRefund()` L263-269 |
| **问题** | CAS 更新 status→2 后，在 `processRefundApproval` 内计算退费金额，但 `totalRefunded` 求和时已包含当前记录（status 已改为 2）。若当前记录 amount=0（默认），则不影响计算；若前端传了 `refundAmount`，则可能重复计算 |
| **风险** | 低——当前 `sumApprovedByEnrollmentId` 按 amount 求和，而 amount 在 CAS 之后才设置的，所以本次退费的 amount 在第一次求和时通常为 0 |

### P1-3: LessonFlow 中 beforeBalance 在退费场景下取值有误

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `processRefundApproval()` L337 |
| **问题** | `BigDecimal before = account.getRemainingLessons()` — 取的是扣减**前**的值，good。但 `flow.setAfterBalance` 用的是 `account.getRemainingLessons()` 即扣减**后**的值，这也 correct。不过 account 的 `totalLessons` 此时还没修改（见 P0-1），导致流水中的 `afterBalance` 与 `totalLessons` 的对应关系断裂 |

---

### P2-1: ClassStudent 恢复(status=3→1) 无并发保护

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `createPayment()` L163-164 |
| **修复** | `classStudentMapper.updateById(refundedRecord)` → 改用 CAS 或添加 version |

### P2-2: 缴费时未校验 enrollment 已被删除

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `createPayment()` L98 |
| **问题** | `selectById` 在 @TableLogic 下会过滤已删记录，但无明确校验与提示 |
| **修复** | 无操作必要，MyBatis-Plus 已自动过滤 |

### P2-3: 财务流水中 `remark` 在退费场景未使用传入值

| 字段 | 值 |
|------|-----|
| **文件** | `FinanceServiceImpl.java` `processRefundApproval()` L356 |
| **问题** | 退费流水的 remark 硬编码为 "退费审核通过，回退课时"，未使用 record.getRemark() |
| **修复** | 使用 record.getRemark() 或拼接 |

---

## Salary 模块 (L2-7)

### P2-4: 薪资核算中 `classCourseMap` 查询使用 `selectList` 而非 `selectBatchIds`

| 字段 | 值 |
|------|-----|
| **文件** | `SalaryServiceImpl.java` `calculateSalary()` L239-240 |
| **问题** | 已在修复中集成，ClassGroup 查询使用 IN 子句分页，正确 |

### P2-5: `defaultRule` 取 `rules.get(0)` 无排序约定

| 字段 | 值 |
|------|-----|
| **文件** | `SalaryServiceImpl.java` `calculateSalary()` L229 |
| **代码** | `SalaryRule defaultRule = rules.get(0);` |
| **影响** | 若无 courseId 匹配的规则，兜底规则取列表第一条，但列表顺序不稳定（无 ORDER BY） |
| **修复** | 明确按 createTime DESC 排序或取全局默认规则（course_id IS NULL） |

---

## 总结

| 级别 | 数量 | 说明 |
|------|------|------|
| **P0** | 3 | totalLessons 未扣减、退费后可重复退费、薪资未统迟到 |
| **P1** | 3 | enrollment 更新无 CAS、refundAmount 检查时序、流水余额断裂 |
| **P2** | 3 | ClassStudent 恢复无保护、薪资规则排序、remark 硬编码 |

**已确认安全的部分**：
- LessonAccount @Version 乐观锁完整覆盖 ✅
- auditRefund CAS 原子更新 ✅
- 退费金额上限校验 ✅
- 同人隔离审核检测 ✅
- 缴费→入班→课时账户完整链路 ✅
