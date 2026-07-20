import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mountPage } from './helpers'

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { scheduleApi } from '@/api/edu'
import ScheduleList from '@/views/edu/ScheduleList.vue'

function m() { return mountPage(ScheduleList, { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] } }) }

describe('ScheduleList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('排课管理') })
  it('renders scheduling action buttons', () => {
    const t = m().text()
    expect(t).toContain('智能排课')
    expect(t).toContain('批量排课')
    expect(t).toContain('新增课次')
  })
  it('calls list API', () => { m(); expect(vi.mocked(scheduleApi.list)).toHaveBeenCalled() })
  it('add opens dialog', async () => {
    const w = m()
    await w.findAll('button').find(b => b.text() === '新增课次')!.trigger('click')
    await nextTick()
    expect(w.find('.el-dialog').exists()).toBe(true)
  })
})
