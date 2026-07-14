import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const loadingDirective = { mounted() {}, updated() {}, unmounted() {} }

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { enrollmentApi } from '@/api/edu'
import EnrollmentList from '@/views/edu/EnrollmentList.vue'

function m() {
  return mount(EnrollmentList, {
    global: {
      directives: { loading: loadingDirective },
      stubs: {
        'el-table': true, 'el-table-column': true, 'el-tag': true, 'el-pagination': true,
        'el-button': { template: '<button><slot /></button>', props: ['type', 'size', 'loading'], emits: ['click'] },
        'el-dialog': true, 'el-input': true,
      },
    },
  })
}

describe('EnrollmentList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders enrollment audit page', () => {
    const h = m().html()
    expect(h).toContain('报名审核')
  })
  it('calls enrollmentApi.list on mount', () => {
    m()
    expect(vi.mocked(enrollmentApi.list)).toHaveBeenCalled()
  })
})
