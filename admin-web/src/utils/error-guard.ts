import { showError } from './error'

let installed = false

/**
 * 全局未捕获 Promise 拒绝兜底。
 *
 * 业务异常（后端 Result.code !== 0）经 request 拦截器以 `new Error(message)` 形式 reject，
 * 多数页面已用 try/catch + showError 处理；但凡遗漏的，会表现为浏览器控制台
 * "Uncaught (in promise) Error: xxx" 且无任何界面提示。
 *
 * 这里统一拦截 unhandledrejection：把错误原因以 ElMessage 形式直接展示给用户，
 * 并 preventDefault 抑制控制台默认的 Uncaught 打印，避免"报错只在控制台、界面无反馈"。
 *
 * 注意：axios / HTTP 错误（reason.isAxiosError 或带 response）已由 request.ts 拦截器
 * 单独提示，此处跳过以免重复弹窗。
 */
export function installGlobalErrorGuard(): void {
  if (installed || typeof window === 'undefined') return
  installed = true

  window.addEventListener('unhandledrejection', (event: PromiseRejectionEvent) => {
    const reason: any = event.reason
    // HTTP / 框架层错误已由 request 拦截器统一提示，跳过避免重复
    if (reason && (reason.isAxiosError || reason.response)) return
    // ElMessageBox 取消等预期拒绝（getErrorMessage 返回空串）不提示
    showError(reason, '系统异常')
    // 抑制浏览器默认控制台 "Uncaught (in promise)" 打印
    event.preventDefault()
  })
}
