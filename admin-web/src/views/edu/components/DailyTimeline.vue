<template>
  <div class="day-timeline">
    <div v-for="slot in timeSlots" :key="slot.hour + (slot.overlaps && slot.overlaps.length > 0 ? '-g' : '')" class="dt-slot">
      <div class="dt-hour">{{ slot.hour }}:00</div>
      <div class="dt-content">
        <!-- 无课程 -->
        <div v-if="!slot.items?.length && !slot.overlaps?.length" class="dt-empty"></div>
        <!-- 单课程 -->
        <div v-for="item in slot.items" :key="item.id" class="dt-card"
             :style="{ borderLeftColor: hashColor(item.courseId) }">
          <div class="dt-card-title">{{ item.courseName || item.className }}</div>
          <div class="dt-card-row">🕐 {{ item.startTime?.slice(0,5) }} - {{ item.endTime?.slice(0,5) }}</div>
          <div class="dt-card-row">👤 {{ item.teacherName }} · 🏫 {{ item.classroomName }}</div>
          <div class="dt-card-row">📚 {{ item.className }}
            <el-tag size="small" :type="statusType(item.status)">{{ statusLabel(item.status) }}</el-tag>
          </div>
        </div>
        <!-- 重叠课程纵向堆叠 -->
        <div v-if="slot.overlaps?.length" class="dt-overlap-group">
          <div v-for="(item, idx) in slot.overlaps.slice(0, 3)" :key="item.id"
               class="dt-card dt-overlap-card"
               :style="{ borderLeftColor: hashColor(item.courseId) }">
            <div class="dt-card-title">{{ item.courseName || item.className }}</div>
            <div>🕐 {{ item.startTime?.slice(0,5) }} - {{ item.endTime?.slice(0,5) }} · 👤 {{ item.teacherName }}</div>
            <div>🏫 {{ item.classroomName }}
              <el-tag size="small" :type="statusType(item.status)">{{ statusLabel(item.status) }}</el-tag>
            </div>
          </div>
          <div v-if="slot.overlaps.length > 3" class="dt-more">
            +{{ slot.overlaps.length - 3 }} 更多课程
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  lessons: any[]
  date: string
}>()

const timeSlots = computed(() => {
  // 过滤当天课程
  const dayLessons = props.lessons.filter(l => l.lessonDate === props.date)
  // 按开始时间排序
  dayLessons.sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''))

  const slots: any[] = []
  const startHour = 7
  const endHour = 22

  for (let h = startHour; h <= endHour; h++) {
    const hStr = String(h).padStart(2, '0') + ':00'
    // 找出该小时开始的课程
    const items: any[] = []
    const remaining: any[] = []
    for (const l of dayLessons) {
      const s = l.startTime || ''
      const e = l.endTime || ''
      if (s >= hStr && s < String(h + 1).padStart(2, '0') + ':00') {
        // 检查是否有重叠
        let overlap = false
        for (const other of items) {
          if (timeOverlap(s, e, other.startTime, other.endTime)) {
            overlap = true
            break
          }
        }
        if (overlap) {
          remaining.push(l)
        } else {
          items.push(l)
        }
      }
    }

    // 剩余重叠课程归入 overlaps，垂直堆叠
    const overlaps = remaining.length > 0 ? remaining : undefined
    slots.push({ hour: h, items, overlaps })
  }
  return slots
})

function timeOverlap(s1: string, e1: string, s2: string, e2: string) {
  return s1 < e2 && s2 < e1
}

function statusLabel(status: number): string {
  const map: Record<number, string> = { 1: '待上课', 2: '已完成', 3: '已取消', 4: '已调课' }
  return map[status] || ''
}

function statusType(status: number): string {
  const map: Record<number, string> = { 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }
  return map[status] || ''
}

const colors = ['#0E7490','#D97706','#059669','#7C3AED','#DB2777','#2563EB','#DC2626','#0891B2','#A21CAF','#4D7C0F']
function hashColor(id: number | null): string {
  if (!id) return colors[0]
  return colors[Math.abs(id ^ (id >> 4)) % colors.length]
}
</script>

<style scoped>
.day-timeline { border:1px solid #ebeef5; border-radius:6px; overflow:hidden }
.dt-slot { display:flex; min-height:60px; border-bottom:1px solid #eee }
.dt-slot:last-child { border-bottom:none }
.dt-hour { width:56px; padding:8px 4px; text-align:center; font-size:12px; color:#909399; background:#fafafa; border-right:1px solid #ebeef5; flex-shrink:0 }
.dt-content { flex:1; padding:4px 8px; display:flex; flex-direction:column; gap:2px }
.dt-empty { flex:1 }
.dt-card { background:#f0f9ff; border-left:3px solid; border-radius:4px; padding:6px 8px; margin:2px 0; flex:1 }
.dt-card-title { font-size:14px; font-weight:600; color:#303133 }
.dt-card-row { font-size:12px; color:#606266; margin-top:2px }
.dt-overlap-group { display:flex; flex-direction:column; gap:2px; flex:1 }
.dt-overlap-card { flex:1; min-height:40px }
.dt-more { font-size:12px; color:#909399; text-align:center; padding:4px }
</style>
