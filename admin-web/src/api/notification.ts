import { useAuthStore } from '@/stores/auth'
import request from './request'

// 注意：request 实例已设 baseURL='/api'，REST 调用写相对路径即可（axios 会自动补 /api）。
// 切勿在 REST  url 前再手动加 '/api'，否则会拼成 /api/api/... 导致 404。
// streamUrl 直接交给 EventSource（不经 axios、无 baseURL），因此需要完整的 '/api' 前缀。
const SSE_BASE = '/api'

export const notificationApi = {
  /** 未读数 */
  unreadCount: () => request.get('/notifications/unread-count'),
  /** 通知列表 */
  list: (pageNum = 1, pageSize = 10) =>
    request.get('/notifications', { params: { pageNum, pageSize } }),
  /** 标记已读 */
  markRead: (id: number) => request.put(`/notifications/${id}/read`),
  /** SSE 流地址 */
  streamUrl: () => {
    const authStore = useAuthStore()
    return `${SSE_BASE}/notifications/stream?token=${authStore.token}`
  },
}
