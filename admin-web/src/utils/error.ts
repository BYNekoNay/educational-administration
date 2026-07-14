import { ElMessage } from 'element-plus'

/**
 * 从错误对象提取可读的错误消息
 * 优先级：后端返回的业务消息 > HTTP 状态描述 > 异常 message > 默认文案
 */
export function getErrorMessage(e: any, defaultMsg = '操作失败'): string {
  // 1. 后端返回的 Result.data.message（来自 response 拦截器 reject 或手动提取）
  if (typeof e === 'string' && e) {
    return e
  }
  if (e?.response?.data?.message) {
    return e.response.data.message
  }
  // 2. Axios HTTP 状态码错误
  if (e?.response) {
    const status = e.response.status
    const statusMessages: Record<number, string> = {
      400: e.response?.data?.message || '请求参数有误，请检查输入',
      401: '登录已过期，请重新登录',
      403: '没有操作权限，请联系管理员',
      404: '请求的资源不存在',
      405: '请求方式不正确',
      409: '数据冲突，请刷新后重试',
      422: '请求参数校验失败',
      429: '操作太频繁，请稍后再试',
      500: '服务器内部错误，请稍后重试',
      502: '网关错误，服务暂不可用',
      503: '服务维护中，请稍后重试',
    }
    if (statusMessages[status]) return statusMessages[status]
  }
  // 3. Axios 网络错误
  if (e?.message === 'Network Error') {
    return '网络连接失败，请检查后端服务是否启动'
  }
  if (e?.message?.includes('timeout')) {
    return '请求超时，请检查网络后重试'
  }
  if (e?.message?.includes('cancel')) {
    return ''
  }
  // 4. 其他异常
  if (e?.message) {
    // Axios 标准化错误如 "Request failed with status code 500" → 转为中文
    const match = e.message.match(/status code (\d+)/)
    if (match) {
      const statusMessages: Record<string, string> = {
        '400': '请求参数有误',
        '401': '登录已过期',
        '403': '没有操作权限',
        '404': '资源不存在',
        '500': '服务器内部错误',
      }
      return statusMessages[match[1]] || `服务器返回错误 (${match[1]})`
    }
    return e.message
  }
  // 5. 兜底
  return defaultMsg
}

/**
 * 显示错误提示（自动提取可读消息）
 */
export function showError(e: any, defaultMsg = '操作失败'): void {
  const msg = getErrorMessage(e, defaultMsg)
  if (msg) {
    ElMessage.error(msg)
  }
}
