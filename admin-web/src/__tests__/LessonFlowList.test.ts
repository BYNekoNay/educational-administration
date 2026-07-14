import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountPage } from './helpers'

vi.mock('@/api/finance', async () => {
  const { createFinanceApiMocks } = await import('./mocks/factory')
  return createFinanceApiMocks()
})

import { lessonFlowApi } from '@/api/finance'
import LessonFlowList from '@/views/finance/LessonFlowList.vue'

describe('LessonFlowList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  function m() { return mountPage(LessonFlowList) }

  it('renders title', () => { expect(m().html()).toContain('课时流水') })
  it('calls list API', () => { m(); expect(vi.mocked(lessonFlowApi.list)).toHaveBeenCalled() })
})
