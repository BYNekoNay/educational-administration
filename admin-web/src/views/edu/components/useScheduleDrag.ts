/**
 * 排课拖拽纯逻辑（P1）
 *
 * 与 UI/依赖无关的时间计算、可拖拽判定、目标格时间计算、本地简化冲突预检与载荷组装，
 * 便于 Vitest 纯单测。本地预检仅覆盖"教师/教室/班级"三要素（基于已加载课次）；
 * 学员跨班冲突与教室预约等需后端 check-conflict 复核，保存时以后端为准。
 */

export interface DragLesson {
  id: number
  status?: number
  lessonDate?: string
  startTime?: string
  endTime?: string
  classId?: number | null
  teacherId?: number | null
  classroomId?: number | null
  courseName?: string
  className?: string
  teacherName?: string
  classroomName?: string
}

export interface DragPeriod {
  id?: number | string
  name?: string
  startTime?: string
  endTime?: string
}

export interface DropTarget {
  /** yyyy-MM-dd */
  lessonDate: string
  /** HH:mm 或 HH:mm:ss */
  startTime: string
  endTime: string
}

export interface DragDecision {
  allowed: boolean
  reason?: string
}

export interface QuickAdjustPayload {
  lessonDate: string
  startTime: string
  endTime: string
  reason: string
}

const pad = (n: number) => String(n).padStart(2, '0')

/** Date → yyyy-MM-dd（本地时区） */
export function toDateStr(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** yyyy-MM-dd → 本地时区当天 00:00 的 Date */
export function parseDateStr(s: string): Date {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, (m || 1) - 1, d || 1)
}

/** 所在周的周一 */
export function getMonday(d: Date): Date {
  const day = d.getDay() || 7
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - day + 1)
}

/** 所在周 7 天日期（周一 → 周日） */
export function weekDates(anchor: Date): Date[] {
  const monday = getMonday(anchor)
  return Array.from({ length: 7 }, (_, i) => new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + i))
}

/** "HH:mm[:ss]" → 当天分钟数；非法返回 null */
export function timeToMinutes(t?: string | null): number | null {
  if (!t) return null
  const m = /^(\d{1,2}):(\d{2})/.exec(t.trim())
  if (!m) return null
  const h = Number(m[1])
  const min = Number(m[2])
  if (h > 23 || min > 59) return null
  return h * 60 + min
}

/** 同一日期的两个时间段是否有重叠（同为某目标日的本地预检） */
export function timesOverlap(aStart?: string | null, aEnd?: string | null,
                             bStart?: string | null, bEnd?: string | null): boolean {
  const as = timeToMinutes(aStart)
  const ae = timeToMinutes(aEnd)
  const bs = timeToMinutes(bStart)
  const be = timeToMinutes(bEnd)
  if (as === null || ae === null || bs === null || be === null) return false
  return as < be && ae > bs
}

/**
 * 可拖拽判定：仅 status=1 且上课日期在今天或未来；今天已过开始时间禁止拖拽。
 */
export function canDragLesson(lesson: DragLesson, now: Date = new Date()): DragDecision {
  if (!lesson || lesson.status !== 1) {
    return { allowed: false, reason: '仅待上课且未开始的课次可调' }
  }
  const todayStr = toDateStr(now)
  const dateStr = lesson.lessonDate
  if (!dateStr) return { allowed: false, reason: '课次缺少日期，不可调' }
  if (dateStr < todayStr) {
    return { allowed: false, reason: '已过去的课次不可调' }
  }
  if (dateStr === todayStr) {
    const start = timeToMinutes(lesson.startTime)
    const nowMin = now.getHours() * 60 + now.getMinutes()
    if (start !== null && start <= nowMin) {
      return { allowed: false, reason: '该课次已开始，不可调' }
    }
  }
  return { allowed: true }
}

/** 目标日期是否为今天之前（禁止放置，置灰） */
export function isPastDay(dayDateStr: string, now: Date = new Date()): boolean {
  return dayDateStr < toDateStr(now)
}

/** 目标格（日期 × 时段） → 载荷中的时间 */
export function targetForPeriod(dayDateStr: string, period: DragPeriod): DropTarget {
  return {
    lessonDate: dayDateStr,
    startTime: period.startTime || '',
    endTime: period.endTime || '',
  }
}

/**
 * 本地简化冲突预检：教师 / 教室 / 班级 在目标日同时间段已有课次（排除自身）。
 * 返回原因文案列表；空数组表示本地无冲突（仍须后端复核）。
 */
export function localConflicts(lesson: DragLesson, target: DropTarget, allLessons: DragLesson[]): string[] {
  const conflicts: string[] = []
  if (!target.lessonDate || !target.startTime || !target.endTime) return conflicts
  for (const other of allLessons || []) {
    if (!other || other.id === lesson.id) continue
    if (other.lessonDate !== target.lessonDate) continue
    const status = other.status
    // 与后端一致：已取消(3)/已调课(4)不参与占用判定；null 视为待上课
    if (status === 3 || status === 4) continue
    if (!timesOverlap(target.startTime, target.endTime, other.startTime, other.endTime)) continue
    if (lesson.teacherId != null && lesson.teacherId === other.teacherId) {
      conflicts.push(`教师冲突：${other.teacherName || `教师${other.teacherId}`} ${fmtTime(other.startTime)}-${fmtTime(other.endTime)} 已有课`)
    }
    if (lesson.classroomId != null && lesson.classroomId === other.classroomId) {
      conflicts.push(`教室冲突：${other.classroomName || `教室${other.classroomId}`} 该时段已被占用`)
    }
    if (lesson.classId != null && lesson.classId === other.classId) {
      conflicts.push(`班级冲突：该班 ${fmtTime(other.startTime)}-${fmtTime(other.endTime)} 已有课次`)
    }
  }
  return conflicts
}

/** 组装快速调课请求载荷 */
export function buildQuickAdjustPayload(lesson: DragLesson, target: DropTarget, reason = ''): QuickAdjustPayload {
  return {
    lessonDate: target.lessonDate,
    startTime: target.startTime,
    endTime: target.endTime,
    reason: reason?.trim() || '',
  }
}

/** 由目标日期偏移周几索引（0=周一 … 6=周日） */
export function dayIndex(dateStr: string, weekStart: string[]): number {
  if (!dateStr || !weekStart?.length) return -1
  return weekStart.indexOf(dateStr)
}

/** "HH:mm[:ss]" → "HH:mm" 展示 */
export function fmtTime(t?: string | null): string {
  return t ? String(t).slice(0, 5) : ''
}
