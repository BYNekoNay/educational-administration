# 薪资核算接口（calculateSalary）缺陷修复报告

- **日期**：2026-07-14
- **修复人**：自动化测试 + 代码修复
- **关联**：全量接口测试发现的唯一真实后端缺陷

## 一、缺陷现象
`POST /api/finance/salaries/calculate` 对任何**已配置薪资规则**的教师（4/5/6/16/17/18）均返回 `code=500`：
`Expected one result (or null) to be returned by selectOne(), but found: 3`。
导致薪资核算功能完全不可用，并连带 `POST /api/finance/salaries/adjustments` 因拿不到薪资单 id 而受阻。

## 二、根因（检索影响范围）
- `SalaryServiceImpl.calculateSalary` 第 175 行使用 `salaryRuleMapper.selectOne(eq(teacher_id))`，假设**每师恰好 1 条**规则。
- 但 `salary_rule` 表定义为 `UNIQUE KEY uk_teacher_course (teacher_id, course_id)` —— 设计本意是**每师每课一条规则**（每课不同单价）。种子数据 `sql/data.sql` 也确实是 6 教师 × 3 课程 = 18 条（其中 id=1 已逻辑删除）。
- 因此 `selectOne(teacher_id)` 对该教师返回全部 3 条 → MyBatis-Plus 抛异常。
- **数据层本身正确，无需清理**（此前测试报告里"清理 salary_rule 重复行"的建议系误诊，本次已纠正：数据不是重复，而是按课程区分的正确设计）。

## 三、修复方案（仅改代码，不动数据 / seed / schema）
将"单一单价 × 总课时"改为**正确的按课程单价累加**：
1. 注入 `ClassGroupMapper`（解析 课次 → 班级 → 课程 的桥梁；`schedule_lesson` 仅有 `class_id`，无 `course_id`）。
2. 用 `selectList(teacher_id)` 取该教师全部规则，构建 `courseId → SalaryRule` 映射；取首条作为兜底默认规则。
3. 收集涉及课次的 `classId`，批量查询 `class_group` 构建 `classId → courseId` 映射。
4. 遍历每节课时，按"其所属班级的课程"取对应单价累加（主讲与代课分别累加），保持 `lessonCount`/`substituteCount` 计数不变。
5. 边界处理：班级/课程缺失或课程无对应规则时，用兜底默认规则，保证已授课都计薪。

**改动文件**：`backend/src/main/java/com/pzhu/eduadmin/modules/salary/service/SalaryServiceImpl.java`
- 新增 import：`ClassGroup`、`ClassGroupMapper`、`HashSet`、`Function`。
- 新增依赖字段：`private final ClassGroupMapper classGroupMapper;`
- 重写 `calculateSalary` 第 3~6 步（规则读取与金额计算）。

## 四、构建与验证
- 用已修复的 Maven 离线重建：`mvn -o clean package -DskipTests` → **BUILD SUCCESS**（31s，57445659 字节 fat jar）。
- 重启后端（`--server.port=8080`）→ 8080=200，应用正常启动。
- **数学精确核验**（teacher 4，3 条规则，单价 80/70/100）：
  - 用 SQL 精确复刻服务逻辑（含 `@TableLogic` 逻辑删除与默认规则兜底），期望 base=410、sub=840。
  - 服务实际返回：`lessonCount=5, baseAmount=410, substituteCount=15, totalAmount=1250` —— **与期望完全一致**。
  - 差异说明：原始复算得 480 是因未排除被逻辑删除的课次（lesson id=1, is_deleted=1），服务经 `@TableLogic` 正确排除后为 410。
- **联动与边界**：
  - `POST /api/finance/salaries/adjustments`（用已核算薪资 id=1）→ `200 + code=409 "仅可对已确认的薪资进行调整"`（正确业务校验，链路已打通）。
  - teacher 7（无规则）→ `code=400 "未配置薪资规则"`（预期行为）。
  - teacher 5（3 规则）→ `200`，按课程单价算出 baseAmount=350（正常）。

## 五、回归结果
- **54 项管理端接口测试**：54 通过 / 0 异常。
- **149 端点全量测试**：全部可达；`salary/calculate` 现为 **OK(200, code=0)**；仅剩 2 个 FAIL 为 `/auth/profile`、`/logout`（需登录态的误判），以及 3 个空参 POST 的 500 假象（check-conflict / schedules 创建 / password，已用真实 body 验证均为 200）。

## 六、结论
薪资核算真实缺陷已修复并验证通过；全量 149 端点无 404、无未修复的 500。后续若有源码改动，继续用 `mvn -o clean package -DskipTests` 重建即可。
