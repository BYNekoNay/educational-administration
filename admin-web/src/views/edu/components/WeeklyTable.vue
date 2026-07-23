<template>
  <div class="timetable-wrap">
    <div class="tt-toolbar">
      <el-date-picker v-model="weekStart" type="date" placeholder="选择周一"
                      value-format="YYYY-MM-DD" @change="loadLessons" />
      <el-button @click="goThisWeek">本周</el-button>
      <el-select v-model="filters.courseId" placeholder="课程筛选" clearable style="width:150px" @change="loadLessons">
        <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filters.teacherId" placeholder="教师筛选" clearable style="width:130px" @change="loadLessons">
        <el-option v-for="t in teacherList" :key="t.id" :label="t.realName || t.username" :value="t.id" />
      </el-select>
    </div>

    <div v-loading="loading" class="tt-grid">
      <div class="tt-header">
        <div class="tt-corner">节次</div>
        <div v-for="(d,di) in weekDays" :key="di" class="tt-dayhead"
             :class="{ today: d.isToday }">
          <div class="dd">{{ d.dayLabel }}</div>
          <div class="dd-date">{{ d.dateLabel }}</div>
        </div>
      </div>
      <div v-for="p in periods" :key="p.id" class="tt-row">
        <div class="tt-slot">{{ p.name }}<br/><small>{{ p.startTime?.slice(0,5) }}-{{ p.endTime?.slice(0,5) }}</small></div>
        <div v-for="(d,di) in weekDays" :key="di" class="tt-cell"
             :class="{ 'tt-has-lesson': cellLesson(p.id, d.dateStr) }"
             @click="onCellClick(p, d)">
          <template v-if="cellLesson(p.id, d.dateStr)">
            <div class="tt-lesson-title">{{ cellLesson(p.id, d.dateStr)?.courseName || cellLesson(p.id, d.dateStr)?.className }}</div>
            <div class="tt-lesson-sub">{{ cellLesson(p.id, d.dateStr)?.teacherName }} · {{ cellLesson(p.id, d.dateStr)?.classroomName }}</div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { periodApi, scheduleApi, courseApi, teacherApi } from '@/api/edu'
import { showError } from '@/utils/error'

const loading = ref(false)
const periods = ref<any[]>([])
const lessons = ref<any[]>([])
const courseList = ref<any[]>([])
const teacherList = ref<any[]>([])
const filters = ref({ courseId: null as number | null, teacherId: null as number | null })

function getMonday(d: Date): Date {
  const day = d.getDay() || 7
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - day + 1)
}
function toDateStr(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}

const weekStart = ref(toDateStr(getMonday(new Date())))

const weekDays = computed(() => {
  const mon = new Date(weekStart.value + 'T00:00:00')
  const now = new Date()
  const todayStr = toDateStr(now)
  const names = ['一','二','三','四','五','六','日']
  return Array.from({length:7}, (_,i)=>{
    const d = new Date(mon.getTime() + i*86400000)
    return {
      dateStr: toDateStr(d),
      dayLabel: '周' + names[i],
      dateLabel: d.getMonth()+1 + '/' + d.getDate(),
      isToday: toDateStr(d) === todayStr
    }
  })
})

function cellLesson(periodId: number, dateStr: string) {
  return lessons.value.find(l => l.periodId === periodId && l.lessonDate === dateStr) || null
}

function goThisWeek() {
  weekStart.value = toDateStr(getMonday(new Date()))
  loadLessons()
}

function onCellClick(period: any, day: any) {
  const lesson = cellLesson(period.id, day.dateStr)
  if (!lesson) return
  // 无交互简洁视图：不弹窗
}

async function loadOptions() {
  try {
    const [cRes, tRes] = await Promise.all([
      courseApi.list({ pageNum: 1, pageSize: 200 }),
      teacherApi.list(),
    ])
    courseList.value = cRes.data?.records || []
    teacherList.value = tRes.data || []
  } catch { /* ignore */ }
}

async function loadPeriods() {
  try {
    const res = await periodApi.list()
    periods.value = res.data || []
  } catch { /* ignore */ }
}

async function loadLessons() {
  loading.value = true
  try {
    const dateFrom = weekDays.value[0].dateStr
    const dateTo = weekDays.value[6].dateStr
    const params: any = { pageNum: 1, pageSize: 500, dateFrom, dateTo }
    if (filters.value.courseId) params.courseId = filters.value.courseId
    if (filters.value.teacherId) params.teacherId = filters.value.teacherId
    const res = await scheduleApi.list(params)
    lessons.value = res.data?.records || []
  } catch (e) { showError(e, '加载课表失败') }
  finally { loading.value = false }
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadPeriods()])
  loadLessons()
})
</script>

<style scoped>
.timetable-wrap { background: #fff; border-radius: 6px; overflow: hidden }
.tt-toolbar { display: flex; gap: 12px; align-items: center; padding: 12px 16px; border-bottom: 1px solid #ebeef5 }
.tt-grid { overflow-x: auto }
.tt-header, .tt-row { display: flex }
.tt-corner, .tt-slot {
  width: 90px; min-width: 90px; padding: 8px 6px; text-align: center;
  font-size: 13px; border-right: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5;
  background: #f5f7fa; display: flex; flex-direction: column; justify-content: center
}
.tt-slot small { color: #909399; font-size: 11px }
.tt-dayhead {
  flex:1; min-width: 100px; padding: 8px 4px; text-align: center;
  border-right: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5; background: #f5f7fa
}
.tt-dayhead.today { background: #e0f2fe }
.tt-dayhead .dd { font-size: 13px; font-weight: 600 }
.tt-dayhead .dd-date { font-size: 12px; color: #606266; margin-top: 2px }
.tt-cell {
  flex:1; min-width: 100px; height: 62px; padding: 4px 6px;
  border-right: 1px solid #f0f0f0; border-bottom: 1px solid #f0f0f0;
  cursor: default; overflow: hidden
}
.tt-cell.tt-has-lesson { background: #ecf5ff; cursor: pointer }
.tt-cell.tt-has-lesson:hover { background: #d9ecff }
.tt-lesson-title { font-size: 12px; font-weight: 600; color: #0E7490; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.tt-lesson-sub { font-size: 11px; color: #909399; margin-top: 2px }
</style>
