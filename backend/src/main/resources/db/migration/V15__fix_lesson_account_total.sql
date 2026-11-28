-- ============================================================
-- V15：课时账目一致性——存量 total 重算 + remaining≤total CHECK
--
-- 背景（用户实测：某学员「少儿美术启蒙班」账户 total=46 / remaining=48）：
-- 旧版种子脚本曾固定发放 48 课时包，且其"同步账户余额"环节按流水汇总
-- 覆盖 remaining_lessons、不动 total_lessons，导致存量账户 remaining > total；
-- 退费页据此计算的"可退课时/金额"虚高。
--
-- 修复（两步，均只上调 total、绝不触碰 remaining——零负余额风险）：
-- 1) 按流水重算 total_lessons：total = Σ(缴费 source_type=1 + 人工调整 5 − 退费 4)。
--    考勤消费(2)与其回冲(3)不计入——当初扣减/回冲时也未计入 total。
-- 2) 重算后仍 remaining > total 的账户：total := remaining 补齐。
--    * 说明：部分存量账户（非种子校准）流水汇总为负甚至不守恒，
--      不存在可用的"按流水补账"路径；total 少记属脏数据，直接补记到
--      与剩余一致（已消耗显示归零），是唯一不动真实余额的安全做法。
--    * v1 初版用 PREPARE 执行 SIGNAL 防御，实测 MySQL 1295（SIGNAL 不支持
--      prepared statement 协议）；v2 改补录流水 + 按流水和归位，实测把
--      流水和为负的原有账户 remaining 归位成负数触发 chk_1（3819）。
--      两版均已在服务器试错并回滚，v3 为最终方案。
-- 3) 加 CHECK (remaining_lessons <= total_lessons) 防线（MySQL 8.0.16+ 生效），
--    与已有 inline CHECK (remaining_lessons >= 0) 共同约束账目边界。
--
-- 幂等：步骤 1 以 total<>重算值为条件、步骤 2 以 remaining>total 为条件、
--       CHECK 以 TABLE_CONSTRAINTS 判重——重跑均安全。
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

-- 2. 补齐仍违规账户的 total（只上调 total，不动 remaining）
UPDATE lesson_account
SET total_lessons = remaining_lessons, version = version + 1
WHERE is_deleted = 0 AND remaining_lessons > total_lessons;

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
