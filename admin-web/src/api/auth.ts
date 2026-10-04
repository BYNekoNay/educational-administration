import request from './request'
import type {
  CreateUserPayload,
  DashboardStats,
  LoginResult,
  MenuItem,
  Notice,
  OperationLog,
  Organization,
  PageData,
  PageParams,
  PermissionItem,
  RiskStudentRow,
  RiskSummary,
  RoleInfo,
  RolePermissionResult,
  UpdateUserPayload,
  UserInfo,
} from '@/types'

/** 认证 */
export const authApi = {
  login: (data: { username: string; password: string }) =>
    request.post<LoginResult>('/auth/login', data),
  logout: () => request.post<void>('/auth/logout'),
  /** 获取当前用户可见菜单树（按权限过滤） */
  myMenus: () => request.get<MenuItem[]>('/auth/menus'),
}

/** 用户管理 */
export const userApi = {
  list: (params?: PageParams) => request.get<PageData<UserInfo>>('/admin/users', { params }),
  create: (data: CreateUserPayload) => request.post<UserInfo>('/admin/users', data),
  update: (id: number, data: UpdateUserPayload) => request.put<UserInfo>(`/admin/users/${id}`, data),
  updateStatus: (id: number, status: number) =>
    request.put<void>(`/admin/users/${id}/status`, null, { params: { status } }),
  resetPassword: (id: number, newPassword: string) =>
    request.put<void>(`/admin/users/${id}/password`, { newPassword }),
}

/** 角色管理 */
export const roleApi = {
  list: () => request.get<RoleInfo[]>('/admin/roles'),
  /** 返回 { roleId, roleCode, permissionCodes: string[] } */
  permissions: (id: number) => request.get<RolePermissionResult>(`/admin/roles/${id}/permissions`),
  /** 直接发送权限码数组（后端 @RequestBody List<String>） */
  updatePermissions: (id: number, permissionCodes: string[]) =>
    request.put<void>(`/admin/roles/${id}/permissions`, permissionCodes),
  /** 新增角色 { roleCode, roleName } */
  create: (data: { roleCode: string; roleName: string }) =>
    request.post<RoleInfo>('/admin/roles', data),
  /** 更新角色名称 { roleName } */
  update: (id: number, data: { roleName: string }) =>
    request.put<RoleInfo>(`/admin/roles/${id}`, data),
  /** 删除角色 */
  delete: (id: number) => request.delete<void>(`/admin/roles/${id}`),
}

/** 菜单权限定义管理 */
export const permissionApi = {
  list: () => request.get<PermissionItem[]>('/admin/permissions'),
  getById: (id: number) => request.get<PermissionItem>(`/admin/permissions/${id}`),
  create: (data: { permissionCode: string; path: string; type: number }) =>
    request.post<PermissionItem>('/admin/permissions', data),
  update: (id: number, data: { permissionCode: string; path: string; type: number }) =>
    request.put<PermissionItem>(`/admin/permissions/${id}`, data),
  delete: (id: number) => request.delete<void>(`/admin/permissions/${id}`),
}

/** 系统菜单管理 */
export const menuApi = {
  tree: () => request.get<MenuItem[]>('/admin/menus/tree'),
  getById: (id: number) => request.get<MenuItem>(`/admin/menus/${id}`),
  create: (data: Partial<MenuItem>) => request.post<MenuItem>('/admin/menus', data),
  update: (id: number, data: Partial<MenuItem>) => request.put<MenuItem>(`/admin/menus/${id}`, data),
  delete: (id: number) => request.delete<void>(`/admin/menus/${id}`),
}

/** 机构配置 */
export const organizationApi = {
  get: () => request.get<Organization>('/admin/organization'),
  update: (data: Partial<Organization>) => request.put<Organization>('/admin/organization', data),
}

/** 看板与统计 */
export const dashboardApi = {
  get: () => request.get<DashboardStats>('/admin/dashboard'),
}

export const statisticsApi = {
  teacherWorkload: (month = '') =>
    request.get<Array<Record<string, unknown>>>('/admin/statistics/teacher-workload', { params: { month } }),
  studentLoss: () => request.get<Array<Record<string, unknown>>>('/admin/statistics/student-loss'),
  classActivity: () => request.get<Array<Record<string, unknown>>>('/admin/statistics/class-activity'),
  courseProfit: () => request.get<Array<Record<string, unknown>>>('/admin/statistics/course-profit'),
  paymentRate: () => request.get<Array<Record<string, unknown>>>('/admin/statistics/payment-rate'),
  /** 流失预警名单（分页） */
  riskWarnings: (params?: PageParams) =>
    request.get<PageData<RiskStudentRow>>('/admin/risk-warnings', { params }),
  /** 流失预警风险分布汇总 */
  riskSummary: () => request.get<RiskSummary>('/admin/risk-warnings/summary'),
  /** 标记跟进状态 { status, remark } */
  riskFollowUp: (studentId: number, data: { status: number; remark?: string }) =>
    request.put<void>(`/admin/risk-warnings/${studentId}/follow-up`, data),
  /** 一键站内触达家长 { studentIds, message? } → { parentCount, notifiedParentCount } */
  riskNotify: (data: { studentIds: number[]; message?: string }) =>
    request.post<{ parentCount?: number; notifiedParentCount?: number }>('/admin/risk-warnings/notify', data),
}

/** 通知公告 */
export const noticeApi = {
  list: (params?: PageParams) => request.get<PageData<Notice>>('/admin/notices', { params }),
  create: (data: Partial<Notice>) => request.post<Notice>('/admin/notices', data),
  update: (id: number, data: Partial<Notice>) => request.put<Notice>(`/admin/notices/${id}`, data),
  delete: (id: number) => request.delete<void>(`/admin/notices/${id}`),
}

/** 操作日志 */
export const operationLogApi = {
  list: (params?: PageParams) => request.get<PageData<OperationLog>>('/admin/operation-logs', { params }),
}