<template>
  <div class="schedule-calendar">
    <div class="sc-header">
      <div class="sc-corner">星期</div>
      <div v-for="(d, i) in weekDays" :key="i" class="sc-day-head">{{ d }}</div>
    </div>
    <div class="sc-body">
      <div class="sc-day-col" v-for="(d, i) in weekDays" :key="i">
        <div
          v-for="lesson in lessonsByDay[i + 1] || []"
          :key="lesson.id"
          class="sc-lesson"
          :style="{ borderLeftColor: lesson.color || '#0E7490' }"
        >
          <div class="sc-lesson-time">{{ lesson.start }} - {{ lesson.end }}</div>
          <div class="sc-lesson-title">{{ lesson.title }}</div>
          <div v-if="lesson.location" class="sc-lesson-loc">{{ lesson.location }}</div>
        </div>
        <div v-if="!(lessonsByDay[i + 1] && lessonsByDay[i + 1].length)" class="sc-empty">—</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ScheduleItem } from './types'

const props = withDefaults(
  defineProps<{
    lessons?: ScheduleItem[]
    weekDays?: string[]
  }>(),
  {
    lessons: () => [],
    weekDays: () => ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  }
)

const lessonsByDay = computed<Record<number, ScheduleItem[]>>(() => {
  const map: Record<number, ScheduleItem[]> = {}
  for (const l of props.lessons) {
    const arr = map[l.dayOfWeek] || (map[l.dayOfWeek] = [])
    arr.push(l)
  }
  for (const k of Object.keys(map)) {
    map[Number(k)].sort((a, b) => a.start.localeCompare(b.start))
  }
  return map
})
</script>

<style scoped>
.schedule-calendar {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}
.sc-header {
  display: flex;
  background: #f5f7fa;
  font-weight: 600;
  color: #303133;
}
.sc-corner {
  width: 56px;
  padding: 10px 0;
  text-align: center;
  border-right: 1px solid #ebeef5;
  font-size: 13px;
}
.sc-day-head {
  flex: 1;
  padding: 10px 0;
  text-align: center;
  border-right: 1px solid #ebeef5;
  font-size: 13px;
}
.sc-day-head:last-child {
  border-right: none;
}
.sc-body {
  display: flex;
  min-height: 220px;
}
.sc-day-col {
  flex: 1;
  border-right: 1px solid #ebeef5;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sc-day-col:first-child {
  border-left: none;
}
.sc-day-col:last-child {
  border-right: none;
}
.sc-lesson {
  background: #f0f9ff;
  border-left: 3px solid #0e7490;
  border-radius: 4px;
  padding: 6px 8px;
}
.sc-lesson-time {
  font-size: 12px;
  color: #606266;
}
.sc-lesson-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-top: 2px;
}
.sc-lesson-loc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.sc-empty {
  color: #c0c4cc;
  text-align: center;
  font-size: 12px;
  padding: 8px 0;
}
</style>
