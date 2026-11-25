-- 艺培通核心数据库关系图生成专用 DDL（精简版）
-- 仅保留 8 张最能表达答辩主线的表：招生、课程、班级、课次、考勤、课时、收费。
-- 用途：导入 ER 图生成工具；不用于生产建库。

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

CREATE TABLE `class_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '班级ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `teacher_name` VARCHAR(50) NOT NULL COMMENT '授课教师',
    `class_name` VARCHAR(100) NOT NULL COMMENT '班级名称',
    `max_student_count` INT NOT NULL COMMENT '班级容量',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_class_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE = InnoDB COMMENT = '班级';

CREATE TABLE `enrollment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报名ID',
    `student_id` BIGINT NOT NULL COMMENT '学员ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `class_id` BIGINT NULL COMMENT '意向班级ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '报名状态',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_enrollment_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_enrollment_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
    CONSTRAINT `fk_enrollment_class` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`)
) ENGINE = InnoDB COMMENT = '报名申请';

CREATE TABLE `schedule_lesson` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课次ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `lesson_date` DATE NOT NULL COMMENT '上课日期',
    `start_time` TIME NOT NULL COMMENT '开始时间',
    `end_time` TIME NOT NULL COMMENT '结束时间',
    `classroom_name` VARCHAR(50) NOT NULL COMMENT '教室',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_lesson_class` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`)
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
