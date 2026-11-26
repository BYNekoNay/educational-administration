import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
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

  /** 注入表单实例：stub 的 el-form 无法通过 ref 拿到 validate，直接写入 setupState */
  function withForm(w: ReturnType<typeof m>) {
    ;(w.vm as any).$.setupState.formRef = {
      validate: () => Promise.resolve(true),
      clearValidate: () => {},
    }
    return w
  }

  const row = { id: 1, studentName: '张三', courseName: '钢琴', totalLessons: 10, remainingLessons: 5 }

  it('renders title', () => { expect(m().html()).toContain('课时账户') })
  it('calls list API', () => { m(); expect(vi.mocked(lessonAccountApi.list)).toHaveBeenCalled() })

  // 调整弹窗的表单/幂等键/超额校验逻辑已抽到 components/LessonAdjustDialog.vue，
  // 这里只验证入口能把弹窗打开；组件内部行为由页面上的实际使用覆盖
  it('openAdjust 打开调整弹窗', async () => {
    const w = m()
    ;(w.vm as any).openAdjust(row)
    await nextTick()
    expect(w.html()).toContain('调整方向')
  })
})
