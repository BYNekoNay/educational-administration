<template>
  <el-button :type="type" :loading="loading" :icon="Download" @click="handleExport">
    {{ label }}
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import axios from 'axios'

// 单独实例：仅注入鉴权头，不挂载响应拦截器，
// 避免 blob 响应（无 code 字段）被统一拦截器误判为失败。
const exportAxios = axios.create({ baseURL: '/api', timeout: 30000 })
exportAxios.interceptors.request.use((config) => {
  const token = localStorage.getItem('token') || ''
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

const props = withDefaults(
  defineProps<{
    /** 导出接口路径（相对于 /api），例如 /api/edu/students/export */
    url: string
    /** 下载文件名（后端 Content-Disposition 优先） */
    filename?: string
    /** 查询参数（GET 拼 query，POST 放 body） */
    params?: Record<string, any>
    method?: 'get' | 'post'
    label?: string
    type?: 'primary' | 'default' | 'success' | 'warning' | 'danger' | 'info'
  }>(),
  {
    filename: '',
    params: () => ({}),
    method: 'get',
    label: '导出',
    type: 'primary'
  }
)

const loading = ref(false)

function parseFilename(disposition?: string, fallback = 'export.xlsx'): string {
  if (disposition) {
    const m = disposition.match(/filename\*?=(?:UTF-8'')?["']?([^"';]+)/i)
    if (m) return decodeURIComponent(m[1])
  }
  return props.filename || fallback
}

async function handleExport() {
  loading.value = true
  try {
    const cfg = { responseType: 'blob' as const }
    const res = props.method === 'post'
      ? await exportAxios.post(props.url, props.params, cfg)
      : await exportAxios.get(props.url, { params: props.params, ...cfg })

    const blob = res.data as Blob
    if (blob && blob.type.includes('application/json')) {
      const text = await blob.text()
      const json = JSON.parse(text)
      ElMessage.error(json.message || '导出失败')
      return
    }
    const disposition = (res.headers as any)?.['content-disposition'] as string | undefined
    const name = parseFilename(disposition)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    loading.value = false
  }
}
</script>
