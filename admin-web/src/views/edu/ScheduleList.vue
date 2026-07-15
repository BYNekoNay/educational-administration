<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>排课管理</h3>
      <div>
        <el-button type="success" @click="showBatchDialog">批量排课</el-button>
        <el-button type="primary" @click="openDialog(null)">新增课次</el-button>
      </div>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索班级/教师/教室" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="className" label="班级" min-width="140" sortable />
      <el-table-column prop="teacherName" label="教师" min-width="80" sortable />
      <el-table-column prop="classroomName" label="教室" min-width="100" sortable />
      <el-table-column prop="lessonDate" label="日期" width="110" sortable="custom" />
      <el-table-column prop="startTime" label="开始" width="80" sortable="custom" />
      <el-table-column prop="endTime" label="结束" width="80" sortable="custom" />
      <el-table-column prop="status" label="状态" width="90" sortable="custom">
        <template #default="{ row }">
          <el-tag v-if="row.status===1" type="warning">待上课</el-tag>
          <el-tag v-else-if="row.status===2" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status===3" type="info">已取消</el-tag>
          <el-tag v-else-if="row.status===4" type="danger">已调课</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="total,prev,pager,next" @change="loadData"
    />

    <!-- 新增/编辑课次弹窗 -->
    <el-dialog :title="isEdit?'编辑课次':'新增课次'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="班级">
          <el-select v-model="form.classId" placeholder="请选择班级" filterable style="width:100%">
            <el-option v-for="c in classList" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师">
          <el-select v-model="form.teacherId" placeholder="请选择教师" filterable style="width:100%">
            <el-option v-for="t in teacherList" :key="t.id" :label="t.realName" :value="t.id">
              <span>{{ t.realName }}</span>
              <el-tag v-for="sp in (t.specialties || [])" :key="sp.id"
                      size="small" type="info" style="margin-left:4px;font-size:10px">
                {{ sp.name }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="form.classroomId" placeholder="请选择教室" filterable style="width:100%">
            <el-option v-for="r in roomList" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.lessonDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-time-picker v-model="form.startTime" placeholder="开始时间" value-format="HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-time-picker v-model="form.endTime" placeholder="结束时间" value-format="HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option :value="1" label="待上课" />
            <el-option :value="2" label="已完成" />
            <el-option :value="3" label="已取消" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量排课弹窗 -->
    <el-dialog title="批量排课" v-model="batchVisible" width="700px">
      <div v-for="(item,idx) in batchItems" :key="idx" style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
        <el-select v-model="item.classId" size="small" style="width:120px" placeholder="班级" filterable>
          <el-option v-for="c in classList" :key="c.id" :label="c.className" :value="c.id" />
        </el-select>
        <el-select v-model="item.teacherId" size="small" style="width:100px" placeholder="教师" filterable>
          <el-option v-for="t in teacherList" :key="t.id" :label="t.realName" :value="t.id" />
        </el-select>
        <el-select v-model="item.classroomId" size="small" style="width:100px" placeholder="教室" filterable>
          <el-option v-for="r in roomList" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-date-picker v-model="item.lessonDate" type="date" size="small" value-format="YYYY-MM-DD" style="width:120px" placeholder="日期" />
        <el-input v-model="item.startTime" size="small" style="width:80px" placeholder="开始" />
        <el-input v-model="item.endTime" size="small" style="width:80px" placeholder="结束" />
        <el-button size="small" type="danger" @click="batchItems.splice(idx,1)">X</el-button>
      </div>
      <el-button size="small" @click="batchItems.push({classId:null,teacherId:null,classroomId:null,lessonDate:'',startTime:'',endTime:'',status:1})">+ 添加行</el-button>
      <template #footer>
        <el-button @click="batchVisible=false">取消</el-button>
        <el-button type="primary" @click="handleBatchCreate" :loading="batching">批量创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showError } from '@/utils/error'
import { scheduleApi, classApi, teacherApi, classroomApi } from '@/api/edu'

const loading = ref(false), saving = ref(false), batching = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const tableData = ref<any[]>([]), pageNum = ref(1), pageSize = ref(10), total = ref(0)
const dialogVisible = ref(false), batchVisible = ref(false), isEdit = ref(false)
const classList = ref<any[]>([])
const teacherList = ref<any[]>([])
const roomList = ref<any[]>([])
const form = reactive<any>({ classId: null, teacherId: null, classroomId: null, lessonDate: '', startTime: '', endTime: '', status: 1 })
const batchItems = ref<any[]>([{ classId: null, teacherId: null, classroomId: null, lessonDate: '', startTime: '', endTime: '', status: 1 }])

async function loadOptions() {
  try {
    const [clRes, tRes, rRes] = await Promise.all([
      classApi.list({ pageNum: 1, pageSize: 100 }),
      teacherApi.list(),
      classroomApi.list({ pageNum: 1, pageSize: 100 }),
    ])
    classList.value = clRes.data?.records || []
    teacherList.value = tRes.data || []
    roomList.value = rRes.data?.records || []
  } catch (e) { showError(e, '加载排课选项失败') }
}

async function loadData() {
  loading.value = true
  const r = await scheduleApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
  tableData.value = r.data.records; total.value = r.data.total; loading.value = false
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
  if (row) {
    form.id = row.id
    form.classId = row.classId
    form.teacherId = row.teacherId
    form.classroomId = row.classroomId
    form.lessonDate = row.lessonDate
    form.startTime = row.startTime
    form.endTime = row.endTime
    form.status = row.status
  } else {
    form.id = undefined
    form.classId = null
    form.teacherId = null
    form.classroomId = null
    form.lessonDate = ''
    form.startTime = ''
    form.endTime = ''
    form.status = 1
  }
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      classId: form.classId,
      teacherId: form.teacherId,
      classroomId: form.classroomId,
      lessonDate: form.lessonDate,
      startTime: form.startTime,
      endTime: form.endTime,
      status: form.status,
    }
    if (isEdit.value) {
      await scheduleApi.update(form.id, payload)
      ElMessage.success('已更新')
    } else {
      await scheduleApi.create(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await scheduleApi.delete(row.id)
  ElMessage.success('已删除')
  loadData()
}

function showBatchDialog() { batchVisible.value = true }

async function handleBatchCreate() {
  batching.value = true
  try {
    await scheduleApi.batchCreate(batchItems.value)
    ElMessage.success('批量排课成功')
    batchVisible.value = false
    loadData()
  } finally { batching.value = false }
}

onMounted(() => { loadOptions(); loadData() })
</script>
