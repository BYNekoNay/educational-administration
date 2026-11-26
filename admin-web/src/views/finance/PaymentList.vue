<template>
  <div>
    <div class="page-header">
      <h3>收费管理</h3>
      <el-button type="primary" @click="showPaymentDialog">新增收费</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索学员/课程" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="courseName" label="课程" min-width="100" />
      <el-table-column prop="lessonCount" label="课时数" width="80" sortable="custom" />
      <el-table-column prop="amount" label="金额" width="100" sortable="custom" />
      <el-table-column prop="payType" label="支付方式" width="100">
        <template #default="{ row }">{{ row.payType === 1 ? '现金' : row.payType === 2 ? '模拟支付' : '其他' }}</template>
      </el-table-column>
      <el-table-column prop="payTime" label="缴费时间" width="170" sortable="custom" />
      <el-table-column prop="remark" label="备注" />
    </el-table>
    <el-pagination v-if="total > 0"
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      @change="loadData"
    />

    <!-- 收费登记弹窗 -->
    <el-dialog title="收费登记" v-model="dialogVisible" width="450px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="报名记录">
          <el-select v-model="form.enrollmentId" filterable style="width:100%" placeholder="搜索选择报名记录" @change="onEnrollmentChange">
            <el-option v-for="e in payableEnrollments" :key="e.id" :label="`${e.studentName || '学员' + e.studentId} - ${e.courseName || '课程' + e.courseId}`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学员">
          <el-select v-model="form.studentId" placeholder="由报名记录自动带出" filterable disabled style="width:100%">
            <el-option v-for="s in studentList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" placeholder="由报名记录自动带出" filterable disabled style="width:100%">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课时数"><el-input-number v-model="form.lessonCount" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.amount" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="form.payType"><el-option :value="2" label="模拟支付" /><el-option :value="1" label="现金" /><el-option :value="3" label="其他" /></el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreatePayment" :loading="saving">确认收费并开通课时</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { showError } from '@/utils/error'
import { paymentApi, financeOptionApi } from '@/api/finance'
import type { Course, Enrollment, PaymentRecord, Student } from '@/types'

const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const loading = ref(false), saving = ref(false)
const tableData = ref<PaymentRecord[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)
const dialogVisible = ref(false)
const studentList = ref<Student[]>([])
const courseList = ref<Course[]>([])
const enrollmentList = ref<Enrollment[]>([])
// 后端仅允许 status=2(审核通过)/3(已缴费，续费) 的报名缴费，其余状态会 409，这里提前过滤避免无效选项
const payableEnrollments = computed(() => enrollmentList.value.filter((e: Enrollment) => e.status === 2 || e.status === 3))
// 后端会强制用报名记录的 studentId/courseId 覆盖前端传值，选择报名后自动带出，禁止手工改动
function onEnrollmentChange(id: number | null) {
  const e = enrollmentList.value.find((x: Enrollment) => x.id === id)
  form.studentId = e ? e.studentId : null
  form.courseId = e ? e.courseId : null
  // 原实现把课时/金额写死 24 / 2400，与所选课程无关；改为按课程单价联动
  applyCoursePricing(e?.courseId ?? null)
}

// 单价 = 课程总价 / 课程总课时；默认买 24 课时（不超过课程总课时）
function applyCoursePricing(courseId: number | null | undefined) {
  const c = courseList.value.find((x: Course) => x.id === courseId)
  const total = Number(c?.totalLessons || 0)
  const price = Number(c?.price || 0)
  if (!total || !price) {
    form.lessonCount = 24
    form.amount = 2400
    return
  }
  const count = Math.min(24, total)
  form.lessonCount = count
  form.amount = Math.round((price / total) * count * 100) / 100
}
const form = reactive<Partial<PaymentRecord>>({ enrollmentId: null, studentId: null, courseId: null, lessonCount: 24, amount: 2400, payType: 2, remark: '' })

// 用户手工改课时数时，金额跟着重算（仍可再手工改金额）。
// 必须放在 form 定义之后：watch 的 getter 在 setup 阶段会立即执行一次
watch(() => form.lessonCount, (n) => {
  const c = courseList.value.find((x: Course) => x.id === form.courseId)
  const total = Number(c?.totalLessons || 0)
  const price = Number(c?.price || 0)
  if (total && price && n) {
    form.amount = Math.round((price / total) * Number(n) * 100) / 100
  }
})

async function loadOptions() {
  // 财务对 /api/edu/* 一律 403（只能超管/教务），改调财务专用只读下拉。
  // 用 allSettled：原 Promise.all 任一失败即整体 reject，连成功的响应也被丢弃，
  // 导致弹窗里学员/课程/报名全空。
  const [sRes, cRes, eRes] = await Promise.allSettled([
    financeOptionApi.students(),
    financeOptionApi.courses(),
    financeOptionApi.enrollments(),
  ])
  if (sRes.status === 'fulfilled') studentList.value = (sRes.value.data || []) as any
  if (cRes.status === 'fulfilled') courseList.value = (cRes.value.data || []) as any
  if (eRes.status === 'fulfilled') enrollmentList.value = (eRes.value.data || []) as any
  const failed = [sRes, cRes, eRes].filter(r => r.status === 'rejected').length
  if (failed) ElMessage.warning(`部分下拉数据加载失败（${failed} 项）`)
}

async function loadData() {
  loading.value = true
  try {
    const res = await paymentApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined,
      // 后端已支持 keyword（按学员姓名/课程名匹配）；清空时传空串而不是残留上一次的关键字
      keyword: keyword.value || '' })
    tableData.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) { showError(e, '加载收费记录失败') }
  finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }
function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

function formatLocalTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  // 必须输出 ISO-8601（日期与时间之间为 'T'）：后端 payTime 为 LocalDateTime 且无 @JsonFormat/全局日期配置，
  // Jackson 仅接受 'T' 分隔符，空格分隔会被拒绝为 400，导致管理端收费始终失败
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function showPaymentDialog() {
  // 每次打开都复位表单，避免上次取消/提交后的残留值被误用到新收费单
  form.enrollmentId = null
  form.studentId = null
  form.courseId = null
  form.lessonCount = 24
  form.amount = 2400
  form.payType = 2
  form.remark = ''
  if (studentList.value.length === 0) loadOptions()
  dialogVisible.value = true
}

async function handleCreatePayment() {
  if (form.enrollmentId == null) { ElMessage.warning('请选择报名记录'); return }
  if (form.amount == null || Number(form.amount) <= 0) { ElMessage.warning('缴费金额必须大于零'); return }
  saving.value = true
  try {
    await paymentApi.create({
      enrollmentId: form.enrollmentId, studentId: form.studentId, courseId: form.courseId,
      lessonCount: form.lessonCount, amount: form.amount, payType: form.payType,
      payTime: formatLocalTime(new Date()), remark: form.remark
    })
    ElMessage.success('收费成功，已自动开通课时账户并写入流水')
    dialogVisible.value = false
    pageNum.value = 1
    loadData()
  } catch (e) {
    showError(e, '收费失败')
  } finally { saving.value = false }
}

onMounted(() => { loadData() })
</script>
