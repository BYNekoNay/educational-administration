import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mountPage } from './helpers'
import { nextTick } from 'vue'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { statisticsApi } from '@/api/auth'
import { classApi, courseApi } from '@/api/edu'
import { useAuthStore } from '@/stores/auth'
import RiskWarningPanel from '@/views/dashboard/components/RiskWarningPanel.vue'

describe('RiskWarningPanel.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })

  function m(role = 'EDU_ADMIN') {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().roleCode = role
    return mountPage(RiskWarningPanel, {}, [pinia])
  }

  it('loads summary and warning list on mount', () => {
    m()
    expect(vi.mocked(statisticsApi.riskSummary)).toHaveBeenCalled()
    expect(vi.mocked(statisticsApi.riskWarnings)).toHaveBeenCalled()
  })

  it('renders distribution chips', async () => {
    vi.mocked(statisticsApi.riskSummary).mockResolvedValue({
      data: { total: 3, high: 2, medium: 1, low: 0, byLevel: [] },
    } as any)
    const t = m()
    await nextTick()
    await nextTick()
    const text = t.text()
    expect(text).toContain('风险学员总数')
    expect(text).toContain('高风险')
    expect(text).toContain('中风险')
    expect(text).toContain('低风险')
    expect(text).toContain('2')
    expect(text).toContain('1')
  })

  it('renders list headers and export button for edu admin', () => {
    const text = m().text()
    expect(text).toContain('导出名单')
  })

  it('hides operate actions for finance role', () => {
    const text = m('FINANCE').text()
    expect(text).not.toContain('一键站内通知家长')
  })

  it('skips class/course option loading for finance (avoids 403 spam)', () => {
    // 财务无 /edu/classes、/edu/courses 权限：不得触发加载与渲染，消除首屏"无权访问该接口"
    const wrapper = m('FINANCE')
    expect(vi.mocked(classApi.list)).not.toHaveBeenCalled()
    expect(vi.mocked(courseApi.list)).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('全部班级')
    expect(wrapper.text()).not.toContain('全部课程')
    // 风险数据本身仍正常加载
    expect(vi.mocked(statisticsApi.riskSummary)).toHaveBeenCalled()
    expect(vi.mocked(statisticsApi.riskWarnings)).toHaveBeenCalled()
  })

  it('loads class/course options for edu admin', () => {
    m('EDU_ADMIN')
    expect(vi.mocked(classApi.list)).toHaveBeenCalled()
    expect(vi.mocked(courseApi.list)).toHaveBeenCalled()
  })

  it('applies filters when searching', async () => {
    const wrapper = m('EDU_ADMIN')
    // 直接改 filters 后触发查询按钮逻辑：搜索按钮绑定了 @click="search"
    const buttons = wrapper.findAll('button')
    const queryBtn = buttons.find((b) => b.text().includes('查询'))
    expect(queryBtn).toBeTruthy()
    await queryBtn!.trigger('click')
    expect(vi.mocked(statisticsApi.riskWarnings)).toHaveBeenCalled()
  })
})
