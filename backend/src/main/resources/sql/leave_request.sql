CREATE TABLE IF NOT EXISTS leave_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    parent_user_id BIGINT NOT NULL,
    lesson_date DATE NOT NULL,
    schedule_id BIGINT NULL,
    reason VARCHAR(500),
    status INT DEFAULT 1,
    audit_user_id BIGINT NULL,
    audit_remark VARCHAR(200),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student (student_id),
    INDEX idx_parent (parent_user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
