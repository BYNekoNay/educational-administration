-- Existing installations are baselined at version 5. This first managed
-- migration adds only indexes that are safe for historical production data.

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'enrollment'
        AND INDEX_NAME = 'idx_parent_status_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_parent_status_created ON enrollment (parent_user_id, status, is_deleted, create_time)'
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
        AND TABLE_NAME = 'enrollment'
        AND INDEX_NAME = 'idx_course_status_class'
    ),
    'SELECT 1',
    'CREATE INDEX idx_course_status_class ON enrollment (course_id, status, class_id, is_deleted)'
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
        AND TABLE_NAME = 'payment_record'
        AND INDEX_NAME = 'idx_enrollment_deleted_time'
    ),
    'SELECT 1',
    'CREATE INDEX idx_enrollment_deleted_time ON payment_record (enrollment_id, is_deleted, pay_time)'
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
        AND INDEX_NAME = 'idx_status_deleted_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_status_deleted_created ON refund_record (status, is_deleted, create_time)'
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
        AND INDEX_NAME = 'idx_enrollment_status_deleted'
    ),
    'SELECT 1',
    'CREATE INDEX idx_enrollment_status_deleted ON refund_record (enrollment_id, status, is_deleted)'
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
        AND INDEX_NAME = 'idx_account_time'
    ),
    'SELECT 1',
    'CREATE INDEX idx_account_time ON lesson_flow (account_id, create_time)'
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
        AND TABLE_NAME = 'schedule_adjust_request'
        AND INDEX_NAME = 'idx_status_deleted_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_status_deleted_created ON schedule_adjust_request (status, is_deleted, create_time)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
