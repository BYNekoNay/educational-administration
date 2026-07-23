<template>
  <div class="role-page">
    <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center">
      <span style="color: #606266; font-size: 14px">系统角色管理（内置角色不可删除，点击分配权限可为角色绑定菜单权限）</span>
      <el-button type="primary" @click="showRoleDialog()" :icon="Plus">新增角色</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索角色名称/编码" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <el-table
      :data="filteredRoles"
      border
      stripe
      v-loading="loading"
      size="small"
    >
      <el-table-column prop="roleName" label="角色名称" min-width="140" sortable />
      <el-table-column prop="roleCode" label="角色编码" min-width="140" sortable />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="isSystemRole(row.roleCode) ? 'warning' : 'info'" size="small">
            {{ isSystemRole(row.roleCode) ? '内置' : '自定义' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center; justify-content: center">
            <el-button link type="primary" size="small" @click="openPermDialog(row)">
              分配权限
            </el-button>
            <el-button
              v-if="!isSystemRole(row.roleCode)"
              link
              type="primary"
              size="small"
              @click="showRoleDialog(row)"
            >
              编辑
            </el-button>
            <el-popconfirm
              v-if="!isSystemRole(row.roleCode)"
              title="确定删除该角色？关联的权限分配也将被清除"
              @confirm="handleRoleDelete(row.id)"
            >
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑角色弹窗 -->
    <el-dialog
      :title="editingRole ? '编辑角色' : '新增角色'"
      v-model="roleDialogVisible"
      width="460px"
      @closed="onRoleDialogClosed"
    >
      <el-form :model="roleForm" label-width="90px">
        <el-form-item label="角色编码" required>
          <el-input
            v-model="roleForm.roleCode"
            placeholder="如 CUSTOM_ADMIN"
            :disabled="!!editingRole"
          />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="如 自定义管理员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRoleSave" :loading="roleSaving">
          {{ editingRole ? '保存修改' : '确认新增' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 分配权限弹窗 -->
    <el-dialog
      :title="`分配权限 — ${permRole?.roleName || ''}`"
      v-model="permDialogVisible"
      width="700px"
      @closed="onPermDialogClosed"
    >
      <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 10px">
        <el-button size="small" @click="handleSelectAll">全选</el-button>
        <el-button size="small" @click="handleClearAll">清空</el-button>
        <el-button size="small" @click="handleReset">重置</el-button>
        <span style="color: #909399; font-size: 13px">
          已选 {{ checkedPermissions.length }} / {{ allPermissions.length }}
        </span>
      </div>

      <el-table
        :data="permGroups"
        border
        size="small"
        row-key="name"
        default-expand-all
      >
        <el-table-column type="expand">
          <template #default="{ row: group }">
            <div style="display: flex; flex-wrap: wrap; gap: 8px; padding: 8px">
              <el-checkbox
                v-for="perm in group.perms"
                :key="perm.code"
                :model-value="checkedPermissions.includes(perm.code)"
                @change="togglePerm(perm.code, $event)"
                border
                size="small"
              >
                <span>{{ perm.label }}</span>
                <span style="color: #b0b8c4; font-size: 11px; margin-left: 4px; font-family: monospace">{{ perm.code }}</span>
              </el-checkbox>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="模块" min-width="120">
          <template #default="{ row: group }">
            <span>{{ group.icon }}</span>
            <span style="margin-left: 4px; font-weight: 500">{{ group.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权限数" width="80" align="center">
          <template #default="{ row: group }">
            {{ group.perms.length }}
          </template>
        </el-table-column>
        <el-table-column label="选中状态" width="100" align="center">
          <template #default="{ row: group }">
            <el-tag size="small" :type="groupCheckState(group).type">
              {{ groupCheckState(group).text }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end">
        <el-button type="primary" @click="handlePermSave" :loading="saving" :icon="Check">
          保存权限
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { showError } from '@/utils/error'
import { Check, Plus } from '@element-plus/icons-vue'
import { roleApi, permissionApi } from '@/api/auth'

// === 权限码中文映射 ===
const PERM_LABEL_MAP: Record<string, string> = {
  'menu:dashboard': '运营看板',
  'menu:user': '用户管理',
  'menu:role': '角色管理',
  'menu:menu': '菜单管理',
  'menu:permission': '权限码管理',
  'menu:organization': '机构配置',
  'menu:notice': '公告管理',
  'menu:log': '操作日志',
  'menu:student': '学员管理',
  'menu:course': '课程管理',
  'menu:class': '班级管理',
  'menu:enrollment': '报名管理',
  'menu:schedule': '排课管理',
  'menu:big-schedule': '大课表',
  'menu:adjust': '调课管理',
  'menu:classroom': '教室管理',
  'menu:attendance': '考勤管理',
  'menu:exam': '考级管理',
  'menu:payment': '收费管理',
  'menu:refund': '退费管理',
  'menu:lesson-flow': '课时流水',
  'menu:salary': '薪资管理',
  'menu:revenue': '营收统计',
}

// === 权限分组 ===
interface PermItem { code: string; label: string }
interface PermGroup { name: string; icon: string; perms: PermItem[] }

// 注意：分组必须覆盖 permission 表中的全部权限码，漏列的码在弹窗中不可见，
// "清空→保存"会随全量覆盖（后端先删后插）被静默移除。新增权限码时务必同步此表。
const PERM_CATEGORIES: { name: string; icon: string; codes: string[] }[] = [
  { name: '运营看板', icon: '📊', codes: ['menu:dashboard'] },
  { name: '系统管理', icon: '⚙️', codes: ['menu:user', 'menu:role', 'menu:menu', 'menu:permission', 'menu:organization', 'menu:notice', 'menu:log'] },
  { name: '教务管理', icon: '📚', codes: ['menu:student', 'menu:course', 'menu:class', 'menu:enrollment', 'menu:schedule', 'menu:big-schedule', 'menu:adjust', 'menu:classroom', 'menu:attendance', 'menu:exam'] },
  { name: '财务管理', icon: '💰', codes: ['menu:payment', 'menu:refund', 'menu:lesson-flow', 'menu:salary', 'menu:revenue'] },
]

// === 角色列表 ===
interface RoleItem { id: number; roleCode: string; roleName: string }
const roles = ref<RoleItem[]>([])
const loading = ref(false)
const keyword = ref('')
const filteredRoles = computed(() => {
  if (!keyword.value) return roles.value
  const kw = keyword.value.toLowerCase()
  return roles.value.filter(r => r.roleName.toLowerCase().includes(kw) || r.roleCode.toLowerCase().includes(kw))
})

// === 新增/编辑角色 ===
const roleDialogVisible = ref(false)
const roleSaving = ref(false)
const editingRole = ref<RoleItem | null>(null)
const roleForm = reactive({ roleCode: '', roleName: '' })

// === 权限分配 ===
const permDialogVisible = ref(false)
const permRole = ref<RoleItem | null>(null)
const checkedPermissions = ref<string[]>([])
const originalPermissions = ref<string[]>([])
const saving = ref(false)
const allPermissions = ref<PermItem[]>([])

/** 角色弹窗关闭后复位（避免在模板中对 const 绑定直接赋值） */
function onRoleDialogClosed() {
  roleForm.roleCode = ''
  roleForm.roleName = ''
  editingRole.value = null
}

/** 分配权限弹窗关闭后复位 */
function onPermDialogClosed() {
  permRole.value = null
  checkedPermissions.value = []
  originalPermissions.value = []
}

const permGroups = computed<PermGroup[]>(() => {
  return PERM_CATEGORIES.map(cat => ({
    name: cat.name,
    icon: cat.icon,
    perms: allPermissions.value.filter(p => cat.codes.includes(p.code)),
  })).filter(g => g.perms.length > 0)
})

function isSystemRole(roleCode: string): boolean {
  return ['SUPER_ADMIN', 'EDU_ADMIN', 'FINANCE', 'TEACHER', 'PARENT'].includes(roleCode)
}

function groupCheckState(group: PermGroup) {
  const codes = group.perms.map(p => p.code)
  const selected = codes.filter(c => checkedPermissions.value.includes(c)).length
  if (selected === 0) return { type: 'info' as const, text: '无' }
  if (selected === codes.length) return { type: 'success' as const, text: '全选' }
  return { type: 'warning' as const, text: `${selected}/${codes.length}` }
}

// === 角色表加载 ===
async function loadRoles() {
  loading.value = true
  try {
    const res = await roleApi.list()
    roles.value = res.data || []
  } catch (e) { showError(e, '加载角色列表失败') }
  finally { loading.value = false }
}

function handleSearch() { /* computed filteredRoles auto-updates */ }
function resetSearch() { keyword.value = '' }

// === 权限列表加载 ===
async function loadAllPermissions(): Promise<PermItem[]> {
  try {
    const res = await permissionApi.list()
    const list: any[] = res.data || []
    return list.map((p: any) => ({
      code: p.permissionCode,
      label: PERM_LABEL_MAP[p.permissionCode] || p.permissionCode.replace('menu:', ''),
    }))
  } catch (e) {
    // 不能静默吞掉：allPermissions 为空时"全选→保存"会把角色权限清空（后端先删后插）
    showError(e, '加载权限列表失败')
    return []
  }
}

// === 角色增删改 ===
function showRoleDialog(role?: RoleItem) {
  if (role) {
    editingRole.value = role
    roleForm.roleCode = role.roleCode
    roleForm.roleName = role.roleName
  } else {
    editingRole.value = null
    roleForm.roleCode = ''
    roleForm.roleName = ''
  }
  roleDialogVisible.value = true
}

async function handleRoleSave() {
  if (!roleForm.roleCode || !roleForm.roleName) {
    ElMessage.warning('角色编码和角色名称不能为空')
    return
  }
  if (!editingRole.value && !/^[A-Za-z][A-Za-z0-9_]*$/.test(roleForm.roleCode)) {
    ElMessage.warning('角色编码须以字母开头，仅含字母、数字、下划线')
    return
  }
  roleSaving.value = true
  try {
    if (editingRole.value) {
      await roleApi.update(editingRole.value.id, { roleName: roleForm.roleName })
      ElMessage.success(`角色「${roleForm.roleName}」已更新`)
    } else {
      await roleApi.create({ ...roleForm })
      ElMessage.success(`角色「${roleForm.roleName}」已创建`)
    }
    roleDialogVisible.value = false
    await loadRoles()
    allPermissions.value = await loadAllPermissions()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally { roleSaving.value = false }
}

async function handleRoleDelete(id: number) {
  try {
    await roleApi.delete(id)
    ElMessage.success('角色已删除')
    await loadRoles()
  } catch (e: any) {
    showError(e, '删除失败')
  }
}

// === 权限分配 ===
async function openPermDialog(role: RoleItem) {
  permRole.value = role
  // 权限全量列表为空（onMounted 加载失败）时先重试；仍为空则不打开弹窗——
  // 否则"全选"会置空、保存将清空角色原有权限（后端先删后插）
  if (allPermissions.value.length === 0) {
    allPermissions.value = await loadAllPermissions()
    if (allPermissions.value.length === 0) {
      ElMessage.error('权限列表加载失败，无法分配权限，请刷新页面重试')
      return
    }
  }
  try {
    const res = await roleApi.permissions(role.id)
    const codes: string[] = res.data?.permissionCodes || []
    checkedPermissions.value = [...codes]
    originalPermissions.value = [...codes]
  } catch (e) {
    showError(e, '加载权限失败')
    // 加载失败时不得打开弹窗：空勾选状态下保存会覆盖角色原有权限（后端先删后插）
    return
  }
  permDialogVisible.value = true
}

function handleReset() { checkedPermissions.value = [...originalPermissions.value] }
function handleSelectAll() { checkedPermissions.value = allPermissions.value.map(p => p.code) }
function handleClearAll() { checkedPermissions.value = [] }
function togglePerm(code: string, checked: boolean) {
  if (checked) {
    if (!checkedPermissions.value.includes(code)) checkedPermissions.value.push(code)
  } else {
    checkedPermissions.value = checkedPermissions.value.filter(c => c !== code)
  }
}

async function handlePermSave() {
  if (!permRole.value) return
  saving.value = true
  try {
    await roleApi.updatePermissions(permRole.value.id, checkedPermissions.value)
    originalPermissions.value = [...checkedPermissions.value]
    ElMessage.success(`已保存「${permRole.value.roleName}」的权限配置`)
    permDialogVisible.value = false
  } catch (e) {
    showError(e, '保存失败')
  } finally { saving.value = false }
}

onMounted(async () => {
  allPermissions.value = await loadAllPermissions()
  loadRoles()
})
</script>
