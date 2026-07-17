# L2-6: Attendance 考勤与请假模块深度检查报告

**日期**: 2026-07-17  
**方法**: 四维度交叉验证  
**状态**: 发现 1 P0 + 2 P1 + 3 P2（共 6 项）

---

## P0: reverseDeduct 未覆盖 status=2(迟到)

| 字段 | 值 |
|------|-----|
| **文件** | `AttendanceServiceImpl.java` `reverseDeduct()` L291 |
| **代码** | `if (old.getStatus() != 1 || ...)` |
| **根因** | submit() 已改为 status=1\|2 均扣课时，但 reverseDeduct 仍只检查 status==1 |
| **影响** | 迟到考勤被修改为其他状态时，已扣课时无法回冲 → 课时被永久扣除 |
| **修复** | `old.getStatus() != 1 && old.getStatus() != 2` |

---

## P1-1: leaveRequest.audit updateById 无 CAS

| 文件 | `LeaveRequestServiceImpl.java` `audit()` L134-137 |
|------|------|
| **根因** | 读-校验-写模式，无并发保护 |
| **修复** | 改用 LambdaUpdateWrapper CAS |

## P1-2: batchSubmit 缺少事务，部分失败不回滚

| 文件 | `AttendanceServiceImpl.java` `batchSubmit()` L203-211 |
|------|------|
| **根因** | 循环调用 submit()（各有 @Transactional），外层 batchSubmit 无事务 |
| **影响** | 批量提交时第5个学员失败，前4个已提交不回滚 |
| **修复** | batchSubmit 也加 @Transactional |

---

## P2-1: createLeaveAttendance 重复 updateById

| 文件 | `LeaveRequestServiceImpl.java` `createLeaveAttendance()` L198-199 |
|------|------|
| **问题** | audit() 已 updateById(lr)，createLeaveAttendance 再 updateById 设 scheduleId |
| **影响** | 两次 update 之间无版本保护，可能覆盖 |
| **修复** | 在 audit 中一次性设 scheduleId 再 updateById |

## P2-2: findAccount 遇软删除班级静默返回 null

| 文件 | `AttendanceServiceImpl.java` `findAccount()` L357-358 |
|------|------|
| **问题** | ClassGroup 有 @TableLogic，selectById 对已删班级返回 null |
| **影响** | 考勤扣课时/回冲时找不到 account → skip，课时账本不完整 |
| **修复** | 使用绕过 @TableLogic 的查询方法（如 selectBatchIdsIncludeDeleted） |

## P2-3: reverseDeduct 回冲时不更新 totalLessons

| 文件 | `AttendanceServiceImpl.java` `reverseDeduct()` L299 |
|------|------|
| **问题** | 仅 `remainingLessons.add()`，不调 `totalLessons` |
| **影响** | totalLessons 与 remainingLessons 逐渐偏离（与 Finance refund 问题对称） |
| **修复** | 回冲时同步递增 totalLessons |
