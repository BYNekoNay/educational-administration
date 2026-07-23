import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const loadingDirective = { mounted() {}, updated() {}, unmounted() {} }

vi.mock('@/api/finance', async () => {
  const { createFinanceApiMocks } = await import('./mocks/factory')
  return createFinanceApiMocks()
})
vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { salaryApi } from '@/api/finance'
import SalaryList from '@/views/finance/SalaryList.vue'

function m() {
  return mount(SalaryList, {
    global: {
      directives: { loading: loadingDirective },
      stubs: {
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': { template: '<div><slot /></div>' },
        'el-table': true, 'el-table-column': true, 'el-tag': true, 'el-pagination': true,
        'el-button': { template: '<button><slot /></button>', props: ['type', 'size', 'loading'], emits: ['click'] },
        'el-dialog': true,
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': { template: '<input />' },
        'el-input-number': { template: '<div><slot /></div>' },
        'el-select': { template: '<div><slot /></div>' },
        'el-option': { template: '<div><slot /></div>' },
        'el-card': { template: '<div><slot name="header" /><slot /></div>' },
        'el-date-picker': { template: '<div><slot /></div>' },
        'el-avatar': { template: '<span><slot /></span>' },
        ExportButton: { template: '<button>{{ label }}</button>', props: ['label'] },
      },
    },
  })
}

describe('SalaryList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('薪资管理') })
  it('renders buttons', () => {
    const t = m().text()
    expect(t).toContain('新增规则')
    expect(t).toContain('核算薪资')
    expect(t).toContain('一键结算本月')
    expect(t).toContain('导出薪资')
  })
  it('calls APIs', () => {
    m()
    expect(vi.mocked(salaryApi.rules)).toHaveBeenCalled()
    expect(vi.mocked(salaryApi.list)).toHaveBeenCalled()
  })
})
