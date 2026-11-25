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

  it('openAdjust 打开调整弹窗并生成幂等键', async () => {
    const w = m()
    ;(w.vm as any).openAdjust(row)
    await nextTick()
    // el-dialog 的 stub 不渲染 title 属性，改为断言表单标签与弹窗节点
    expect(w.html()).toContain('调整方向')
    expect(w.find('.el-dialog').exists()).toBe(true)
    expect((w.vm as any).form.bizNo).toBeTruthy()
  })

  it('扣减超过剩余课时时提示且不调用 adjust', async () => {
    const w = withForm(m())
    const vm = w.vm as any
    vm.openAdjust({ ...row, remainingLessons: 1 })
    vm.form.direction = 2
    vm.form.amount = 5
    await vm.submitAdjust()
    expect(vi.mocked(lessonAccountApi.adjust)).not.toHaveBeenCalled()
  })

  it('提交时传递 direction/amount/remark/bizNo 四个字段', async () => {
    const w = withForm(m())
    const vm = w.vm as any
    vm.openAdjust(row)
    vm.form.remark = '停课补偿'
    await vm.submitAdjust()
    expect(vi.mocked(lessonAccountApi.adjust)).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ direction: 1, amount: 0.5, remark: '停课补偿', bizNo: expect.any(String) })
    )
  })

  it('重复提交被 submitting 拦截，只调用一次 adjust', async () => {
    const w = withForm(m())
    const vm = w.vm as any
    vm.openAdjust(row)
    vm.form.remark = '停课补偿'
    vm.submitting = true
    await vm.submitAdjust()
    expect(vi.mocked(lessonAccountApi.adjust)).not.toHaveBeenCalled()
  })
})
