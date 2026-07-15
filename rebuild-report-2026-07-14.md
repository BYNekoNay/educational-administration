# 后端 Maven 修复与正规重建报告

- **日期**：2026-07-14
- **目标**：修复本机损坏的 Maven，并用正规 `mvn package` 重建 `eduadmin.jar`，覆盖此前手工补丁的局限。
- **结果**：✅ Maven 已修复、✅ 构建成功、✅ 54 项接口测试全绿。

---

## 一、Maven 故障根因

| 项 | 原值（错误） | 正确值 |
|---|---|---|
| `MAVEN_HOME` | `D:\apache-maven-3.9.11`（**目录不存在**） | `D:\bianyiheji\apache-maven-3.9.4`（真实安装，已在 PATH） |
| 现象 | `mvn -v` → `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher` | 已恢复 |

根因：在 Git Bash 中 `mvn` 解析到 Unix 版 `mvn` 启动脚本，它沿用错误的 `MAVEN_HOME` 用 Windows 风格路径拼 `boot/plexus-classworlds-*.jar` 的 classpath，导致类加载器启动类找不到。

**修复动作**（永久生效）：
```powershell
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "D:\bianyiheji\apache-maven-3.9.4", "User")
```
验证：`PowerShell` 调用 `D:\bianyiheji\apache-maven-3.9.4\bin\mvn.cmd -v` → `Apache Maven 3.9.4` + `Java version: 17.0.8.1` 正常。

> 注：原生 `mvn.cmd`（cmd/PowerShell/IntelliJ）始终自行解析正确路径，不受 `MAVEN_HOME` 影响；本次同时修正了环境变量，使 IDE 与全局环境一致。

---

## 二、正规重建

```bash
cd backend
mvn -o clean package -DskipTests
```

| 指标 | 结果 |
|---|---|
| 模式 | 离线（`-o`），`.m2` 依赖齐全 |
| 编译 | 154 个主源文件 + 7 个测试源文件（javac release 17） |
| 测试 | 跳过（`-DskipTests`，7 个 `@SpringBootTest` 需连库，避免阻断 jar 产出） |
| 打包 | `spring-boot:repackage` 执行，生成 fat jar |
| 耗时 | 29.7s |
| 产物 | `backend/target/eduadmin.jar`（57.4 MB） |
| 结论 | **BUILD SUCCESS** |

> 如要补跑测试：`mvn -o test`（需 MySQL 可用；默认 profile 连接 `edu_admin`）。

---

## 三、新 jar 完整性校验

- 请假模块 6 个类全部打入：`AdminLeaveRequestController`、`ParentLeaveRequestController`、`LeaveRequest`、`LeaveRequestMapper`、`LeaveRequestService`、`LeaveRequestServiceImpl`。
- **全量比对**：源码 144 个顶层类 = jar 144 个顶层类，**差异为 0**（彻底与源码同步）。
- Manifest：`Main-Class: org.springframework.boot.loader.launch.JarLauncher`，`Start-Class: com.pzhu.eduadmin.EduAdminApplication`。

---

## 四、重启与回归验证

1. 停止旧后端进程，释放 jar 锁；备份当时可用 jar 为 `eduadmin.working.bak`。
2. 用新 jar 启动：`java -jar target/eduadmin.jar --server.port=8080`。
3. 健康检查：`8080 → 200`；登录 `admin/123456 → code=0, role=SUPER_ADMIN`。
   - 启动日志中的 `NoResourceFoundException: No static resource .` 仅为对根路径 `/` 的 404 健康检查，**非致命**，应用已 `Started EduAdminApplication`。
4. 重跑 54 项管理端接口测试 → **54 通过 / 0 异常**（此前 3 个请假模块异常已消失）。
5. 显式验证请假模块三接口：
   - `GET /api/edu/leave-requests`（管理员）→ 200
   - `GET /api/parent/leave-requests`（家长）→ 200
   - `POST /api/parent/leave-requests`（家长提交）→ 200
6. 测试数据已清理，库内 `leave_request` 表 0 条。
7. 管理后台 `http://localhost:5173` → 200，正常。

---

## 五、备份与遗留文件

| 文件 | 说明 | 建议 |
|---|---|---|
| `backend/target/eduadmin.jar` | 本次正规重建产物（**当前使用**） | 保留 |
| `backend/target/eduadmin.jar.original` | Maven repackage 前的普通 jar | 可删 |
| `backend/target/eduadmin.working.bak` | 本会话补丁后可用版（回滚点） | 确认新 jar 稳定后可删 |
| `backend/target/eduadmin.jar.bak` | 补丁**前**的陈旧版（缺请假模块） | **可删**（已无用） |
| `backend_build.log` | 本次 Maven 构建日志 | 可留档 |
| `test_runner.py` / `test-report-2026-07-14.md` | 接口测试脚本与报告 | 可留档 |

---

## 六、结论

- 本机 Maven 已正规修复，后续任何源码改动均可用 `mvn -o clean package -DskipTests` 一步重建。
- 此前「手工补丁 jar」方案（见 `spring-boot-jar-hotpatch` 技能）降级为 Maven 不可用时的应急手段。
- 教务管理平台后端现已处于**源码↔jar 完全同步**的健康状态，54 项管理端功能全部正常。
