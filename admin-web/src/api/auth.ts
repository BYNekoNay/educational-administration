import request from './request'

/** 认证 */
export const authApi = {
  login: (data: { username: string; password: string }) =>
    request.post('/auth/login', data),
  logout: () => request.post('/auth/logout'),
  /** 获取当前用户可见菜单树（按权限过滤） */
  myMenus: () => request.get('/auth/menus'),
}

/** 用户管理 */
export const userApi = {
  list: (params?: any) => request.get('/admin/users', { params }),
  create: (data: any) => request.post('/admin/users', data),
  update: (id: number, data: any) => request.put(`/admin/users/${id}`, data),
  updateStatus: (id: number, status: number) =>
    request.put(`/admin/users/${id}/status`, null, { params: { status } }),
  resetPassword: (id: number, newPassword: string) =>
    request.put(`/admin/users/${id}/password`, { newPassword }),
}

/** 角色管理 */
export const roleApi = {
  list: () => request.get('/admin/roles'),
  /** 返回 { roleId, roleCode, permissionCodes: string[] } */
  permissions: (id: number) => request.get(`/admin/roles/${id}/permissions`),
  /** 直接发送权限码数组（后端 @RequestBody List<String>） */
  updatePermissions: (id: number, permissionCodes: string[]) =>
    request.put(`/admin/roles/${id}/permissions`, permissionCodes),
  /** 新增角色 { roleCode, roleName } */
  create: (data: { roleCode: string; roleName: string }) =>
    request.post('/admin/roles', data),
  /** 更新角色名称 { roleName } */
  update: (id: number, data: { roleName: string }) =>
    request.put(`/admin/roles/${id}`, data),
  /** 删除角色 */
  delete: (id: number) => request.delete(`/admin/roles/${id}`),
}

/** 菜单权限定义管理 */
export const permissionApi = {
  list: () => request.get('/admin/permissions'),
  getById: (id: number) => request.get(`/admin/permissions/${id}`),
  create: (data: { permissionCode: string; path: string; type: number }) =>
    request.post('/admin/permissions', data),
  update: (id: number, data: { permissionCode: string; path: string; type: number }) =>
    request.put(`/admin/permissions/${id}`, data),
  delete: (id: number) => request.delete(`/admin/permissions/${id}`),
}

/** 系统菜单管理 */
export const menuApi = {
  tree: () => request.get('/admin/menus/tree'),
  getById: (id: number) => request.get(`/admin/menus/${id}`),
  create: (data: any) => request.post('/admin/menus', data),
  update: (id: number, data: any) => request.put(`/admin/menus/${id}`, data),
  delete: (id: number) => request.delete(`/admin/menus/${id}`),
}

/** 机构配置 */
export const organizationApi = {
  get: () => request.get('/admin/organization'),
  update: (data: any) => request.put('/admin/organization', data),
}

/** 看板与统计 */
export const dashboardApi = {
  get: () => request.get('/admin/dashboard'),
}

export const statisticsApi = {
  teacherWorkload: (month = '') => request.get('/admin/statistics/teacher-workload', { params: { month } }),
  studentLoss: () => request.get('/admin/statistics/student-loss'),
  classActivity: () => request.get('/admin/statistics/class-activity'),
  courseProfit: () => request.get('/admin/statistics/course-profit'),
  paymentRate: () => request.get('/admin/statistics/payment-rate'),
  /** 流失预警名单（分页） */
  riskWarnings: (params?: any) => request.get('/admin/risk-warnings', { params }),
  /** 流失预警风险分布汇总 */
  riskSummary: () => request.get('/admin/risk-warnings/summary'),
  /** 标记跟进状态 { status, remark } */
  riskFollowUp: (studentId: number, data: any) =>
    request.put(`/admin/risk-warnings/${studentId}/follow-up`, data),
  /** 一键站内触达家长 { studentIds, message } */
  riskNotify: (data: any) => request.post('/admin/risk-warnings/notify', data),
}

/** 通知公告 */
export const noticeApi = {
  list: (params?: any) => request.get('/admin/notices', { params }),
  create: (data: any) => request.post('/admin/notices', data),
  update: (id: number, data: any) => request.put(`/admin/notices/${id}`, data),
  delete: (id: number) => request.delete(`/admin/notices/${id}`),
}

/** 操作日志 */
export const operationLogApi = {
  list: (params?: any) => request.get('/admin/operation-logs', { params }),
}
