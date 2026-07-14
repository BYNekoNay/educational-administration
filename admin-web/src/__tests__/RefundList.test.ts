import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mountPage } from './helpers'

vi.mock('@/api/finance', async () => {
  const { createFinanceApiMocks } = await import('./mocks/factory')
  return createFinanceApiMocks()
})
vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { refundApi } from '@/api/finance'
import RefundList from '@/views/finance/RefundList.vue'

function m() { return mountPage(RefundList, { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] } }) }

describe('RefundList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('退费管理') })
  it('renders add button', () => { expect(m().text()).toContain('新增退费申请') })
  it('calls list API', () => { m(); expect(vi.mocked(refundApi.list)).toHaveBeenCalled() })
  it('add opens dialog', async () => {
    const w = m()
    await w.findAll('button').find(b => b.text() === '新增退费申请')!.trigger('click')
    await nextTick()
    expect(w.find('.el-dialog').exists()).toBe(true)
  })
})
