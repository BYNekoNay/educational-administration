-- ============================================================
-- V15：课时账目一致性——存量 total 重算 + remaining≤total CHECK
--
-- 背景（用户实测：某学员「少儿美术启蒙班」账户 total=46 / remaining=48）：
-- 旧版种子脚本曾固定发放 48 课时包，且其"同步账户余额"环节按流水汇总
-- 覆盖 remaining_lessons、不动 total_lessons，导致存量账户 remaining > total；
-- 退费页据此计算的"可退课时/金额"虚高。
--
-- 修复（一步到位，均只上调 total、绝不触碰 remaining——零负余额风险）：
-- 1) 重算 total 并与 remaining 取大者：total = GREATEST(Σ(1,4,5), remaining)。
--    * 正常账户（净考勤消耗 ≥ 0）：GREATEST 取流水重算值 → total 修正为按流水口径；
--    * 异常账户（回冲流水多于扣减，Σ(1,4,5) < remaining）：GREATEST 取 remaining → 补齐；
--    * GREATEST 恒 ≥ remaining → 本步骤自身不违反目标 CHECK，且幂等（重跑不命中）。
--    * v1 初版用 PREPARE 执行 SIGNAL 防御，实测 MySQL 1295（SIGNAL 不支持
--      prepared statement 协议）；v2/v3 拆成"重算 + 补齐"两步，实测在 CHECK
--      已存在的库上互相打架（重算把补齐过的 total 打回去再次违规）。
--      两版均已在服务器试错并回滚，v4 为最终方案。
-- 2) 加 CHECK (remaining_lessons <= total_lessons) 防线（MySQL 8.0.16+ 生效），
--    与已有 inline CHECK (remaining_lessons >= 0) 共同约束账目边界。
--
-- 幂等：UPDATE 以 total<>GREATEST 值为条件、CHECK 以 TABLE_CONSTRAINTS 判重——重跑均安全。
-- 注意：MySQL PREPARE 协议不支持 SIGNAL（错误 1295），迁移脚本中禁用。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 重算 total 并取 GREATEST（派生表规避 MySQL 1093；total 变化的账户递增 version）
UPDATE lesson_account a
JOIN (
  SELECT account_id, COALESCE(SUM(CASE WHEN source_type IN (1, 4, 5) THEN change_amount ELSE 0 END), 0) AS t
  FROM lesson_flow
  WHERE is_deleted = 0
  GROUP BY account_id
) f ON f.account_id = a.id
SET a.total_lessons = GREATEST(f.t, a.remaining_lessons), a.version = a.version + 1
WHERE a.is_deleted = 0 AND a.total_lessons <> GREATEST(f.t, a.remaining_lessons);

-- 2. 条件加 CHECK 约束
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
