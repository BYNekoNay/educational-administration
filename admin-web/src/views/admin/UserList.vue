<template>
  <div class="user-list">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
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
    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
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
      <el-table-column prop="createTime" label="创建时间" min-width="160">
        <template #default="{ row }">
          {{ row.createTime ? formatTime(row.createTime) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openEditDialog(row)">
            编辑
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
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>

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
import { userApi } from '@/api/auth'

// ====== 角色映射 ======
const roleMap: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  EDU_ADMIN: '教务管理员',
  FINANCE: '财务',
  TEACHER: '教师',
  PARENT: '家长',
}

const roleOptions = Object.entries(roleMap).map(([code, name]) => ({ code, name }))

function roleLabel(code: string): string {
  return roleMap[code] || code
}

function roleTagType(code: string): string {
  const map: Record<string, string> = {
    SUPER_ADMIN: 'danger',
    EDU_ADMIN: 'warning',
    FINANCE: 'success',
    TEACHER: '',
    PARENT: 'info',
  }
  return map[code] || ''
}

// ====== 数据状态 ======
const loading = ref(false)
const tableData = ref<any[]>([])
const searchKeyword = ref('')
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await userApi.list({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: searchKeyword.value || undefined,
    })
    const data = res.data
    tableData.value = data.records || []
    pagination.total = data.total || 0
    pagination.pageNum = data.pageNum
    pagination.pageSize = data.pageSize
  } catch {
    // 错误已在拦截器中提示
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
  pagination.pageNum = 1
  fetchData()
}

// ====== 启用/禁用 ======
async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await userApi.updateStatus(row.id, newStatus)
    row.status = newStatus
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  } catch {
    // 已在拦截器提示
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
})

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
  editUserId.value = null
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  dialogMode.value = 'create'
  resetForm()
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
      })
      ElMessage.success('新增成功')
    } else {
      await userApi.update(editUserId.value!, {
        username: form.username,
        realName: form.realName,
        phone: form.phone || undefined,
        roleCode: form.roleCode,
      })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch {
    // 已在拦截器提示
  } finally {
    submitting.value = false
  }
}

// ====== 工具函数 ======
function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  fetchData()
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
.toolbar h3 {
  margin: 0;
  font-size: 18px;
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
