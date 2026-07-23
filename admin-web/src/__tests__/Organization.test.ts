import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import Organization from '@/views/admin/Organization.vue'

describe('Organization.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  afterEach(() => { localStorage.removeItem('userInfo') })

  function m() { return mountPage(Organization) }

  it('renders title', () => { expect(m().html()).toContain('机构信息配置') })
  it('renders form labels', () => { const t = m().text(); expect(t).toContain('机构名称'); expect(t).toContain('校区'); expect(t).toContain('联系电话'); expect(t).toContain('地址') })

  // 后端 PUT /admin/organization 仅放行 SUPER_ADMIN：保存按钮按角色门禁渲染
  it('renders save button for SUPER_ADMIN', () => {
    localStorage.setItem('userInfo', JSON.stringify({ roleCode: 'SUPER_ADMIN' }))
    expect(m().text()).toContain('保存')
  })
  it('read-only for non SUPER_ADMIN', () => {
    localStorage.setItem('userInfo', JSON.stringify({ roleCode: 'EDU_ADMIN' }))
    const t = m().text()
    expect(t).toContain('仅超级管理员可修改机构配置')
    expect(t).not.toContain('保存')
  })
})
