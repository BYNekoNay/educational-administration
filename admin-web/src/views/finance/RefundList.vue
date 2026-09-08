<template>
  <div>
    <div class="page-header">
      <h3>退费管理</h3>
      <el-button type="primary" @click="showRefundDialog">新增退费申请</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索学员/申请人" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="amount" label="退费金额" width="110" sortable="custom">
        <template #default="{ row }">¥{{ row.amount ? Number(row.amount).toFixed(2) : '0.00' }}</template>
      </el-table-column>
      <el-table-column prop="lessonCount" label="课时数" width="80" />
      <el-table-column prop="status" label="状态" width="90" sortable="custom">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'warning' : row.status === 2 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '待审核' : row.status === 2 ? '已通过' : '已拒绝' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="applicantName" label="申请人" min-width="80" />
      <el-table-column prop="createTime" label="申请时间" width="170" sortable="custom" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
            <template v-if="row.status === 1">
              <el-button size="small" type="success" @click="handleAudit(row, 2)">通过</el-button>
              <el-button size="small" type="danger" @click="handleAudit(row, 3)">拒绝</el-button>
            </template>
            <span v-else style="color: #909399">—</span>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="loadData" />

    <el-dialog title="新增退费申请" v-model="dialogVisible" width="450px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="学员">
          <el-select v-model="form.studentId" placeholder="请选择学员" filterable style="width:100%">
            <el-option v-for="s in studentList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名记录">
          <el-select v-model="form.enrollmentId" filterable style="width:100%" placeholder="搜索选择报名记录">
            <el-option v-for="e in enrollmentList" :key="e.id" :label="`${e.studentName || '学员' + e.studentId} - ${e.courseName || '课程' + e.courseId}`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="缴费记录">
          <el-select v-model="form.paymentRecordId" filterable style="width:100%" placeholder="搜索选择缴费记录">
            <el-option v-for="p in paymentList" :key="p.id" :label="`${p.studentName || '学员' + p.studentId} ¥${p.amount || 0} (${p.payTime || ''})`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="退费课时数"><el-input-number v-model="form.lessonCount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="退费金额"><el-input-number v-model="form.amount" :min="0" :precision="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateRefund" :loading="saving">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog title="审核通过" v-model="auditVisible" width="420px">
      <el-form label-width="100px">
        <el-form-item label="退费记录"><span>{{ auditRow?.id }}</span></el-form-item>
        <el-form-item label="学员"><span>{{ auditRow?.studentName || auditRow?.studentId }}</span></el-form-item>
        <el-form-item label="退费金额">
          <el-input-number v-model="auditAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <p style="color:#909399;font-size:13px;margin-top:8px">通过后将自动回退课时、写入流水并联动退班</p>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="success" @click="confirmAudit" :loading="auditing">确认通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { refundApi, paymentApi } from '@/api/finance'
import { studentApi, enrollmentApi } from '@/api/edu'
import { showError } from '@/utils/error'

const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const loading = ref(false), saving = ref(false), auditing = ref(false)
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)
const dialogVisible = ref(false)
const studentList = ref<any[]>([])
const enrollmentList = ref<any[]>([])
const paymentList = ref<any[]>([])
const form = reactive<any>({ studentId: null, enrollmentId: null, paymentRecordId: null, lessonCount: 0, amount: 0 })

async function loadOptions() {
  try {
    const [sRes, eRes, pRes] = await Promise.all([
      studentApi.list({ pageNum: 1, pageSize: 100 }),
      enrollmentApi.list({ pageNum: 1, pageSize: 200 }),
      paymentApi.list({ pageNum: 1, pageSize: 200 }),
    ])
    studentList.value = sRes.data?.records || []
    enrollmentList.value = eRes.data?.records || []
    paymentList.value = pRes.data?.records || []
  } catch (e) { showError(e, '加载选项数据失败') }
}

const auditVisible = ref(false), auditRow = ref<any>(null), auditAmount = ref(0)

async function loadData() {
  loading.value = true
  const res = await refundApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
  tableData.value = res.data.records; total.value = res.data.total; loading.value = false
}

function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }
function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

function showRefundDialog() {
  if (studentList.value.length === 0) loadOptions()
  dialogVisible.value = true
}

async function handleCreateRefund() {
  if (!form.enrollmentId) { ElMessage.warning('请选择报名记录'); return }
  if (!form.paymentRecordId) { ElMessage.warning('请选择缴费记录'); return }
  saving.value = true
  try {
    await refundApi.create({ ...form })
    ElMessage.success('退费申请已提交')
    dialogVisible.value = false
    loadData()
  } catch (e: any) { showError(e, '提交失败') } finally { saving.value = false }
}

function handleAudit(row: any, status: number) {
  if (status === 3) {
    ElMessageBox.confirm('确认拒绝该申请？', '拒绝确认', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
      .then(() => doAudit(row.id, 3, 0))
      .catch((e: any) => { if (e !== 'cancel' && e !== 'close') showError(e, '操作失败') })
  } else {
    auditRow.value = row; auditAmount.value = row.amount || 0; auditVisible.value = true
  }
}

async function confirmAudit() {
  auditing.value = true
  try {
    await doAudit(auditRow.value.id, 2, auditAmount.value)
    auditVisible.value = false
  } catch (e: any) {
    showError(e, '审核失败')
  } finally {
    auditing.value = false
  }
}

async function doAudit(id: number, status: number, refundAmount: number) {
  await refundApi.audit(id, { status, refundAmount })
  ElMessage.success(status === 2 ? '审核通过：已回退课时、写入流水并退班' : '已拒绝')
  loadData()
}

onMounted(() => { loadOptions(); loadData() })
</script>
