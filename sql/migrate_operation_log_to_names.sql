-- ============================================================================
-- 操作日志历史数据迁移：ID → 实际名称
-- 此脚本将旧格式的日志记录转换为新格式（学员/课程用真名）
-- 幂等：可重复执行（已迁移的记录 LIKE 匹配会失败，UPDATE 影响 0 行）
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 模式 1: 登记收费(studentId=X,金额=Y,id=Z)
--        → 登记收费（学员=姓名，金额=Y，id=Z）
-- ----------------------------------------------------------------------------
UPDATE operation_log ol
JOIN student s ON s.id = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, 'studentId=', -1), ',', 1) AS UNSIGNED)
SET ol.operation = CONCAT(
    '登记收费（学员=', s.name, '，金额=',
    SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, '金额=', -1), ',', 1),
    '，id=',
    SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, 'id=', -1), ')', 1),
    '）'
)
WHERE ol.operation LIKE '登记收费(studentId=%';

-- ----------------------------------------------------------------------------
-- 模式 2: 审核通过报名(id=X)  -- 通过 enrollment 表关联
--        → 审核通过报名（学员=姓名，课程=课程名，id=X）
-- ----------------------------------------------------------------------------
UPDATE operation_log ol
JOIN enrollment e ON e.id = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, 'id=', -1), ')', 1) AS UNSIGNED)
JOIN student s ON s.id = e.student_id
JOIN course c ON c.id = e.course_id
SET ol.operation = CONCAT(
    '审核通过报名（学员=', s.name, '，课程=', c.name,
    '，id=', SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, 'id=', -1), ')', 1),
    '）'
)
WHERE ol.operation LIKE '审核通过报名(id=%';

-- ----------------------------------------------------------------------------
-- 模式 3: 审核通过报名ID=X（学生X→课程）  -- 旧 seed 数据
--        → 审核通过报名（学员=姓名，课程=课程，id=X）
-- ----------------------------------------------------------------------------
UPDATE operation_log ol
JOIN student s ON s.id = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, 'ID=', -1), '（', 1) AS UNSIGNED)
SET ol.operation = CONCAT(
    '审核通过报名（学员=', s.name, '，课程=',
    SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, '→', -1), '）', 1),
    '，id=', SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, 'ID=', -1), '（', 1),
    '）'
)
WHERE ol.operation LIKE '审核通过报名ID=%';

-- ----------------------------------------------------------------------------
-- 模式 4: 收费确认 payment_id=X（Y元） -- 通过 payment_record 表
--        → 收费确认（学员=姓名，金额=Y）
-- ----------------------------------------------------------------------------
UPDATE operation_log ol
JOIN payment_record p ON p.id = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(ol.operation, 'payment_id=', -1), '（', 1) AS UNSIGNED)
JOIN student s ON s.id = p.student_id
SET ol.operation = CONCAT('收费确认（学员=', s.name, '，金额=', p.amount, '）')
WHERE ol.operation LIKE '收费确认 payment_id=%';

-- ----------------------------------------------------------------------------
-- 验证：迁移后的日志记录
-- ----------------------------------------------------------------------------
SELECT id, operation FROM operation_log ORDER BY id;
