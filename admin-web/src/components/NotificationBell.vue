<template>
  <el-popover placement="bottom-end" width="320" trigger="click" @show="loadList">
    <template #reference>
      <el-badge :value="store.unreadCount" :hidden="store.unreadCount === 0" :max="99">
        <el-button link @click="store.fetchUnreadCount()">
          <el-icon size="20"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </template>

    <div style="max-height:360px;overflow-y:auto">
      <div v-if="notifications.length === 0" style="text-align:center;color:#909399;padding:20px">
        暂无通知
      </div>
      <div v-for="n in notifications" :key="n.id" class="notif-item"
           :style="{ fontWeight: n.isRead ? 'normal' : 'bold' }"
           @click="handleClick(n)">
        <div class="notif-title">{{ n.title }}</div>
        <div class="notif-content">{{ n.content }}</div>
        <div class="notif-time">{{ formatTime(n.createTime) }}</div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import { notificationApi } from '@/api/notification'

const store = useNotificationStore()
const notifications = ref<any[]>([])

async function loadList() {
  try {
    const res = await notificationApi.list(1, 20)
    notifications.value = res.data?.records || []
  } catch {
    notifications.value = []
  }
}

async function handleClick(n: any) {
  if (!n.isRead) {
    try { await notificationApi.markRead(n.id) } catch {}
    store.unreadCount = Math.max(0, store.unreadCount - 1)
    n.isRead = 1
  }
}

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.notif-item {
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
}
.notif-item:hover { background: #f5f7fa }
.notif-title { font-size: 13px; color: #303133 }
.notif-content { font-size: 12px; color: #606266; margin-top: 2px }
.notif-time { font-size: 11px; color: #c0c4cc; margin-top: 4px }
</style>
