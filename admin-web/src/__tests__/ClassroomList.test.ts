import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountPage } from './helpers'

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { classroomApi } from '@/api/edu'
import ClassroomList from '@/views/edu/ClassroomList.vue'

function m() { return mountPage(ClassroomList, { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] } }) }

describe('ClassroomList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('教室管理') })
  it('renders add button', () => { expect(m().text()).toContain('新增教室') })
  it('calls list API', () => { m(); expect(vi.mocked(classroomApi.list)).toHaveBeenCalled() })
})
