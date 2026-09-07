import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { weekDates, toDateStr } from '@/views/edu/components/useScheduleDrag'

vi.mock('@/api/edu', async () => {
  const { createEduApiMocks } = await import('./mocks/factory')
  return createEduApiMocks()
})

import { periodApi } from '@/api/edu'
import EditableWeekGrid from '@/views/edu/components/EditableWeekGrid.vue'

const fmtWeek = (d: Date) => weekDates(d).map(toDateStr)
// 未来周（保证全部日期在今天之后，避免与真实日期耦合导致拖拽判定不稳定）
const futureWeek = fmtWeek(new Date(Date.now() + 7 * 86400000))
const pastWeek = fmtWeek(new Date(Date.now() - 7 * 86400000))
const dayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function baseLesson(overrides: Record<string, any> = {}) {
  return {
    id: 11,
    status: 1,
    lessonDate: futureWeek[0],
    startTime: '09:00',
    endTime: '09:45',
    teacherId: 1,
    classroomId: 2,
    classId: 3,
    courseName: '钢琴',
    className: '一班',
    teacherName: '张老师',
    classroomName: 'A教室',
    ...overrides,
  }
}

async function mountGrid(props: Record<string, any>) {
  vi.mocked(periodApi.list).mockResolvedValue({
    data: [
      { id: 1, name: '第一节', startTime: '09:00', endTime: '10:00' },
      { id: 2, name: '第二节', startTime: '10:00', endTime: '11:00' },
    ],
  } as any)
  const wrapper = mount(EditableWeekGrid, {
    props: {
      lessons: [],
      weekDays: dayLabels,
      weekDates: futureWeek,
      canEdit: true,
      ...props,
    },
    global: { stubs: { 'el-tag': true } },
  })
  await nextTick()
  await nextTick()
  return wrapper
}

describe('EditableWeekGrid.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('渲染表头与课时时段行', async () => {
    const w = await mountGrid({})
    const headers = w.findAll('th')
    expect(headers).toHaveLength(8) // 时段 + 7 天
    expect(w.text()).toContain('第一节')
    expect(w.text()).toContain('第二节')
  })

  it('按日期列渲染课次', async () => {
    const lesson = baseLesson()
    const w = await mountGrid({ lessons: [lesson] })
    const row1Cells = w.findAll('tbody tr')[0].findAll('td')
    // 第一行首列是时段名，第一课次应落在周一列
    expect(row1Cells[1].text()).toContain('钢琴')
    expect(row1Cells[2].text()).not.toContain('钢琴')
  })

  it('未来空单元格显示加号，过去单元格显示已过去', async () => {
    const w = await mountGrid({ weekDates: pastWeek, lessons: [] })
    expect(w.text()).toContain('已过去')
    expect(w.text()).not.toContain('＋')
  })

  it('可拖拽课次拖放到空目标格后 emit scheduleMove', async () => {
    const lesson = baseLesson()
    const w = await mountGrid({ lessons: [lesson] })
    const cellCols = w.findAll('tbody tr')[0].findAll('td')
    const targetCell = cellCols[4] // 周四（无课）
    const dataTransfer = { setData: vi.fn(), effectAllowed: '' }
    await w.findAll('.ewg-lesson')[0].trigger('dragstart', { dataTransfer })
    await targetCell.trigger('dragover')
    // 悬停合法格高亮
    expect(targetCell.classes()).toContain('cell-ok')
    await targetCell.trigger('drop')
    const emitted = w.emitted('scheduleMove')
    expect(emitted).toBeTruthy()
    const payload = emitted![0][0] as any
    expect(payload.lesson.id).toBe(11)
    expect(payload.target).toEqual({
      lessonDate: futureWeek[3],
      startTime: '09:00',
      endTime: '10:00',
    })
  })

  it('本地冲突格悬停标红且 drop 不 emit', async () => {
    // 冲突课：周五同一时段同一教师
    const lesson = baseLesson()
    const conflict = baseLesson({
      id: 12,
      lessonDate: futureWeek[4],
      startTime: '09:00',
      endTime: '09:30',
      courseName: '声乐',
      className: '二班',
    })
    const w = await mountGrid({ lessons: [lesson, conflict] })
    const row1Cells = w.findAll('tbody tr')[0].findAll('td')
    const targetCell = row1Cells[5] // 周五，conflict 所在
    await w.findAll('.ewg-lesson')[0].trigger('dragstart')
    await targetCell.trigger('dragover')
    expect(targetCell.classes()).toContain('cell-conflict')
    await targetCell.trigger('drop')
    expect(w.emitted('scheduleMove')).toBeFalsy()
    expect(w.text()).toContain('存在冲突')
  })

  it('过去日期禁止放置且不 emit', async () => {
    // 构造"昨天、今天、未来…"的 7 天列：拖拽未来的课次放到昨天所在列应被拦截
    const base = new Date(Date.now() - 86400000)
    const mixedWeek = Array.from({ length: 7 }, (_, i) =>
      toDateStr(new Date(base.getFullYear(), base.getMonth(), base.getDate() + i)),
    )
    const lesson = baseLesson({ lessonDate: mixedWeek[2] })
    const w = await mountGrid({ weekDates: mixedWeek, lessons: [lesson] })
    const row1Cells = w.findAll('tbody tr')[0].findAll('td')
    const targetCell = row1Cells[1] // 昨天列
    expect(w.findAll('.ewg-lesson').length).toBe(1)
    await w.findAll('.ewg-lesson')[0].trigger('dragstart')
    await targetCell.trigger('dragover')
    await targetCell.trigger('drop')
    expect(w.emitted('scheduleMove')).toBeFalsy()
    expect(w.text()).toContain('目标为过去日期')
  })
})
