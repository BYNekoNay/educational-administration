import { describe, it, expect } from 'vitest'
import {
  toDateStr,
  parseDateStr,
  getMonday,
  weekDates,
  timeToMinutes,
  timesOverlap,
  canDragLesson,
  isPastDay,
  targetForPeriod,
  localConflicts,
  buildQuickAdjustPayload,
  dayIndex,
  fmtTime,
} from '@/views/edu/components/useScheduleDrag'

describe('useScheduleDrag 日期工具', () => {
  it('toDateStr 输出 yyyy-MM-dd', () => {
    expect(toDateStr(new Date(2026, 5, 15))).toBe('2026-06-15')
    expect(toDateStr(new Date(2026, 0, 5))).toBe('2026-01-05')
  })

  it('parseDateStr 解析为本地当天零点', () => {
    const d = parseDateStr('2026-06-15')
    expect(d.getFullYear()).toBe(2026)
    expect(d.getMonth()).toBe(5)
    expect(d.getDate()).toBe(15)
    expect(d.getHours()).toBe(0)
  })

  it('parseDateStr 与 toDateStr 互为往返', () => {
    const s = '2026-12-31'
    expect(toDateStr(parseDateStr(s))).toBe(s)
  })

  it('getMonday 返回所在周的周一', () => {
    // 2026-06-15 为周一
    expect(toDateStr(getMonday(new Date(2026, 5, 15)))).toBe('2026-06-15')
    expect(toDateStr(getMonday(new Date(2026, 5, 18)))).toBe('2026-06-15')
    // 周日（getDay()=0）同样归到本周一
    expect(toDateStr(getMonday(new Date(2026, 5, 21)))).toBe('2026-06-15')
  })

  it('weekDates 返回周一至周日连续 7 天', () => {
    const days = weekDates(new Date(2026, 5, 17))
    expect(days).toHaveLength(7)
    expect(toDateStr(days[0])).toBe('2026-06-15')
    expect(toDateStr(days[6])).toBe('2026-06-21')
  })

  it('timeToMinutes 支持 HH:mm 与 HH:mm:ss，非法返回 null', () => {
    expect(timeToMinutes('09:05')).toBe(545)
    expect(timeToMinutes('09:05:30')).toBe(545)
    expect(timeToMinutes('23:59')).toBe(1439)
    expect(timeToMinutes('')).toBeNull()
    expect(timeToMinutes('25:00')).toBeNull()
    expect(timeToMinutes('9:5')).toBeNull()
    expect(timeToMinutes(undefined)).toBeNull()
  })

  it('timesOverlap 判断同一日时间段重叠', () => {
    expect(timesOverlap('09:00', '10:00', '09:30', '10:30')).toBe(true)
    expect(timesOverlap('09:00', '10:00', '10:00', '11:00')).toBe(false)
    expect(timesOverlap('09:00', '10:00', '08:00', '09:00')).toBe(false)
    expect(timesOverlap('09:00', '10:00', '08:00', '09:30')).toBe(true)
    expect(timesOverlap('09:00', '', '08:00', '09:30')).toBe(false)
  })
})

describe('useScheduleDrag 可拖拽判定', () => {
  // 固定"当前"时间：2026-06-17（周三）10:00
  const now = new Date(2026, 5, 17, 10, 0)

  it('非待上课课次不可拖拽', () => {
    expect(canDragLesson({ id: 1, status: 2, lessonDate: '2026-06-18', startTime: '09:00' }, now)).toEqual({
      allowed: false,
      reason: '仅待上课且未开始的课次可调',
    })
  })

  it('已过去的课次不可拖拽', () => {
    expect(canDragLesson({ id: 1, status: 1, lessonDate: '2026-06-16', startTime: '09:00' }, now)).toEqual({
      allowed: false,
      reason: '已过去的课次不可调',
    })
  })

  it('今日已开始的课次不可拖拽', () => {
    const r = canDragLesson({ id: 1, status: 1, lessonDate: '2026-06-17', startTime: '09:00' }, now)
    expect(r.allowed).toBe(false)
    expect(r.reason).toContain('已开始')
  })

  it('今日未开始的课次可拖拽', () => {
    expect(canDragLesson({ id: 1, status: 1, lessonDate: '2026-06-17', startTime: '11:00' }, now).allowed).toBe(true)
  })

  it('未来课次可拖拽', () => {
    expect(canDragLesson({ id: 1, status: 1, lessonDate: '2026-06-18', startTime: '09:00' }, now).allowed).toBe(true)
  })

  it('缺少日期返回不可拖拽', () => {
    expect(canDragLesson({ id: 1, status: 1 }, now).allowed).toBe(false)
  })

  it('isPastDay 将今天视为非过去', () => {
    expect(isPastDay('2026-06-16', now)).toBe(true)
    expect(isPastDay('2026-06-17', now)).toBe(false)
    expect(isPastDay('2026-06-18', now)).toBe(false)
  })
})

