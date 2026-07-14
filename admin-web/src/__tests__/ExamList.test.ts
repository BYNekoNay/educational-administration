import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const loadingDirective = { mounted() {}, updated() {}, unmounted() {} }

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { examApi } from '@/api/edu'
import ExamList from '@/views/edu/ExamList.vue'

function m() {
  return mount(ExamList, {
    global: {
      directives: { loading: loadingDirective },
      stubs: {
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': { template: '<div><slot /></div>' },
        'el-table': true, 'el-table-column': true, 'el-tag': true, 'el-pagination': true,
        'el-button': { template: '<button><slot /></button>', props: ['type', 'size', 'loading'], emits: ['click'] },
        'el-dialog': true, 'el-form': true, 'el-form-item': true, 'el-input': true, 'el-input-number': true,
        'el-select': true, 'el-option': true, 'el-date-picker': true,
      },
    },
  })
}

describe('ExamList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('考级管理') })
  it('calls levels API', () => { m(); expect(vi.mocked(examApi.levels)).toHaveBeenCalled() })
  it('renders add buttons', () => {
    const t = m().text()
    expect(t).toContain('新增项目')
    expect(t).toContain('新增报名')
  })
})
