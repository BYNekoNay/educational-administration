<template>
  <div class="monthly-cal">
    <div class="mo-header">
      <div v-for="d in days" :key="d" class="mo-day-head">{{ d }}</div>
    </div>
    <div class="mo-body" :style="{ gridTemplateRows: `repeat(${rows}, 1fr)` }">
      <div
        v-for="cell in cells"
        :key="cell.key"
        class="mo-cell"
        :class="{ 'mo-other-month': !cell.currentMonth, 'mo-today': cell.isToday }"
      >
        <div class="mo-date">{{ cell.day }}</div>
        <div v-for="l in cell.lessons.slice(0, maxShow)" :key="l.id" class="mo-lesson"
             :class="'st-' + (l.status || 0)"
             :title="statusLabel(l.status)"
             :style="{ borderLeftColor: hashColor(l.courseId) }">
          {{ l.courseName || l.className }} {{ l.startTime?.slice(0,5) }}
        </div>
        <div v-if="cell.lessons.length > maxShow" class="mo-more">
          +{{ cell.lessons.length - maxShow }} 更多
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  lessons: any[]
  year: number
  month: number
}>()

const days = ['周一','周二','周三','周四','周五','周六','周日']
const maxShow = 3

const cells = computed(() => {
  const first = new Date(props.year, props.month - 1, 1)
  const last = new Date(props.year, props.month, 0)
  const totalDays = last.getDate()
  // 周一起始偏移（0=周一..6=周日）
  let startDow = first.getDay() - 1
  if (startDow < 0) startDow = 6

  const byDay = new Map<string, any[]>()
  for (const l of props.lessons) {
    const k = l.lessonDate
    if (!byDay.has(k)) byDay.set(k, [])
    byDay.get(k)!.push(l)
  }
  // 同日内按开始时间排序（后端默认仅按日期排，日内顺序不定；与 WeeklyCalendar/DailyTimeline 保持一致）
  for (const arr of byDay.values()) {
    arr.sort((a, b) => String(a.startTime || '').localeCompare(String(b.startTime || '')))
  }

  const now = new Date()
  const todayStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`

  const result: any[] = []
  // 前置空白格
  for (let i = 0; i < startDow; i++) {
    result.push({ key: `e${i}`, day: '', currentMonth: false, isToday: false, lessons: [] })
  }
  // 当月日期
  for (let d = 1; d <= totalDays; d++) {
    const ds = `${props.year}-${String(props.month).padStart(2,'0')}-${String(d).padStart(2,'0')}`
    result.push({
      key: `d${d}`,
      day: d,
      currentMonth: true,
      isToday: ds === todayStr,
      lessons: byDay.get(ds) || []
    })
  }
  // 补齐到7的倍数
  while (result.length % 7 !== 0) {
    result.push({ key: `l${result.length}`, day: '', currentMonth: false, isToday: false, lessons: [] })
  }
  return result
})

const rows = computed(() => Math.ceil(cells.value.length / 7))

const colors = ['#0E7490','#D97706','#059669','#7C3AED','#DB2777','#2563EB','#DC2626','#0891B2','#A21CAF','#4D7C0F']
function hashColor(id: number | null): string {
  if (!id) return colors[0]
  return colors[Math.abs(id ^ (id >> 4)) % colors.length]
}

const statusText: Record<number, string> = { 1: '待上课', 2: '已完成', 3: '已取消', 4: '已调课' }
function statusLabel(status: number): string {
  return statusText[status] || ''
}
</script>

<style scoped>
.monthly-cal { border:1px solid #ebeef5; border-radius:6px; overflow:hidden }
.mo-header { display:grid; grid-template-columns:repeat(7,1fr); background:#f5f7fa }
.mo-day-head { padding:8px 0; text-align:center; font-weight:600; font-size:13px; border-right:1px solid #ebeef5 }
.mo-day-head:last-child { border-right:none }
.mo-body { display:grid; grid-template-columns:repeat(7,1fr); min-height:400px }
.mo-cell { border-right:1px solid #ebeef5; border-bottom:1px solid #ebeef5; padding:4px; min-height:80px; font-size:12px }
.mo-cell:nth-child(7n) { border-right:none }
.mo-other-month { background:#fafafa }
.mo-today { background:#e0f2fe }
.mo-date { font-weight:600; color:#303133; margin-bottom:2px }
.mo-lesson { padding:1px 4px; margin:1px 0; border-left:3px solid; border-radius:2px; background:#f0f9ff; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; cursor:default }
.mo-lesson.st-2 { opacity:.55 }
.mo-lesson.st-3, .mo-lesson.st-4 { opacity:.5; text-decoration:line-through }
.mo-more { font-size:11px; color:#909399; margin-top:1px }
</style>
