-- ============================================================
-- 补全菜单管理 - 大课表
-- 日期：2026-07-21
-- 说明：将之前在 AppLayout.vue 硬编码的「大课表」菜单正式纳入 sys_menu 管理
--       使用独立权限码 menu:big-schedule，与排课管理 (menu:schedule) 解耦
-- 适用：已存在数据库的迁移；新装环境 data.sql 已包含，无须执行
-- ============================================================

USE edu_admin;

SET NAMES utf8mb4;

-- 1. 注册新权限码
INSERT IGNORE INTO `permission` (permission_code, path, type) VALUES
('menu:big-schedule', '/edu/big-schedule', 1);

-- 2. 授权给相关角色（SUPER_ADMIN 已通过 SELECT 拿全所有权限，这里手工补登）
INSERT IGNORE INTO `role_permission` (role_code, permission_code) VALUES
('SUPER_ADMIN', 'menu:big-schedule'),
('EDU_ADMIN',   'menu:big-schedule');

-- 3. 注册菜单节点（id=25 紧跟 报名管理 id=15 之后）
INSERT IGNORE INTO `sys_menu` (id, parent_id, menu_name, icon, path, permission_code, sort_order, visible, is_deleted) VALUES
(25, 3, '大课表', NULL, '/edu/big-schedule', 'menu:big-schedule', 5, 1, 0);

-- 4. 重新排序：让大课表 (5) 出现在报名管理(4) 和排课管理(原5) 之间
UPDATE `sys_menu` SET sort_order = 6 WHERE id = 16 AND menu_name = '排课管理';
UPDATE `sys_menu` SET sort_order = 7 WHERE id = 17 AND menu_name = '教室管理';
UPDATE `sys_menu` SET sort_order = 8 WHERE id = 18 AND menu_name = '考勤管理';
UPDATE `sys_menu` SET sort_order = 9 WHERE id = 19 AND menu_name = '考级管理';