describe('useScheduleDrag 冲突预检与载荷', () => {
  const lesson = {
    id: 1,
    status: 1,
    lessonDate: '2026-06-18',
    startTime: '09:00',
    endTime: '10:00',
    teacherId: 10,
    classroomId: 20,
    classId: 30,
  }
  const target = { lessonDate: '2026-06-18', startTime: '09:00', endTime: '10:00' }

  it('targetForPeriod 组装目标时间', () => {
    expect(targetForPeriod('2026-06-18', { id: 2, name: '第二节', startTime: '10:00', endTime: '11:00' })).toEqual({
      lessonDate: '2026-06-18',
      startTime: '10:00',
      endTime: '11:00',
    })
  })

  it('无重叠时本地无冲突', () => {
    const others = [{ id: 2, lessonDate: '2026-06-18', startTime: '11:00', endTime: '12:00', teacherId: 99, classroomId: 99, classId: 99, status: 1 }]
    expect(localConflicts(lesson, target, others)).toEqual([])
  })

  it('识别教师冲突', () => {
    const others = [{ id: 2, lessonDate: '2026-06-18', startTime: '09:30', endTime: '10:30', teacherId: 10, teacherName: '张老师', status: 1 }]
    const c = localConflicts(lesson, target, others)
    expect(c.some((t) => t.includes('教师冲突'))).toBe(true)
  })

  it('识别教室冲突', () => {
    const others = [{ id: 2, lessonDate: '2026-06-18', startTime: '09:30', endTime: '10:30', classroomId: 20, classroomName: 'A教室', status: 1 }]
    const c = localConflicts(lesson, target, others)
    expect(c.some((t) => t.includes('教室冲突'))).toBe(true)
  })

  it('识别班级冲突', () => {
    const others = [{ id: 2, lessonDate: '2026-06-18', startTime: '09:30', endTime: '10:30', classId: 30, status: 1 }]
    const c = localConflicts(lesson, target, others)
    expect(c.some((t) => t.includes('班级冲突'))).toBe(true)
  })

  it('跳过自身、不同日期、已取消/已调课课次', () => {
    const others = [
      { id: 1, lessonDate: '2026-06-18', startTime: '09:30', endTime: '10:30', teacherId: 10, status: 1 },
      { id: 2, lessonDate: '2026-06-19', startTime: '09:30', endTime: '10:30', teacherId: 10, status: 1 },
      { id: 3, lessonDate: '2026-06-18', startTime: '09:30', endTime: '10:30', teacherId: 10, status: 3 },
      { id: 4, lessonDate: '2026-06-18', startTime: '09:30', endTime: '10:30', teacherId: 10, status: 4 },
    ]
    expect(localConflicts(lesson, target, others)).toEqual([])
  })

  it('buildQuickAdjustPayload 组装并裁剪 reason', () => {
    expect(buildQuickAdjustPayload(lesson, target, '  换到上午  ')).toEqual({
      lessonDate: '2026-06-18',
      startTime: '09:00',
      endTime: '10:00',
      reason: '换到上午',
    })
    expect(buildQuickAdjustPayload(lesson, target).reason).toBe('')
  })

  it('dayIndex 返回目标日在一周中的列索引', () => {
    const week = ['2026-06-15', '2026-06-16', '2026-06-17', '2026-06-18', '2026-06-19', '2026-06-20', '2026-06-21']
    expect(dayIndex('2026-06-18', week)).toBe(3)
    expect(dayIndex('2026-07-01', week)).toBe(-1)
    expect(dayIndex('', week)).toBe(-1)
  })

  it('fmtTime 统一裁剪到 HH:mm', () => {
    expect(fmtTime('09:05:30')).toBe('09:05')
    expect(fmtTime('09:05')).toBe('09:05')
    expect(fmtTime('')).toBe('')
    expect(fmtTime(null)).toBe('')
  })
})
