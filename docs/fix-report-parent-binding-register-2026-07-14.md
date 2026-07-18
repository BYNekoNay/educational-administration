# 修复报告：绑定家长下拉混入非家长角色 + 新增家长自助注册（2026-07-14）

## 一、问题：绑定家长下拉出现家长以外的角色
- **现象**：管理后台「学员管理 › 绑定家长」下拉框里出现了 admin / 教务 / 财务 / 教师等非家长用户。
- **根因**：`StudentList.vue` 的 `loadUserOptions` 原本调用 `userApi.list()` 拉取**全部用户**，没有按角色过滤。
- **修复**：
  - 后端新增 `GET /api/edu/students/parent-options`（`StudentServiceImpl.listParentOptions`），按 `role_code='PARENT' AND status=1` 过滤，密码字段置 null 后返回。该接口受 `@RequireRole({"SUPER_ADMIN","EDU_ADMIN"})` 保护，仅管理端可用。
  - 前端 `admin-web/src/api/edu.ts` 增加 `studentApi.parentOptions()`；`StudentList.vue` 改为调用该接口，并移除对 `userApi` 的依赖、补 `showError` 兜底。

## 二、家长用户是怎么来的（原提问答复）
系统里的家长（PARENT）原本只有两种来源：
1. **种子数据**：`sql/data.sql` 预置 `parent1`~`parent7`（演示账号）。
2. **管理员手动创建**：「系统管理 › 用户管理 › 新增用户」选角色「学员家长」（仅 SUPER_ADMIN 可进）。
→ 如果系统里没有 PARENT 用户，绑定家长下拉就会是空的。

## 三、新增：移动端家长自助注册
为让真实家长能自行开户，新增移动端自助注册，注册成功即获得 PARENT 账号并自动登录。
- **后端**
  - 新增 DTO `user/dto/RegisterRequest`（username / password / realName / phone，带校验注解）。
  - `IAuthService` / `AuthServiceImpl.register`：强制 `roleCode="PARENT"`（不信任前端角色），复用 `UserService.createUser`，注册后直接签发 JWT 返回 `LoginResponse`（自动登录）。
  - `AuthController` 暴露 `POST /api/auth/register`；`WebMvcConfig` 放行 `/api/auth/register` 与 `/api/auth/login`。
- **移动端**
  - 新页面 `mobile-uniapp/src/pages/register/register.vue`（账号 / 密码 / 确认 / 姓名 / 手机，含前端校验）。
  - `pages.json` 增加注册路由；`login.vue` 增加「立即注册」入口；提交后存 token → 加载家长学员 → 跳首页。

## 四、顺带修复的注册 500 隐患
- **现象**：首次验证注册接口返回 `code=500`「系统数据异常，请联系管理员」。
- **根因**：`UserServiceImpl.createUser` 内的 `logOperation` 调用 `CurrentUserHolder.get().getUserId()`，而注册是白名单接口、此时无登录态，`CurrentUserHolder.get()` 为 **null** → NPE → 500。该问题在**任何无登录态调用 createUser** 时都会触发，属通用隐患。
- **修复**：`logOperation` 改为空安全 —— `operatorId = operator != null ? operator.getUserId() : 0L`，并补 `import ...security.LoginUser`。

## 五、验证结果
- 后端 `mvn -o test`：**30 用例 / 0 失败 / 0 错误**，BUILD SUCCESS。
- 后端 `mvn -o clean package -DskipTests`：BUILD SUCCESS；重启 jar（`:8080` 正常）。
- 真实 API 复测（Python urllib 直连 8080）：
  1. `POST /api/auth/register`（新用户名）→ `code=0`，返回 token、roleCode=PARENT、userId=24 ✅
  2. 新家长出现在 `GET /api/edu/students/parent-options`（admin token）→ 总数 9，命中 ✅
  3. `POST /api/auth/login`（该家长）→ roleCode=PARENT ✅
  4. 重复注册同名 → `code=400`「用户名已存在」（正确拦截）✅

## 六、涉及文件
- 后端：`StudentController.java`、`StudentService.java`/`Impl.java`、`UserServiceImpl.java`、`AuthController.java`、`IAuthService.java`/`AuthService.java`、`user/dto/RegisterRequest.java`、`config/WebMvcConfig.java`
- 前端：`admin-web/src/api/edu.ts`、`admin-web/src/views/edu/StudentList.vue`
- 移动端：`mobile-uniapp/src/pages/register/register.vue`、`pages.json`、`pages/login/login.vue`

## 七、结论
- 家长用户现有三种来源：种子数据 / 管理员手动建 / 移动端自助注册。
- 绑定家长下拉已严格只显示 PARENT 角色，不再混入其他角色。
- 注册接口 500 隐患（无登录态创建用户）已彻底修复。
