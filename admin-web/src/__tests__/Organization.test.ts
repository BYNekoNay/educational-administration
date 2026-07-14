import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import Organization from '@/views/admin/Organization.vue'

describe('Organization.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  function m() { return mountPage(Organization) }

  it('renders title', () => { expect(m().html()).toContain('机构信息配置') })
  it('renders form labels', () => { const t = m().text(); expect(t).toContain('机构名称'); expect(t).toContain('校区'); expect(t).toContain('联系电话'); expect(t).toContain('地址') })
  it('renders save button', () => { expect(m().text()).toContain('保存') })
})
