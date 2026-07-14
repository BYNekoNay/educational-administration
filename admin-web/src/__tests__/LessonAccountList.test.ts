import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountPage } from './helpers'

vi.mock('@/api/finance', async () => {
  const { createFinanceApiMocks } = await import('./mocks/factory')
  return createFinanceApiMocks()
})

import { lessonAccountApi } from '@/api/finance'
import LessonAccountList from '@/views/finance/LessonAccountList.vue'

describe('LessonAccountList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  function m() { return mountPage(LessonAccountList) }

  it('renders title', () => { expect(m().html()).toContain('课时账户') })
  it('calls list API', () => { m(); expect(vi.mocked(lessonAccountApi.list)).toHaveBeenCalled() })
})
