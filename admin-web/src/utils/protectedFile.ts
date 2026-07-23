import axios from 'axios'

export function normalizeProtectedFileUrl(url: string) {
  return url.startsWith('/files/') ? `/api${url}` : url
}

export async function openProtectedFile(url: string) {
  const popup = window.open('', '_blank')
  try {
    const token = localStorage.getItem('token') || ''
    const response = await axios.get(normalizeProtectedFileUrl(url), {
      responseType: 'blob',
      timeout: 30000,
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    // 后端 GlobalExceptionHandler 把错误（文件不存在/无权限等）包成 HTTP 200 + JSON Result，
    // axios 不会对 200 抛错，需自行识别 JSON 错误体，否则会把错误 JSON 当文件打开
    const blob: Blob = response.data
    if (blob && blob.type && blob.type.includes('application/json')) {
      let message = '文件加载失败'
      try {
        const parsed = JSON.parse(await blob.text())
        if (parsed && typeof parsed.message === 'string' && parsed.message) message = parsed.message
      } catch { /* 解析失败沿用默认消息 */ }
      throw new Error(message)
    }
    const objectUrl = URL.createObjectURL(blob)
    if (popup) popup.location.href = objectUrl
    else window.open(objectUrl, '_blank')
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
  } catch (error) {
    popup?.close()
    throw error
  }
}
