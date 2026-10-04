-- V9: 第三轮性能审查点名的三条缺失索引（M1/M2/M3）。
--
-- 为什么需要：这三条均是本轮 SQL 审查中被确认为"已有索引无法覆盖"的真实查询模式，
-- 而非推测性加索引——每条都对应下面标注的具体调用点：
--   M1 schedule_lesson(lesson_date, status)
--      请假匹配（LeaveRequestServiceImpl: 按请假日期找当日课次）与考勤/范围查询以
--      日期为首要过滤条件；现有 idx_teacher_time / idx_classroom_time / idx_class_time
--      首列均为 ID，无法按日期范围走索引，只能全表扫。
--   M2 payment_record(course_id)
--      课程删除前校验（CourseServiceImpl: count by course_id）与课程维度的收入统计
--      （StatisticsServiceImpl: in(course_id)）；该表此前无任何以 course_id 打头的索引。
--   M3 class_student(status)
--      6+ 处以"在班学员(status=1)"为过滤条件的查询；现有索引为 (student_id, status)，
--      首列在前、status 单独做谓词时无法命中。
--
-- 幂等写法沿用 V6：先查 information_schema 判断索引是否已存在，仅缺失时才执行 CREATE，
-- 保证该脚本可在历史库（已基线化到 V8）与安全 planners 下重复运行。
-- 注意：sql/schema.sql 已同步内建这三条索引，新装环境无需依赖本迁移。

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'schedule_lesson'
        AND INDEX_NAME = 'idx_lesson_date_status'
    ),
    'SELECT 1',
    'CREATE INDEX idx_lesson_date_status ON schedule_lesson (lesson_date, status)'
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
        AND INDEX_NAME = 'idx_course_deleted_time'
    ),
    'SELECT 1',
    'CREATE INDEX idx_course_deleted_time ON payment_record (course_id, is_deleted, pay_time)'
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
        AND TABLE_NAME = 'class_student'
        AND INDEX_NAME = 'idx_class_status'
    ),
    'SELECT 1',
    'CREATE INDEX idx_class_status ON class_student (class_id, status, is_deleted)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
