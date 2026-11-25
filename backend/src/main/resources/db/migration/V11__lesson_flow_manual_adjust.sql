-- ============================================================
-- V11: 课时人工调整（source_type=5）
--   1) lesson_flow 增加 biz_no 幂等键（历史行为 NULL，无需回填）
--   2) 增加唯一索引 uk_lesson_flow_biz_no
--
-- 幂等理由同 V10：ALTER TABLE 隐式提交，中途失败会让 flyway 停在 10，
-- 重跑必须不因 "Duplicate column name / Duplicate key name" 卡死。
--
-- 注意：MySQL 唯一索引允许多个 NULL，因此存量 4 类流水（biz_no 为 NULL）
-- 不会互相冲突，无需任何 UPDATE 回填。
--
-- 不改 source_type 列注释：MODIFY COLUMN 在大表上有重建风险，
-- 字典权威口径统一维护在 sql/schema.sql 与 docs/05、docs/10。
-- ============================================================

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'lesson_flow'
        AND COLUMN_NAME = 'biz_no'
    ),
    'SELECT 1',
    'ALTER TABLE lesson_flow ADD COLUMN biz_no VARCHAR(64) NULL COMMENT ''人工调整幂等键（客户端生成），其余来源为 NULL'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'lesson_flow'
        AND INDEX_NAME = 'uk_lesson_flow_biz_no'
    ),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_lesson_flow_biz_no ON lesson_flow (biz_no)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
