-- ============================================================
-- V15：课时账目一致性——存量 total 重算 + remaining≤total CHECK
--
-- 背景（用户实测：某学员「少儿美术启蒙班」账户 total=46 / remaining=48）：
-- 旧版种子脚本曾固定发放 48 课时包，且其"同步账户余额"环节按流水汇总
-- 覆盖 remaining_lessons、不动 total_lessons，导致存量账户 remaining > total；
-- 退费页据此计算的"可退课时/金额"虚高。
--
-- 修复：
-- 1) 按流水重算 total_lessons：total = Σ(缴费 source_type=1 + 人工调整 5 − 退费 4)。
--    考勤消费(2)与其回冲(3)不计入——当初扣减/回冲时也未计入 total，净额守恒。
-- 2) 重算后仍 remaining > total 的账户（回冲流水多于扣减流水的异常账户，
--    见测试调课回冲种子扣减场景）：补录一条人工调整流水（source_type=5）
--    使 流水和 = remaining = total 三者重新守恒，并同步递增余额与 total。
--    * v1 初版此处用 PREPARE 执行 SIGNAL 做防御，实测 MySQL 1295
--      （SIGNAL 不支持 prepared statement 协议）导致迁移失败，故改补录方案。
-- 3) 加 CHECK (remaining_lessons <= total_lessons) 防线（MySQL 8.0.16+ 生效），
--    与已有 CHECK (remaining_lessons >= 0) 共同约束账目边界。
--
-- 幂等：UPDATE 以 total<>重算值为条件、补录流水 biz_no=V15:FIX:{account_id}
--       语义唯一、CHECK 以 TABLE_CONSTRAINTS 判重——重跑均安全。
-- 注意：MySQL PREPARE 协议不支持 SIGNAL（错误 1295），迁移脚本中禁用。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 存量重算（派生表规避 MySQL 1093；total 变化的账户递增 version 使客户端缓存失效）
UPDATE lesson_account a
JOIN (
  SELECT account_id, COALESCE(SUM(CASE WHEN source_type IN (1, 4, 5) THEN change_amount ELSE 0 END), 0) AS t
  FROM lesson_flow
  WHERE is_deleted = 0
  GROUP BY account_id
) f ON f.account_id = a.id
SET a.total_lessons = f.t, a.version = a.version + 1
WHERE a.is_deleted = 0 AND a.total_lessons <> f.t;

-- 2. 对重算后仍 remaining > total 的账户补录差额流水（人工调整口径），
--    使 流水和 = remaining = total 三者重新守恒
INSERT INTO lesson_flow (account_id, student_id, lesson_id, source_type, source_id, change_amount,
                         change_type, before_balance, after_balance, remark, biz_no, create_time)
SELECT a.id, a.student_id, NULL, 5, NULL,
       a.remaining_lessons - a.total_lessons, 4,
       a.remaining_lessons, a.remaining_lessons * 2 - a.total_lessons,
       'V15 账目订正：补录历史差额课时', CONCAT('V15:FIX:', a.id), NOW()
FROM lesson_account a
WHERE a.is_deleted = 0 AND a.remaining_lessons > a.total_lessons;

-- 补录后按流水和归位违规账户的 remaining 与 total（其余账户不受影响）
UPDATE lesson_account a
JOIN (SELECT account_id, SUM(change_amount) s FROM lesson_flow WHERE is_deleted = 0 GROUP BY account_id) f
  ON f.account_id = a.id
SET a.remaining_lessons = f.s, a.total_lessons = f.s, a.version = a.version + 1
WHERE a.is_deleted = 0 AND a.remaining_lessons > a.total_lessons;

-- 3. 条件加 CHECK 约束
SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'lesson_account'
        AND CONSTRAINT_NAME = 'chk_lesson_remaining_le_total'
    ),
    'SELECT 1',
    'ALTER TABLE lesson_account ADD CONSTRAINT chk_lesson_remaining_le_total CHECK (remaining_lessons <= total_lessons)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
