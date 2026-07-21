<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>班级管理</h3>
      <el-button type="primary" @click="openDialog(null)">新增班级</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索班级/课程/教师" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="className" label="班级名称" sortable />
      <el-table-column prop="courseName" label="课程" min-width="120" sortable />
      <el-table-column prop="teacherName" label="教师" min-width="100" sortable />
      <el-table-column prop="maxStudentCount" label="最大人数" width="80" sortable="custom" />
      <el-table-column label="当前人数" width="100">
        <template #default="{ row }">
          <span :style="{
            color: (row.currentStudentCount || 0) > (row.maxStudentCount || 0) ? '#f56c6c'
                 : (row.currentStudentCount || 0) >= (row.maxStudentCount || 0) ? '#e6a23c'
                 : '#67c23a',
            fontWeight: 600
          }">
            {{ row.currentStudentCount ?? 0 }} / {{ row.maxStudentCount || '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="startDate" label="开课日期" width="110" sortable="custom" />
      <el-table-column prop="status" label="状态" width="80" sortable="custom">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" @click="openAddStudent(row)">加入学员</el-button>
            <el-button size="small" type="primary" @click="openStudentList(row)">查看学员</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="sizes, total, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      @change="loadData"
    />

    <!-- 新增/编辑班级弹窗 -->
    <el-dialog :title="isEdit ? '编辑班级' : '新增班级'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="班级名称"><el-input v-model="form.className" /></el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" style="width:100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师">
          <el-select v-model="form.teacherId" style="width:100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.realName || t.username" :value="t.id">
              <span>{{ t.realName || t.username }}</span>
              <el-tag v-for="sp in (t.specialties || [])" :key="sp.id"
                      size="small" type="info" style="margin-left:4px;font-size:10px">
                {{ sp.name }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="最大人数"><el-input-number v-model="form.maxStudentCount" :min="1" /></el-form-item>
        <el-form-item label="开课日期">
          <el-date-picker v-model="form.startDate" type="date" placeholder="选择开课日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
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
    <el-dialog title="加入学员" v-model="addStudentVisible" width="460px" @closed="addForm.studentId = null">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="班级">{{ currentClass?.className }}</el-form-item>
        <el-form-item label="选择学员">
          <el-select
            v-model="addForm.studentId"
            filterable
            remote
            :remote-method="searchStudents"
            :loading="studentSearchLoading"
            placeholder="输入姓名/电话/家长搜索"
            style="width:100%"
            clearable
          >
            <el-option
              v-for="s in studentOptions"
              :key="s.id"
              :label="`${s.name}（ID:${s.id} · ${s.school || '无学校'}）`"
              :value="s.id"
            >
              <span style="float:left">{{ s.name }}</span>
              <span style="float:right; color:#999; font-size:12px">
                {{ s.contactPhone || s.parentName || `ID:${s.id}` }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addStudentVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddStudent" :loading="adding">确认加入</el-button>
      </template>
    </el-dialog>

    <!-- 班级学员管理弹窗 -->
    <el-dialog :title="'班级学员 - ' + (currentClass?.className || '')" v-model="studentListVisible" width="700px">
      <el-table :data="classStudents" border stripe v-loading="studentLoading">
        <el-table-column prop="studentName" label="学员" min-width="100" />
        <el-table-column prop="joinTime" label="加入时间" width="170" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : 'warning'">
              {{ row.status === 1 ? '在班' : row.status === 2 ? '已转出' : '已退出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
              <el-button v-if="row.status === 1" size="small" type="warning" @click="handleTransferStudent(row)">转班</el-button>
              <el-button v-if="row.status === 1" size="small" type="danger" @click="handleWithdrawStudent(row)">退班</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="studentListVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 转班弹窗 -->
    <el-dialog :title="`转班 — 学员：${transferringStudentName || ''}`" v-model="transferVisible" width="420px">
      <el-form label-width="80px">
        <el-form-item label="当前班级">{{ currentClass?.className }}</el-form-item>
        <el-form-item label="目标班级">
          <el-select v-model="targetClassId" placeholder="请选择目标班级" style="width:100%" filterable>
            <el-option v-for="c in allClasses" :key="c.id"
              :label="`${c.className}（${c.courseName || '无课程'} · 教师:${c.teacherName || '未指定'}）`"
              :value="c.id"
              :disabled="c.id === currentClass?.id" />
          </el-select>
        </el-form-item>
      </el-form>
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
import { showError } from '@/utils/error'
import { classApi, studentApi, courseApi, teacherApi } from '@/api/edu'

const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
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
const courses = ref<any[]>([])
const teachers = ref<any[]>([])
const targetClassId = ref<number | null>(null)
const transferringStudentId = ref<number | null>(null)
const transferringStudentName = ref<string>('')
const form = reactive<any>({ className: '', courseId: null, teacherId: null, maxStudentCount: 15, startDate: '', status: 1 })
const addForm = reactive({ studentId: null as number | null })
const studentOptions = ref<any[]>([])
const studentSearchLoading = ref(false)
let studentSearchTimer: number | null = null

async function loadData() {
  loading.value = true
  const res = await classApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
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

async function loadOptions() {
  if (courses.value.length > 0 && teachers.value.length > 0) return
  try {
    const [cRes, tRes] = await Promise.all([
      courseApi.list({ pageNum: 1, pageSize: 100 }),
      teacherApi.list()
    ])
    courses.value = cRes.data?.records || []
    teachers.value = tRes.data || []
  } catch (e) { showError(e, '加载课程或教师数据失败') }
}

async function openDialog(row: any) {
  isEdit.value = !!row
  await loadOptions()
  if (row) Object.assign(form, row)
  else Object.assign(form, { className: '', courseId: null, teacherId: null, maxStudentCount: 15, startDate: '', status: 1 })
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    const payload = { ...form }
    if (!payload.startDate) payload.startDate = null
    if (isEdit.value) {
      await classApi.update(form.id, payload)
      ElMessage.success('班级已更新')
    } else {
      await classApi.create(payload)
      ElMessage.success('班级已创建')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) { showError(e, '保存失败') } finally { saving.value = false }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定删除该班级？', '提示', { type: 'warning' })
  try {
    await classApi.delete(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e) { showError(e, '删除失败') }
}

async function openAddStudent(row: any) {
  currentClass.value = row
  addForm.studentId = null
  studentOptions.value = []
  addStudentVisible.value = true
  // 默认加载前 50 名在读学员作为初始下拉选项
  await searchStudents('')
}

function searchStudents(keyword: string) {
  // 远程搜索防抖
  if (studentSearchTimer) window.clearTimeout(studentSearchTimer)
  studentSearchTimer = window.setTimeout(async () => {
    studentSearchLoading.value = true
    try {
      const res = await studentApi.list({ pageNum: 1, pageSize: 50, keyword: keyword || undefined })
      // 仅展示在读学员
      studentOptions.value = (res.data?.records || []).filter((s: any) => s.status === 1)
    } catch (e) {
      showError(e, '搜索学员失败')
      studentOptions.value = []
    } finally {
      studentSearchLoading.value = false
    }
  }, 300)
}

async function handleAddStudent() {
  if (!addForm.studentId) { ElMessage.warning('请先选择学员'); return }
  adding.value = true
  try {
    await classApi.addStudent(currentClass.value.id, {
      studentId: addForm.studentId,
      status: 1
    })
    ElMessage.success('学员已加入班级')
    addStudentVisible.value = false
  } catch (e) { showError(e, '加入学员失败') } finally { adding.value = false }
}

async function openStudentList(row: any) {
  currentClass.value = row
  studentListVisible.value = true
  studentLoading.value = true
  try {
    const res = await classApi.students(row.id, { pageSize: 100 })
    classStudents.value = res.data.records
  } catch (e) { showError(e, '加载学员列表失败') } finally { studentLoading.value = false }
}

async function handleWithdrawStudent(row: any) {
  await ElMessageBox.confirm(`确定将学员"${row.studentName || row.studentId}"退班？`, '退班确认', { type: 'warning' })
  try {
    await studentApi.withdraw(row.studentId)
    ElMessage.success('退班申请已提交，待财务审核')
    openStudentList(currentClass.value)
  } catch (e) { showError(e, '退班操作失败') }
}

async function handleTransferStudent(row: any) {
  transferringStudentId.value = row.studentId
  transferringStudentName.value = row.studentName || `学员${row.studentId}`
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
  } catch (e) { showError(e, '转班失败') } finally { transferring.value = false }
}

onMounted(loadData)
</script>
