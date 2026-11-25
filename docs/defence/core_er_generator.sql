-- 艺培通核心数据库关系图生成专用 DDL
-- 用途：导入 ER 图生成工具，展示核心实体关系。
-- 注意：这是可视化副本，不用于生产建库；生产 schema.sql 继续采用应用层引用校验，不启用物理外键。

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `real_name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `role_code` VARCHAR(30) NOT NULL COMMENT '角色编码',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = '用户账号';

CREATE TABLE `student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '学员ID',
    `name` VARCHAR(50) NOT NULL COMMENT '学员姓名',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = '学员档案';

CREATE TABLE `course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程ID',
    `name` VARCHAR(100) NOT NULL COMMENT '课程名称',
    `total_lessons` INT NOT NULL COMMENT '总课时',
    `price` DECIMAL(10,2) NOT NULL COMMENT '课程价格',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = '课程类型/套餐';

CREATE TABLE `classroom` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '教室ID',
    `name` VARCHAR(50) NOT NULL COMMENT '教室名称',
    `capacity` INT NOT NULL COMMENT '容纳人数',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = '教室';

CREATE TABLE `class_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '班级ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `teacher_id` BIGINT NOT NULL COMMENT '授课教师ID',
    `class_name` VARCHAR(100) NOT NULL COMMENT '班级名称',
    `max_student_count` INT NOT NULL COMMENT '班级容量',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_class_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
    CONSTRAINT `fk_class_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB COMMENT = '班级';

CREATE TABLE `class_student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-在班 2-转出 3-退出',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_student` (`class_id`, `student_id`),
    CONSTRAINT `fk_class_student_class` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`),
    CONSTRAINT `fk_class_student_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE = InnoDB COMMENT = '班级学员关系';

CREATE TABLE `enrollment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报名ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `parent_user_id` BIGINT NOT NULL COMMENT '报名家长ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `class_id` BIGINT NULL COMMENT '意向班级ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '报名状态',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_enrollment_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_enrollment_parent` FOREIGN KEY (`parent_user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_enrollment_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
    CONSTRAINT `fk_enrollment_class` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`)
) ENGINE = InnoDB COMMENT = '报名申请';

CREATE TABLE `schedule_lesson` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课次ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `teacher_id` BIGINT NOT NULL COMMENT '授课教师ID',
    `classroom_id` BIGINT NOT NULL COMMENT '教室ID',
    `lesson_date` DATE NOT NULL COMMENT '上课日期',
    `start_time` TIME NOT NULL COMMENT '开始时间',
    `end_time` TIME NOT NULL COMMENT '结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '课次状态',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_lesson_class` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`),
    CONSTRAINT `fk_lesson_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_lesson_classroom` FOREIGN KEY (`classroom_id`) REFERENCES `classroom` (`id`)
) ENGINE = InnoDB COMMENT = '课次';

CREATE TABLE `attendance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '考勤ID',
    `lesson_id` BIGINT NOT NULL COMMENT '课次ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `status` TINYINT NOT NULL COMMENT '1-到课 2-迟到 3-请假 4-缺勤',
    `deduct_lessons` DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '扣减课时',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lesson_student` (`lesson_id`, `student_id`),
    CONSTRAINT `fk_attendance_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `schedule_lesson` (`id`),
    CONSTRAINT `fk_attendance_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE = InnoDB COMMENT = '考勤';

CREATE TABLE `lesson_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课时账户ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `total_lessons` DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '总课时',
    `remaining_lessons` DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '剩余课时',
    `expire_date` DATE NULL COMMENT '到期日期',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_course` (`student_id`, `course_id`),
    CONSTRAINT `fk_account_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_account_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE = InnoDB COMMENT = '课时账户';

CREATE TABLE `lesson_flow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    `account_id` BIGINT NOT NULL COMMENT '课时账户ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `lesson_id` BIGINT NULL COMMENT '课次ID',
    `change_type` TINYINT NOT NULL COMMENT '1-增加 2-消耗 3-退费 4-调整',
    `change_amount` DECIMAL(6,2) NOT NULL COMMENT '变动课时',
    `before_balance` DECIMAL(6,2) NOT NULL COMMENT '变动前余额',
    `after_balance` DECIMAL(6,2) NOT NULL COMMENT '变动后余额',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_flow_account` FOREIGN KEY (`account_id`) REFERENCES `lesson_account` (`id`),
    CONSTRAINT `fk_flow_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_flow_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `schedule_lesson` (`id`)
) ENGINE = InnoDB COMMENT = '课时流水';

CREATE TABLE `payment_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '收费记录ID',
    `enrollment_id` BIGINT NOT NULL COMMENT '报名ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `lesson_count` DECIMAL(6,2) NOT NULL COMMENT '购买课时',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '收费金额',
    `pay_time` DATETIME NOT NULL COMMENT '缴费时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_payment_enrollment` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollment` (`id`),
    CONSTRAINT `fk_payment_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_payment_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE = InnoDB COMMENT = '收费记录';

CREATE TABLE `refund_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '退费记录ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `payment_record_id` BIGINT NOT NULL COMMENT '原收费记录ID',
    `enrollment_id` BIGINT NOT NULL COMMENT '报名ID',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '退费金额',
    `lesson_count` DECIMAL(6,2) NOT NULL COMMENT '退费课时',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '退费状态',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_refund_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_refund_payment` FOREIGN KEY (`payment_record_id`) REFERENCES `payment_record` (`id`),
    CONSTRAINT `fk_refund_enrollment` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollment` (`id`)
) ENGINE = InnoDB COMMENT = '退费记录';
