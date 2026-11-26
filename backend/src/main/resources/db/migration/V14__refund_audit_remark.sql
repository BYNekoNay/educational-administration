-- ============================================================
-- V14：退费审核备注
--
-- 背景：管理端退费「拒绝」只有确认框，无原因输入；后端 refund_record 也没有
-- 备注字段，审核结果无法留痕（用户实测：拒绝后刷新即丢，无法追溯）。
--
-- 幂等：先用 information_schema 判断列是否已存在，再条件执行 DDL。
-- 注意：refund_record 有 STORED 生成列 pending_enrollment_id，本迁移只 ADD COLUMN，
--      不要 MODIFY 该生成列。
-- ============================================================

SET NAMES utf8mb4;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'refund_record'
        AND COLUMN_NAME = 'audit_remark'
    ),
    'SELECT 1',
    'ALTER TABLE refund_record ADD COLUMN audit_remark VARCHAR(255) NULL COMMENT ''审核备注（驳回原因 / 通过附言）'' AFTER status'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
