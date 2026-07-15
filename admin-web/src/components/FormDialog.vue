<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <el-form :model="inner" label-width="92px">
      <el-form-item
        v-for="f in fields"
        :key="f.prop"
        :label="f.label"
        :required="f.required"
      >
        <el-input
          v-if="!f.type || f.type === 'input'"
          v-model="inner[f.prop]"
          :placeholder="f.placeholder"
          :clearable="f.clearable"
        />
        <el-input
          v-else-if="f.type === 'textarea'"
          v-model="inner[f.prop]"
          type="textarea"
          :rows="3"
          :placeholder="f.placeholder"
        />
        <el-input-number
          v-else-if="f.type === 'number'"
          v-model="inner[f.prop]"
          :min="f.min"
          :max="f.max"
          :placeholder="f.placeholder"
          controls-position="right"
          style="width: 100%"
        />
        <el-select
          v-else-if="f.type === 'select'"
          v-model="inner[f.prop]"
          :placeholder="f.placeholder || '请选择'"
          :clearable="f.clearable !== false"
          style="width: 100%"
        >
          <el-option
            v-for="opt in f.options || []"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-date-picker
          v-else-if="f.type === 'date'"
          v-model="inner[f.prop]"
          type="date"
          :value-format="f.valueFormat || 'YYYY-MM-DD'"
          :placeholder="f.placeholder || '选择日期'"
          style="width: 100%"
        />
        <el-switch
          v-else-if="f.type === 'switch'"
          v-model="inner[f.prop]"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { FormField } from './types'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    fields: FormField[]
    /** 初始表单数据（打开弹窗时拷贝进内部模型） */
    form?: Record<string, any>
    saving?: boolean
    width?: string
  }>(),
  {
    title: '',
    saving: false,
    width: '500px',
    form: () => ({})
  }
)

const emit = defineEmits<{
  'update:modelValue': [boolean]
  save: [Record<string, any>]
  cancel: []
}>()

const inner = reactive<Record<string, any>>({})

function syncInner() {
  for (const k of Object.keys(inner)) delete inner[k]
  Object.assign(inner, props.form || {})
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) syncInner()
  }
)

function onCancel() {
  emit('update:modelValue', false)
  emit('cancel')
}

function onSave() {
  for (const f of props.fields) {
    if (f.required) {
      const val = inner[f.prop]
      if (val === undefined || val === null || val === '') {
        ElMessage.warning(`请填写${f.label}`)
        return
      }
    }
  }
  emit('save', { ...inner })
}
</script>
