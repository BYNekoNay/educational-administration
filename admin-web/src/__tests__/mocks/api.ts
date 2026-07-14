const mockPage = {
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
}

function mockResolve(data: any) {
  return vi.fn().mockResolvedValue({ data })
}

import { vi } from 'vitest'

// ====== auth API ======
export const authApi = {
  login: mockResolve({ token: 'fake-token', userId: 1, username: 'admin', realName: '管理员', roleCode: 'SUPER_ADMIN' }),
  profile: mockResolve({ userId: 1, username: 'admin', realName: '管理员', roleCode: 'SUPER_ADMIN' }),
  logout: mockResolve({}),
}

export const userApi = {
  list: mockResolve(mockPage),
  create: mockResolve({}),
  update: mockResolve({}),
  updateStatus: mockResolve({}),
}

export const roleApi = {
  list: mockResolve([{ id: 1, roleCode: 'SUPER_ADMIN', roleName: '超级管理员' }]),
  permissions: mockResolve({ roleId: 1, roleCode: 'SUPER_ADMIN', permissionCodes: ['menu:dashboard'] }),
  updatePermissions: mockResolve({}),
  create: mockResolve({}),
  update: mockResolve({}),
  delete: mockResolve({}),
}

export const permissionApi = {
  list: mockResolve([{ id: 1, permissionCode: 'menu:dashboard' }]),
  getById: mockResolve({}),
  create: mockResolve({}),
  update: mockResolve({}),
  delete: mockResolve({}),
}

export const menuApi = {
  tree: mockResolve([]),
  getById: mockResolve({}),
  create: mockResolve({}),
  update: mockResolve({}),
  delete: mockResolve({}),
}

export const organizationApi = {
  get: mockResolve({ name: '测试机构', address: '' }),
  update: mockResolve({}),
}

export const dashboardApi = {
  get: mockResolve({ cards: {}, charts: { lessonTrend: [], revenueTrend: [], attendanceTrend: [] } }),
}

export const noticeApi = {
  list: mockResolve(mockPage),
  create: mockResolve({}),
  update: mockResolve({}),
  delete: mockResolve({}),
}

export const operationLogApi = {
  list: mockResolve(mockPage),
}
