<template>
  <el-container style="height: 100vh">
    <el-aside :width="collapsed ? '64px' : '220px'" class="app-aside">
      <div class="aside-logo" :class="{ collapsed }">
        <template v-if="!collapsed">教务管理平台</template>
        <template v-else>艺培</template>
      </div>
      <el-menu
        class="el-menu-vertical aside-menu"
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
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
      <el-header class="app-header">
        <div class="header-left">
          <el-tooltip :content="collapsed ? '展开菜单' : '收起菜单'" placement="bottom">
            <button class="collapse-btn" type="button" @click="toggleCollapsed">
              <el-icon :size="18"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
            </button>
          </el-tooltip>
          <el-breadcrumb v-if="pageTitle" separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="activeMenu !== '/admin/dashboard'">{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <NotificationBell />
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user-chip" type="button">
              <span class="user-avatar">{{ avatarChar }}</span>
              <span class="user-meta">
                <span class="user-name">{{ authStore.userInfo?.realName || '用户' }}</span>
                <span class="user-role">{{ roleName }}</span>
              </span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <el-icon><User /></el-icon>{{ roleName }}
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { authApi } from '@/api/auth'
import { Monitor, Setting, Document, Money, Fold, Expand, ArrowDown, User, SwitchButton } from '@element-plus/icons-vue'
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

/* ─── 侧边栏折叠（记忆到 localStorage）─── */
const COLLAPSE_KEY = 'eduadmin.sidebar-collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')
function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
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
const pageTitle = computed(() => (route.meta?.title as string) || '')

const roleNameMap: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  EDU_ADMIN: '教务管理员',
  FINANCE: '财务管理员',
  TEACHER: '授课教师',
  PARENT: '学员家长'
}
const roleName = computed(() => roleNameMap[authStore.roleCode] || authStore.roleCode)
const avatarChar = computed(() => (authStore.userInfo?.realName || 'U').trim().charAt(0).toUpperCase())

function handleUserCommand(cmd: string) {
  if (cmd === 'logout') void handleLogout()
}

async function handleLogout() {
  // 先通知后端吊销 Token（version+1 使所有已发 Token 立即失效），失败不阻塞本地登出
  try { await authApi.logout() } catch { /* 网络/权限错误也继续本地清理 */ }
  notificationStore.disconnect()
  notificationStore.reset()
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
/* ─── 侧边栏：logo 固定 + 菜单独立滚动 ─── */
.app-aside {
  display: flex;
  flex-direction: column;
  background-color: var(--sidebar-bg);
  overflow: hidden;
  transition: width var(--duration-normal) var(--ease-out-quart);
}
.aside-logo {
  flex-shrink: 0;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.06em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  white-space: nowrap;
}
.aside-logo.collapsed { font-size: 15px; letter-spacing: 0; }
.aside-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none;
}
.aside-menu:not(.el-menu--collapse) { width: 100%; }

/* ─── 顶栏 ─── */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  gap: 12px;
}
.header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--neutral-500);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quart);
}
.collapse-btn:hover { background: var(--neutral-100); color: var(--brand-primary); }

.header-right { display: flex; align-items: center; gap: 14px; }
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-out-quart);
}
.user-chip:hover { background: var(--neutral-100); }
.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  background: var(--brand-gradient);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-meta { display: flex; flex-direction: column; align-items: flex-start; line-height: 1.2; }
.user-name { font-size: 13px; font-weight: var(--font-semibold); color: var(--neutral-700); }
.user-role { font-size: 11px; color: var(--neutral-400); }
.user-arrow { color: var(--neutral-400); font-size: 12px; }

/* ─── 主内容区 ─── */
.app-main {
  background: var(--surface-page);
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
