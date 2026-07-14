import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mountPage } from './helpers'

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { attendanceApi } from '@/api/edu'
import AttendanceList from '@/views/edu/AttendanceList.vue'

function m() { return mountPage(AttendanceList, { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] } }) }

describe('AttendanceList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('考勤管理') })
  it('renders add button', () => { expect(m().text()).toContain('新增考勤') })
  it('calls list API', () => { m(); expect(vi.mocked(attendanceApi.list)).toHaveBeenCalled() })
  it('add opens dialog', async () => {
    const w = m()
    await w.findAll('button').find(b => b.text() === '新增考勤')!.trigger('click')
    await nextTick()
    expect(w.find('.el-dialog').exists()).toBe(true)
  })
})
