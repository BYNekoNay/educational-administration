import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { mountPage } from './helpers'

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { scheduleApi } from '@/api/edu'
import { useAuthStore } from '@/stores/auth'
import BigSchedule from '@/views/edu/BigSchedule.vue'

// 自定义 el-switch stub：可点击切换 modelValue（baseStubs 中的 true stub 无法触发 v-model）
const ElSwitchStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<button class="el-switch-stub" @click="$emit(\'update:modelValue\', !modelValue)">{{ modelValue ? "on" : "off" }}</button>',
}

// EditableWeekGrid stub：点击模拟一次 scheduleMove（drop 结果），便于在页面层验证确认弹窗
const EditableWeekGridStub = {
  template: '<div class="ewg-stub" @click="$emit(\'scheduleMove\', { lesson: { id: 5, status: 1, lessonDate: \'2026-01-01\', startTime: \'09:00\', endTime: \'10:00\', className: \'一班\', teacherName: \'张老师\', classroomName: \'A教室\' }, target: { lessonDate: \'2026-01-02\', startTime: \'10:00\', endTime: \'11:00\' } })">EWG</div>',
  props: ['lessons', 'weekDays', 'weekDates', 'canEdit'],
  emits: ['scheduleMove'],
}

const extraStubs = {
  // baseStubs 的 el-button 声明了 emits:['click']，原生 click 不会透传到父级 @click，
  // 此处改用无 emits 的最小 stub，使 trigger('click') 能触发页面事件（与 ScheduleList.test 一致）
  'el-button': { template: '<button><slot /></button>' },
  'el-switch': ElSwitchStub,
  'el-radio-group': { template: '<div class="el-radio-group"><slot /></div>' },
  'el-radio-button': { template: '<button class="el-radio-button"><slot /></button>' },
  EditableWeekGrid: EditableWeekGridStub,
}

function m(role = 'SUPER_ADMIN') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().roleCode = role
  return mountPage(BigSchedule, extraStubs, [pinia])
}

async function settle() {
  // 冲掉 onMounted 中 loadOptions -> loadLessons 的多级 Promise 链
  for (let i = 0; i < 5; i += 1) await nextTick()
  await new Promise((r) => setTimeout(r, 10))
  for (let i = 0; i < 5; i += 1) await nextTick()
}

describe('BigSchedule.vue P1 拖拽调课', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('渲染标题并加载课表', async () => {
    const w = m()
    await settle()
    expect(w.text()).toContain('大课表')
    expect(vi.mocked(scheduleApi.list)).toHaveBeenCalled()
  })

  it('教务管理员/超级管理员可见编辑模式开关', () => {
    expect(m('SUPER_ADMIN').find('.el-switch-stub').exists()).toBe(true)
    expect(m('EDU_ADMIN').find('.el-switch-stub').exists()).toBe(true)
  })

  it('非教务角色不可见编辑模式开关', () => {
    expect(m('FINANCE').find('.el-switch-stub').exists()).toBe(false)
  })

  it('开启编辑模式后渲染可编辑周网格', async () => {
    const w = m('SUPER_ADMIN')
    expect(w.find('.ewg-stub').exists()).toBe(false)
    await w.find('.el-switch-stub').trigger('click')
    await nextTick()
    expect(w.find('.ewg-stub').exists()).toBe(true)
  })

  it('拖放后弹出确认框、查询影响范围，确认后调用 quickAdjust 并刷新', async () => {
    vi.mocked(scheduleApi.notifyScope).mockResolvedValue({
      data: { teacherId: 0, teacherName: '李老师', parentCount: 3 },
    } as any)
    const w = m('SUPER_ADMIN')
    await w.find('.el-switch-stub').trigger('click')
    await nextTick()
    // 模拟网格 drop
    await w.find('.ewg-stub').trigger('click')
    await nextTick()
    await nextTick()
    expect(vi.mocked(scheduleApi.notifyScope)).toHaveBeenCalledWith(5)
    const dialogText = w.text()
    expect(dialogText).toContain('新时间')
    expect(dialogText).toContain('2026-01-02')
    expect(dialogText).toContain('将通知 3 位家长')
    expect(dialogText).toContain('李老师')
    // 确认调课
    const confirmBtn = w.findAll('button').find((b) => b.text() === '确认调课')
    expect(confirmBtn).toBeTruthy()
    await confirmBtn!.trigger('click')
    await nextTick()
    expect(vi.mocked(scheduleApi.quickAdjust)).toHaveBeenCalledWith(5, {
      lessonDate: '2026-01-02',
      startTime: '10:00',
      endTime: '11:00',
      reason: '',
    })
    // 成功后重新拉取课表
    expect(vi.mocked(scheduleApi.list).mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(w.find('.el-dialog').exists()).toBe(false)
  })
})
