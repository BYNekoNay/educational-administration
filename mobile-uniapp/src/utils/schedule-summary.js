// 课表日期相关纯函数，统一收敛日期逻辑，避免各页面重复实现。
// 约定：日期统一以 'yyyy-MM-dd' 字符串参与比较（该格式字典序与日期序一致）。

function toDateStr(d) {
  if (!d) return ''
  if (d instanceof Date) {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  }
  return String(d).substring(0, 10)
}

// 判断给定日期是否为今天
export function isToday(date) {
  if (!date) return false
  return toDateStr(date) === toDateStr(new Date())
}

// 返回今天偏移 n 天后的 'yyyy-MM-dd' 字符串（n 可为负）
export function todayPlusDays(n) {
  const d = new Date()
  d.setDate(d.getDate() + Number(n) || 0)
  return toDateStr(d)
}

// 过滤落在区间 [from, to] 内的课次（含端点）；from/to 可为 'yyyy-MM-dd' 或 Date
export function filterUpcoming(lessons, from, to) {
  if (!Array.isArray(lessons)) return []
  const fromStr = toDateStr(from)
  const toStr = toDateStr(to)
  return lessons.filter(l => {
    const d = toDateStr(l && l.lessonDate)
    if (!d) return false
    if (fromStr && d < fromStr) return false
    if (toStr && d > toStr) return false
    return true
  })
}

// 按 lessonDate 分组并按日期升序返回 [{ date, lessons: [...] }]
export function groupLessonsByDate(lessons) {
  if (!Array.isArray(lessons)) return []
  const map = new Map()
  for (const l of lessons) {
    const d = toDateStr(l && l.lessonDate)
    if (!map.has(d)) map.set(d, { date: d, lessons: [] })
    map.get(d).lessons.push(l)
  }
  return Array.from(map.values()).sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0))
}

export default { isToday, todayPlusDays, filterUpcoming, groupLessonsByDate }
