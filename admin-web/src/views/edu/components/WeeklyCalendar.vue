<template>
  <div class="schedule-calendar">
    <div class="sc-header">
      <div v-for="(d, i) in weekDays" :key="i" class="sc-day-head">{{ d }}</div>
    </div>
    <div class="sc-body">
      <div class="sc-day-col" v-for="(d, i) in weekDays" :key="i">
        <div
          v-for="lesson in lessonsByDay[i]" :key="lesson.id"
          class="sc-lesson" :style="{ borderLeftColor: hashColor(lesson.courseId) }"
        >
          <div class="sc-title">
            <span>{{ lesson.courseName || lesson.className }}</span>
            <el-tag size="small" :type="statusType(lesson.status)">{{ statusLabel(lesson.status) }}</el-tag>
          </div>
          <div class="sc-time">{{ lesson.startTime?.slice(0,5) }} - {{ lesson.endTime?.slice(0,5) }}</div>
          <div class="sc-info">{{ lesson.teacherName }} · {{ lesson.classroomName }}</div>
        </div>
        <div v-if="!lessonsByDay[i]?.length" class="sc-empty">—</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  lessons: any[]
  weekDays: string[]
}>()

const lessonsByDay = computed(() => {
  const map: Record<number, any[]> = {}
  for (const l of props.lessons) {
    const d = new Date(l.lessonDate + 'T00:00:00')
    let dow = d.getDay() - 1
    if (dow < 0) dow = 6
    if (!map[dow]) map[dow] = []
    map[dow].push(l)
  }
  for (const k of Object.keys(map)) {
    map[Number(k)].sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''))
  }
  return map
})

const colors = ['#0E7490','#D97706','#059669','#7C3AED','#DB2777','#2563EB','#DC2626','#0891B2','#A21CAF','#4D7C0F']
function hashColor(id: number | null): string {
  if (!id) return colors[0]
  return colors[Math.abs(id ^ (id >> 4)) % colors.length]
}

// 与 DailyTimeline 保持一致的状态映射
function statusLabel(status: number): string {
  const map: Record<number, string> = { 1: '待上课', 2: '已完成', 3: '已取消', 4: '已调课' }
  return map[status] || ''
}
function statusType(status: number): string {
  const map: Record<number, string> = { 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }
  return map[status] || ''
}
</script>

<style scoped>
.schedule-calendar { border:1px solid #ebeef5; border-radius:6px; overflow:hidden; background:#fff }
.sc-header { display:flex; background:#f5f7fa; font-weight:600; color:#303133 }
.sc-day-head { flex:1; padding:10px 4px; text-align:center; border-right:1px solid #ebeef5; font-size:12px }
.sc-day-head:last-child { border-right:none }
.sc-body { display:flex; min-height:400px }
.sc-day-col { flex:1; border-right:1px solid #ebeef5; padding:6px; display:flex; flex-direction:column; gap:6px }
.sc-day-col:last-child { border-right:none }
.sc-lesson { background:#f0f9ff; border-left:3px solid; border-radius:4px; padding:6px }
.sc-title { font-size:13px; font-weight:600; color:#303133 }
.sc-time { font-size:11px; color:#606266; margin-top:2px }
.sc-info { font-size:11px; color:#909399; margin-top:1px }
.sc-empty { color:#c0c4cc; text-align:center; font-size:12px; padding:8px 0 }
</style>
