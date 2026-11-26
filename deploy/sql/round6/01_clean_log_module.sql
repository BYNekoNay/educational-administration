SET NAMES utf8mb4;

-- ============ A1 日志历史英文 module 清洗 ============
CREATE TABLE IF NOT EXISTS bak_operation_log_20261126 AS SELECT * FROM operation_log;

-- 先查：当前分布（人工核对映射覆盖，出现映射外英文 code 则中止）
SELECT module, COUNT(*) FROM operation_log GROUP BY module ORDER BY COUNT(*) DESC;

-- 更新
UPDATE operation_log SET module = CASE module
  WHEN 'enrollment' THEN '报名管理'
  WHEN 'finance'    THEN '财务管理'
  WHEN 'user'       THEN '用户管理'
  WHEN 'course'     THEN '课程管理'
  WHEN 'class'      THEN '班级管理'
  WHEN 'exam'       THEN '考级管理'
  WHEN 'notice'     THEN '公告管理'
  WHEN 'system'     THEN '系统配置'
END
WHERE module REGEXP '^[a-z]+$';

-- 验证
SELECT CONCAT('剩余英文code=', COUNT(*)) AS info FROM operation_log WHERE module REGEXP '^[a-z]+$';
