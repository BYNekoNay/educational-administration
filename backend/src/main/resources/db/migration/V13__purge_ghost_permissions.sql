-- ============================================================
-- V13：清理幽灵权限码
--
-- 背景：
--   permission 表 27 条，sys_menu 去重后 25 条，差集正好是
--   menu:permission（权限码管理页已下线）与 menu:revenue（营收统计页已下线）。
--   MenuServiceImpl.delete() 只删 sys_menu、不回收 permission，导致单向堆积。
--
-- 症状（用户实测）：
--   授权树「分配权限—财务管理员」显示「已选 7 / 27」，但页面上只勾得出 6 个；
--   System 模块仍有「权限码管理」、Money 模块仍有「营收统计」，而菜单管理里已无这两项。
--   RoleServiceImpl 是「先删后插」的全量覆盖，幽灵授权会被前端原样提交回去，
--   永远删不掉，且让计数长期虚高。
--
-- 幂等：DELETE 天然幂等，可重复执行。
-- 顺序：先删引用方 role_permission，再删被引用方 permission。
-- ============================================================

SET NAMES utf8mb4;

-- 前置自检：执行前应为 0 行。若不为 0，说明 sys_menu 又启用了这两个页面，需人工确认
-- SELECT id, menu_name, parent_id FROM sys_menu
--  WHERE permission_code IN ('menu:permission', 'menu:revenue');

-- ① 引用方授权（包含逻辑删除的历史行，否则唯一键会挡住未来重建）
DELETE FROM role_permission
 WHERE permission_code IN ('menu:permission', 'menu:revenue');

-- ② 定义表。permission 有 @TableLogic，逻辑删会在表里留下 is_deleted=1 的幽灵行，
--    而 PermissionController 的 list 不过滤——因此必须物理删
DELETE FROM permission
 WHERE permission_code IN ('menu:permission', 'menu:revenue');

-- 后置校验：以下两条都应返回 0 行
-- SELECT permission_code FROM permission WHERE permission_code NOT IN
--   (SELECT DISTINCT permission_code FROM sys_menu
--     WHERE permission_code IS NOT NULL AND permission_code <> '');
-- SELECT DISTINCT permission_code FROM sys_menu
--  WHERE permission_code IS NOT NULL AND permission_code <> ''
--    AND permission_code NOT IN (SELECT permission_code FROM permission);
