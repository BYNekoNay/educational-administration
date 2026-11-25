<template>
  <div>
    <div class="page-header"><h3>课时账户</h3></div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索学员或课程" clearable style="width:260px" />
    </div>
    <el-table :data="filteredData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="courseName" label="课程" min-width="100" />
      <el-table-column prop="totalLessons" label="总课时" width="80" sortable="custom" />
      <el-table-column prop="remainingLessons" label="剩余课时" width="100" sortable="custom">
        <template #default="{ row }">
          <span :class="{ 'balance-warn': (row.remainingLessons || 0) <= 3 }">
            {{ row.remainingLessons || 0 }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="expireDate" label="到期日期" width="120" sortable="custom" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAdjust(row)">调整课时</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="loadData" />

    <el-dialog v-model="adjustVisible" title="人工调整课时" width="480px" :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="学员">{{ current?.studentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ current?.courseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总课时">{{ current?.totalLessons ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="剩余课时">{{ current?.remainingLessons ?? 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="调整方向" prop="direction">
          <el-radio-group v-model="form.direction">
            <el-radio :value="1">增加课时（补课/赠送）</el-radio>
            <el-radio :value="2">扣减课时</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整课时" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="0.5" style="width:160px" />
          <span style="margin-left:12px;color:var(--el-text-color-secondary)">调整后剩余：</span>
          <span :class="{ 'balance-warn': previewAfter < 0 }">{{ previewAfter }}</span>
        </el-form-item>
        <el-form-item label="调整原因" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" show-word-limit
            placeholder="必填，将写入课时流水与操作日志" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitAdjust">确定调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { lessonAccountApi } from '@/api/finance'
import { showError } from '@/utils/error'
import type { LessonAccount, LessonAdjustPayload } from '@/types'

const loading = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const filteredData = computed(() => {
  if (!keyword.value) return tableData.value
  const kw = keyword.value.toLowerCase()
  return tableData.value.filter((r: LessonAccount) =>
    (r.studentName && String(r.studentName).toLowerCase().includes(kw)) ||
    (r.courseName && String(r.courseName).toLowerCase().includes(kw))
  )
})
const tableData = ref<LessonAccount[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)

// ---- 课时人工调整 ----
const adjustVisible = ref(false), submitting = ref(false)
const current = ref<LessonAccount | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<LessonAdjustPayload>({ direction: 1, amount: 0.5, remark: '', bizNo: '' })

const rules = {
  direction: [{ required: true, message: '请选择调整方向', trigger: 'change' }],
  amount: [{ required: true, message: '请输入调整课时', trigger: 'blur' }],
  remark: [{ required: true, message: '请填写调整原因', trigger: 'blur' }],
}

const previewAfter = computed(() => {
  const base = Number(current.value?.remainingLessons ?? 0)
  const amount = Number(form.amount) || 0
  return form.direction === 1
    ? Number((base + amount).toFixed(2))
    : Number((base - amount).toFixed(2))
})

/** 幂等键：每次打开弹窗生成一次，提交失败重试时复用，避免重复扣课时 */
function newBizNo(): string {
  const cryptoObj = globalThis.crypto as Crypto | undefined
  if (cryptoObj?.randomUUID) return cryptoObj.randomUUID()
  return `adj-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

function openAdjust(row: LessonAccount) {
  current.value = row
  form.direction = 1
  form.amount = 0.5
  form.remark = ''
  form.bizNo = newBizNo()
  adjustVisible.value = true
  formRef.value?.clearValidate()
}

async function submitAdjust() {
  if (submitting.value) return
  // formRef 缺失时不提交（fail-safe），避免"校验被跳过却继续扣课时"
  const formEl = formRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return
  if (form.direction === 2 && previewAfter.value < 0) {
    ElMessage.error(`扣减超过剩余课时（当前剩余 ${current.value?.remainingLessons ?? 0}）`)
    return
  }
  submitting.value = true
  try {
    await lessonAccountApi.adjust(current.value!.id!, { ...form })
    ElMessage.success('课时调整成功')
    adjustVisible.value = false
    await loadData()
  } catch (e) {
    showError(e, '课时调整失败')
  } finally {
    submitting.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await lessonAccountApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined,
      // 后端已支持 keyword（按学员姓名/课程名匹配）；清空时传空串而不是残留上一次的关键字
      keyword: keyword.value || '' })
    tableData.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) { showError(e, '加载课时账户失败') }
  finally { loading.value = false }
}

function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

onMounted(loadData)
</script>

<style scoped>
.balance-warn { color: var(--color-error); font-weight: bold; }
</style>
