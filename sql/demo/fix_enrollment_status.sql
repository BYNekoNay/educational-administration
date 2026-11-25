-- ============================================================
-- 演示数据修正：补「待审核 / 待缴费」报名样本
--
-- 背景：2026-11-26 补学员数据时把 enrollment.status 全写成了 3（已完成），
--       导致「报名 → 审核 → 缴费」闭环在演示数据上断链（管理端看不到待办）。
--
-- ⚠️ 仅用于演示/测试库。生产真实数据禁止执行本脚本。
-- ⚠️ 执行前请备份 enrollment 表。
--
-- 幂等：以 status=3 的记录为取样池，重复执行会继续消耗剩余的已完成记录，
--       因此只应执行一次；如需复原请用备份或把 status 改回 3。
-- ============================================================

SET NAMES utf8mb4;

-- ① 10 条改为待审核（status=1）
UPDATE enrollment SET status = 1
WHERE id IN (
  SELECT id FROM (
    SELECT id FROM enrollment
    WHERE status = 3 AND is_deleted = 0
    ORDER BY id DESC LIMIT 10
  ) t
);

-- ② 5 条改为待缴费（status=2）
UPDATE enrollment SET status = 2
WHERE id IN (
  SELECT id FROM (
    SELECT id FROM enrollment
    WHERE status = 3 AND is_deleted = 0
    ORDER BY id DESC LIMIT 5
  ) t
);

-- 校验：
--   SELECT status, COUNT(*) FROM enrollment WHERE is_deleted = 0 GROUP BY status;
--     → 期望 1:10、2:5、3:79（原 94 条）
