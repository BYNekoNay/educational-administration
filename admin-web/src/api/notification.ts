import { useAuthStore } from './auth'

const BASE = '/api'

export const notificationApi = {
  /** 未读数 */
  unreadCount: () => request.get(`${BASE}/notifications/unread-count`),
  /** 通知列表 */
  list: (pageNum = 1, pageSize = 10) =>
    request.get(`${BASE}/notifications`, { params: { pageNum, pageSize } }),
  /** 标记已读 */
  markRead: (id: number) => request.put(`${BASE}/notifications/${id}/read`),
  /** SSE 流地址 */
  streamUrl: () => {
    const authStore = useAuthStore()
    return `${BASE}/notifications/stream?token=${authStore.token}`
  },
}

async function request(url: string, options?: any) {
  const authStore = useAuthStore()
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${authStore.token}`, 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}
