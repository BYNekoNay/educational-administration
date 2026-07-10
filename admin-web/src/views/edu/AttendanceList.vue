<template>
  <div>
    <div class="page-header">
      <h3>考勤管理</h3>
      <el-button type="primary" @click="showAddDialog" :icon="Plus">新增考勤</el-button>
    </div>
    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="lessonInfo" label="课次信息" min-width="200" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="status" label="考勤状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deductLessons" label="扣课时" width="80" />
      <el-table-column prop="checkTime" label="考勤时间" width="170" />
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="pageNum" :total="total" :page-size="pageSize"
      layout="total,prev,pager,next" @current-change="loadData" style="margin-top:16px"
    />

    <!-- 新增考勤弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增考勤记录" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="课次">
          <el-select v-model="form.lessonId" placeholder="请选择课次" filterable style="width:100%">
            <el-option
              v-for="s in scheduleList"
              :key="s.id"
              :label="scheduleOptionLabel(s)"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学员">
          <el-select v-model="form.studentId" placeholder="请选择学员" filterable style="width:100%">
            <el-option v-for="st in studentList" :key="st.id" :label="st.name" :value="st.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考勤状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option :value="1" label="到课" />
            <el-option :value="2" label="迟到" />
            <el-option :value="3" label="请假" />
            <el-option :value="4" label="缺勤" />
          </el-select>
        </el-form-item>
        <el-form-item label="扣课时">
          <el-input-number v-model="form.deductLessons" :min="0" :precision="1" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { attendanceApi, scheduleApi, studentApi } from '@/api/edu'
import { ElMessage, ElMessageBox } from 'element-plus'

const tableData = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const scheduleList = ref<any[]>([])
const studentList = ref<any[]>([])
const form = ref({ lessonId: null as number | null, studentId: null as number | null, status: 1, deductLessons: 1, remark: '' })

function statusType(s: number) { return s === 1 ? 'success' : s === 2 ? 'warning' : s === 3 ? 'info' : 'danger' }
function statusText(s: number) { return { 1: '到课', 2: '迟到', 3: '请假', 4: '缺勤' }[s] || s }

function scheduleOptionLabel(s: any): string {
  const cn = s.className || ''
  const date = s.lessonDate || ''
  const st = s.startTime || ''
  return `${cn} ${date} ${st}`.trim()
}

async function loadOptions() {
  try {
    const [sRes, stRes] = await Promise.all([
      scheduleApi.list({ pageNum: 1, pageSize: 200 }),
      studentApi.list({ pageNum: 1, pageSize: 100 }),
    ])
    scheduleList.value = sRes.data?.records || []
    studentList.value = stRes.data?.records || []
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const r = await attendanceApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = r.data.records
    total.value = r.data.total
  } catch (e) { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

function showAddDialog() {
  form.value = { lessonId: null, studentId: null, status: 1, deductLessons: 1, remark: '' }
  dialogVisible.value = true
  // 确保下拉数据已加载
  if (scheduleList.value.length === 0) loadOptions()
}

async function handleSubmit() {
  try {
    await attendanceApi.create(form.value)
    ElMessage.success('提交成功')
    dialogVisible.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e.response?.data?.message || '提交失败') }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  try { await attendanceApi.delete(id); ElMessage.success('删除成功'); loadData() }
  catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

onMounted(() => { loadOptions(); loadData() })
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px }
.page-header h3 { margin: 0 }
</style>
