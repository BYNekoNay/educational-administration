# 请假模块异常修复报告（2026-07-14）

## 一、异常来源（来自管理端功能测试）
测试发现 3 个接口异常，全部返回 500 "No static resource …"：
- `GET /api/edu/leave-requests`（管理员）
- `GET /api/parent/leave-requests`（家长）
- `POST /api/parent/leave-requests`（家长提交）

## 二、根因
运行中的 `backend/target/eduadmin.jar`（2026-07-13 构建）是**陈旧构建**，比源码少 6 个 `LeaveRequest` 相关类（含 2 个控制器）：
`AdminLeaveRequestController`、`ParentLeaveRequestController`、`LeaveRequest`、`LeaveRequestMapper`、`LeaveRequestService`、`LeaveRequestServiceImpl`。
即「请假模块」在运行后端中完全不存在。

## 三、修复方法（Maven 本机已损坏，改用 jar 手工补丁）
1. 停后端进程 → 备份原 jar 为 `eduadmin.jar.bak`。
2. 从 fat jar 提取 `BOOT-INF/lib` + `BOOT-INF/classes` 作为编译 classpath。
3. 用 `javac` 编译上述 6 个源文件，关键参数：
   - `-encoding UTF-8`（源码为中文 UTF-8，否则 GBK 报错）
   - `-parameters`（Spring Boot 3 的 `@RequestParam` 依赖参数名，缺此 Long 参数必 500）
   - 从 `.m2` 取 `lombok-1.18.32.jar` 加在 `-cp` 与 `-processorpath`（lombok 为 provided 作用域，不进 fat jar）
4. `jar uf` 将编译产物写回 jar。
5. 重启后端。

## 四、验证结果（真实运行环境，全部通过）
| 接口 | 修复前 | 修复后 |
|---|---|---|
| GET /api/edu/leave-requests（admin） | 500 | **200**，分页正常 |
| GET /api/parent/leave-requests（parent） | 500 | **200** |
| POST /api/parent/leave-requests（parent） | 500 | **200**，成功创建(待审核) |

闭环验证：
- 家长提交请假 → 落库成功（status=1 待审核），家长端/管理员端均可查到同一记录。
- 审核通过（status=2）时会自动创建考勤记录（status=3 请假）。
- 全量比对：源码 144 个顶层类 vs jar 144 个，差异 **0** → jar 与源码完全同步。
- 测试产生的 1 条记录已清理，库内现 0 条。

## 五、残留事项（建议）
- 本次为「手工补丁」，**非正规重建**。本机 `mvn` 仍损坏（classworlds 启动器缺失），长期应修复 Maven 后执行 `mvn package -o` 重建，才能保证任何后续源码改动都进 jar。
- 备份文件 `eduadmin.jar.bak`（陈旧 jar）可作回滚点，确认无误后可删除。
- 工作区根目录遗留 `test_runner.py`、`test-report-2026-07-14.md`、`fix-report-leave-request-2026-07-14.md`。
