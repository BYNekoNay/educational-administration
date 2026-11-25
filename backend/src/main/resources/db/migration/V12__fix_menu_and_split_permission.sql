-- ============================================================
-- V12：修复「教师管理」菜单乱码 + 拆分复用权限码
--
-- 背景一（乱码）：
--   V10__teacher_management.sql 缺 SET NAMES utf8mb4，'教师管理' 经非 UTF-8 连接写入，
--   库内成为 Windows-1252 双重编码（HEX 27 字节 / 12 字符，正常应为 12 字节 / 4 字符）。
--   注意：不能回头改 V10，application-prod.yml 的 validate-on-migrate=true 会因
--   checksum 变化导致启动失败，只能前向修数据。
--
-- 背景二（权限码复用）：
--   id=2 系统管理 / id=5 用户管理 都是 menu:user
--   id=3 教务管理 / id=12 学员管理 都是 menu:student
--   id=22 课时账户 / id=23 课时流水 都是 menu:lesson-flow
--   → 无法单独授权。本迁移给前两者各拆一个顶级目录码，课时账户改用 menu:lesson-account。
--
-- 幂等：全部 UPDATE（按唯一键定位）+ INSERT IGNORE，可重复执行。
-- ============================================================

SET NAMES utf8mb4;

-- ① 修复乱码（按权限码定位，不硬编码 id）
UPDATE sys_menu SET menu_name = '教师管理' WHERE permission_code = 'menu:teacher';

-- ② 新增权限码（permission_code 有唯一键，INSERT IGNORE 幂等）
INSERT IGNORE INTO permission (permission_code, path, type) VALUES
  ('menu:system',         '/admin/system',            1),
  ('menu:edu',            '/edu',                     1),
  ('menu:lesson-account', '/finance/lesson-accounts', 1);

-- ③ 顶级目录与课时账户改用新码
--    目录节点由 AuthService.getMyMenus 的「祖先保留」逻辑兜底（只要子菜单可见即保留父目录），
--    无需为 menu:system / menu:edu 补 role_permission
UPDATE sys_menu SET permission_code = 'menu:system'         WHERE id = 2;
UPDATE sys_menu SET permission_code = 'menu:edu'            WHERE id = 3;
UPDATE sys_menu SET permission_code = 'menu:lesson-account' WHERE id = 22;

-- ④ 补授权：财务需要独立的「课时账户」权限（原来与课时流水共用 menu:lesson-flow）
INSERT IGNORE INTO role_permission (role_code, permission_code) VALUES
  ('FINANCE', 'menu:lesson-account');

-- 执行后人工校验：
--   SELECT HEX(menu_name), CHAR_LENGTH(menu_name) FROM sys_menu WHERE permission_code='menu:teacher';
--     → 期望 CHAR_LENGTH=4，HEX=E69599E5B888E7AEA1E79086
--   SELECT id, menu_name, permission_code FROM sys_menu WHERE id IN (2,3,22,23);
--     → 期望 22 为 menu:lesson-account，2 为 menu:system，3 为 menu:edu，23 仍为 menu:lesson-flow
