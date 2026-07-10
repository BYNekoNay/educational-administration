<template>
  <div class="role-page">
    <el-row :gutter="20">
      <el-col :span="5">
        <el-card shadow="never" header="系统角色">
          <div style="margin-bottom: 10px">
            <el-button type="primary" size="small" @click="showRoleDialog()" :icon="Plus">
              新增角色
            </el-button>
          </div>
          <el-table
            :data="roles"
            highlight-current-row
            @current-change="onRoleSelect"
            style="width: 100%"
            size="small"
          >
            <el-table-column prop="roleName" label="角色名称" min-width="80" />
            <el-table-column prop="roleCode" label="代码" width="90" />
            <el-table-column label="操作" width="50" align="center">
              <template #default="{ row }">
                <el-popconfirm
                  v-if="!isSystemRole(row.roleCode)"
                  title="确定删除该角色？关联的权限分配也将被清除"
                  @confirm="handleRoleDelete(row.id)"
                >
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 10px; color: #909399; font-size: 12px">
            点击角色编辑权限，内置角色不可删除
          </div>
        </el-card>
      </el-col>

      <el-col :span="19">
        <el-card shadow="never"
          :header="selectedRole ? `编辑权限 — ${selectedRole.roleName}` : '请选择一个角色'">
          <template v-if="!selectedRole">
            <el-empty description="请在左侧选择一个角色" />
          </template>
          <template v-else>
            <el-checkbox-group v-model="checkedPermissions" class="perm-grid">
              <el-checkbox
                v-for="p in allPermissions"
                :key="p.code"
                :label="p.code"
                :value="p.code"
                border
                class="perm-checkbox"
              >
                {{ p.label }}
              </el-checkbox>
            </el-checkbox-group>
            <div style="margin-top: 20px; display: flex; gap: 10px; align-items: center">
              <el-button type="primary" @click="handleSave" :loading="saving" :icon="Check">保存权限</el-button>
              <el-button @click="handleReset">重置</el-button>
              <el-button link type="primary" @click="handleSelectAll">全选</el-button>
              <el-button link type="danger" @click="handleClearAll">清空</el-button>
              <span style="margin-left: 8px; color: #909399; font-size: 13px">
                已选 {{ checkedPermissions.length }} / {{ allPermissions.length }}
              </span>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增角色弹窗 -->
    <el-dialog
      title="新增角色"
      v-model="roleDialogVisible"
      width="420px"
      @closed="roleForm = { roleCode: '', roleName: '' }"
    >
      <el-form :model="roleForm" label-width="90px">
        <el-form-item label="角色编码" required>
          <el-input v-model="roleForm.roleCode" placeholder="如 CUSTOM_ADMIN" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="如 自定义管理员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRoleCreate" :loading="roleSaving">
          确认新增
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Plus } from '@element-plus/icons-vue'
import { roleApi, permissionApi } from '@/api/auth'

interface PermItem { code: string; label: string }
const roles = ref<any[]>([])
const selectedRole = ref<any>(null)
const checkedPermissions = ref<string[]>([])
const originalPermissions = ref<string[]>([])
const saving = ref(false)
const allPermissions = ref<PermItem[]>([])

// 新增角色
const roleDialogVisible = ref(false)
const roleSaving = ref(false)
const roleForm = reactive({ roleCode: '', roleName: '' })

function isSystemRole(roleCode: string): boolean {
  return ['SUPER_ADMIN', 'EDU_ADMIN', 'FINANCE', 'TEACHER', 'PARENT'].includes(roleCode)
}

async function loadRoles() {
  try {
    const res = await roleApi.list()
    roles.value = res.data || []
  } catch { ElMessage.error('加载角色列表失败') }
}

async function loadAllPermissions(): Promise<PermItem[]> {
  try {
    const res = await permissionApi.list()
    const list: any[] = res.data || []
    return list.map((p: any) => ({
      code: p.permissionCode,
      label: p.permissionCode.replace('menu:', ''),
    }))
  } catch { return [] }
}

async function onRoleSelect(role: any) {
  if (!role) return
  selectedRole.value = role
  try {
    const res = await roleApi.permissions(role.id)
    const permData = res.data
    const codes: string[] = permData?.permissionCodes || []
    checkedPermissions.value = [...codes]
    originalPermissions.value = [...codes]
  } catch {
    ElMessage.error('加载权限失败')
    checkedPermissions.value = []
    originalPermissions.value = []
  }
}

async function handleSave() {
  if (!selectedRole.value) return
  saving.value = true
  try {
    await roleApi.updatePermissions(selectedRole.value.id, checkedPermissions.value)
    originalPermissions.value = [...checkedPermissions.value]
    ElMessage.success(`已保存 "${selectedRole.value.roleName}" 的权限配置`)
  } catch {
    ElMessage.error('保存失败')
  } finally { saving.value = false }
}

function handleReset() { checkedPermissions.value = [...originalPermissions.value] }
function handleSelectAll() { checkedPermissions.value = allPermissions.value.map(p => p.code) }
function handleClearAll() { checkedPermissions.value = [] }

function showRoleDialog() { roleDialogVisible.value = true }

async function handleRoleCreate() {
  if (!roleForm.roleCode || !roleForm.roleName) {
    ElMessage.warning('角色编码和角色名称不能为空')
    return
  }
  roleSaving.value = true
  try {
    await roleApi.create({ ...roleForm })
    ElMessage.success(`角色 "${roleForm.roleName}" 创建成功`)
    roleDialogVisible.value = false
    await loadRoles()
    allPermissions.value = await loadAllPermissions()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally { roleSaving.value = false }
}

async function handleRoleDelete(id: number) {
  try {
    await roleApi.delete(id)
    ElMessage.success('角色已删除')
    selectedRole.value = null
    checkedPermissions.value = []
    originalPermissions.value = []
    await loadRoles()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(async () => {
  allPermissions.value = await loadAllPermissions()
  loadRoles()
})
</script>

<style scoped>
.perm-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.perm-checkbox {
  margin-right: 0 !important;
  padding: 6px 12px;
  border-radius: 4px;
}
</style>
