<template>
  <div class="upload-file">
    <el-upload
      :list-type="listType"
      :file-list="fileList"
      :multiple="multiple"
      :limit="limit"
      :accept="accept"
      :auto-upload="true"
      :http-request="customUpload"
      :on-remove="handleRemove"
      :on-exceed="handleExceed"
    >
      <el-button type="primary" :loading="uploading">
        <el-icon><Upload /></el-icon> 选择文件
      </el-button>
      <template #tip v-if="tip">
        <div class="el-upload__tip">{{ tip }}</div>
      </template>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import request from '@/api/request'

const props = withDefaults(
  defineProps<{
    /** 已上传文件 URL 列表（v-model） */
    modelValue: string[]
    /** 上传接口，例如 /api/edu/attachments/upload */
    action: string
    accept?: string
    multiple?: boolean
    limit?: number
    listType?: 'text' | 'picture-card'
    tip?: string
    /** 上传字段名，默认 file */
    fieldName?: string
  }>(),
  {
    accept: '',
    multiple: false,
    limit: 5,
    listType: 'text',
    fieldName: 'file'
  }
)

const emit = defineEmits<{
  'update:modelValue': [string[]]
  success: [string]
}>()

const uploading = ref(false)
const fileList = ref<any[]>([])

watch(
  () => props.modelValue,
  (val) => {
    if (val && val.length && fileList.value.length === 0) {
      fileList.value = val.map((url, i) => ({ name: `文件${i + 1}`, url }))
    }
  },
  { immediate: true }
)

function extractUrl(res: any): string {
  const data = res?.data ?? res
  return data?.url ?? data?.fileUrl ?? (typeof data === 'string' ? data : '')
}

function pushUrl(url: string) {
  emit('update:modelValue', [...props.modelValue, url])
  emit('success', url)
}

async function customUpload(options: any) {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append(props.fieldName, options.file)
    const res = await request.post(props.action, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const url = extractUrl(res)
    if (!url) {
      ElMessage.error(res?.data?.message || '上传失败')
      options.onError(new Error('upload failed'))
      return
    }
    pushUrl(String(url))
    options.onSuccess(res)
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
    options.onError(e)
  } finally {
    uploading.value = false
  }
}

function handleRemove(_file: any, list: any[]) {
  const urls = list
    .map((f) => f.url || f.response?.data?.url || f.response?.url)
    .filter(Boolean)
    .map(String)
  emit('update:modelValue', urls)
}

function handleExceed() {
  ElMessage.warning(`最多上传 ${props.limit} 个文件`)
}
</script>

<style scoped>
.upload-file {
  width: 100%;
}
</style>
