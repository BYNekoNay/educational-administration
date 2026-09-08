<template>
  <div class="user-list">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <h3>用户管理</h3>
      <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索用户名或姓名"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <!-- 用户表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="70" sortable="custom" />
      <el-table-column prop="username" label="用户名" min-width="120" sortable="custom" />
      <el-table-column prop="realName" label="姓名" min-width="100" sortable="custom" />
      <el-table-column label="教学特长" min-width="160">
        <template #default="{ row }">
          <template v-if="row.roleCode === 'TEACHER' && row.specialties && row.specialties.length">
            <el-tag v-for="sp in row.specialties" :key="sp.id"
                    size="small" type="info" style="margin-right:4px;margin-bottom:2px">
              {{ sp.name }}
            </el-tag>
          </template>
          <span v-else style="color:#909399">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" min-width="120">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="roleCode" label="角色" width="130">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.roleCode)" size="small">
            {{ roleLabel(row.roleCode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录" min-width="160">
        <template #default="{ row }">
          {{ row.lastLoginTime ? formatTime(row.lastLoginTime) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" sortable="custom">
        <template #default="{ row }">
          {{ row.createTime ? formatTime(row.createTime) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
            <el-button size="small" type="primary" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button size="small" type="warning" @click="openPasswordDialog(row)">
              改密
            </el-button>
            <el-popconfirm
              :title="row.status === 1 ? '确认禁用该用户？' : '确认启用该用户？'"
              @confirm="toggleStatus(row)"
            >
              <template #reference>
                <el-button
                  size="small"
                  :type="row.status === 1 ? 'danger' : 'success'"
                >
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="400px" :close-on-click-modal="false">
      <el-form :model="passwordForm" label-width="100px">
        <el-form-item label="用户">{{ passwordTarget?.username }}（{{ passwordTarget?.realName }}）</el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="handleResetPassword">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增用户' : '编辑用户'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item
          v-if="dialogMode === 'create'"
          label="密码"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode" placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.code"
              :label="role.name"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.roleCode === 'TEACHER'" label="可授课程">
          <el-select
            v-model="form.specialtyCourseIds"
            multiple
            filterable
            placeholder="请选择可授课程（可多选）"
            style="width: 100%"
            :loading="courseLoading"
          >
            <el-option
              v-for="c in courseOptions"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { userApi, roleApi } from '@/api/auth'
import { courseApi } from '@/api/edu'
import { showError } from '@/utils/error'

// ====== 角色映射（仅内置角色显示名兜底） ======
const roleMap: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  EDU_ADMIN: '教务管理员',
  FINANCE: '财务',
  TEACHER: '教师',
  PARENT: '家长',
}

// 角色选项从后端动态加载：RoleList 支持自定义角色，硬编码会导致自定义角色无法分配给用户
const roleOptions = ref<{ code: string; name: string }[]>(
  Object.entries(roleMap).map(([code, name]) => ({ code, name }))
)

async function loadRoleOptions() {
  try {
    const res = await roleApi.list()
    const list: any[] = res.data || []
    if (list.length) {
      roleOptions.value = list.map(r => ({
        code: r.roleCode,
        name: r.roleName || roleMap[r.roleCode] || r.roleCode,
      }))
    }
  } catch (e) {
    showError(e, '加载角色列表失败')
  }
}

function roleLabel(code: string): string {
  return roleOptions.value.find(r => r.code === code)?.name || roleMap[code] || code
}

function roleTagType(code: string): string {
  const map: Record<string, string> = {
    SUPER_ADMIN: 'danger',
    EDU_ADMIN: 'warning',
    FINANCE: 'success',
    TEACHER: 'primary',
    PARENT: 'info',
  }
  return map[code] || ''
}

// ====== 数据状态 ======
const loading = ref(false)
const tableData = ref<any[]>([])
const searchKeyword = ref('')
const sortField = ref(''), sortOrder = ref('')
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await userApi.list({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: searchKeyword.value || undefined,
      sortField: sortField.value || undefined,
      sortOrder: sortOrder.value || undefined,
    })
    const data = res.data
    tableData.value = data.records || []
    pagination.total = data.total || 0
    pagination.pageNum = data.pageNum
    pagination.pageSize = data.pageSize
  } catch (e) {
    showError(e, '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchData()
}

function resetSearch() {
  searchKeyword.value = ''
  sortField.value = ''; sortOrder.value = ''
  pagination.pageNum = 1
  fetchData()
}

function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pagination.pageNum = 1; fetchData()
}

// ====== 启用/禁用 ======
async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await userApi.updateStatus(row.id, newStatus)
    row.status = newStatus
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  } catch (e) {
    showError(e, '状态更新失败')
  }
}

// ====== 新增/编辑对话框 ======
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editUserId = ref<number | null>(null)

const form = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  roleCode: '',
  specialtyCourseIds: [] as number[],
})

// 课程选项（角色为教师时使用）
const courseOptions = ref<any[]>([])
const courseLoading = ref(false)

async function loadCourseOptions() {
  if (courseOptions.value.length > 0) return
  courseLoading.value = true
  try {
    const res = await courseApi.list({ pageSize: 200 })
    courseOptions.value = res.data?.records || []
  } catch (e) { showError(e, '加载课程列表失败') }
  finally { courseLoading.value = false }
}

const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function resetForm() {
  form.username = ''
  form.password = ''
  form.realName = ''
  form.phone = ''
  form.roleCode = ''
  form.specialtyCourseIds = []
  editUserId.value = null
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  dialogMode.value = 'create'
  resetForm()
  loadCourseOptions()
  dialogVisible.value = true
}

function openEditDialog(row: any) {
  dialogMode.value = 'edit'
  resetForm()
  editUserId.value = row.id
  form.username = row.username
  form.realName = row.realName || ''
  form.phone = row.phone || ''
  form.roleCode = row.roleCode
  form.specialtyCourseIds = row.specialtyCourseIds || []
  loadCourseOptions()
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await userApi.create({
        username: form.username,
        password: form.password,
        realName: form.realName,
        phone: form.phone || undefined,
        roleCode: form.roleCode,
        specialtyCourseIds: form.roleCode === 'TEACHER' ? form.specialtyCourseIds : undefined,
      })
      ElMessage.success('新增成功')
    } else {
      await userApi.update(editUserId.value!, {
        username: form.username,
        realName: form.realName,
        phone: form.phone || undefined,
        roleCode: form.roleCode,
        specialtyCourseIds: form.roleCode === 'TEACHER' ? form.specialtyCourseIds : undefined,
      })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    showError(e, '保存失败')
  } finally {
    submitting.value = false
  }
}

// ====== 修改密码 ======
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordTarget = ref<any>(null)
const passwordForm = reactive({ newPassword: '' })

function openPasswordDialog(row: any) {
  passwordTarget.value = row
  passwordForm.newPassword = ''
  passwordVisible.value = true
}

async function handleResetPassword() {
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  passwordSaving.value = true
  try {
    await userApi.resetPassword(passwordTarget.value.id, passwordForm.newPassword)
    ElMessage.success('密码已重置')
    passwordVisible.value = false
  } catch (e) {
    showError(e, '重置密码失败')
  } finally {
    passwordSaving.value = false
  }
}

// ====== 工具函数 ======
function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  fetchData()
  loadRoleOptions()
})
</script>

<style scoped>
.user-list {
  padding: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
