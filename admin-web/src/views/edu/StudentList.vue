<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>学员管理</h3>
      <el-button type="primary" @click="openDialog(null)">新增学员</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索姓名/电话/学校/家长" clearable style="width:260px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="name" label="姓名" sortable="custom" />
      <el-table-column prop="gender" label="性别" width="70">
        <template #default="{ row }">{{ row.gender === 1 ? '男' : row.gender === 2 ? '女' : '-' }}</template>
      </el-table-column>
      <el-table-column prop="birthday" label="出生日期" width="110" sortable="custom" />
      <el-table-column prop="school" label="学校" />
      <el-table-column prop="contactPhone" label="联系电话" width="120" />
      <el-table-column prop="parentName" label="家长" min-width="100" sortable>
        <template #default="{ row }">
          <span v-if="row.parentName">{{ row.parentName }}</span>
          <el-tag v-else type="info" size="small">未绑定</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'info'">
            {{ row.status === 1 ? '在读' : row.status === 2 ? '停课' : '退班' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" @click="openBindDialog(row)">绑定家长</el-button>
          <el-button size="small" type="warning" @click="openTransferDialog(row)">转班</el-button>
          <el-button size="small" type="danger" @click="handleWithdraw(row)">退班</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @change="loadData"
    />

    <!-- 新增/编辑学员弹窗 -->
    <el-dialog :title="isEdit ? '编辑学员' : '新增学员'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.gender"><el-option :value="1" label="男" /><el-option :value="2" label="女" /></el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="form.birthday" type="date" placeholder="选择出生日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="学校"><el-input v-model="form.school" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option :value="1" label="在读" /><el-option :value="2" label="停课" /><el-option :value="3" label="退班" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 绑定家长弹窗 -->
    <el-dialog title="绑定家长" v-model="bindVisible" width="450px">
      <el-form :model="bindForm" label-width="80px">
        <el-form-item label="学员">{{ currentStudent?.name }}</el-form-item>
        <el-form-item label="家长">
          <el-select v-model="bindForm.parentUserId" filterable placeholder="搜索选择家长用户" style="width:100%" @focus="loadUserOptions">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName + ' (' + u.username + ')'" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关系"><el-input v-model="bindForm.relation" placeholder="如 父亲/母亲" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" @click="handleBind" :loading="binding">确认绑定</el-button>
      </template>
    </el-dialog>

    <!-- 转班弹窗 -->
    <el-dialog title="学员转班" v-model="transferVisible" width="400px">
      <el-form label-width="80px">
        <el-form-item label="学员">{{ currentStudent?.name }}</el-form-item>
        <el-form-item label="目标班级">
          <el-select v-model="transferTargetClassId" placeholder="请选择目标班级" style="width:100%">
            <el-option v-for="c in transferClassList" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTransfer" :loading="transferring">确认转班</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showError } from '@/utils/error'
import { studentApi, classApi } from '@/api/edu'
import { userApi } from '@/api/auth'

const loading = ref(false)
const saving = ref(false)
const binding = ref(false)
const transferring = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const tableData = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const bindVisible = ref(false)
const transferVisible = ref(false)
const isEdit = ref(false)
const currentStudent = ref<any>(null)
const form = reactive<any>({ name: '', gender: 1, birthday: '', school: '', contactPhone: '', status: 1 })
const bindForm = reactive({ parentUserId: null as number | null, relation: '父亲' })
const transferTargetClassId = ref<number | null>(null)
const transferClassList = ref<any[]>([])
const userOptions = ref<any[]>([])

async function loadUserOptions() {
  if (userOptions.value.length > 0) return
  try {
    const res = await userApi.list({ pageNum: 1, pageSize: 200 })
    userOptions.value = res.data?.records || []
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  const res = await studentApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
  tableData.value = res.data.records
  total.value = res.data.total
  loading.value = false
}

function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }
function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

function openDialog(row: any) {
  isEdit.value = !!row
  if (row) Object.assign(form, row)
  else Object.assign(form, { name: '', gender: 1, birthday: '', school: '', contactPhone: '', status: 1 })
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    const payload: any = { ...form }
    // 生日空字符串会导致后端 LocalDate 反序列化失败
    if (!payload.birthday) payload.birthday = null
    if (isEdit.value) {
      await studentApi.update(form.id, payload)
      ElMessage.success('学员已更新')
    } else {
      await studentApi.create(payload)
      ElMessage.success('学员已创建')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) { showError(e, '保存失败') } finally { saving.value = false }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定删除该学员？', '提示', { type: 'warning' })
  await studentApi.delete(row.id)
  ElMessage.success('已删除')
  loadData()
}

function openBindDialog(row: any) {
  currentStudent.value = row
  bindForm.parentUserId = null
  bindForm.relation = '父亲'
  bindVisible.value = true
}

async function handleBind() {
  if (!bindForm.parentUserId) { ElMessage.warning('请输入家长用户ID'); return }
  binding.value = true
  try {
    await studentApi.bindParent({
      parentUserId: bindForm.parentUserId,
      studentId: currentStudent.value.id,
      relation: bindForm.relation
    })
    ElMessage.success('家长绑定成功')
    bindVisible.value = false
  } finally { binding.value = false }
}

async function openTransferDialog(row: any) {
  currentStudent.value = row
  transferTargetClassId.value = null
  transferVisible.value = true
  const res = await classApi.list({ pageSize: 100 })
  transferClassList.value = res.data.records
}

async function handleTransfer() {
  if (!transferTargetClassId.value) { ElMessage.warning('请选择目标班级'); return }
  transferring.value = true
  try {
    await studentApi.transfer(currentStudent.value.id, transferTargetClassId.value)
    ElMessage.success('转班成功')
    transferVisible.value = false
    loadData()
  } catch (e) { showError(e, '转班失败') }
  finally { transferring.value = false }
}

async function handleWithdraw(row: any) {
  await ElMessageBox.confirm(`确定将学员"${row.name}"退班？退班申请将提交至财务审核。`, '退班确认', { type: 'warning' })
  try {
    await studentApi.withdraw(row.id)
    ElMessage.success('退班申请已提交，待财务审核')
    loadData()
  } catch (e) { showError(e, '退班操作失败') }
}

onMounted(loadData)
</script>
