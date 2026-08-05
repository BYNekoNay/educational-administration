-- MySQL has no partial indexes. A generated column maps only active pending
-- refunds to enrollment_id; all other rows become NULL and remain repeatable.

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'refund_record'
        AND COLUMN_NAME = 'pending_enrollment_id'
    ),
    'SELECT 1',
    'ALTER TABLE refund_record ADD COLUMN pending_enrollment_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 1 AND is_deleted = 0 THEN enrollment_id ELSE NULL END) STORED'
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
        AND TABLE_NAME = 'refund_record'
        AND INDEX_NAME = 'uk_refund_pending_enrollment'
    ),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_refund_pending_enrollment ON refund_record (pending_enrollment_id)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
