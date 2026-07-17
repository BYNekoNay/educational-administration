-- ============================================================
-- 艺术培训机构全流程教务管理平台 - 数据库建表脚本
-- 版本：v0.9（与 docs/10-数据库规范.md 保持一致）
-- 字符集：utf8mb4，存储引擎：InnoDB
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
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0 COMMENT 'Token 版本号，禁用/角色变更时递增'
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

DROP TABLE IF EXISTS `leave_request`;
CREATE TABLE `leave_request` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  parent_user_id BIGINT NOT NULL,
  lesson_date DATE NOT NULL,
  schedule_id BIGINT COMMENT '关联课次ID（选填）',
  reason VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1-待审核，2-已通过，3-已拒绝',
  audit_user_id BIGINT,
  audit_remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_student_id (student_id),
  INDEX idx_parent_user_id (parent_user_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假申请';

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

DROP TABLE IF EXISTS `teacher_course`;
CREATE TABLE `teacher_course` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '教师用户ID',
  course_id BIGINT NOT NULL COMMENT '可授课程ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_course (user_id, course_id),
  INDEX idx_user_id (user_id),
  INDEX idx_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师可授课程关联表';

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
-- 8. 机构配置（超级管理员维护，单行/少量配置项表）
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
