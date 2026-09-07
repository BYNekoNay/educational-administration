import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import { dashboardApi, statisticsApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import Dashboard from '@/views/dashboard/Dashboard.vue'

describe('Dashboard.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  function m(role = '') {
    const pinia = createPinia()
    setActivePinia(pinia)
    if (role) useAuthStore().roleCode = role
    return mountPage(Dashboard, {}, [pinia])
  }

  it('renders title', () => { expect(m().html()).toContain('运营数据看板') })
  it('renders stat card labels', () => {
    const t = m().text()
    expect(t).toContain('在册学员')
    expect(t).toContain('本月课次')
    expect(t).toContain('本月营收')
    expect(t).toContain('到课率')
  })
  it('renders export buttons', () => {
    const t = m('SUPER_ADMIN').text()
    expect(t).toContain('导出台账')
    expect(t).toContain('导出课时消耗')
    expect(t).toContain('导出薪资')
  })
  it('hides export buttons for non-finance roles', () => {
    const t = m('EDU_ADMIN').text()
    expect(t).not.toContain('导出台账')
    expect(t).not.toContain('导出薪资')
  })
  it('renders multidimensional statistics', () => {
    const t = m().text()
    expect(t).toContain('多维运营分析')
    expect(t).toContain('教师工作量')
    expect(t).toContain('学员流失率')
    expect(t).toContain('班级活跃度')
    expect(t).toContain('课程盈利')
    expect(t).toContain('收费率')
  })
  it('renders 流失预警 tab content and loads risk warning APIs', () => {
    const t = m('EDU_ADMIN').text()
    // el-tab-pane 在测试中被 stub 为仅渲染内容，页签标题不进入文本；断言内容面板已挂载
    expect(t).toContain('风险学员总数')
    expect(vi.mocked(statisticsApi.riskSummary)).toHaveBeenCalled()
    expect(vi.mocked(statisticsApi.riskWarnings)).toHaveBeenCalled()
  })
  it('calls dashboard API', () => { m(); expect(vi.mocked(dashboardApi.get)).toHaveBeenCalled() })
  it('calls multidimensional statistics APIs', () => {
    m()
    expect(vi.mocked(statisticsApi.teacherWorkload)).toHaveBeenCalled()
    expect(vi.mocked(statisticsApi.studentLoss)).toHaveBeenCalled()
    expect(vi.mocked(statisticsApi.classActivity)).toHaveBeenCalled()
    expect(vi.mocked(statisticsApi.courseProfit)).toHaveBeenCalled()
    expect(vi.mocked(statisticsApi.paymentRate)).toHaveBeenCalled()
  })
})
