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
--    业务代码四条账目路径（缴费/调账/退费均同步 total）已核验自洽，本订正
--    只修正种子存量；重算后 remaining ≤ total 由恒等式保证。
-- 2) 加 CHECK (remaining_lessons <= total_lessons) 防线（MySQL 8.0.16+ 生效），
--    与已有 CHECK (remaining_lessons >= 0) 共同约束账目边界。
--
-- 幂等：约束存在性用 information_schema.TABLE_CONSTRAINTS 判断后条件执行。
-- 防御：重算后若仍有违规（净考勤消耗为负的异常账户），SIGNAL 使迁移失败，
--       人工核查后再放行——不允许带病上 CHECK。
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

-- 2. 防御：仍有违规则终止迁移（预期不触发）
SET @violations = (
  SELECT COUNT(*) FROM lesson_account
  WHERE is_deleted = 0 AND remaining_lessons > total_lessons
);
SET @raise = (SELECT IF(@violations > 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''lesson_account 仍存在 remaining>total 账户，请人工核查流水''',
  'SELECT 1'));
PREPARE stmt FROM @raise;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
