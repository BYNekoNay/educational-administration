<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background-color: #304156; overflow-y: auto">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: bold; border-bottom: 1px solid #4a5c6e">
        教务管理平台
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
        style="border-right: none"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><Monitor /></el-icon>
          <span>运营看板</span>
        </el-menu-item>

        <el-sub-menu index="admin" v-if="hasSystemAdmin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/admin/users" v-if="authStore.hasPermission('menu:user')">用户管理</el-menu-item>
          <el-menu-item index="/admin/roles" v-if="authStore.hasPermission('menu:role')">角色管理</el-menu-item>
          <el-menu-item index="/admin/menus" v-if="authStore.hasPermission('menu:menu')">菜单管理</el-menu-item>
          <el-menu-item index="/admin/organization" v-if="authStore.hasPermission('menu:organization')">机构配置</el-menu-item>
          <el-menu-item index="/admin/notices" v-if="authStore.hasPermission('menu:notice')">公告管理</el-menu-item>
          <el-menu-item index="/admin/logs" v-if="authStore.hasPermission('menu:log')">操作日志</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="edu" v-if="hasEduModule">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>教务管理</span>
          </template>
          <el-menu-item index="/edu/students" v-if="authStore.hasPermission('menu:student')">学员管理</el-menu-item>
          <el-menu-item index="/edu/courses" v-if="authStore.hasPermission('menu:course')">课程管理</el-menu-item>
          <el-menu-item index="/edu/classes" v-if="authStore.hasPermission('menu:class')">班级管理</el-menu-item>
          <el-menu-item index="/edu/enrollments" v-if="authStore.hasPermission('menu:enrollment')">报名管理</el-menu-item>
          <el-menu-item index="/edu/schedules" v-if="authStore.hasPermission('menu:schedule')">排课管理</el-menu-item>
          <el-menu-item index="/edu/classrooms" v-if="authStore.hasPermission('menu:classroom')">教室管理</el-menu-item>
          <el-menu-item index="/edu/attendances" v-if="authStore.hasPermission('menu:attendance')">考勤管理</el-menu-item>
          <el-menu-item index="/edu/exams" v-if="authStore.hasPermission('menu:exam')">考级管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="finance" v-if="hasFinanceModule">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>财务管理</span>
          </template>
          <el-menu-item index="/finance/payments" v-if="authStore.hasPermission('menu:payment')">收费管理</el-menu-item>
          <el-menu-item index="/finance/refunds" v-if="authStore.hasPermission('menu:refund')">退费管理</el-menu-item>
          <el-menu-item index="/finance/lesson-accounts" v-if="authStore.hasPermission('menu:lesson-flow')">课时账户</el-menu-item>
          <el-menu-item index="/finance/lesson-flows" v-if="authStore.hasPermission('menu:lesson-flow')">课时流水</el-menu-item>
          <el-menu-item index="/finance/salaries" v-if="authStore.hasPermission('menu:salary')">薪资管理</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: space-between; padding: 0 20px">
        <span style="font-size: 14px; color: #666">{{ authStore.userInfo?.realName }}（{{ roleName }}）</span>
        <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main style="background: #f0f2f5; padding: 20px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Monitor, Setting, Document, Money } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

const hasSystemAdmin = computed(() =>
  authStore.hasPermission('menu:user') ||
  authStore.hasPermission('menu:role') ||
  authStore.hasPermission('menu:menu') ||
  authStore.hasPermission('menu:organization') ||
  authStore.hasPermission('menu:notice') ||
  authStore.hasPermission('menu:log')
)

const hasEduModule = computed(() =>
  authStore.hasPermission('menu:student') ||
  authStore.hasPermission('menu:course') ||
  authStore.hasPermission('menu:class') ||
  authStore.hasPermission('menu:enrollment') ||
  authStore.hasPermission('menu:schedule') ||
  authStore.hasPermission('menu:classroom') ||
  authStore.hasPermission('menu:attendance') ||
  authStore.hasPermission('menu:exam')
)

const hasFinanceModule = computed(() =>
  authStore.hasPermission('menu:payment') ||
  authStore.hasPermission('menu:refund') ||
  authStore.hasPermission('menu:lesson-flow') ||
  authStore.hasPermission('menu:salary')
)

const roleNameMap: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  EDU_ADMIN: '教务管理员',
  FINANCE: '财务管理员',
  TEACHER: '授课教师',
  PARENT: '学员家长'
}
const roleName = computed(() => roleNameMap[authStore.roleCode] || authStore.roleCode)

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>
