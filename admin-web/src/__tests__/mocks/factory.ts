import { vi } from 'vitest'

export function makeResolve(data: any) {
  return vi.fn().mockResolvedValue({ data })
}

const emptyPage = { records: [], total: 0, pageNum: 1, pageSize: 10 }

export function createAuthApiMocks() {
  return {
    authApi: {
      login: makeResolve({ token: 'fake-token', userId: 1, username: 'admin', realName: '管理员', roleCode: 'SUPER_ADMIN' }),
      profile: makeResolve({ userId: 1, username: 'admin', realName: '管理员', roleCode: 'SUPER_ADMIN' }),
      logout: makeResolve({}),
    },
    userApi: {
      list: makeResolve(emptyPage),
      create: makeResolve({}),
      update: makeResolve({}),
      updateStatus: makeResolve({}),
    },
    roleApi: {
      list: makeResolve([{ id: 1, roleCode: 'SUPER_ADMIN', roleName: '超级管理员' }]),
      permissions: makeResolve({ roleId: 1, roleCode: 'SUPER_ADMIN', permissionCodes: [] }),
      updatePermissions: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
    },
    permissionApi: {
      list: makeResolve([{ permissionCode: 'menu:dashboard' }]),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
    },
    menuApi: {
      tree: makeResolve([]),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
    },
    organizationApi: {
      get: makeResolve({ name: '测试机构', address: '' }),
      update: makeResolve({}),
    },
    dashboardApi: {
      get: makeResolve({ cards: { activeStudents: 100, monthlyLessons: 50, monthlyRevenue: 50000, attendanceRate: 95 }, charts: { lessonTrend: [], revenueTrend: [], attendanceTrend: [] } }),
    },
    statisticsApi: {
      teacherWorkload: makeResolve([]),
      studentLoss: makeResolve([]),
      classActivity: makeResolve([]),
      courseProfit: makeResolve([]),
      paymentRate: makeResolve([]),
    },
    noticeApi: {
      list: makeResolve(emptyPage),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
    },
    operationLogApi: {
      list: makeResolve(emptyPage),
    },
  }
}

export function createEduApiMocks() {
  return {
    studentApi: {
      list: makeResolve(emptyPage),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
      bindParent: makeResolve({}),
      transfer: makeResolve({}),
      withdraw: makeResolve({}),
    },
    courseApi: {
      list: makeResolve(emptyPage),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
    },
    classApi: {
      list: makeResolve(emptyPage),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
      students: makeResolve(emptyPage),
      addStudent: makeResolve({}),
      removeStudent: makeResolve({}),
    },
    scheduleApi: {
      list: makeResolve(emptyPage),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
      checkConflict: makeResolve({}),
      batchCreate: makeResolve({}),
      autoSchedule: makeResolve([]),
    },
    teacherApi: {
      list: makeResolve([]),
    },
    attendanceApi: {
      list: makeResolve(emptyPage),
      getById: makeResolve({}),
      create: makeResolve({}),
      delete: makeResolve({}),
    },
    classroomApi: {
      list: makeResolve(emptyPage),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
      roomBookings: makeResolve(emptyPage),
      createRoomBooking: makeResolve({}),
    },
    enrollmentApi: {
      list: makeResolve(emptyPage),
      getById: makeResolve({}),
      create: makeResolve({}),
      update: makeResolve({}),
      delete: makeResolve({}),
      audit: makeResolve({}),
    },
    adjustApi: {
      list: makeResolve(emptyPage),
      create: makeResolve({}),
      audit: makeResolve({}),
    },
    examApi: {
      levels: makeResolve(emptyPage),
      createLevel: makeResolve({}),
      updateLevel: makeResolve({}),
      deleteLevel: makeResolve({}),
      signups: makeResolve(emptyPage),
      signup: makeResolve({}),
      score: makeResolve({}),
    },
  }
}

export function createFinanceApiMocks() {
  return {
    paymentApi: {
      list: makeResolve(emptyPage),
      create: makeResolve({}),
    },
    refundApi: {
      list: makeResolve(emptyPage),
      create: makeResolve({}),
      audit: makeResolve({}),
    },
    lessonAccountApi: {
      list: makeResolve(emptyPage),
    },
    lessonFlowApi: {
      list: makeResolve(emptyPage),
    },
    salaryApi: {
      list: makeResolve(emptyPage),
      calculate: makeResolve({ lessonCount: 0, substituteCount: 0, totalAmount: 0 }),
      confirm: makeResolve({}),
      void: makeResolve({}),
      rules: makeResolve(emptyPage),
      createRule: makeResolve({}),
      updateRule: makeResolve({}),
      adjustments: makeResolve({}),
    },
  }
}
