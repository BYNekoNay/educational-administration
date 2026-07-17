# 数据库初始化脚本

对应 `docs/10-数据库规范.md` 的落地实现，供本地开发和答辩演示环境使用。

## 文件说明

| 文件 | 说明 |
|---|---|
| `reset.sql` | **完整一键脚本**：DROP 旧库 → 建表 → 填充全量演示数据，单文件直灌 |
| `schema.sql` | 建表脚本（参考，已合入 reset.sql） |
| `data.sql` | 初始化数据（参考，已合入 reset.sql） |
| `reset-all.ps1` | Windows PowerShell 快捷方式（效果与直灌 reset.sql 相同） |

## 使用方式

### 推荐（一行命令）
```bash
mysql -uroot -p123456 < sql/reset.sql
```

PowerShell 也可以用：
```powershell
Get-Content sql\reset.sql | mysql -uroot -p123456
```

## 初始化账号（密码均为 `123456`，已按 BCrypt 加密存储）

| 角色 | 用户名 | 说明 |
|---|---|---|
| 超级管理员 | admin | 权限、配置、全局看板 |
| 教务管理员 | edu | 课程、班级、排课、学员 |
| 财务管理员 | finance | 收费、退费、薪资 |
| 授课教师 | teacher1 / teacher2 / teacher3 | 分别对应美术/钢琴/舞蹈班主讲教师 |
| 学员家长 | parent1 / parent2 / parent3 | 分别绑定学员刘小小/陈朵朵/赵一鸣 |

## 与代码的一致性

- `schema.sql` 的 `user.version` 列与 `User.java` 实体字段保持一致（v0.9 新增，避免 `Unknown column 'version'` 错误）
- 关联表（`role_permission`、`teacher_course` 等）虽然 SQL 中有 `is_deleted` 列，但代码层已改用物理删除，规避 @TableLogic 与唯一键冲突
- 表结构字段、索引、约束与 `docs/10-数据库规范.md` 保持一致

## 更新记录

- 2026-07-17：`user` 表新增 `version` 列；旧 `reset.sql`（全量副本）替换为引导脚本 + `reset-all.ps1` 一键工具
