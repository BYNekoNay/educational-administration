-- ============================================================
-- 菜单与权限元数据完整补种
-- 日期：2026-07-21
-- 说明：将所有权限码、菜单树、角色授权用 INSERT IGNORE 安全补全。
--       可对已有数据库重复执行，不会覆盖已有数据。
-- 适用：本地开发库 / 已有生产库 / 菜单数据不完整的任何环境。
-- ============================================================

USE edu_admin;
SET NAMES utf8mb4;

-- ============================================================
-- 1. 权限码（permission）
-- ============================================================
INSERT IGNORE INTO `permission` (permission_code, path, type) VALUES
('menu:dashboard',    '/admin/dashboard',     1),
('menu:user',         '/admin/users',          1),
('menu:role',         '/admin/roles',          1),
('menu:permission',   '/admin/permissions',    1),
('menu:menu',         '/admin/menus',          1),
('menu:organization', '/admin/organization',   1),
('menu:notice',       '/admin/notices',        1),
('menu:log',          '/admin/logs',           1),
('menu:student',      '/edu/students',         1),
('menu:course',       '/edu/courses',          1),
('menu:class',        '/edu/classes',          1),
('menu:enrollment',   '/edu/enrollments',      1),
('menu:schedule',     '/edu/schedules',        1),
('menu:big-schedule', '/edu/big-schedule',     1),
('menu:adjust',       '/edu/adjust',           1),
('menu:classroom',    '/edu/classrooms',       1),
('menu:attendance',   '/edu/attendances',      1),
('menu:exam',         '/edu/exams',            1),
('menu:payment',      '/finance/payments',     1),
('menu:refund',       '/finance/refunds',      1),
('menu:lesson-flow',  '/finance/lesson-flows', 1),
('menu:salary',       '/finance/salaries',     1),
('menu:revenue',      '/finance/revenue',      1);

-- ============================================================
-- 2. 菜单树（sys_menu）
-- ============================================================
INSERT IGNORE INTO `sys_menu` (id, parent_id, menu_name, icon, path, permission_code, sort_order, visible) VALUES
-- 顶级分组（无路由 = 纯目录）
(1,  0, '运营看板', 'Monitor',  '/admin/dashboard',     'menu:dashboard',    1, 1),
(2,  0, '系统管理', 'Setting',  NULL,                   'menu:user',         2, 1),
(3,  0, '教务管理', 'Document', NULL,                   'menu:student',      3, 1),
(4,  0, '财务管理', 'Money',    NULL,                   'menu:payment',      4, 1),
-- 系统管理子菜单
(5,  2, '用户管理',   NULL, '/admin/users',        'menu:user',         1, 1),
(6,  2, '角色管理',   NULL, '/admin/roles',        'menu:role',         2, 1),
(8,  2, '菜单管理',   NULL, '/admin/menus',        'menu:menu',         3, 1),
(9,  2, '机构配置',   NULL, '/admin/organization', 'menu:organization', 4, 1),
(10, 2, '公告管理',   NULL, '/admin/notices',      'menu:notice',       5, 1),
(11, 2, '操作日志',   NULL, '/admin/logs',         'menu:log',          6, 1),
-- 教务管理子菜单
(12, 3, '学员管理', NULL, '/edu/students',    'menu:student',     1, 1),
(13, 3, '课程管理', NULL, '/edu/courses',     'menu:course',      2, 1),
(14, 3, '班级管理', NULL, '/edu/classes',     'menu:class',       3, 1),
(15, 3, '报名管理', NULL, '/edu/enrollments', 'menu:enrollment',  4, 1),
(25, 3, '大课表',   NULL, '/edu/big-schedule','menu:big-schedule',5, 1),
(16, 3, '排课管理', NULL, '/edu/schedules',   'menu:schedule',    6, 1),
(17, 3, '教室管理', NULL, '/edu/classrooms',  'menu:classroom',   7, 1),
(18, 3, '考勤管理', NULL, '/edu/attendances', 'menu:attendance',  8, 1),
(19, 3, '考级管理', NULL, '/edu/exams',       'menu:exam',        9, 1),
-- 财务管理子菜单
(20, 4, '收费管理', NULL, '/finance/payments',       'menu:payment',     1, 1),
(21, 4, '退费管理', NULL, '/finance/refunds',        'menu:refund',      2, 1),
(22, 4, '课时账户', NULL, '/finance/lesson-accounts', 'menu:lesson-flow', 3, 1),
(23, 4, '课时流水', NULL, '/finance/lesson-flows',   'menu:lesson-flow', 4, 1),
(24, 4, '薪资管理', NULL, '/finance/salaries',       'menu:salary',      5, 1);

-- ============================================================
-- 3. 角色授权（role_permission）
-- ============================================================

-- SUPER_ADMIN：拥有全部权限
INSERT IGNORE INTO `role_permission` (role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code FROM `permission`;

-- EDU_ADMIN：教务相关
INSERT IGNORE INTO `role_permission` (role_code, permission_code) VALUES
('EDU_ADMIN', 'menu:dashboard'),
('EDU_ADMIN', 'menu:student'),
('EDU_ADMIN', 'menu:course'),
('EDU_ADMIN', 'menu:class'),
('EDU_ADMIN', 'menu:enrollment'),
('EDU_ADMIN', 'menu:schedule'),
('EDU_ADMIN', 'menu:big-schedule'),
('EDU_ADMIN', 'menu:adjust'),
('EDU_ADMIN', 'menu:classroom'),
('EDU_ADMIN', 'menu:attendance'),
('EDU_ADMIN', 'menu:exam'),
('EDU_ADMIN', 'menu:notice');

-- FINANCE：财务相关
INSERT IGNORE INTO `role_permission` (role_code, permission_code) VALUES
('FINANCE', 'menu:dashboard'),
('FINANCE', 'menu:payment'),
('FINANCE', 'menu:refund'),
('FINANCE', 'menu:lesson-flow'),
('FINANCE', 'menu:salary'),
('FINANCE', 'menu:revenue');

-- TEACHER：课表+考勤
INSERT IGNORE INTO `role_permission` (role_code, permission_code) VALUES
('TEACHER', 'menu:dashboard'),
('TEACHER', 'menu:schedule'),
('TEACHER', 'menu:classroom'),
('TEACHER', 'menu:attendance');

-- PARENT：学员信息+缴费+课表
INSERT IGNORE INTO `role_permission` (role_code, permission_code) VALUES
('PARENT', 'menu:dashboard'),
('PARENT', 'menu:student'),
('PARENT', 'menu:schedule'),
('PARENT', 'menu:payment'),
('PARENT', 'menu:refund'),
('PARENT', 'menu:lesson-flow');

-- ============================================================
-- 4. 修复 sort_order（INSERT IGNORE 不更新已存在行）
--    → 确保 大课表(5) 出现在 排课管理(6) 之前
-- ============================================================
UPDATE `sys_menu` SET sort_order = 6 WHERE id = 16;
UPDATE `sys_menu` SET sort_order = 7 WHERE id = 17;
UPDATE `sys_menu` SET sort_order = 8 WHERE id = 18;
UPDATE `sys_menu` SET sort_order = 9 WHERE id = 19;
