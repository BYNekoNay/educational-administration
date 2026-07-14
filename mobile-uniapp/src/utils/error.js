/**
 * 移动端统一错误工具
 * 提取可读错误消息，映射 HTTP 状态码为中文，提供兜底文案
 */

export function getErrorMessage(err, defaultMsg) {
  if (!err) return defaultMsg || '操作失败'

  // 1. 后端业务消息 (data.message)
  if (err.data && err.data.message) return err.data.message

  // 2. 已包装的 Error 对象
  if (err.message && err.message !== 'request:fail' && err.message !== 'request:fail timeout') {
    return err.message
  }

  // 3. HTTP 状态码映射
  const statusMap = {
    400: '参数错误，请检查输入',
    401: '登录已过期，请重新登录',
    403: '暂无权限执行此操作',
    404: '请求的资源不存在',
    405: '请求方式不正确',
    409: '数据冲突，请刷新后重试',
    422: '请求数据校验失败',
    429: '请求过于频繁，请稍后重试',
    500: '服务器异常，请稍后重试',
    502: '网关错误，请稍后重试',
    503: '服务暂不可用，请稍后重试'
  }
  const code = err.statusCode || (err.response && err.response.statusCode)
  if (code && statusMap[code]) return statusMap[code]

  // 4. uni-app 网络错误
  if (err.errMsg) {
    if (err.errMsg.includes('timeout')) return '请求超时，请检查网络连接'
    if (err.errMsg.includes('fail')) return '网络连接失败，请检查网络'
    return err.errMsg
  }

  return defaultMsg || '操作失败'
}

export function showError(err, defaultMsg) {
  uni.showToast({ title: getErrorMessage(err, defaultMsg), icon: 'none' })
}
