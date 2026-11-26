<template>
  <el-button :type="type" :loading="loading" :icon="Download" @click="handleExport">
    {{ label }}
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import request from '@/api/request'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { getErrorMessage } from '@/utils/error'

// POST 导出无法走 <a href> 直链（仅 GET），保留独立 axios 实例做 blob 回退：
// 仅注入鉴权头，不挂响应拦截器，避免 blob 响应（无 code 字段）被统一拦截器误判。
const exportAxios = axios.create({ baseURL: '/api', timeout: 120000 })
exportAxios.interceptors.request.use((config) => {
  const token = localStorage.getItem('token') || ''
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 第六轮改造：blob 下载在本机 Chrome 会触发 Office 文件安全扫描，下载停在
// .crdownload 永不结束（120 字节假 xlsx 同样复现，txt/csv 正常——非站点缺陷）。
// 改直链下载：先用 Bearer 鉴权换 60s 一次性下载票据（绑定端点、取出即删），
// 再 <a href> 走浏览器原生下载通道（Content-Disposition 命名，由后端已设置）。

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
    if (m) {
      // 非法百分号编码（如裸 %）会让 decodeURIComponent 抛错，
      // 不能因此中断下载——回退用原始文件名
      try { return decodeURIComponent(m[1]) } catch { return m[1] }
    }
  }
  return props.filename || fallback
}

async function handleExport() {
  loading.value = true
  try {
    if (props.method === 'post') {
      // POST 无法直链（<a href> 仅 GET），走 blob 回退（当前无调用方使用）
      await downloadViaBlob()
      return
    }
    // 直链下载：先换一次性票据（绑定端点、60s、取出即删），再 <a href> 走
    // 浏览器原生下载通道——不经 blob，绕开 Office 下载扫描卡 .crdownload
    const endpoint = `/api${props.url}`
    const res = await request.post('/auth/download-ticket', { endpoint })
    const ticket = (res.data as any)?.ticket || ''
    if (!ticket) {
      ElMessage.error('获取下载票据失败')
      return
    }
    const qs = new URLSearchParams({ downloadTicket: ticket })
    for (const [k, v] of Object.entries(props.params || {})) {
      if (v !== undefined && v !== null && v !== '') qs.append(k, String(v))
    }
    const a = document.createElement('a')
    a.href = `${endpoint}?${qs.toString()}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    // 原生下载由浏览器接管（文件名来自后端 Content-Disposition），无需 revoke
  } catch (e: any) {
    // HTTP 401：与主实例 request.ts 保持一致——清除登录态并跳登录页
    if (e?.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
      return
    }
    ElMessage.error(getErrorMessage(e, '导出失败'))
  } finally {
    loading.value = false
  }
}

/** POST 导出的 blob 回退通道（保留组件能力；blob 在部分浏览器会触发 Office 下载扫描） */
async function downloadViaBlob() {
  const cfg = { responseType: 'blob' as const }
  const res = await exportAxios.post(props.url, props.params, cfg)
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
  // 必须延时释放：同步 revoke 会让 Chrome 在 blob 尚未落盘前丢掉引用，
  // 表现为下载停在 .crdownload 且永不结束
  setTimeout(() => window.URL.revokeObjectURL(url), 30000)
}
</script>
