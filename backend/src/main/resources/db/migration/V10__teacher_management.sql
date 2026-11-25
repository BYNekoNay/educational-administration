-- ============================================================
-- V10: 教师管理改造
--   1) 加 7 列（教师生命周期字段 + 操作日志结构化字段）
--   2) 加 1 索引（按对象反查审计）
--   3) 种子：menu:teacher 菜单 + 角色授权
--
-- 【为什么全部幂等】
--   MySQL 的 ALTER TABLE 是隐式提交、无法回滚。若本脚本中途失败，
--   已执行的 DDL 会留下，而 flyway_schema_history 仍停在 9；下次启动
--   重跑时会因 "Duplicate column name" 永久卡住，必须人工进库处理。
--   所以每一步都必须可重复执行。
--
-- 【为什么 7 列各写一个独立块，不合并成一条 ALTER】
--   合并后只要其中任一列已存在，整条 ALTER 就会被 EXISTS 判断跳过，
--   其余列永久缺失，且不报错。
-- ============================================================

-- ------------------------------------------------------------
-- 1. user：三列（强制改密 / 入职日 / 离职生效日）
-- ------------------------------------------------------------

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'user'
        AND COLUMN_NAME = 'must_change_password'
    ),
    'SELECT 1',
    'ALTER TABLE `user` ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT ''1-需强制修改密码（密码非本人设置）'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'user'
        AND COLUMN_NAME = 'hire_date'
    ),
    'SELECT 1',
    'ALTER TABLE `user` ADD COLUMN hire_date DATE NULL COMMENT ''入职日期（展示字段，缺失时可标注待核对）'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'user'
        AND COLUMN_NAME = 'leave_date'
    ),
    'SELECT 1',
    'ALTER TABLE `user` ADD COLUMN leave_date DATE NULL COMMENT ''离职生效日，NULL=在职；<=今天 视为已离职'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. class_student：转出时点（判"当时是否在班"）
-- ------------------------------------------------------------

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'class_student'
        AND COLUMN_NAME = 'leave_time'
    ),
    'SELECT 1',
    'ALTER TABLE class_student ADD COLUMN leave_time DATETIME NULL COMMENT ''转出/退出时间，用于还原历史在班状态'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. operation_log：结构化审计字段
-- ------------------------------------------------------------

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_log'
        AND COLUMN_NAME = 'target_type'
    ),
    'SELECT 1',
    'ALTER TABLE operation_log ADD COLUMN target_type VARCHAR(30) NULL COMMENT ''操作对象类型，如 STUDENT/USER'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_log'
        AND COLUMN_NAME = 'target_id'
    ),
    'SELECT 1',
    'ALTER TABLE operation_log ADD COLUMN target_id BIGINT NULL COMMENT ''操作对象ID，用于按对象反查谁看过'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_log'
        AND COLUMN_NAME = 'hit_count'
    ),
    'SELECT 1',
    'ALTER TABLE operation_log ADD COLUMN hit_count INT NOT NULL DEFAULT 1 COMMENT ''同人同对象同日聚合次数'''
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. 索引：按被操作对象反查
--    （idx_operator_time 已存在，不重复创建）
-- ------------------------------------------------------------

SET @ddl = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_log'
        AND INDEX_NAME = 'idx_operation_target'
    ),
    'SELECT 1',
    'CREATE INDEX idx_operation_target ON operation_log (target_type, target_id, create_time)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5. 种子数据：教师管理菜单
--
-- 顺序不可调换：permission -> sys_menu -> role_permission
--   role_permission 引用 permission_code，顺序反了会插出指向
--   不存在权限码的授权行。
--
-- 注意 sys_menu 不能用 INSERT ... WHERE NOT EXISTS 子查询，
-- 因为 MySQL 禁止在 INSERT 的目标表上做子查询（ERROR 1093）。
-- 这里改为"先取计数到变量，再用 PREPARE 执行"，与本脚本其余部分
-- 保持同一套幂等风格。
-- ------------------------------------------------------------

INSERT IGNORE INTO permission (permission_code, path, type)
VALUES ('menu:teacher', '/edu/teachers', 1);

SET @menu_exists := (
  SELECT COUNT(*) FROM sys_menu WHERE permission_code = 'menu:teacher'
);
SET @ddl := (
  SELECT IF(
    @menu_exists > 0,
    'SELECT 1',
    'INSERT INTO sys_menu (parent_id, menu_name, icon, path, permission_code, sort_order) VALUES (3, ''教师管理'', ''User'', ''/edu/teachers'', ''menu:teacher'', 11)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO role_permission (role_code, permission_code)
VALUES ('SUPER_ADMIN', 'menu:teacher'), ('EDU_ADMIN', 'menu:teacher');
