<template>
  <el-dialog :model-value="modelValue" title="人工调整课时" width="480px" :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)">
    <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
      <el-descriptions-item label="学员">{{ account?.studentName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="课程">{{ account?.courseName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="总课时">{{ account?.totalLessons ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="剩余课时">{{ account?.remainingLessons ?? 0 }}</el-descriptions-item>
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
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitAdjust">确定调整</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { lessonAccountApi } from '@/api/finance'
import { showError } from '@/utils/error'
import type { LessonAccount, LessonAdjustPayload } from '@/types'

const props = defineProps<{
  modelValue: boolean
  account: LessonAccount | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'adjusted'): void
}>()

const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<LessonAdjustPayload>({ direction: 1, amount: 0.5, remark: '', bizNo: '' })

const rules = {
  direction: [{ required: true, message: '请选择调整方向', trigger: 'change' }],
  amount: [{ required: true, message: '请输入调整课时', trigger: 'blur' }],
  remark: [{ required: true, message: '请填写调整原因', trigger: 'blur' }],
}

const previewAfter = computed(() => {
  const base = Number(props.account?.remainingLessons ?? 0)
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

// 每次打开都复位，避免上次取消/提交后的残留值被用到新目标
watch(() => props.modelValue, (open) => {
  if (!open) return
  form.direction = 1
  form.amount = 0.5
  form.remark = ''
  form.bizNo = newBizNo()
  formRef.value?.clearValidate()
})

async function submitAdjust() {
  if (submitting.value) return
  // formRef 缺失时不提交（fail-safe），避免"校验被跳过却继续扣课时"
  const formEl = formRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) { ElMessage.warning('请填写调整原因并确认课时数'); return }
  if (form.direction === 2 && previewAfter.value < 0) {
    ElMessage.error(`扣减超过剩余课时（当前剩余 ${props.account?.remainingLessons ?? 0}）`)
    return
  }
  if (!props.account?.id) return
  submitting.value = true
  try {
    await lessonAccountApi.adjust(props.account.id, { ...form })
    ElMessage.success('课时调整成功')
    emit('update:modelValue', false)
    emit('adjusted')
  } catch (e) {
    showError(e, '课时调整失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.balance-warn {
  color: var(--accent-rose, #dc2626);
  font-weight: 600;
}
</style>
