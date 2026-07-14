import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import { dashboardApi } from '@/api/auth'
import Dashboard from '@/views/dashboard/Dashboard.vue'

describe('Dashboard.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  function m() { return mountPage(Dashboard) }

  it('renders title', () => { expect(m().html()).toContain('运营数据看板') })
  it('renders stat card labels', () => {
    const t = m().text()
    expect(t).toContain('在册学员')
    expect(t).toContain('本月课次')
    expect(t).toContain('本月营收')
    expect(t).toContain('到课率')
  })
  it('renders export buttons', () => {
    const t = m().text()
    expect(t).toContain('导出台账')
    expect(t).toContain('导出课时消耗')
    expect(t).toContain('导出薪资')
  })
  it('calls dashboard API', () => { m(); expect(vi.mocked(dashboardApi.get)).toHaveBeenCalled() })
})
