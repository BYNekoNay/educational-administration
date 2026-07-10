import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'admin/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '运营看板', permission: 'menu:dashboard' }
      },
      {
        path: 'admin/users',
        name: 'Users',
        component: () => import('@/views/admin/UserList.vue'),
        meta: { title: '用户管理', permission: 'menu:user' }
      },
      {
        path: 'admin/roles',
        name: 'Roles',
        component: () => import('@/views/admin/RoleList.vue'),
        meta: { title: '角色管理', permission: 'menu:role' }
      },
      {
        path: 'admin/menus',
        name: 'Menus',
        component: () => import('@/views/admin/MenuManagement.vue'),
        meta: { title: '菜单管理', permission: 'menu:menu' }
      },
      {
        path: 'admin/organization',
        name: 'Organization',
        component: () => import('@/views/admin/Organization.vue'),
        meta: { title: '机构配置', permission: 'menu:organization' }
      },
      {
        path: 'admin/logs',
        name: 'OperationLogs',
        component: () => import('@/views/admin/OperationLogs.vue'),
        meta: { title: '操作日志', permission: 'menu:log' }
      },
      {
        path: 'admin/notices',
        name: 'Notices',
        component: () => import('@/views/admin/NoticeList.vue'),
        meta: { title: '公告管理', permission: 'menu:notice' }
      },
      {
        path: 'edu/students',
        name: 'Students',
        component: () => import('@/views/edu/StudentList.vue'),
        meta: { title: '学员管理', permission: 'menu:student' }
      },
      {
        path: 'edu/courses',
        name: 'Courses',
        component: () => import('@/views/edu/CourseList.vue'),
        meta: { title: '课程管理', permission: 'menu:course' }
      },
      {
        path: 'edu/classes',
        name: 'Classes',
        component: () => import('@/views/edu/ClassList.vue'),
        meta: { title: '班级管理', permission: 'menu:class' }
      },
      {
        path: 'edu/enrollments',
        name: 'Enrollments',
        component: () => import('@/views/edu/EnrollmentList.vue'),
        meta: { title: '报名管理', permission: 'menu:enrollment' }
      },
      {
        path: 'edu/schedules',
        name: 'Schedules',
        component: () => import('@/views/edu/ScheduleList.vue'),
        meta: { title: '排课管理', permission: 'menu:schedule' }
      },
      {
        path: 'edu/classrooms',
        name: 'Classrooms',
        component: () => import('@/views/edu/ClassroomList.vue'),
        meta: { title: '教室管理', permission: 'menu:classroom' }
      },
      {
        path: 'edu/attendances',
        name: 'Attendances',
        component: () => import('@/views/edu/AttendanceList.vue'),
        meta: { title: '考勤管理', permission: 'menu:attendance' }
      },
      {
        path: 'edu/exams',
        name: 'Exams',
        component: () => import('@/views/edu/ExamList.vue'),
        meta: { title: '考级管理', permission: 'menu:exam' }
      },
      {
        path: 'finance/payments',
        name: 'Payments',
        component: () => import('@/views/finance/PaymentList.vue'),
        meta: { title: '收费管理', permission: 'menu:payment' }
      },
      {
        path: 'finance/refunds',
        name: 'Refunds',
        component: () => import('@/views/finance/RefundList.vue'),
        meta: { title: '退费管理', permission: 'menu:refund' }
      },
      {
        path: 'finance/lesson-accounts',
        name: 'LessonAccounts',
        component: () => import('@/views/finance/LessonAccountList.vue'),
        meta: { title: '课时账户', permission: 'menu:lesson-flow' }
      },
      {
        path: 'finance/lesson-flows',
        name: 'LessonFlows',
        component: () => import('@/views/finance/LessonFlowList.vue'),
        meta: { title: '课时流水', permission: 'menu:lesson-flow' }
      },
      {
        path: 'finance/salaries',
        name: 'Salaries',
        component: () => import('@/views/finance/SalaryList.vue'),
        meta: { title: '薪资管理', permission: 'menu:salary' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/admin/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  if (to.path === '/login') {
    if (authStore.token) {
      next('/admin/dashboard')
    } else {
      next()
    }
    return
  }
  if (!authStore.token) {
    next('/login')
    return
  }
  // 优先使用动态权限码检查
  const requiredPermission = to.meta.permission as string | undefined
  if (requiredPermission) {
    if (!authStore.hasPermission(requiredPermission)) {
      next('/admin/dashboard')
      return
    }
  }
  document.title = (to.meta.title as string) || '教务管理'
  next()
})

export default router
