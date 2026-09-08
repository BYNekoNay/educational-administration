<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background-color: var(--sidebar-bg); overflow-y: auto">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: bold; border-bottom: 1px solid rgba(255,255,255,0.12); letter-spacing: 0.04em">
        教务管理平台
      </div>
      <el-menu
        class="el-menu-vertical"
        :default-active="activeMenu"
        router
        style="border-right: none"
      >
        <!-- 菜单：完全从后端按权限拉取（运营看板也在内，id=1） -->
        <MenuItem
          v-for="node in authStore.menuTree"
          :key="node.id"
          :item="node"
          :icon-map="iconMap"
        />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="display: flex; align-items: center; justify-content: space-between; padding: 0 20px">
        <span style="font-size: 14px; color: var(--neutral-500); font-weight: var(--font-medium)">
          <span style="color: var(--neutral-700); font-weight: var(--font-semibold)">{{ authStore.userInfo?.realName }}</span>
          （{{ roleName }}）
        </span>
        <div style="display:flex;align-items:center;gap:12px">
          <NotificationBell />
          <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main style="background: var(--surface-page); padding: 20px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { authApi } from '@/api/auth'
import { Monitor, Setting, Document, Money } from '@element-plus/icons-vue'
import NotificationBell from '@/components/NotificationBell.vue'
import MenuItem from '@/components/MenuItem.vue'
import type { Component } from 'vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()

/** 图标名 → Vue 组件映射，上线有新图标时维护此表即可 */
const iconMap: Record<string, Component> = {
  Monitor, Setting, Document, Money,
}

onMounted(async () => {
  notificationStore.requestPermission()
  notificationStore.connect()
  notificationStore.fetchUnreadCount()
  // 首次加载动态菜单树
  await authStore.fetchMyMenus()
})

onUnmounted(() => {
  notificationStore.disconnect()
})

const activeMenu = computed(() => route.path)

const roleNameMap: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  EDU_ADMIN: '教务管理员',
  FINANCE: '财务管理员',
  TEACHER: '授课教师',
  PARENT: '学员家长'
}
const roleName = computed(() => roleNameMap[authStore.roleCode] || authStore.roleCode)

async function handleLogout() {
  // 先通知后端吊销 Token（version+1 使所有已发 Token 立即失效），失败不阻塞本地登出
  try { await authApi.logout() } catch { /* 网络/权限错误也继续本地清理 */ }
  notificationStore.disconnect()
  notificationStore.reset()
  authStore.logout()
  router.push('/login')
}
</script>
