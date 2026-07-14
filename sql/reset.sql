-- ============================================================
-- 艺术培训机构全流程教务管理平台 - 数据库重置脚本
-- 用途：一键删除旧库、建表、填充初始化数据
-- 使用方法：mysql -u root -p < sql/reset.sql
-- 版本：v0.9
-- ============================================================

DROP DATABASE IF EXISTS edu_admin;

-- ============================================================
-- 第一部分：建表（schema.sql）
-- ============================================================

CREATE DATABASE IF NOT EXISTS edu_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE edu_admin;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. 用户与权限
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50) NOT NULL,
  phone VARCHAR(20),
  role_code VARCHAR(30) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-启用，2-禁用',
  last_login_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(30) NOT NULL UNIQUE,
  role_name VARCHAR(50) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(60) NOT NULL UNIQUE,
  path VARCHAR(100),
  type TINYINT NOT NULL COMMENT '1-菜单，2-接口',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID，0-顶级',
  menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
  icon VARCHAR(50) COMMENT 'Element Plus图标名',
  path VARCHAR(200) COMMENT '前端路由路径',
  permission_code VARCHAR(60) COMMENT '关联权限码',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
  visible TINYINT NOT NULL DEFAULT 1 COMMENT '1-可见 0-隐藏',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单';

DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(30) NOT NULL,
  permission_code VARCHAR(60) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_role_permission (role_code, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- ------------------------------------------------------------
-- 2. 学员、家长、课程与班级
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `student`;
CREATE TABLE `student` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  gender TINYINT,
  birthday DATE,
  school VARCHAR(100),
  contact_phone VARCHAR(20),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员档案';

DROP TABLE IF EXISTS `parent_student`;
CREATE TABLE `parent_student` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_user_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  relation VARCHAR(20) COMMENT '父/母/监护人等',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_parent_student (parent_user_id, student_id),
  INDEX idx_parent_user_id (parent_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长学员关系';

DROP TABLE IF EXISTS `course`;
CREATE TABLE `course` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  category VARCHAR(50),
  total_lessons INT NOT NULL,
  lesson_duration INT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程类型/套餐';

DROP TABLE IF EXISTS `class_group`;
CREATE TABLE `class_group` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  class_name VARCHAR(100) NOT NULL,
  teacher_id BIGINT NOT NULL,
  max_student_count INT NOT NULL,
  start_date DATE,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_course_id (course_id),
  INDEX idx_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级';

DROP TABLE IF EXISTS `class_student`;
CREATE TABLE `class_student` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-在班，2-已转出，3-已退出',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_class_student (class_id, student_id),
  INDEX idx_student_status (student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级学员关系';

DROP TABLE IF EXISTS `enrollment`;
CREATE TABLE `enrollment` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  parent_user_id BIGINT NOT NULL COMMENT '报名申请人',
  course_id BIGINT NOT NULL,
  class_id BIGINT COMMENT '审核通过后确定的意向班级',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-待审核，2-待缴费，3-已完成，4-已拒绝，5-已失效(超时释放)',
  auditor_id BIGINT COMMENT '审核人',
  audit_remark VARCHAR(255),
  hold_expire_time DATETIME COMMENT '待缴费留位截止时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_student_id (student_id),
  INDEX idx_class_id (class_id),
  INDEX idx_status_hold (status, hold_expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名申请';

-- ------------------------------------------------------------
-- 3. 教室、课次、调课
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `classroom`;
CREATE TABLE `classroom` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  capacity INT NOT NULL,
  campus VARCHAR(50),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教室';

DROP TABLE IF EXISTS `room_booking`;
CREATE TABLE `room_booking` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  classroom_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  purpose VARCHAR(100),
  applicant_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_classroom_time (classroom_id, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教室预约（非常规占用）';

DROP TABLE IF EXISTS `schedule_lesson`;
CREATE TABLE `schedule_lesson` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  classroom_id BIGINT NOT NULL,
  lesson_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-待上课，2-已完成，3-已取消，4-已调课',
  source_lesson_id BIGINT COMMENT '调课后新课次回填原课次ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_teacher_time (teacher_id, lesson_date, start_time, end_time),
  INDEX idx_classroom_time (classroom_id, lesson_date, start_time, end_time),
  INDEX idx_class_time (class_id, lesson_date, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课次';

DROP TABLE IF EXISTS `schedule_adjust_request`;
CREATE TABLE `schedule_adjust_request` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id BIGINT NOT NULL COMMENT '原课次ID',
  applicant_id BIGINT NOT NULL COMMENT '发起教师',
  reason VARCHAR(255),
  expect_time DATETIME COMMENT '期望调整到的时间',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-待审核，2-已通过，3-已驳回',
  auditor_id BIGINT,
  audit_remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_lesson_id (lesson_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调课申请';

-- ------------------------------------------------------------
-- 4. 考勤、作业与学情
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `attendance`;
CREATE TABLE `attendance` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status TINYINT NOT NULL COMMENT '1-到课，2-迟到，3-请假，4-缺勤',
  deduct_lessons DECIMAL(6,2) NOT NULL DEFAULT 0,
  check_time DATETIME,
  remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_lesson_student (lesson_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤';

DROP TABLE IF EXISTS `homework`;
CREATE TABLE `homework` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  content VARCHAR(1000),
  attachment_url VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_lesson_id (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课堂作业';

DROP TABLE IF EXISTS `learning_record`;
CREATE TABLE `learning_record` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  teacher_comment VARCHAR(500),
  growth_tag VARCHAR(100),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_lesson_student (lesson_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学情记录';

-- ------------------------------------------------------------
-- 5. 课时账户与流水
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `lesson_account`;
CREATE TABLE `lesson_account` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  total_lessons DECIMAL(6,2) NOT NULL DEFAULT 0,
  remaining_lessons DECIMAL(6,2) NOT NULL DEFAULT 0 CHECK (remaining_lessons >= 0),
  expire_date DATE,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_student_course (student_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课时账户';

DROP TABLE IF EXISTS `lesson_flow`;
CREATE TABLE `lesson_flow` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  lesson_id BIGINT,
  source_type TINYINT NOT NULL COMMENT '1-payment_record，2-refund_record，3-attendance，4-人工调整',
  source_id BIGINT,
  change_amount DECIMAL(6,2) NOT NULL,
  change_type TINYINT NOT NULL COMMENT '1-增加，2-消耗，3-退费，4-调整',
  before_balance DECIMAL(6,2) NOT NULL,
  after_balance DECIMAL(6,2) NOT NULL,
  remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_student_time (student_id, create_time),
  INDEX idx_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课时流水';

-- ------------------------------------------------------------
-- 6. 财务与薪资
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enrollment_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  lesson_count DECIMAL(6,2) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  pay_type TINYINT NOT NULL COMMENT '1-现金，2-模拟支付，3-其他',
  pay_time DATETIME NOT NULL,
  operator_id BIGINT,
  operator_role VARCHAR(30),
  remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_pay_time (pay_time),
  INDEX idx_student_id (student_id),
  INDEX idx_enrollment_id (enrollment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收费记录';

DROP TABLE IF EXISTS `refund_record`;
CREATE TABLE `refund_record` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  payment_record_id BIGINT NOT NULL COMMENT '关联的原缴费记录',
  enrollment_id BIGINT NOT NULL,
  applicant_id BIGINT NOT NULL COMMENT '退费申请人',
  applicant_role VARCHAR(30) NOT NULL COMMENT '申请人角色：EDU_ADMIN 或 PARENT',
  auditor_id BIGINT COMMENT '审核人，须与 applicant_id 不同',
  amount DECIMAL(10,2) NOT NULL,
  lesson_count DECIMAL(6,2) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-待审核，2-已通过，3-已拒绝',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_payment_record_id (payment_record_id),
  INDEX idx_enrollment_id (enrollment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退费记录';

DROP TABLE IF EXISTS `salary_rule`;
CREATE TABLE `salary_rule` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  lesson_unit_price DECIMAL(10,2) NOT NULL,
  substitute_rate DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT '代课系数，默认(0,1]按主讲单价折算，机构可配置>1表示加成',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_teacher_course (teacher_id, course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='薪资规则';

DROP TABLE IF EXISTS `teacher_salary`;
CREATE TABLE `teacher_salary` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  salary_month VARCHAR(7) NOT NULL,
  lesson_count DECIMAL(6,2) NOT NULL DEFAULT 0,
  substitute_count DECIMAL(6,2) NOT NULL DEFAULT 0,
  base_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  bonus_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-待确认，2-已确认，3-已发放，4-已撤销',
  calc_snapshot_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_teacher_month (teacher_id, salary_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师薪资结算';

DROP TABLE IF EXISTS `salary_adjustment`;
CREATE TABLE `salary_adjustment` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_salary_id BIGINT NOT NULL,
  adjust_amount DECIMAL(10,2) NOT NULL COMMENT '正数为补发，负数为扣回',
  reason VARCHAR(255) NOT NULL,
  operator_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_teacher_salary_id (teacher_salary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='薪资调整记录';

-- ------------------------------------------------------------
-- 7. 考级、通知、统计与日志
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `exam_level`;
CREATE TABLE `exam_level` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  level_name VARCHAR(50) NOT NULL,
  exam_date DATE,
  fee DECIMAL(10,2) NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考级项目';

DROP TABLE IF EXISTS `exam_signup`;
CREATE TABLE `exam_signup` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  exam_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  score DECIMAL(6,2),
  certificate_no VARCHAR(60),
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-已报名，2-已考试，3-已发证',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_exam_id (exam_id),
  INDEX idx_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考级报名';

DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  content VARCHAR(2000),
  receiver_type VARCHAR(30) NOT NULL COMMENT '接收范围：ALL/PARENT/TEACHER/角色代码或具体user_id列表(JSON)',
  receiver_id BIGINT COMMENT '若为定向通知，指向具体用户ID',
  notice_type TINYINT NOT NULL DEFAULT 1 COMMENT '1-公告，2-调课通知，3-上课提醒，4-课时不足提醒，5-报名留位到期提醒，6-考级通知',
  publish_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  INDEX idx_receiver (receiver_type, receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知公告';

DROP TABLE IF EXISTS `statistics_snapshot`;
CREATE TABLE `statistics_snapshot` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date DATE NOT NULL,
  stat_type VARCHAR(50) NOT NULL COMMENT '如 enrollment/attendance_rate/revenue/teacher_workload 等',
  stat_value DECIMAL(14,2),
  extra_json VARCHAR(2000) COMMENT '扩展维度数据，JSON字符串',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_date_type (stat_date, stat_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统计快照';

DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT NOT NULL,
  module VARCHAR(50) NOT NULL,
  operation VARCHAR(100) NOT NULL,
  ip VARCHAR(50),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_operator_time (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- ------------------------------------------------------------
-- 8. 机构配置
-- ------------------------------------------------------------

DROP TABLE IF EXISTS `organization`;
CREATE TABLE `organization` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  org_name VARCHAR(100) NOT NULL,
  campus VARCHAR(100),
  contact_phone VARCHAR(20),
  address VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构信息配置';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 第二部分：初始化数据（data.sql）
-- ============================================================

USE edu_admin;

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 角色与权限
-- ------------------------------------------------------------

INSERT INTO `role` (role_code, role_name) VALUES
('SUPER_ADMIN', '超级管理员'),
('EDU_ADMIN', '教务管理员'),
('FINANCE', '财务管理员'),
('TEACHER', '授课教师'),
('PARENT', '学员家长');

INSERT INTO `permission` (permission_code, path, type) VALUES
('menu:dashboard', '/admin/dashboard', 1),
('menu:user', '/admin/users', 1),
('menu:role', '/admin/roles', 1),
('menu:organization', '/admin/organization', 1),
('menu:notice', '/admin/notices', 1),
('menu:log', '/admin/logs', 1),
('menu:course', '/edu/courses', 1),
('menu:class', '/edu/classes', 1),
('menu:enrollment', '/edu/enrollments', 1),
('menu:schedule', '/edu/schedules', 1),
('menu:adjust', '/edu/adjust', 1),
('menu:classroom', '/edu/classrooms', 1),
('menu:student', '/edu/students', 1),
('menu:exam', '/edu/exams', 1),
('menu:attendance', '/edu/attendances', 1),
('menu:payment', '/finance/payments', 1),
('menu:refund', '/finance/refunds', 1),
('menu:lesson-flow', '/finance/lesson-flows', 1),
('menu:salary', '/finance/salaries', 1),
('menu:revenue', '/finance/revenue', 1),
('menu:permission', '/admin/permissions', 1),
('menu:menu', '/admin/menus', 1);

INSERT INTO `role_permission` (role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code FROM `permission`;

INSERT INTO `role_permission` (role_code, permission_code) VALUES
('EDU_ADMIN', 'menu:dashboard'),
('EDU_ADMIN', 'menu:course'),
('EDU_ADMIN', 'menu:class'),
('EDU_ADMIN', 'menu:enrollment'),
('EDU_ADMIN', 'menu:schedule'),
('EDU_ADMIN', 'menu:adjust'),
('EDU_ADMIN', 'menu:classroom'),
('EDU_ADMIN', 'menu:student'),
('EDU_ADMIN', 'menu:exam'),
('EDU_ADMIN', 'menu:attendance'),
('EDU_ADMIN', 'menu:notice');

INSERT INTO `role_permission` (role_code, permission_code) VALUES
('FINANCE', 'menu:dashboard'),
('FINANCE', 'menu:payment'),
('FINANCE', 'menu:refund'),
('FINANCE', 'menu:lesson-flow'),
('FINANCE', 'menu:salary'),
('FINANCE', 'menu:revenue');

-- ------------------------------------------------------------
-- 2. 初始化账号（22人，密码统一 BCrypt($2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu) = 123456）
-- ------------------------------------------------------------

INSERT INTO `user` (id, username, password, real_name, phone, role_code, status) VALUES
-- 超级管理员 (3人)
(1, 'admin',    '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '超级管理员',    '13800000001', 'SUPER_ADMIN', 1),
(10, 'admin2',  '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '系统管理员陈明', '13800000010', 'SUPER_ADMIN', 1),
(11, 'admin3',  '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '系统管理员黄丽', '13800000011', 'SUPER_ADMIN', 1),
-- 教务管理员 (3人)
(2, 'edu',      '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '教务小李',       '13800000002', 'EDU_ADMIN', 1),
(12, 'edu2',    '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '教务小张',       '13800000012', 'EDU_ADMIN', 1),
(13, 'edu3',    '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '教务小刘',       '13800000013', 'EDU_ADMIN', 1),
-- 财务管理员 (3人)
(3, 'finance',  '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '财务小王',       '13800000003', 'FINANCE', 1),
(14, 'finance2', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '财务小周',      '13800000014', 'FINANCE', 1),
(15, 'finance3', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '财务小吴',      '13800000015', 'FINANCE', 1),
-- 授课教师 (6人)
(4, 'teacher1', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '张老师(美术)',   '13800000004', 'TEACHER', 1),
(5, 'teacher2', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '李老师(钢琴)',   '13800000005', 'TEACHER', 1),
(6, 'teacher3', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '王老师(舞蹈)',   '13800000006', 'TEACHER', 1),
(16, 'teacher4', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '周老师(书法)',   '13800000016', 'TEACHER', 1),
(17, 'teacher5', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '孙老师(声乐)',   '13800000017', 'TEACHER', 1),
(18, 'teacher6', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '郑老师(主持)',   '13800000018', 'TEACHER', 1),
-- 学员家长 (7人)
(7, 'parent1',  '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '刘家长',         '13900000001', 'PARENT', 1),
(8, 'parent2',  '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '陈家长',         '13900000002', 'PARENT', 1),
(9, 'parent3',  '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '赵家长',         '13900000003', 'PARENT', 1),
(19, 'parent4', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '黄家长',         '13900000004', 'PARENT', 1),
(20, 'parent5', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '周家长',         '13900000005', 'PARENT', 1),
(21, 'parent6', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '吴家长',         '13900000006', 'PARENT', 1),
(22, 'parent7', '$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu', '郑家长',         '13900000007', 'PARENT', 1);

-- ------------------------------------------------------------
-- 3. 机构配置
-- ------------------------------------------------------------

INSERT INTO `organization` (id, org_name, campus, contact_phone, address) VALUES
(1, '知行艺术培训中心', '本部校区', '010-88889999', '北京市朝阳区示例路1号');

-- ------------------------------------------------------------
-- 4. 课程（12门）
-- ------------------------------------------------------------

INSERT INTO `course` (id, name, category, total_lessons, lesson_duration, price, status) VALUES
(1,  '少儿美术启蒙班',  '美术', 24, 60, 2400.00, 1),
(2,  '钢琴基础一对多',  '钢琴', 20, 45, 3000.00, 1),
(3,  '中国舞初级班',    '舞蹈', 24, 60, 2160.00, 1),
(4,  '硬笔书法班',      '书法', 16, 45, 1280.00, 1),
(5,  '少儿水彩画班',    '美术', 20, 60, 2000.00, 1),
(6,  '钢琴考级一对一',  '钢琴', 16, 45, 4800.00, 1),
(7,  '芭蕾舞基础班',    '舞蹈', 20, 90, 2800.00, 1),
(8,  '软笔书法班',      '书法', 16, 60, 1600.00, 1),
(9,  '少儿声乐班',      '声乐', 20, 45, 2200.00, 1),
(10, '少儿主持班',      '主持', 16, 60, 1920.00, 1),
(11, '素描基础班',      '美术', 24, 90, 3600.00, 1),
(12, '朗诵表演班',      '主持', 16, 45, 1600.00, 1);

-- ------------------------------------------------------------
-- 5. 教室（10间）
-- ------------------------------------------------------------

INSERT INTO `classroom` (id, name, capacity, campus, status) VALUES
(1,  'A101画室',     15, '本部校区', 1),
(2,  'B201琴房',     8,  '本部校区', 1),
(3,  'C301舞蹈室',   20, '本部校区', 1),
(4,  'A102画室',     12, '本部校区', 1),
(5,  'B202琴房',     6,  '本部校区', 1),
(6,  'C302多功能厅', 25, '本部校区', 1),
(7,  'D401书法室',   16, '本部校区', 1),
(8,  'E501声乐室',   10, '本部校区', 1),
(9,  'F601主持教室', 15, '本部校区', 1),
(10, 'A103素描室',   12, '本部校区', 1);

-- ------------------------------------------------------------
-- 6. 班级（12个）
-- ------------------------------------------------------------

INSERT INTO `class_group` (id, course_id, class_name, teacher_id, max_student_count, start_date, status) VALUES
(1,  1,  '美术周末A班',   4,  15, '2026-07-11', 1),
(2,  2,  '钢琴周中B班',   5,  8,  '2026-07-13', 1),
(3,  3,  '舞蹈周末C班',   6,  20, '2026-07-12', 1),
(4,  4,  '书法暑假D班',   16, 16, '2026-07-13', 1),
(5,  5,  '水彩画暑假E班', 4,  12, '2026-07-14', 1),
(6,  6,  '钢琴考级F班',   5,  6,  '2026-07-14', 1),
(7,  7,  '芭蕾舞暑假G班', 6,  20, '2026-07-15', 1),
(8,  8,  '软笔书法H班',   16, 16, '2026-07-16', 1),
(9,  9,  '声乐暑假I班',   17, 10, '2026-07-16', 1),
(10, 10, '主持暑假J班',   18, 15, '2026-07-17', 1),
(11, 11, '素描提高K班',   4,  12, '2026-07-17', 1),
(12, 12, '朗诵暑假L班',   18, 15, '2026-07-18', 1);

-- ------------------------------------------------------------
-- 7. 学员（12名）
-- ------------------------------------------------------------

INSERT INTO `student` (id, name, gender, birthday, school, contact_phone, status) VALUES
(1,  '刘小小',  1, '2018-03-01', '示例小学', '13900000001', 1),
(2,  '陈朵朵',  2, '2017-09-12', '示例小学', '13900000002', 1),
(3,  '赵一鸣',  1, '2016-05-20', '示例小学', '13900000003', 1),
(4,  '黄思源',  1, '2015-08-15', '阳光小学', '13900000004', 1),
(5,  '周雨彤',  2, '2017-01-20', '蓝天小学', '13900000005', 1),
(6,  '吴俊杰',  1, '2016-11-08', '实验二小', '13900000006', 1),
(7,  '郑悦然',  2, '2018-06-25', '百花小学', '13900000007', 1),
(8,  '林子涵',  1, '2015-12-03', '阳光小学', '13900000004', 1),
(9,  '王诗琪',  2, '2017-04-17', '蓝天小学', '13900000005', 1),
(10, '陈浩宇',  1, '2016-09-28', '实验二小', '13900000006', 1),
(11, '赵雨萱',  2, '2018-01-10', '百花小学', '13900000007', 1),
(12, '刘子轩',  1, '2015-07-05', '示例小学', '13900000001', 1);

-- ------------------------------------------------------------
-- 8. 家长-学员关系
-- ------------------------------------------------------------

INSERT INTO `parent_student` (parent_user_id, student_id, relation) VALUES
(7,  1,  '父亲'),  (7,  12, '父亲'),
(8,  2,  '母亲'),
(9,  3,  '父亲'),
(19, 4,  '父亲'), (19, 8,  '父亲'),
(20, 5,  '母亲'), (20, 9,  '母亲'),
(21, 6,  '父亲'), (21, 10, '父亲'),
(22, 7,  '母亲'), (22, 11, '母亲');

-- ------------------------------------------------------------
-- 9. 报名记录（15条）
-- ------------------------------------------------------------

INSERT INTO `enrollment` (id, student_id, parent_user_id, course_id, class_id, status, auditor_id, audit_remark) VALUES
(1,  1,  7,  1,  1,  3, 2,  '资料齐全，审核通过'),
(2,  2,  8,  2,  2,  3, 2,  '资料齐全，审核通过'),
(3,  3,  9,  3,  3,  3, 2,  '资料齐全，审核通过'),
(4,  4,  19, 4,  4,  3, 2,  '新生报名，资料完整'),
(5,  5,  20, 5,  5,  3, 2,  '有美术基础，分配水彩班'),
(6,  6,  21, 6,  6,  3, 12, '钢琴四级基础，安排考级班'),
(7,  7,  22, 7,  7,  3, 2,  '零基础，适合芭蕾入门'),
(8,  8,  19, 8,  8,  3, 12, '硬笔书法毕业，报软笔提高'),
(9,  9,  20, 9,  9,  3, 13, '嗓音条件好，推荐声乐班'),
(10, 10, 21, 10, 10, 3, 12, '语言表达能力强'),
(11, 11, 22, 11, 11, 3, 2,  '有绘画天赋，推荐素描提高'),
(12, 12, 7,  12, 12, 3, 13, '活泼开朗，适合朗诵表演'),
(13, 3,  9,  4,  4,  3, 2,  '加报硬笔书法，提升书写'),
(14, 1,  7,  5,  5,  3, 12, '加报水彩画，发展兴趣'),
(15, 4,  19, 11, 11, 3, 13, '加报素描，系统学习美术');

-- ------------------------------------------------------------
-- 10. 班级学员关系
-- ------------------------------------------------------------

INSERT INTO `class_student` (class_id, student_id, status) VALUES
(1,1,1),(2,2,1),(3,3,1),(4,3,1),(4,4,1),(5,1,1),(5,5,1),(6,6,1),(7,7,1),(8,8,1),
(9,9,1),(10,10,1),(11,4,1),(11,11,1),(12,12,1);

-- ------------------------------------------------------------
-- 11. 收费记录（15条）
-- ------------------------------------------------------------

INSERT INTO `payment_record` (id, enrollment_id, student_id, course_id, lesson_count, amount, pay_type, pay_time, operator_id, operator_role, remark) VALUES
(1,  1,  1,  1,  24, 2400.00, 2, '2026-07-08 10:00:00', 3,  'FINANCE', '首次缴费开通课时-美术启蒙'),
(2,  2,  2,  2,  20, 3000.00, 2, '2026-07-08 10:10:00', 3,  'FINANCE', '首次缴费开通课时-钢琴基础'),
(3,  3,  3,  3,  24, 2160.00, 2, '2026-07-08 10:20:00', 3,  'FINANCE', '首次缴费开通课时-中国舞'),
(4,  4,  4,  4,  16, 1280.00, 2, '2026-07-09 09:00:00', 14, 'FINANCE', '缴费开通-硬笔书法'),
(5,  5,  5,  5,  20, 2000.00, 2, '2026-07-09 09:30:00', 14, 'FINANCE', '缴费开通-水彩画'),
(6,  6,  6,  6,  16, 4800.00, 2, '2026-07-09 10:00:00', 14, 'FINANCE', '缴费开通-钢琴考级'),
(7,  7,  7,  7,  20, 2800.00, 2, '2026-07-10 09:00:00', 15, 'FINANCE', '缴费开通-芭蕾基础'),
(8,  8,  8,  8,  16, 1600.00, 2, '2026-07-10 09:30:00', 15, 'FINANCE', '缴费开通-软笔书法'),
(9,  9,  9,  9,  20, 2200.00, 2, '2026-07-10 10:00:00', 15, 'FINANCE', '缴费开通-声乐'),
(10, 10, 10, 10, 16, 1920.00, 2, '2026-07-10 10:30:00', 3,  'FINANCE', '缴费开通-主持'),
(11, 11, 11, 11, 24, 3600.00, 2, '2026-07-10 11:00:00', 3,  'FINANCE', '缴费开通-素描'),
(12, 12, 12, 12, 16, 1600.00, 2, '2026-07-10 11:30:00', 3,  'FINANCE', '缴费开通-朗诵表演'),
(13, 13, 3,  4,  16, 1280.00, 2, '2026-07-10 14:00:00', 14, 'FINANCE', '加报缴费-硬笔书法'),
(14, 14, 1,  5,  20, 2000.00, 2, '2026-07-10 14:30:00', 14, 'FINANCE', '加报缴费-水彩画'),
(15, 15, 4,  11, 24, 3600.00, 2, '2026-07-10 15:00:00', 15, 'FINANCE', '加报缴费-素描');

-- ------------------------------------------------------------
-- 12. 课时账户（15个）
-- ------------------------------------------------------------

INSERT INTO `lesson_account` (id, student_id, course_id, total_lessons, remaining_lessons, expire_date, version) VALUES
(1, 1,1, 24, 22,  '2027-07-11', 0), (2, 2,2, 20, 18,  '2027-07-13', 0),
(3, 3,3, 24, 21,  '2027-07-12', 0), (4, 4,4, 16, 12,  '2027-07-13', 0),
(5, 5,5, 20, 16,  '2027-07-14', 0), (6, 6,6, 16, 14.5,'2027-07-14', 0),
(7, 7,7, 20, 16,  '2027-07-15', 0), (8, 8,8, 16, 14,  '2027-07-16', 0),
(9, 9,9, 20, 16,  '2027-07-16', 0), (10,10,10,16, 12,  '2027-07-17', 0),
(11,11,11,24, 22,  '2027-07-17', 0), (12,12,12,16, 14,  '2027-07-18', 0),
(13,3, 4,  16, 14,  '2027-07-13', 0), (14,1, 5,  20, 18,  '2027-07-14', 0),
(15,4, 11, 24, 22,  '2027-07-17', 0);

-- ------------------------------------------------------------
-- 13. 课时流水 - 缴费开通（15条）
-- ------------------------------------------------------------

INSERT INTO `lesson_flow` (account_id, student_id, lesson_id, source_type, source_id, change_amount, change_type, before_balance, after_balance, remark) VALUES
(1,1,NULL,1,1,24,1,0,24,'缴费开通课时-美术启蒙'), (2,2,NULL,1,2,20,1,0,20,'缴费开通课时-钢琴基础'),
(3,3,NULL,1,3,24,1,0,24,'缴费开通课时-中国舞'), (4,4,NULL,1,4,16,1,0,16,'缴费开通课时-硬笔书法'),
(5,5,NULL,1,5,20,1,0,20,'缴费开通课时-水彩画'), (6,6,NULL,1,6,16,1,0,16,'缴费开通课时-钢琴考级'),
(7,7,NULL,1,7,20,1,0,20,'缴费开通课时-芭蕾基础'), (8,8,NULL,1,8,16,1,0,16,'缴费开通课时-软笔书法'),
(9,9,NULL,1,9,20,1,0,20,'缴费开通课时-声乐'), (10,10,NULL,1,10,16,1,0,16,'缴费开通课时-主持'),
(11,11,NULL,1,11,24,1,0,24,'缴费开通课时-素描'), (12,12,NULL,1,12,16,1,0,16,'缴费开通课时-朗诵表演'),
(13,13,NULL,1,13,16,1,0,16,'加报缴费-硬笔书法'), (14,14,NULL,1,14,20,1,0,20,'加报缴费-水彩画'),
(15,15,NULL,1,15,24,1,0,24,'加报缴费-素描');

-- ------------------------------------------------------------
-- 14. 排课数据（36条）
-- ------------------------------------------------------------

INSERT INTO `schedule_lesson` (id, class_id, teacher_id, classroom_id, lesson_date, start_time, end_time, status) VALUES
(1,1,4,1,'2026-07-12','09:00:00','10:00:00',2),(16,1,4,1,'2026-07-19','09:00:00','10:00:00',2),(28,1,4,1,'2026-07-26','09:00:00','10:00:00',1),
(2,2,5,2,'2026-07-13','16:00:00','16:45:00',2),(17,2,5,2,'2026-07-20','16:00:00','16:45:00',2),(29,2,5,2,'2026-07-27','16:00:00','16:45:00',1),
(3,3,6,3,'2026-07-12','14:00:00','15:00:00',2),(18,3,6,3,'2026-07-19','14:00:00','15:00:00',2),(30,3,6,3,'2026-07-26','14:00:00','15:00:00',1),
(4,4,16,7,'2026-07-13','09:00:00','09:45:00',2),(19,4,16,7,'2026-07-20','09:00:00','09:45:00',2),(31,4,16,7,'2026-07-27','09:00:00','09:45:00',1),
(5,5,4,4,'2026-07-14','09:00:00','10:00:00',2),(20,5,4,4,'2026-07-21','09:00:00','10:00:00',2),(32,5,4,4,'2026-07-28','09:00:00','10:00:00',1),
(6,6,5,5,'2026-07-14','16:00:00','16:45:00',2),(21,6,5,5,'2026-07-21','16:00:00','16:45:00',2),(33,6,5,5,'2026-07-28','16:00:00','16:45:00',1),
(7,7,6,3,'2026-07-15','09:30:00','11:00:00',2),(22,7,6,3,'2026-07-22','09:30:00','11:00:00',2),(34,7,6,3,'2026-07-29','09:30:00','11:00:00',1),
(8,8,16,7,'2026-07-16','09:00:00','10:00:00',2),(23,8,16,7,'2026-07-23','09:00:00','10:00:00',2),(35,8,16,7,'2026-07-30','09:00:00','10:00:00',1),
(9,9,17,8,'2026-07-16','14:00:00','14:45:00',2),(24,9,17,8,'2026-07-23','14:00:00','14:45:00',2),(36,9,17,8,'2026-07-30','14:00:00','14:45:00',1),
(10,10,18,9,'2026-07-17','09:00:00','10:00:00',2),(25,10,18,9,'2026-07-24','09:00:00','10:00:00',2),(37,10,18,9,'2026-07-31','09:00:00','10:00:00',1),
(11,11,4,10,'2026-07-17','14:00:00','15:30:00',2),(26,11,4,10,'2026-07-24','14:00:00','15:30:00',2),(38,11,4,10,'2026-07-31','14:00:00','15:30:00',1),
(12,12,18,9,'2026-07-18','14:00:00','14:45:00',2),(27,12,18,9,'2026-07-25','14:00:00','14:45:00',2),(39,12,18,9,'2026-08-01','14:00:00','14:45:00',1);

-- ------------------------------------------------------------
-- 15. 考勤记录（24条）
-- ------------------------------------------------------------

INSERT INTO `attendance` (lesson_id, student_id, status, deduct_lessons, check_time, remark) VALUES
(1,1,1,1,'2026-07-12 10:00:00','正常到课，画画积极'),(16,1,1,1,'2026-07-19 10:00:00','到课，作品完成度高'),
(2,2,1,1,'2026-07-13 16:45:00','正常到课'),(17,2,1,1,'2026-07-20 16:45:00','到课，指法有进步'),
(3,3,1,1,'2026-07-12 15:00:00','正常到课'),(18,3,3,0,'2026-07-19 15:00:00','家长请假，身体不适'),
(4,3,1,1,'2026-07-13 09:50:00','到课，握笔姿势需纠正'),(4,4,1,1,'2026-07-13 09:50:00','到课'),
(19,3,1,1,'2026-07-20 09:50:00','到课，进步明显'),(19,4,1,1,'2026-07-20 09:50:00','到课'),
(5,1,1,1,'2026-07-14 10:00:00','到课'),(5,5,1,1,'2026-07-14 10:00:00','到课，配色感好'),
(20,1,1,1,'2026-07-21 10:00:00','到课'),(20,5,1,1,'2026-07-21 10:00:00','到课'),
(6,6,1,1,'2026-07-14 16:45:00','到课，节奏感好'),(21,6,2,0.5,'2026-07-21 16:50:00','迟到15分钟，扣半课时'),
(7,7,1,1,'2026-07-15 11:00:00','到课，柔韧性好'),(22,7,1,1,'2026-07-22 11:00:00','到课'),
(8,8,1,1,'2026-07-16 10:00:00','到课'),(23,8,1,1,'2026-07-23 10:00:00','到课，字体工整'),
(9,9,1,1,'2026-07-16 14:45:00','到课，音准好'),(24,9,1,1,'2026-07-23 14:45:00','到课'),
(10,10,1,1,'2026-07-17 10:00:00','到课'),(25,10,4,1,'2026-07-24 10:00:00','缺勤，未请假'),
(11,11,1,1,'2026-07-17 15:30:00','到课'),(11,4,1,1,'2026-07-17 15:30:00','到课，造型能力好'),
(26,11,1,1,'2026-07-24 15:30:00','到课'),(26,4,1,1,'2026-07-24 15:30:00','到课'),
(12,12,1,1,'2026-07-18 14:45:00','到课，表演欲强'),(27,12,1,1,'2026-07-25 14:45:00','到课');

-- ------------------------------------------------------------
-- 16. 课时流水 - 考勤消耗
-- ------------------------------------------------------------

INSERT INTO `lesson_flow` (account_id, student_id, lesson_id, source_type, source_id, change_amount, change_type, before_balance, after_balance, remark) VALUES
(1,1,1,3,1,-1,2,24,23,'考勤扣减-美术A班第1次'),(1,1,16,3,16,-1,2,23,22,'考勤扣减-美术A班第2次'),
(2,2,2,3,2,-1,2,20,19,'考勤扣减-钢琴B班第1次'),(2,2,17,3,17,-1,2,19,18,'考勤扣减-钢琴B班第2次'),
(3,3,3,3,3,-1,2,24,23,'考勤扣减-舞蹈C班第1次'),
(4,4,4,3,4,-1,2,16,15,'考勤扣减-书法D班第1次'),(4,4,19,3,19,-1,2,15,14,'考勤扣减-书法D班第2次'),
(5,5,5,3,5,-1,2,20,19,'考勤扣减-水彩E班第1次'),(5,5,20,3,20,-1,2,19,18,'考勤扣减-水彩E班第2次'),
(6,6,6,3,6,-1,2,16,15,'考勤扣减-钢琴考级F班第1次'),(6,6,21,3,21,-0.5,2,15,14.5,'考勤扣减-钢琴考级F班第2次(迟到)'),
(7,7,7,3,7,-1,2,20,19,'考勤扣减-芭蕾G班第1次'),(7,7,22,3,22,-1,2,19,18,'考勤扣减-芭蕾G班第2次'),
(8,8,8,3,8,-1,2,16,15,'考勤扣减-软笔书法H班第1次'),(8,8,23,3,23,-1,2,15,14,'考勤扣减-软笔书法H班第2次'),
(9,9,9,3,9,-1,2,20,19,'考勤扣减-声乐I班第1次'),(9,9,24,3,24,-1,2,19,18,'考勤扣减-声乐I班第2次'),
(10,10,10,3,10,-1,2,16,15,'考勤扣减-主持J班第1次'),(10,10,25,3,25,-1,2,15,14,'考勤扣减-主持J班第2次(缺勤)'),
(11,11,11,3,11,-1,2,24,23,'考勤扣减-素描K班第1次'),(11,11,26,3,26,-1,2,23,22,'考勤扣减-素描K班第2次'),
(12,12,12,3,12,-1,2,16,15,'考勤扣减-朗诵L班第1次'),(12,12,27,3,27,-1,2,15,14,'考勤扣减-朗诵L班第2次'),
(13,3,4,3,4,-1,2,16,15,'考勤扣减-书法D班第1次(加报)'),(13,3,19,3,19,-1,2,15,14,'考勤扣减-书法D班第2次(加报)'),
(14,1,5,3,5,-1,2,20,19,'考勤扣减-水彩E班第1次(加报)'),(14,1,20,3,20,-1,2,19,18,'考勤扣减-水彩E班第2次(加报)'),
(15,4,11,3,11,-1,2,24,23,'考勤扣减-素描K班第1次(加报)'),(15,4,26,3,26,-1,2,23,22,'考勤扣减-素描K班第2次(加报)');

-- ------------------------------------------------------------
-- 17. 薪资规则（18条）
-- ------------------------------------------------------------

INSERT INTO `salary_rule` (teacher_id, course_id, lesson_unit_price, substitute_rate) VALUES
(4,1,80.00,0.80),(4,5,70.00,0.80),(4,11,100.00,0.80),
(5,2,100.00,0.80),(5,6,150.00,0.80),(5,9,80.00,0.80),
(6,3,90.00,0.80),(6,7,110.00,0.80),(6,10,80.00,0.80),
(16,4,60.00,0.80),(16,8,70.00,0.80),(16,12,60.00,0.80),
(17,9,90.00,0.80),(17,10,70.00,0.80),(17,12,70.00,0.80),
(18,10,80.00,0.80),(18,12,75.00,0.80),(18,9,75.00,0.80);

-- ------------------------------------------------------------
-- 18. 教师薪资结算（12条）
-- ------------------------------------------------------------

INSERT INTO `teacher_salary` (teacher_id, salary_month, lesson_count, substitute_count, base_amount, bonus_amount, total_amount, status, calc_snapshot_time) VALUES
(4,'2026-07',6,0,500.00,100.00,600.00,1,'2026-07-30 12:00:00'),(5,'2026-07',5,0,525.00,50.00,575.00,1,'2026-07-30 12:00:00'),
(6,'2026-07',5,0,490.00,50.00,540.00,1,'2026-07-30 12:00:00'),(16,'2026-07',6,0,390.00,60.00,450.00,1,'2026-07-30 12:00:00'),
(17,'2026-07',4,0,340.00,30.00,370.00,1,'2026-07-30 12:00:00'),(18,'2026-07',4,0,310.00,30.00,340.00,1,'2026-07-30 12:00:00'),
(4,'2026-06',8,0,640.00,100.00,740.00,3,'2026-06-30 12:00:00'),(5,'2026-06',6,0,620.00,80.00,700.00,3,'2026-06-30 12:00:00'),
(6,'2026-06',8,0,760.00,80.00,840.00,3,'2026-06-30 12:00:00'),(16,'2026-06',6,0,390.00,50.00,440.00,3,'2026-06-30 12:00:00'),
(17,'2026-06',6,0,480.00,50.00,530.00,3,'2026-06-30 12:00:00'),(18,'2026-06',4,0,310.00,30.00,340.00,2,'2026-06-30 12:00:00');

-- ------------------------------------------------------------
-- 19. 薪资调整记录（10条）
-- ------------------------------------------------------------

INSERT INTO `salary_adjustment` (teacher_salary_id, adjust_amount, reason, operator_id) VALUES
(1,50.00,'暑期高温补贴',3),(2,30.00,'暑期高温补贴',3),(3,30.00,'暑期高温补贴',3),
(4,40.00,'暑期高温补贴',14),(7,80.00,'6月全勤奖金',14),(8,60.00,'6月全勤奖金',3),
(9,60.00,'6月全勤奖金',3),(10,30.00,'6月全勤奖金',14),(11,30.00,'6月全勤奖金',15),
(5,-20.00,'7月迟到一次扣款',15);

-- ------------------------------------------------------------
-- 20. 退费记录（10条）
-- ------------------------------------------------------------

INSERT INTO `refund_record` (id, student_id, payment_record_id, enrollment_id, applicant_id, applicant_role, auditor_id, amount, lesson_count, status) VALUES
(1,2,2,2,8,'PARENT',3,150.00,1,3),(2,5,5,5,20,'PARENT',14,200.00,2,2),
(3,3,3,3,9,'PARENT',3,180.00,2,2),(4,6,6,6,21,'PARENT',14,300.00,1,1),
(5,8,8,8,19,'PARENT',NULL,200.00,2,1),(6,9,9,9,20,'PARENT',15,220.00,2,2),
(7,10,10,10,21,'PARENT',3,240.00,2,2),(8,1,1,1,2,'EDU_ADMIN',12,200.00,2,1),
(9,4,4,4,19,'PARENT',15,160.00,2,2),(10,7,7,7,22,'PARENT',14,280.00,2,2);

INSERT INTO `lesson_flow` (account_id, student_id, lesson_id, source_type, source_id, change_amount, change_type, before_balance, after_balance, remark) VALUES
(5,5,NULL,2,2,-2,3,18,16,'退费扣减课时-水彩E班(已通过)'),(3,3,NULL,2,3,-2,3,23,21,'退费扣减课时-舞蹈C班(已通过)'),
(9,9,NULL,2,6,-2,3,18,16,'退费扣减课时-声乐I班(已通过)'),(10,10,NULL,2,7,-2,3,14,12,'退费扣减课时-主持J班(已通过)'),
(4,4,NULL,2,9,-2,3,14,12,'退费扣减课时-书法D班(已通过)'),(7,7,NULL,2,10,-2,3,18,16,'退费扣减课时-芭蕾G班(已通过)');

-- ------------------------------------------------------------
-- 21. 考级项目（10个）
-- ------------------------------------------------------------

INSERT INTO `exam_level` (id, name, level_name, exam_date, fee) VALUES
(1, '中国美术学院社会艺术水平考级','一级','2026-12-20',200.00),(2,'中国美术学院社会艺术水平考级','二级','2026-12-20',250.00),
(3, '中央音乐学院钢琴考级','三级','2026-11-15',300.00),(4,'中央音乐学院钢琴考级','四级','2026-11-15',350.00),
(5, '北京舞蹈学院中国舞考级','一级','2026-12-10',220.00),(6,'北京舞蹈学院中国舞考级','二级','2026-12-10',260.00),
(7, '中国书法家协会书法考级','一级','2026-12-05',180.00),(8,'中国书法家协会书法考级','二级','2026-12-05',220.00),
(9, '中国音乐家协会声乐考级','一级','2026-12-18',240.00),(10,'全国青少年语言艺术等级测评','一级','2026-12-25',200.00);

-- ------------------------------------------------------------
-- 22. 考级报名（15条）
-- ------------------------------------------------------------

INSERT INTO `exam_signup` (exam_id, student_id, score, certificate_no, status) VALUES
(1,1,85.50,'MZ2026-YJ-001',3),(1,5,78.00,'MZ2026-YJ-002',3),(2,1,0,NULL,1),(2,4,0,NULL,1),
(3,2,0,NULL,2),(4,6,0,NULL,1),(5,3,82.00,'WD2026-CJ-001',3),(5,7,0,NULL,2),(6,3,0,NULL,1),
(7,4,0,NULL,2),(7,8,0,NULL,1),(8,4,0,NULL,1),(9,9,0,NULL,1),(10,10,0,NULL,1),(10,12,0,NULL,1);

-- ------------------------------------------------------------
-- 23. 通知公告（12条）
-- ------------------------------------------------------------

INSERT INTO `notice` (title, content, receiver_type, notice_type, publish_time) VALUES
('机构暑期课程安排通知','暑期课程已排定，请家长和教师留意课表变化。所有班级从7月11日起陆续开课。','ALL',1,'2026-07-09 09:00:00'),
('2026年秋季招生简章','知行艺术培训中心2026年秋季班开始招生。','ALL',1,'2026-07-10 10:00:00'),
('机构放假通知','8月15日至8月20日机构放假，所有课程暂停。','ALL',1,'2026-07-10 14:00:00'),
('关于加强校区安全管理的规定','即日起所有家长接送学员须在前台登记。','ALL',1,'2026-07-08 08:00:00'),
('美术周末A班调课通知','因张老师7月19日临时有事，美术周末A班周六课程调整至周日同一时间。','PARENT',2,'2026-07-15 16:00:00'),
('钢琴周中B班调课通知','钢琴周中B班7月27日课程调整至7月28日16:00上课。','PARENT',2,'2026-07-22 10:00:00'),
('周末班上课提醒','各位家长：周末班明日正常上课，请准时送学员到校。','PARENT',3,'2026-07-17 18:00:00'),
('周中班上课提醒','明天（周一）书法D班和钢琴B班正常上课。','PARENT',3,'2026-07-12 18:00:00'),
('课时余额不足提醒','刘小小同学的美术启蒙课程剩余课时已不足5节，请及时续费。','PARENT',4,'2026-07-20 09:00:00'),
('报名留位即将到期提醒','王诗琪同学声乐班留位将于24小时后到期。','PARENT',5,'2026-07-15 10:00:00'),
('2026年美术考级报名通知','中国美术学院社会艺术水平考级开始报名，考试时间12月20日。','ALL',6,'2026-07-10 08:00:00');

-- ------------------------------------------------------------
-- 24. 作业（15条）
-- ------------------------------------------------------------

INSERT INTO `homework` (lesson_id, teacher_id, content) VALUES
(1,4,'临摹一张静物组合（水果+陶罐），注意明暗关系和构图。'),(16,4,'完成一幅风景画，画出远近层次。'),
(2,5,'练习《拜厄》第35-37条，注意左手伴奏的力度控制。'),(17,5,'练习《车尔尼599》第12条，速度均匀。'),
(3,6,'练习横叉、竖叉拉伸，每天10分钟。'),(4,16,'练习基本笔画：横、竖、撇、捺各写一页。'),
(19,16,'临摹《多宝塔碑》选字20个。'),(5,4,'用水彩画出三种渐变色，练习混色技巧。'),
(6,5,'练习考级曲目《小奏鸣曲》第一乐章。'),(7,6,'练习一位到五位的脚位转换。'),
(8,16,'练习中锋行笔，用毛笔写"永"字10遍。'),(9,17,'练习腹式呼吸5分钟/天，学唱《送别》第一段。'),
(10,18,'练习绕口令《四是四，十是十》。'),(11,4,'完成一张石膏几何体组合素描。'),(12,18,'背诵并朗诵《春》选段。');

-- ------------------------------------------------------------
-- 25. 学情记录（30+条）
-- ------------------------------------------------------------

INSERT INTO `learning_record` (lesson_id, student_id, teacher_comment, growth_tag) VALUES
(1,1,'线条运用较流畅，但色彩搭配还需加强。','想象力提升'),(16,1,'风景画进步很大，透视感明显增强。','透视感突破'),
(2,2,'指法正确，节奏感好，左手均匀。','节奏感稳定'),(17,2,'《车尔尼》12条速度稳定，手指独立性有提升。','手指独立性提升'),
(3,3,'基本功扎实，身体协调性好。','协调性优秀'),(4,3,'握笔姿势仍有待改进。','需加强笔画基础'),
(4,4,'起笔收笔到位，字迹端正。','书写规范'),(19,3,'笔画出锋明显改善，结构已有进步。','笔画进步明显'),
(19,4,'临摹《多宝塔碑》有模有样。','临帖表现优秀'),(5,1,'混色大胆有创意，水分控制还需练习。','色彩感强'),
(5,5,'渐变过渡自然，控水能力好。','控水能力佳'),(20,1,'水分控制明显改善。','控水进步'),
(20,5,'本次作业完成度很高。','细节表现力佳'),(6,6,'音阶练习规范，考级曲目完成度高。','音乐表现力'),
(21,6,'快速跑动段落偶有卡顿，需拆分慢练。','考级曲目攻坚中'),(7,7,'柔韧性好，基本体态优雅。','体态优雅'),
(22,7,'脚位转换进步明显。','脚位转换进步'),(8,8,'中锋行笔较为稳定。','基本功扎实'),
(23,8,'"永字八法"各笔画要领掌握良好。','领悟力突出'),(9,9,'音准好，气息不够稳定。','音准优秀'),
(24,9,'气息控制有进步。','气息控制提升'),(10,10,'语言流畅，有主持天赋。','语言天赋突出'),
(11,4,'造型能力较强。','造型感好'),(11,11,'初学者表现不错，透视理解正确。','零基础快速入门'),
(26,4,'石膏几何体素描完成度高。','明暗处理进步'),(26,11,'透视线和明暗交界线都画对了。','透视理解正确'),
(12,12,'声音洪亮有感情，表演欲望强。','表现力强'),(27,12,'朗诵节奏感好。','节奏感提升');

-- ------------------------------------------------------------
-- 26. 教室预约（10条）
-- ------------------------------------------------------------

INSERT INTO `room_booking` (classroom_id, start_time, end_time, purpose, applicant_id) VALUES
(6,'2026-07-18 09:00:00','2026-07-18 12:00:00','暑期家长开放日活动',2),
(6,'2026-07-25 14:00:00','2026-07-25 17:00:00','教师教学研讨会',12),
(3,'2026-08-01 09:00:00','2026-08-01 12:00:00','舞蹈考级模拟考试',6),
(9,'2026-07-20 14:00:00','2026-07-20 16:00:00','小主持人选拔赛',18),
(2,'2026-07-23 09:00:00','2026-07-23 11:00:00','钢琴学员汇报演出彩排',5),
(7,'2026-07-30 14:00:00','2026-07-30 17:00:00','书法作品展布置',16),
(1,'2026-08-05 09:00:00','2026-08-05 11:00:00','美术写生体验课',4),
(8,'2026-07-29 14:00:00','2026-07-29 16:00:00','声乐公开课',17),
(6,'2026-08-08 09:00:00','2026-08-08 12:00:00','暑期结课汇报演出',2),
(3,'2026-08-10 14:00:00','2026-08-10 17:00:00','舞蹈暑期集训',6);

-- ------------------------------------------------------------
-- 27. 调课申请（10条）
-- ------------------------------------------------------------

INSERT INTO `schedule_adjust_request` (lesson_id, applicant_id, reason, expect_time, status, auditor_id, audit_remark) VALUES
(16,4,'临时有事需外出','2026-07-19 10:00:00',3,2,'请自行与其他教师协调换课'),
(17,5,'身体不适，申请调课','2026-07-21 14:00:00',2,12,'同意调至周二下午'),
(28,4,'需参加教研活动','2026-07-27 10:00:00',1,NULL,NULL),
(31,16,'家中有急事','2026-07-28 09:00:00',2,13,'同意，已通知学员'),
(33,5,'考级前加课申请','2026-07-29 16:00:00',1,NULL,NULL),
(36,17,'声音沙哑无法上课','2026-07-31 14:00:00',2,2,'同意调至周五'),
(38,4,'暑期写生外出冲突','2026-08-01 14:00:00',1,NULL,NULL),
(29,5,'与个人课程冲突','2026-07-28 16:00:00',3,12,'无法协调，建议保持原时间'),
(22,6,'教室设备故障','2026-07-23 09:00:00',2,13,'已安排备用教室'),
(34,6,'学员请假人数过多','2026-07-30 09:30:00',1,NULL,NULL);

-- ------------------------------------------------------------
-- 28. 统计快照（12条）
-- ------------------------------------------------------------

INSERT INTO `statistics_snapshot` (stat_date, stat_type, stat_value, extra_json) VALUES
('2026-07-10','student_total',12,'{"active": 12, "new": 9}'),
('2026-07-10','course_total',12,'{"categories": 6}'),
('2026-07-10','class_total',12,'{"open": 12, "max_capacity": 180}'),
('2026-07-17','enrollment_count',15,'{"completed": 15, "pending": 0}'),
('2026-07-17','revenue',37200.00,'{"payment_count": 15, "avg_amount": 2480.00}'),
('2026-07-17','attendance_rate',91.67,'{"total": 24, "present": 20, "late": 1, "leave": 1, "absent": 1}'),
('2026-07-24','attendance_rate',91.67,'{"total": 24, "present": 20, "late": 1, "leave": 1, "absent": 1}'),
('2026-07-17','teacher_workload',24,'{"teacher_4":6,"teacher_5":4,"teacher_6":4,"teacher_16":4,"teacher_17":2,"teacher_18":4}'),
('2026-07-24','teacher_workload',36,'{"teacher_4":9,"teacher_5":6,"teacher_6":6,"teacher_16":6,"teacher_17":3,"teacher_18":6}'),
('2026-07-10','exam_signup',15,'{"art":4,"piano":2,"dance":3,"calligraphy":3,"vocal":1,"host":2}'),
('2026-07-10','refund_total',10,'{"pending": 3, "approved": 6, "rejected": 1}'),
('2026-06-30','revenue',32000.00,'{"payment_count": 12, "month": "2026-06"}');

-- ------------------------------------------------------------
-- 29. 操作日志（15条）
-- ------------------------------------------------------------

INSERT INTO `operation_log` (operator_id, module, operation, ip, create_time) VALUES
(1,'user','创建教务账号 edu2','192.168.1.100','2026-07-08 09:00:00'),
(1,'user','创建财务账号 finance2','192.168.1.100','2026-07-08 09:05:00'),
(2,'course','新增课程：少儿水彩画班','192.168.1.101','2026-07-08 10:00:00'),
(2,'course','新增课程：芭蕾舞基础班','192.168.1.101','2026-07-08 10:10:00'),
(2,'class','创建班级：书法暑假D班','192.168.1.101','2026-07-08 14:00:00'),
(2,'class','创建班级：水彩画暑假E班','192.168.1.101','2026-07-08 14:15:00'),
(2,'enrollment','审核通过报名ID=4','192.168.1.101','2026-07-09 09:00:00'),
(12,'enrollment','审核通过报名ID=6','192.168.1.102','2026-07-09 09:30:00'),
(13,'enrollment','审核通过报名ID=12','192.168.1.103','2026-07-09 10:00:00'),
(3,'finance','收费确认 payment_id=1（2400元）','192.168.1.104','2026-07-08 10:00:00'),
(14,'finance','收费确认 payment_id=5（2000元）','192.168.1.105','2026-07-09 09:30:00'),
(15,'finance','收费确认 payment_id=7（2800元）','192.168.1.106','2026-07-10 09:00:00'),
(2,'exam','添加考级项目：钢琴考级三级','192.168.1.101','2026-07-10 08:00:00'),
(2,'notice','发布公告：暑期课程安排通知','192.168.1.101','2026-07-09 09:00:00'),
(1,'system','系统初始化完成','192.168.1.100','2026-07-08 08:00:00');

-- ------------------------------------------------------------
-- 30. 系统菜单（23条）
-- ------------------------------------------------------------

INSERT INTO `sys_menu` (id, parent_id, menu_name, icon, path, permission_code, sort_order) VALUES
(1,0,'运营看板','Monitor','/admin/dashboard','menu:dashboard',1),
(2,0,'系统管理','Setting',NULL,'menu:user',2),
(3,0,'教务管理','Document',NULL,'menu:student',3),
(4,0,'财务管理','Money',NULL,'menu:payment',4),
(5,2,'用户管理',NULL,'/admin/users','menu:user',1),
(6,2,'角色管理',NULL,'/admin/roles','menu:role',2),
(8,2,'菜单管理',NULL,'/admin/menus','menu:menu',3),
(9,2,'机构配置',NULL,'/admin/organization','menu:organization',4),
(10,2,'公告管理',NULL,'/admin/notices','menu:notice',5),
(11,2,'操作日志',NULL,'/admin/logs','menu:log',6),
(12,3,'学员管理',NULL,'/edu/students','menu:student',1),
(13,3,'课程管理',NULL,'/edu/courses','menu:course',2),
(14,3,'班级管理',NULL,'/edu/classes','menu:class',3),
(15,3,'报名管理',NULL,'/edu/enrollments','menu:enrollment',4),
(16,3,'排课管理',NULL,'/edu/schedules','menu:schedule',5),
(17,3,'教室管理',NULL,'/edu/classrooms','menu:classroom',6),
(18,3,'考勤管理',NULL,'/edu/attendances','menu:attendance',7),
(19,3,'考级管理',NULL,'/edu/exams','menu:exam',8),
(20,4,'收费管理',NULL,'/finance/payments','menu:payment',1),
(21,4,'退费管理',NULL,'/finance/refunds','menu:refund',2),
(22,4,'课时账户',NULL,'/finance/lesson-accounts','menu:lesson-flow',3),
(23,4,'课时流水',NULL,'/finance/lesson-flows','menu:lesson-flow',4),
(24,4,'薪资管理',NULL,'/finance/salaries','menu:salary',5);
