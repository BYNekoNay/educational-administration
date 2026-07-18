import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  let eventSource: EventSource | null = null

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
    const url = notificationApi.streamUrl()
    const es = new EventSource(url)
    eventSource = es

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
      setTimeout(connect, 10000) // 10 秒后重连
    }
  }

  function disconnect() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  // 请求浏览器通知权限
  function requestPermission() {
    if (window.Notification && Notification.permission === 'default') {
      Notification.requestPermission()
    }
  }

  return { unreadCount, fetchUnreadCount, connect, disconnect, requestPermission }
})
