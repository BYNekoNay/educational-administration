<template>
  <div>
    <h3>大课表</h3>

    <!-- 过滤栏 -->
    <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:12px 0">
      <el-input
        v-model="keyword"
        placeholder="搜索课程/班级/教师/教室"
        clearable
        style="width:220px"
        :prefix-icon="Search"
        @input="onKeywordInput"
        @keyup.enter="onKeywordEnter"
      />
      <el-select v-model="filters.courseId" placeholder="全部课程" clearable style="width:150px" @change="loadLessons">
        <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filters.classId" placeholder="全部班级" clearable style="width:150px" @change="loadLessons">
        <el-option v-for="c in classList" :key="c.id" :label="c.className" :value="c.id" />
      </el-select>
      <el-select v-model="filters.teacherId" placeholder="全部教师" clearable style="width:130px" @change="loadLessons">
        <el-option v-for="t in teacherList" :key="t.id" :label="t.realName || t.username" :value="t.id" />
      </el-select>
      <el-select v-model="filters.classroomId" placeholder="全部教室" clearable style="width:150px" @change="loadLessons">
        <el-option v-for="r in roomList" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:120px" @change="loadLessons">
        <el-option label="待上课" :value="1" />
        <el-option label="已完成" :value="2" />
        <el-option label="已取消" :value="3" />
        <el-option label="已调课" :value="4" />
      </el-select>
    </div>

    <!-- 导航 + 模式切换 -->
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
      <div style="display:flex;align-items:center;gap:8px">
        <el-button size="small" @click="prev" :icon="ArrowLeft" circle />
        <span style="font-size:16px;font-weight:600;min-width:160px;text-align:center">{{ rangeLabel }}</span>
        <el-button size="small" @click="next" :icon="ArrowRight" circle />
        <el-button size="small" @click="goToday">今天</el-button>
      </div>
      <el-radio-group v-model="viewMode" size="small" @change="loadLessons">
        <el-radio-button value="month">月</el-radio-button>
        <el-radio-button value="week">周</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 视图 -->
    <div v-loading="loading" style="min-height:300px">
      <MonthlyCalendar v-if="viewMode === 'month'" :lessons="lessons" :year="year" :month="month" />
      <WeeklyCalendar v-if="viewMode === 'week'" :lessons="lessons" :weekDays="weekDayLabels" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Search } from '@element-plus/icons-vue'
import { scheduleApi, courseApi, classApi, teacherApi, classroomApi } from '@/api/edu'
import { showError } from '@/utils/error'
import MonthlyCalendar from './components/MonthlyCalendar.vue'
import WeeklyCalendar from './components/WeeklyCalendar.vue'

// 模式
const viewMode = ref<'month' | 'week'>('week')
const currentDate = ref(new Date())
const loading = ref(false)

// 关键字搜索（防抖 300ms）
const keyword = ref('')
let searchTimer: any = null
function onKeywordInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadLessons(), 300)
}
// 回车立即搜索：先取消挂起的防抖，避免"回车一次、请求两发"
function onKeywordEnter() {
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null }
  loadLessons()
}
onBeforeUnmount(() => {
  // 页面销毁后防抖回调不应再触发请求
  if (searchTimer) { clearTimeout(searchTimer); searchTimer = null }
})

// 过滤
const filters = reactive({ courseId: null as number | null, classId: null as number | null, teacherId: null as number | null, classroomId: null as number | null, status: null as number | null })

// 选项列表
const courseList = ref<any[]>([])
const classList = ref<any[]>([])
const teacherList = ref<any[]>([])
const roomList = ref<any[]>([])

// 课次数据
const lessons = ref<any[]>([])

// 计算日期范围
const year = computed(() => currentDate.value.getFullYear())
const month = computed(() => currentDate.value.getMonth() + 1)

// 周范围标签
const rangeLabel = computed(() => {
  if (viewMode.value === 'month') return `${year.value}年${month.value}月`
  const monday = getMonday(currentDate.value)
  const sunday = new Date(monday.getTime() + 6 * 86400000)
  if (monday.getMonth() === sunday.getMonth())
    return `${monday.getMonth() + 1}月${monday.getDate()}日 - ${sunday.getDate()}日`
  return `${monday.getMonth() + 1}/${monday.getDate()} - ${sunday.getMonth() + 1}/${sunday.getDate()}`
})

// 周几标签
const weekDayLabels = computed(() => {
  const monday = getMonday(currentDate.value)
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday.getTime() + i * 86400000)
    return `${d.getMonth() + 1}/${d.getDate()} 周${'一二三四五六日'[i]}`
  })
})

function toDateStr(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getMonday(d: Date): Date {
  const day = d.getDay() || 7
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - day + 1)
}

function prev() {
  if (viewMode.value === 'month') currentDate.value = new Date(year.value, month.value - 2, 1)
  else currentDate.value = new Date(currentDate.value.getTime() - 7 * 86400000)
  loadLessons()
}

function next() {
  if (viewMode.value === 'month') currentDate.value = new Date(year.value, month.value, 1)
  else currentDate.value = new Date(currentDate.value.getTime() + 7 * 86400000)
  loadLessons()
}

function goToday() {
  currentDate.value = new Date()
  loadLessons()
}

async function loadOptions() {
  try {
    const [cRes, clRes, tRes, rRes] = await Promise.all([
      courseApi.list({ pageNum: 1, pageSize: 200 }),
      classApi.list({ pageNum: 1, pageSize: 200 }),
      teacherApi.list(),
      classroomApi.list({ pageNum: 1, pageSize: 200 }),
    ])
    courseList.value = cRes.data?.records || []
    classList.value = clRes.data?.records || []
    teacherList.value = (tRes.data || []) as any[]
    roomList.value = rRes.data?.records || []
  } catch {
    // 选项加载失败时降级为空列表，不阻塞课表主体加载
  }
}

async function loadLessons() {
  loading.value = true
  try {
    const { dateFrom, dateTo } = getDateRange()
    // 后端单页上限为 200，请求 500 会被静默截断，这里按上限请求并在超限时提示
    const params: any = { pageNum: 1, pageSize: 200, dateFrom, dateTo }
    if (keyword.value?.trim()) params.keyword = keyword.value.trim()
    if (filters.courseId) params.courseId = filters.courseId
    if (filters.classId) params.classId = filters.classId
    if (filters.teacherId) params.teacherId = filters.teacherId
    if (filters.classroomId) params.classroomId = filters.classroomId
    if (filters.status) params.status = filters.status
    const res = await scheduleApi.list(params)
    const records = res.data?.records || []
    lessons.value = records
    const total = res.data?.total || 0
    if (total > records.length) {
      ElMessage.warning(`当前范围共 ${total} 条课次，仅显示前 ${records.length} 条，请缩小日期范围或筛选条件`)
    }
  } catch (e) {
    showError(e, '加载课表失败')
  } finally {
    loading.value = false
  }
}

function getDateRange() {
  if (viewMode.value === 'month') {
    const first = new Date(year.value, month.value - 1, 1)
    const last = new Date(year.value, month.value, 0)
    return { dateFrom: toDateStr(first), dateTo: toDateStr(last) }
  }
  // week
  const mon = getMonday(currentDate.value)
  const sun = new Date(mon.getTime() + 6 * 86400000)
  return { dateFrom: toDateStr(mon), dateTo: toDateStr(sun) }
}

onMounted(async () => {
  await loadOptions()
  loadLessons()
})
</script>
