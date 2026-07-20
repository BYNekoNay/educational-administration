SET @certificate_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'exam_signup'
    AND COLUMN_NAME = 'certificate_file_url'
);
SET @certificate_sql = IF(
  @certificate_column_exists = 0,
  'ALTER TABLE exam_signup ADD COLUMN certificate_file_url VARCHAR(255) NULL AFTER certificate_no',
  'SELECT 1'
);
PREPARE certificate_stmt FROM @certificate_sql;
EXECUTE certificate_stmt;
DEALLOCATE PREPARE certificate_stmt;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL COMMENT 'SCHEDULE_CHANGE|CLASS_REMINDER|EXAM_NOTICE|ANNOUNCEMENT|LESSON_EXPIRY',
  title VARCHAR(200) NOT NULL,
  content VARCHAR(500),
  related_id BIGINT,
  dedupe_key VARCHAR(160) NULL COMMENT '定时提醒幂等键',
  is_read TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id_read (user_id, is_read, create_time DESC),
  UNIQUE KEY uk_notification_dedupe (dedupe_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知';

SET @dedupe_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'notification'
    AND COLUMN_NAME = 'dedupe_key'
);
SET @dedupe_sql = IF(
  @dedupe_column_exists = 0,
  'ALTER TABLE notification ADD COLUMN dedupe_key VARCHAR(160) NULL COMMENT ''定时提醒幂等键'', ADD UNIQUE KEY uk_notification_dedupe (dedupe_key)',
  'SELECT 1'
);
PREPARE dedupe_stmt FROM @dedupe_sql;
EXECUTE dedupe_stmt;
DEALLOCATE PREPARE dedupe_stmt;

CREATE TABLE IF NOT EXISTS schedule_lock (
  id TINYINT PRIMARY KEY,
  lock_name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排课事务互斥锁';
INSERT IGNORE INTO schedule_lock (id, lock_name) VALUES (1, 'auto_schedule');
