import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  let eventSource: EventSource | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  // 连续失败次数：仅在连接成功（onopen）时归零；disconnect() 不清零，
  // 否则 onerror → disconnect → connect 链路会把退避计数反复重置为 1
  let retryAttempts = 0

  async function fetchUnreadCount() {
    try {
      const res = await notificationApi.unreadCount()
      unreadCount.value = res.data || 0
    } catch {
      // ignore
    }
  }

  function connect() {
    disconnect()
    // 未登录（无 token）时不建立 SSE：避免登出/token 失效后仍周期性发起无效连接
    if (!localStorage.getItem('token')) return
    const url = notificationApi.streamUrl()
    const es = new EventSource(url)
    eventSource = es

    es.onopen = () => {
      // 连接稳定后重置退避计数，并重新对齐未读数（断线期间的事件推送不会重放）
      retryAttempts = 0
      fetchUnreadCount()
    }

    es.addEventListener('notification', (e) => {
      try {
        const data = JSON.parse(e.data)
        unreadCount.value++
        // Toast 通知
        if (window.Notification && Notification.permission === 'granted') {
          new Notification(data.title, { body: data.content })
        }
      } catch {
        // parse error, ignore
      }
    })

    es.onerror = () => {
      disconnect()
      // 后端每个用户仅保留一个 SseEmitter（新订阅会踢掉旧连接），多标签页固定间隔
      // 重连会互相踢成死循环；改为指数退避 + 随机抖动 + 上限，错开各标签重连峰值。
      // 2s → 4s → 8s → … → 封顶 5min（实际延迟取 [base/2, base] 的随机值）
      retryAttempts++
      const base = Math.min(1000 * Math.pow(2, retryAttempts), 5 * 60 * 1000)
      const delay = base / 2 + Math.random() * (base / 2)
      reconnectTimer = setTimeout(connect, delay)
    }
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  // 请求浏览器通知权限
  function requestPermission() {
    if (window.Notification && Notification.permission === 'default') {
      // 浮空 Promise 必须兜底：部分环境会以 TypeError 拒绝，
      // 未捕获会经全局 error-guard 弹出英文"系统异常"提示
      const p = Notification.requestPermission() as unknown as Promise<string> | undefined
      if (p && typeof p.catch === 'function') p.catch(() => {})
    }
  }

  // 登出/切换账号时清零未读数，避免短暂显示上一用户的角标
  function reset() {
    unreadCount.value = 0
  }

  return { unreadCount, fetchUnreadCount, connect, disconnect, requestPermission, reset }
})
