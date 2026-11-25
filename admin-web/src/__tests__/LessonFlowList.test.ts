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

  it('sourceType=5 渲染为「人工调整」', () => {
    const vm = m().vm as any
    expect(vm.sourceTypeLabel(5)).toBe('人工调整')
    expect(vm.sourceTypeTag(5)).toBe('success')
  })

  it('未知 sourceType 回退为「其他」', () => {
    const vm = m().vm as any
    expect(vm.sourceTypeLabel(99)).toBe('其他')
  })
})
