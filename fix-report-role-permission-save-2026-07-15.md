# 修复报告：角色权限分配无法更新（2026-07-15）

## 现象
管理后台「系统角色管理 → 分配权限」勾选权限后点击「保存权限」，前端提示成功但实际未持久化；后端返回 `409 数据已存在，请勿重复提交`。

## 根因
`role_permission` 关联表同时存在：
- `@TableLogic`（MyBatis-Plus 逻辑删除 → `is_deleted=1`）
- `UNIQUE KEY (role_code, permission_code)` 唯一约束

`RoleServiceImpl.updateRolePermissions` 的"先清后建"流程：
1. `delete(eq(roleCode))` → 实际是 `UPDATE ... SET is_deleted=1 WHERE is_deleted=0 AND role_code=?`
2. `insert(...)` → 22 次 INSERT

**问题**：在第 1 步的 UPDATE 中，被更新的行（要变成 `is_deleted=1`）会与表中**已存在**的 `is_deleted=1` 同 (role_code, permission_code) 旧逻辑删除行撞键，MySQL 在 UPDATE 阶段就抛 `Duplicate entry`，整事务回滚，第 2 步的 INSERT 全部不执行 → 旧数据看似未动。

数据库验证：
```
UPDATE role_permission SET is_deleted=1 WHERE role_code='SUPER_ADMIN' AND is_deleted=0;
ERROR 1062 (23000): Duplicate entry 'SUPER_ADMIN-menu:dashboard-1' for key 'uk_role_permission'
```

## 修复方案
**真删 + 实体保留 @TableLogic**。关联表是纯关系数据，无业务保留价值，用物理删除避免与唯一键纠缠。

### 变更
1. **`RolePermissionMapper.java`** — 新增 `@Delete` 注解方法 `realDeleteByRoleCode(roleCode)`，绕过 @TableLogic 直接物理删除。
2. **`RoleServiceImpl.updateRolePermissions`** — 改用 `realDeleteByRoleCode` 替代 `delete(LambdaQueryWrapper)`。
3. **MySQL 库清理**（一次性）— `DELETE FROM role_permission WHERE role_code='SUPER_ADMIN'` 清除 19 条历史 is_deleted=1 脏数据。
4. **schema.sql / docs/10** — 保持原 `UNIQUE KEY (role_code, permission_code)`，加注释说明"必须真删"。

### 验证
| 场景 | 结果 |
|---|---|
| 保存 2 项 → 查询 | ✅ 200，权限数=2 |
| 恢复全 22 项 → 查询 | ✅ 200，权限数=22 |
| 清空 (`[]`) → 查询 | ✅ 200，权限数=0 |
| 反复切换 3 次（核心回归） | ✅ 全部 200，无 409 |
| 最终恢复 SUPER_ADMIN 全 22 项 | ✅ 200，权限数=22 |

## 规范记录（避免再犯）
- **MyBatis-Plus @TableLogic + 唯一键 冲突**：当表的 `is_deleted` 是 `@TableLogic` 而**唯一键不包含 is_deleted** 时，"逻辑删除后重建同键"会因 UPDATE 阶段就触发 unique 冲突而整事务回滚。
- **关联/关系表不要用 @TableLogic**：role_permission / user_role / student_tag 等纯关系表应当用物理删除（或把唯一键改为 `(field1, field2, is_deleted)`，但这会让数据无限增长且失去意义）。
- **修复原则**：先清后建场景一律走 `realDelete` mapper 方法 + 物理 INSERT。

## 涉及文件
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/mapper/RolePermissionMapper.java`（新增 realDeleteByRoleCode）
- `backend/src/main/java/com/pzhu/eduadmin/modules/user/service/RoleServiceImpl.java`（updateRolePermissions 改用真删）
- `sql/schema.sql`（注释说明）
- `docs/10-数据库规范.md`（注释说明）
- MySQL `edu_admin.role_permission`（清掉 19 条脏 is_deleted=1 行）
