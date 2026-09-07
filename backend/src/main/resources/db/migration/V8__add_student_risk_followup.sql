-- V8: 新增学员流失预警跟进状态表（决策 D5）
-- 用于 P4「流失预警待办」：教务对预警学员勾选"已跟进/暂不跟进"形成管理闭环。
-- 幂等写法：探测表不存在才执行 CREATE，与既有 V6/V7 的 information_schema + PREPARE 模式一致。

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'student_risk_followup'
    ),
    'SELECT 1',
    'CREATE TABLE `student_risk_followup` (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      student_id BIGINT NOT NULL COMMENT ''学员ID'',
      status TINYINT NOT NULL DEFAULT 0 COMMENT ''0-待跟进，1-已跟进，2-暂不跟进'',
      remark VARCHAR(255) COMMENT ''跟进备注'',
      operator_id BIGINT COMMENT ''操作人(教务/超管)ID'',
      followup_time DATETIME COMMENT ''最近跟进时间'',
      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      UNIQUE KEY uk_student (student_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''流失预警跟进状态'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
