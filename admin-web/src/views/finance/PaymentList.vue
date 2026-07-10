<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>收费管理</h3>
      <el-button type="primary" @click="showPaymentDialog">新增收费</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="courseName" label="课程" min-width="100" />
      <el-table-column prop="lessonCount" label="课时数" width="80" />
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column prop="payType" label="支付方式" width="100">
        <template #default="{ row }">{{ row.payType === 1 ? '现金' : row.payType === 2 ? '模拟支付' : '其他' }}</template>
      </el-table-column>
      <el-table-column prop="payTime" label="缴费时间" width="170" />
      <el-table-column prop="remark" label="备注" />
    </el-table>
    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="total, prev, pager, next" @change="loadData"
    />

    <!-- 收费登记弹窗 -->
    <el-dialog title="收费登记" v-model="dialogVisible" width="450px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="报名ID"><el-input-number v-model="form.enrollmentId" :min="1" /></el-form-item>
        <el-form-item label="学员">
          <el-select v-model="form.studentId" placeholder="请选择学员" filterable style="width:100%">
            <el-option v-for="s in studentList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" placeholder="请选择课程" filterable style="width:100%">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课时数"><el-input-number v-model="form.lessonCount" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.amount" :min="0" :precision="2" /></el-form-item>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { paymentApi } from '@/api/finance'
import { studentApi, courseApi } from '@/api/edu'

const loading = ref(false), saving = ref(false)
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)
const dialogVisible = ref(false)
const studentList = ref<any[]>([])
const courseList = ref<any[]>([])
const form = reactive<any>({ enrollmentId: 1, studentId: null, courseId: null, lessonCount: 24, amount: 2400, payType: 2, remark: '' })

async function loadOptions() {
  try {
    const [sRes, cRes] = await Promise.all([
      studentApi.list({ pageNum: 1, pageSize: 100 }),
      courseApi.list({ pageNum: 1, pageSize: 100 }),
    ])
    studentList.value = sRes.data?.records || []
    courseList.value = cRes.data?.records || []
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  const res = await paymentApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
  tableData.value = res.data.records; total.value = res.data.total; loading.value = false
}

function showPaymentDialog() {
  if (studentList.value.length === 0) loadOptions()
  dialogVisible.value = true
}

async function handleCreatePayment() {
  saving.value = true
  try {
    await paymentApi.create({
      enrollmentId: form.enrollmentId, studentId: form.studentId, courseId: form.courseId,
      lessonCount: form.lessonCount, amount: form.amount, payType: form.payType,
      payTime: new Date().toISOString().slice(0, 19).replace('T', ' '), remark: form.remark
    })
    ElMessage.success('收费成功，已自动开通课时账户并写入流水')
    dialogVisible.value = false
    loadData()
  } finally { saving.value = false }
}

onMounted(() => { loadOptions(); loadData() })
</script>
