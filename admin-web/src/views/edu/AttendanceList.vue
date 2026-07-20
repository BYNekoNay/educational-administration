<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 考勤管理 tab -->
      <el-tab-pane label="考勤管理" name="attendance">
        <div class="page-header">
          <h3>考勤管理</h3>
          <el-button type="primary" @click="showAddDialog" :icon="Plus">新增考勤</el-button>
        </div>
        <div style="margin-bottom:12px;display:flex;gap:8px">
          <el-input v-model="keyword" placeholder="搜索学员或课次" clearable style="width:260px" />
        </div>
        <el-table :data="filteredData" border stripe v-loading="loading" @sort-change="handleSortChange">
          <el-table-column prop="id" label="ID" width="70" sortable="custom" />
          <el-table-column prop="lessonInfo" label="课次信息" min-width="200" sortable />
          <el-table-column prop="studentName" label="学员" min-width="80" sortable />
          <el-table-column prop="status" label="考勤状态" width="100" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deductLessons" label="扣课时" width="80" sortable="custom" />
          <el-table-column prop="checkTime" label="考勤时间" width="170" sortable="custom" />
          <el-table-column prop="remark" label="备注" min-width="120" sortable />
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
      </el-tab-pane>

      <!-- 请假审核 tab -->
      <el-tab-pane label="请假审核" name="leaveAudit">
        <div class="page-header">
          <h3>请假审核</h3>
        </div>
        <el-table :data="leaveList" border stripe v-loading="leaveLoading">
          <el-table-column prop="studentName" label="学生" min-width="100" />
          <el-table-column prop="lessonDate" label="请假日期" min-width="120">
            <template #default="{ row }">{{ row.lessonDate || '-' }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="请假原因" min-width="180" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="leaveStatusType(row.status)">{{ leaveStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <el-button type="success" size="small" @click="handleApprove(row)">通过</el-button>
                <el-button type="danger" size="small" @click="showRejectDialog(row)">拒绝</el-button>
              </template>
              <span v-else style="color: #999; font-size: 13px">已处理</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="leavePageNum" :total="leaveTotal" :page-size="leavePageSize"
          layout="total,prev,pager,next" @current-change="loadLeaveData" style="margin-top:16px"
        />
      </el-tab-pane>
    </el-tabs>

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

    <!-- 拒绝请假弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝请假" width="420px">
      <el-form label-width="80px">
        <el-form-item label="拒绝原因">
          <el-input
            v-model="rejectRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入拒绝原因（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="handleReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { attendanceApi, scheduleApi, studentApi, leaveRequestApi } from '@/api/edu'
import { ElMessage, ElMessageBox } from 'element-plus'

// ─── 考勤管理 ───
const activeTab = ref('attendance')
const tableData = ref<any[]>([])
const loading = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const filteredData = computed(() => {
  if (!keyword.value) return tableData.value
  const kw = keyword.value.toLowerCase()
  return tableData.value.filter((r: any) =>
    (r.studentName && String(r.studentName).toLowerCase().includes(kw)) ||
    (r.lessonInfo && String(r.lessonInfo).toLowerCase().includes(kw))
  )
})
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
    const r = await attendanceApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
    tableData.value = r.data.records
    total.value = r.data.total
  } catch (e) { showError(e, '加载考勤数据失败') }
  finally { loading.value = false }
}

function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
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
  } catch (e: any) { showError(e, '提交失败') }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  try { await attendanceApi.delete(id); ElMessage.success('删除成功'); loadData() }
  catch (e) { if (e !== 'cancel') showError(e, '删除失败') }
}

// ─── 请假审核 ───
const leaveList = ref<any[]>([])
const leaveLoading = ref(false)
const leavePageNum = ref(1)
const leavePageSize = ref(10)
const leaveTotal = ref(0)
const rejectDialogVisible = ref(false)
const rejectRemark = ref('')
const rejectingRow = ref<any>(null)

function leaveStatusType(s: number) {
  return s === 1 ? 'warning' : s === 2 ? 'success' : 'danger'
}
function leaveStatusText(s: number) {
  return { 1: '待审核', 2: '已通过', 3: '已拒绝' }[s] || s
}

async function loadLeaveData() {
  leaveLoading.value = true
  try {
    const r = await leaveRequestApi.list({ pageNum: leavePageNum.value, pageSize: leavePageSize.value })
    leaveList.value = r.data?.records || []
    leaveTotal.value = r.data?.total || 0
  } catch (e) { showError(e, '加载请假数据失败') }
  finally { leaveLoading.value = false }
}

async function handleApprove(row: any) {
  try {
    await ElMessageBox.confirm('确定通过该请假申请？', '确认', { type: 'info' })
  } catch { return }
  try {
    await leaveRequestApi.audit(row.id, { status: 2, remark: '' })
    ElMessage.success('已通过')
    loadLeaveData()
  } catch (e) { showError(e, '审核失败') }
}

function showRejectDialog(row: any) {
  rejectingRow.value = row
  rejectRemark.value = ''
  rejectDialogVisible.value = true
}

async function handleReject() {
  if (!rejectingRow.value) return
  try {
    await leaveRequestApi.audit(rejectingRow.value.id, { status: 3, remark: rejectRemark.value })
    ElMessage.success('已拒绝')
    rejectDialogVisible.value = false
    loadLeaveData()
  } catch (e) { showError(e, '审核失败') }
}

// 切换到请假审核 tab 时自动加载数据
watch(activeTab, (val) => {
  if (val === 'leaveAudit' && leaveList.value.length === 0) {
    loadLeaveData()
  }
})

// ─── 通用 ───
function showError(e: any, msg: string) {
  const detail = e?.response?.data?.message || e?.message || ''
  ElMessage.error(detail ? `${msg}: ${detail}` : msg)
}

onMounted(() => { loadOptions(); loadData() })
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px }
.page-header h3 { margin: 0 }
</style>
