-- ============================================================
-- 集成测试建表脚本（H2，MODE=MySQL）
-- 仅覆盖集成测试涉及的表；字段结构与 sql/schema.sql 保持一致，
-- 去掉 MySQL 专属语法（ENGINE / CHARSET / COMMENT / ON UPDATE CURRENT_TIMESTAMP）。
-- 由 application-test.yml 的 spring.sql.init 在测试上下文启动时执行。
-- ============================================================

DROP TABLE IF EXISTS user;
CREATE TABLE user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50) NOT NULL,
  phone VARCHAR(20),
  role_code VARCHAR(30) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS student;
CREATE TABLE student (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  gender TINYINT,
  birthday DATE,
  school VARCHAR(100),
  contact_phone VARCHAR(20),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS parent_student;
CREATE TABLE parent_student (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_user_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  relation VARCHAR(20),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_parent_student UNIQUE (parent_user_id, student_id)
);

DROP TABLE IF EXISTS course;
CREATE TABLE course (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  category VARCHAR(50),
  total_lessons INT NOT NULL,
  lesson_duration INT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS class_group;
CREATE TABLE class_group (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  class_name VARCHAR(100) NOT NULL,
  teacher_id BIGINT NOT NULL,
  max_student_count INT NOT NULL,
  start_date DATE,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS class_student;
CREATE TABLE class_student (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  class_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_class_student UNIQUE (class_id, student_id)
);

DROP TABLE IF EXISTS enrollment;
CREATE TABLE enrollment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  parent_user_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  class_id BIGINT,
  status TINYINT NOT NULL DEFAULT 1,
  auditor_id BIGINT,
  audit_remark VARCHAR(255),
  hold_expire_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS classroom;
CREATE TABLE classroom (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  capacity INT NOT NULL,
  campus VARCHAR(50),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS schedule_lesson;
CREATE TABLE schedule_lesson (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  class_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  classroom_id BIGINT NOT NULL,
  lesson_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  source_lesson_id BIGINT,
  period_id BIGINT,
  period_count INT DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS attendance;
CREATE TABLE attendance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lesson_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status TINYINT NOT NULL,
  deduct_lessons DECIMAL(6,2) NOT NULL DEFAULT 0,
  check_time DATETIME,
  remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_attendance_lesson_student UNIQUE (lesson_id, student_id)
);

DROP TABLE IF EXISTS lesson_account;
CREATE TABLE lesson_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  total_lessons DECIMAL(6,2) NOT NULL DEFAULT 0,
  remaining_lessons DECIMAL(6,2) NOT NULL DEFAULT 0 CHECK (remaining_lessons >= 0),
  expire_date DATE,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_lesson_account_student_course UNIQUE (student_id, course_id)
);

DROP TABLE IF EXISTS lesson_flow;
CREATE TABLE lesson_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  lesson_id BIGINT,
  source_type TINYINT NOT NULL,
  source_id BIGINT,
  change_amount DECIMAL(6,2) NOT NULL,
  change_type TINYINT NOT NULL,
  before_balance DECIMAL(6,2) NOT NULL,
  after_balance DECIMAL(6,2) NOT NULL,
  remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS payment_record;
CREATE TABLE payment_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  enrollment_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  lesson_count DECIMAL(6,2) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  pay_type TINYINT NOT NULL,
  pay_time DATETIME NOT NULL,
  operator_id BIGINT,
  operator_role VARCHAR(30),
  remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

-- pending_enrollment_id：与生产库一致的计算列，保证"同一报名仅一条待审核退费"
DROP TABLE IF EXISTS refund_record;
CREATE TABLE refund_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  payment_record_id BIGINT NOT NULL,
  enrollment_id BIGINT NOT NULL,
  applicant_id BIGINT NOT NULL,
  applicant_role VARCHAR(30) NOT NULL,
  auditor_id BIGINT,
  amount DECIMAL(10,2) NOT NULL,
  lesson_count DECIMAL(6,2) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  pending_enrollment_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN status = 1 AND is_deleted = 0 THEN enrollment_id ELSE NULL END
  ),
  CONSTRAINT uk_refund_pending_enrollment UNIQUE (pending_enrollment_id)
);

DROP TABLE IF EXISTS room_booking;
CREATE TABLE room_booking (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  classroom_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  purpose VARCHAR(100),
  applicant_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS period;
CREATE TABLE period (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(10) NOT NULL,
  slot_order INT NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS schedule_lock;
CREATE TABLE schedule_lock (
  id TINYINT PRIMARY KEY,
  lock_name VARCHAR(50) NOT NULL UNIQUE
);
INSERT INTO schedule_lock (id, lock_name) VALUES (1, 'auto_schedule');

DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NOT NULL,
  module VARCHAR(50) NOT NULL,
  operation VARCHAR(100) NOT NULL,
  ip VARCHAR(50),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS notification;
CREATE TABLE notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content VARCHAR(500),
  related_id BIGINT,
  dedupe_key VARCHAR(160) NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_notification_dedupe UNIQUE (dedupe_key)
);