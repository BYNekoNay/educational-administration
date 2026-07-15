# 修复报告：机构信息配置无法保存（2026-07-14）

## 1. 现象

管理后台「系统管理 > 机构信息配置」页面中，修改「机构名称」和「联系电话」后点击「保存」，页面提示「保存成功」，但刷新后字段恢复为空。

截图显示：
- 「机构名称」输入框为空，只显示 placeholder「请输入机构名称」
- 「校区」显示「本部校区」
- 「联系电话」输入框为空，只显示 placeholder「请输入联系电话」
- 「地址」显示「北京市朝阳区示例路1号」

## 2. 根因分析

### 2.1 字段名不一致

后端 `Organization` 实体字段名为：

```java
private Long id;
private String orgName;       // 对应表 org_name
private String campus;
private String contactPhone;  // 对应表 contact_phone
private String address;
```

而前端 `Organization.vue` 表单字段名为：

```ts
const form = reactive({
  name: '',
  campus: '',
  phone: '',
  address: '',
})
```

两者存在两处错位：
- `form.name` ↔ 后端 `orgName`
- `form.phone` ↔ 后端 `contactPhone`

因此：
- **加载时**：前端从 `data.name` 和 `data.phone` 取值，但 GET 接口实际返回的是 `orgName` 和 `contactPhone`，所以这两个字段在页面初始化时就是空的。
- **保存时**：前端把 `name` 和 `phone` 发送给后端，后端实体无法绑定这两个字段，导致数据库中 `org_name` 和 `contact_phone` 实际没有被更新。`campus` 和 `address` 因为字段名一致，所以能正常显示。

### 2.2 缺少主键 id

前端保存请求：`PUT /api/admin/organization`，body 为 `{ name, campus, phone, address }`，没有 `id`。

后端 `updateOrganization` 使用 `organizationMapper.updateById(organization)`，在实体 id 为 null 时不会命中任何记录，最终返回 `data: null`，但统一响应的 `code` 仍为 0，所以前端弹出「保存成功」，实际数据未更新。

## 3. 修复方案

### 3.1 前端：`admin-web/src/views/admin/Organization.vue`

- 表单字段统一改为与后端一致的命名：`id` / `orgName` / `campus` / `contactPhone` / `address`（页面显示标签保持不变，仍为「机构名称」「联系电话」）。
- 加载时从 `data.orgName` / `data.contactPhone` 读取。
- 保存时发送完整对象，包括 `id`。

关键修改后代码示例：

```ts
const form = reactive({
  id: undefined as number | undefined,
  orgName: '',
  campus: '',
  contactPhone: '',
  address: '',
})

async function loadData() {
  const res = await organizationApi.get()
  const data = res.data
  if (data) {
    form.id = data.id
    form.orgName = data.orgName || ''
    form.campus = data.campus || ''
    form.contactPhone = data.contactPhone || ''
    form.address = data.address || ''
  }
}

async function handleSave() {
  await organizationApi.update({
    id: form.id,
    orgName: form.orgName,
    campus: form.campus,
    contactPhone: form.contactPhone,
    address: form.address,
  })
}
```

### 3.2 后端：`StatisticsServiceImpl.updateOrganization`

增加唯一记录兜底：如果前端未传 `id`（兼容旧请求或其他客户端），直接取机构信息表的第一条记录作为更新目标。

```java
if (organization.getId() == null) {
    Organization existing = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>().last("LIMIT 1"));
    if (existing != null) organization.setId(existing.getId());
}
organizationMapper.updateById(organization);
```

## 4. 验证

| 步骤 | 结果 |
|---|---|
| 前端 `npm run build` | ✅ built（仅预存的 chunk>500kB 警告） |
| 后端 `mvn -o clean package -DskipTests` | BUILD SUCCESS |
| 后端启动 | `Started EduAdminApplication`；`GET /api/auth/login` 返回 200 |

**真实 API 测试**：

```
GET /api/admin/organization
→ { id: 1, orgName: '知行艺术培训中心', campus: '本部校区', contactPhone: '010-88889999', address: '北京市朝阳区示例路1号' }

PUT /api/admin/organization
{ id: 1, orgName: '测试新名称', campus: '测试校区', contactPhone: '13912345678', address: '测试地址' }
→ code: 0, data: { id: 1, orgName: '测试新名称', ... }

GET /api/admin/organization
→ { id: 1, orgName: '测试新名称', ... }  // 已持久化

再恢复原始值后 GET 确认数据已还原。
```

## 5. 改动文件

- `admin-web/src/views/admin/Organization.vue`
- `backend/src/main/java/com/pzhu/eduadmin/modules/statistics/service/StatisticsServiceImpl.java`

## 6. 结论

机构信息配置无法保存的问题已修复。根本原因是前端表单字段名与后端实体字段名不一致，导致 `orgName` 和 `contactPhone` 在加载/保存时始终未正确映射。修复后「机构名称」和「联系电话」可正常保存并持久化。同时后端增加了对缺失 `id` 的兜底处理，提高接口健壮性。
