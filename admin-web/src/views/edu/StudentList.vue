<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>学员管理</h3>
      <el-button type="primary" @click="openDialog(null)">新增学员</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="gender" label="性别" width="70">
        <template #default="{ row }">{{ row.gender === 1 ? '男' : row.gender === 2 ? '女' : '-' }}</template>
      </el-table-column>
      <el-table-column prop="birthday" label="出生日期" width="110" />
      <el-table-column prop="school" label="学校" />
      <el-table-column prop="contactPhone" label="联系电话" width="120" />
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
        <el-form-item label="出生日期"><el-input v-model="form.birthday" placeholder="如 2020-01-01" /></el-form-item>
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
    <el-dialog title="绑定家长" v-model="bindVisible" width="400px">
      <el-form :model="bindForm" label-width="80px">
        <el-form-item label="学员">{{ currentStudent?.name }}</el-form-item>
        <el-form-item label="家长ID"><el-input-number v-model="bindForm.parentUserId" :min="1" /></el-form-item>
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
import { studentApi, classApi } from '@/api/edu'

const loading = ref(false)
const saving = ref(false)
const binding = ref(false)
const transferring = ref(false)
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

async function loadData() {
  loading.value = true
  const res = await studentApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
  tableData.value = res.data.records
  total.value = res.data.total
  loading.value = false
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
    if (isEdit.value) {
      await studentApi.update(form.id, form)
      ElMessage.success('学员已更新')
    } else {
      await studentApi.create(form)
      ElMessage.success('学员已创建')
    }
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
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
  } catch { ElMessage.error('转班失败') }
  finally { transferring.value = false }
}

async function handleWithdraw(row: any) {
  await ElMessageBox.confirm(`确定将学员"${row.name}"退班？退班申请将提交至财务审核。`, '退班确认', { type: 'warning' })
  try {
    await studentApi.withdraw(row.id)
    ElMessage.success('退班申请已提交，待财务审核')
    loadData()
  } catch { ElMessage.error('操作失败') }
}

onMounted(loadData)
</script>
