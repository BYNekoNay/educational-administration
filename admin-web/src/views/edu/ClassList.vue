<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>班级管理</h3>
      <el-button type="primary" @click="openDialog(null)">新增班级</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="className" label="班级名称" />
      <el-table-column prop="courseId" label="课程ID" width="80" />
      <el-table-column prop="teacherId" label="教师ID" width="80" />
      <el-table-column prop="maxStudentCount" label="最大人数" width="80" />
      <el-table-column prop="startDate" label="开课日期" width="110" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" @click="openAddStudent(row)">加入学员</el-button>
          <el-button size="small" type="primary" @click="openStudentList(row)">查看学员</el-button>
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

    <!-- 新增/编辑班级弹窗 -->
    <el-dialog :title="isEdit ? '编辑班级' : '新增班级'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="班级名称"><el-input v-model="form.className" /></el-form-item>
        <el-form-item label="课程ID"><el-input-number v-model="form.courseId" :min="1" /></el-form-item>
        <el-form-item label="教师ID"><el-input-number v-model="form.teacherId" :min="1" /></el-form-item>
        <el-form-item label="最大人数"><el-input-number v-model="form.maxStudentCount" :min="1" /></el-form-item>
        <el-form-item label="开课日期"><el-input v-model="form.startDate" placeholder="如 2026-07-01" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option :value="1" label="启用" /><el-option :value="0" label="停用" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 加入学员弹窗 -->
    <el-dialog title="加入学员" v-model="addStudentVisible" width="400px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="班级">{{ currentClass?.className }}</el-form-item>
        <el-form-item label="学员ID"><el-input-number v-model="addForm.studentId" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addStudentVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddStudent" :loading="adding">确认加入</el-button>
      </template>
    </el-dialog>

    <!-- 班级学员管理弹窗 -->
    <el-dialog :title="'班级学员 - ' + (currentClass?.className || '')" v-model="studentListVisible" width="700px">
      <el-table :data="classStudents" border stripe v-loading="studentLoading">
        <el-table-column prop="studentId" label="学员ID" width="80" />
        <el-table-column prop="joinTime" label="加入时间" width="170" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : 'warning'">
              {{ row.status === 1 ? '在班' : row.status === 2 ? '已转出' : '已退出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" type="warning" @click="handleTransferStudent(row)">转班</el-button>
            <el-button v-if="row.status === 1" size="small" type="danger" @click="handleWithdrawStudent(row)">退班</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="studentListVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 转班弹窗 -->
    <el-dialog title="选择目标班级" v-model="transferVisible" width="400px">
      <el-select v-model="targetClassId" placeholder="请选择目标班级" style="width:100%">
        <el-option v-for="c in allClasses" :key="c.id" :label="c.className" :value="c.id"
          :disabled="c.id === currentClass?.id" />
      </el-select>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmTransfer" :loading="transferring">确认转班</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { classApi, studentApi } from '@/api/edu'

const loading = ref(false)
const saving = ref(false)
const adding = ref(false)
const studentLoading = ref(false)
const transferring = ref(false)
const tableData = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const addStudentVisible = ref(false)
const studentListVisible = ref(false)
const transferVisible = ref(false)
const isEdit = ref(false)
const currentClass = ref<any>(null)
const classStudents = ref<any[]>([])
const allClasses = ref<any[]>([])
const targetClassId = ref<number | null>(null)
const transferringStudentId = ref<number | null>(null)
const form = reactive<any>({ className: '', courseId: 1, teacherId: 1, maxStudentCount: 15, startDate: '', status: 1 })
const addForm = reactive({ studentId: null as number | null })

async function loadData() {
  loading.value = true
  const res = await classApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
  tableData.value = res.data.records
  total.value = res.data.total
  loading.value = false
}

function openDialog(row: any) {
  isEdit.value = !!row
  if (row) Object.assign(form, row)
  else Object.assign(form, { className: '', courseId: 1, teacherId: 1, maxStudentCount: 15, startDate: '', status: 1 })
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (isEdit.value) {
      await classApi.update(form.id, form)
      ElMessage.success('班级已更新')
    } else {
      await classApi.create(form)
      ElMessage.success('班级已创建')
    }
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定删除该班级？', '提示', { type: 'warning' })
  await classApi.delete(row.id)
  ElMessage.success('已删除')
  loadData()
}

function openAddStudent(row: any) {
  currentClass.value = row
  addForm.studentId = null
  addStudentVisible.value = true
}

async function handleAddStudent() {
  if (!addForm.studentId) { ElMessage.warning('请输入学员ID'); return }
  adding.value = true
  try {
    await classApi.addStudent(currentClass.value.id, {
      studentId: addForm.studentId,
      status: 1
    })
    ElMessage.success('学员已加入班级')
    addStudentVisible.value = false
  } finally { adding.value = false }
}

async function openStudentList(row: any) {
  currentClass.value = row
  studentListVisible.value = true
  studentLoading.value = true
  try {
    const res = await classApi.students(row.id, { pageSize: 100 })
    classStudents.value = res.data.records
  } catch { ElMessage.error('加载学员列表失败') }
  finally { studentLoading.value = false }
}

async function handleWithdrawStudent(row: any) {
  await ElMessageBox.confirm(`确定将学员(ID=${row.studentId})退班？`, '退班确认', { type: 'warning' })
  try {
    await studentApi.withdraw(row.studentId)
    ElMessage.success('退班申请已提交，待财务审核')
    openStudentList(currentClass.value)
  } catch { ElMessage.error('操作失败') }
}

async function handleTransferStudent(row: any) {
  transferringStudentId.value = row.studentId
  targetClassId.value = null
  transferVisible.value = true
  // 加载可选班级列表
  const res = await classApi.list({ pageSize: 100 })
  allClasses.value = res.data.records
}

async function confirmTransfer() {
  if (!targetClassId.value || !transferringStudentId.value) {
    ElMessage.warning('请选择目标班级')
    return
  }
  transferring.value = true
  try {
    await studentApi.transfer(transferringStudentId.value, targetClassId.value)
    ElMessage.success('转班成功')
    transferVisible.value = false
    openStudentList(currentClass.value)
  } catch { ElMessage.error('转班失败') }
  finally { transferring.value = false }
}

onMounted(loadData)
</script>
