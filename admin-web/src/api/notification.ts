import request from './request'
import type { NotificationItem, PageData } from '@/types'

// 注意：request 实例已设 baseURL='/api'，REST 调用写相对路径即可（axios 会自动补 /api）。
// 切勿在 REST  url 前再手动加 '/api'，否则会拼成 /api/api/... 导致 404。
// streamUrl 直接交给 EventSource（不经 axios、无 baseURL），因此需要完整的 '/api' 前缀。
const SSE_BASE = '/api'

export const notificationApi = {
  /** 未读数 */
  unreadCount: () => request.get<number>('/notifications/unread-count'),
  /** 通知列表 */
  list: (pageNum = 1, pageSize = 10) =>
    request.get<PageData<NotificationItem>>('/notifications', { params: { pageNum, pageSize } }),
  /** 标记已读 */
  markRead: (id: number) => request.put<void>(`/notifications/${id}/read`),
  /**
   * 换取 SSE 一次性票据（60 秒、单次使用）。
   * EventSource 不能带 Authorization 头，但把长有效期 JWT 拼进 URL 会泄漏到
   * nginx 日志/浏览器历史/Referer，改成先用正常鉴权换短期票据。
   */
  streamTicket: () => request.post<{ ticket: string; expiresIn: number }>('/auth/stream-ticket'),
  /** SSE 流地址（用票据，不再带 token） */
  streamUrl: (ticket: string) => `${SSE_BASE}/notifications/stream?ticket=${encodeURIComponent(ticket)}`,
}