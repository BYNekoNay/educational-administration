import { describe, expect, it } from 'vitest'
import { filterUpcoming, groupLessonsByDate, isToday, todayPlusDays } from './schedule-summary'

describe('isToday', () => {
  it('treats todayPlusDays(0) as today and todayPlusDays(1) as not today', () => {
    expect(isToday(todayPlusDays(0))).toBe(true)
    expect(isToday(todayPlusDays(1))).toBe(false)
    expect(isToday(todayPlusDays(-1))).toBe(false)
  })

  it('returns false for empty input', () => {
    expect(isToday('')).toBe(false)
    expect(isToday(null)).toBe(false)
  })
})

describe('todayPlusDays', () => {
  it('returns a yyyy-MM-dd string', () => {
    expect(todayPlusDays(0)).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('offsets the date by the given number of days', () => {
    const base = todayPlusDays(0)
    const [y, m, d] = base.split('-').map(Number)
    const next = new Date(y, m - 1, d + 7)
    const expected = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-${String(next.getDate()).padStart(2, '0')}`
    expect(todayPlusDays(7)).toBe(expected)
  })
})

describe('filterUpcoming', () => {
  const lessons = [
    { id: 1, lessonDate: '2026-09-08', courseName: '钢琴' },
    { id: 2, lessonDate: '2026-09-10', courseName: '美术' },
    { id: 3, lessonDate: '2026-09-15', courseName: '舞蹈' },
    { id: 4, lessonDate: '2026-09-20', courseName: '声乐' },
    { id: 5, lessonDate: '', courseName: '无日期' },
  ]

  it('keeps only lessons within [from, to] inclusive', () => {
    const result = filterUpcoming(lessons, '2026-09-08', '2026-09-15')
    expect(result.map(l => l.id)).toEqual([1, 2, 3])
  })

  it('drops lessons whose date is before from or after to', () => {
    expect(filterUpcoming(lessons, '2026-09-11', '2026-09-19').map(l => l.id)).toEqual([3])
  })

  it('returns empty for non-array input', () => {
    expect(filterUpcoming(null, '2026-09-01', '2026-09-30')).toEqual([])
  })
})

describe('groupLessonsByDate', () => {
  it('groups lessons by date and sorts groups ascending', () => {
    const lessons = [
      { id: 1, lessonDate: '2026-09-10' },
      { id: 2, lessonDate: '2026-09-08' },
      { id: 3, lessonDate: '2026-09-10' },
    ]
    const groups = groupLessonsByDate(lessons)
    expect(groups.map(g => g.date)).toEqual(['2026-09-08', '2026-09-10'])
    expect(groups[1].lessons.map(l => l.id)).toEqual([1, 3])
  })

  it('returns empty for non-array input', () => {
    expect(groupLessonsByDate(undefined)).toEqual([])
  })
})
