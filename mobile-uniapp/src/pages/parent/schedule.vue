<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />
    <text class="page-title">学员课表</text>
    <text class="page-sub">近期课程安排（按课程分组）</text>

    <view v-if="loading" class="empty-state"><text>加载中...</text></view>
    <view v-else-if="groups.length === 0" class="empty-state">
      <text>暂无课次安排</text>
      <text class="empty-tip">完成报名并加入班级后会自动显示</text>
    </view>

    <!-- 按课程分组 -->
    <view v-for="g in groups" :key="g.courseId" class="course-group">
      <view class="group-header" @click="toggleGroup(g.courseId)">
        <view class="group-header-main">
          <text class="group-course-name">{{ g.courseName || '已退课程' }}</text>
          <text class="group-count">共{{ g.lessons.length }}次</text>
          <text class="group-next" v-if="g.nextLesson">
            {{ formatDay(g.nextLesson) }} {{ formatWeekdayShort(g.nextLesson) }}
          </text>
        </view>
        <view class="group-arrow" :class="{ 'group-arrow-down': !collapsedGroups.has(g.courseId) }"></view>
      </view>

      <!-- 折叠时显示摘要条 -->
      <view v-if="collapsedGroups.has(g.courseId) && g.nextLesson" class="collapsed-hint">
        <text class="collapsed-hint-date">{{ formatDay(g.nextLesson) }} {{ formatWeekday(g.nextLesson) }}</text>
        <text class="collapsed-hint-class">{{ g.className }}</text>
      </view>

      <!-- 展开时显示课次列表 -->
      <view v-if="!collapsedGroups.has(g.courseId)" class="lesson-list">
        <view v-for="lesson in g.lessons" :key="lesson.id" class="lesson-card" :class="{ 'lesson-card-leave': lesson.leaveStatus === 2 }">
          <view class="lesson-card-top">
            <view class="lesson-card-date">
              <text class="date-day">{{ formatDay(lesson.lessonDate) }}</text>
              <text class="date-weekday">{{ formatWeekday(lesson.lessonDate) }}</text>
            </view>
            <text class="lesson-tag" :class="tagClass(lesson)">{{ statusText(lesson) }}</text>
          </view>

          <view class="lesson-divider"></view>

          <view class="lesson-row">
            <text class="lesson-icon">⏰</text>
            <text class="lesson-label">时间</text>
            <text class="lesson-value-strong">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
          </view>
          <view class="lesson-row">
            <text class="lesson-icon">📚</text>
            <text class="lesson-label">班级</text>
            <text class="lesson-value">{{ lesson.className || '—' }}</text>
          </view>
          <view class="lesson-row">
            <text class="lesson-icon">👨‍🏫</text>
            <text class="lesson-label">教师</text>
            <text class="lesson-value">{{ lesson.teacherName || '未指定' }}</text>
          </view>
          <view class="lesson-row">
            <text class="lesson-icon">🏫</text>
            <text class="lesson-label">教室</text>
            <text class="lesson-value">{{ lesson.classroomName || '—' }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const rawLessons = ref([])
const loading = ref(false)
const studentId = ref(getCurrentStudentId())
const collapsedGroups = reactive(new Set())

function toggleGroup(courseId) {
  if (collapsedGroups.has(courseId)) {
    collapsedGroups.delete(courseId)
  } else {
    collapsedGroups.add(courseId)
  }
}

function onStudentChange(id) {
  studentId.value = id
  collapsedGroups.clear()
  // 先清空旧学员课表：若新学员请求失败，不能把上一个学员的课表挂在新学员名下
  rawLessons.value = []
  fetchSchedule()
}

const groups = computed(() => {
  const map = new Map()
  for (const l of rawLessons.value) {
    const key = l.courseId || 0
    if (!map.has(key)) {
      map.set(key, { courseId: key, courseName: l.courseName, className: '', nextLesson: null, lessons: [] })
    }
    map.get(key).lessons.push(l)
  }
  for (const g of map.values()) {
    g.lessons.sort((a, b) => (a.lessonDate > b.lessonDate ? 1 : -1))
    // 摘一个班级名 + 未来第一节课日期作为摘要
    if (g.lessons.length > 0) {
      g.className = g.lessons[0].className || ''
      const future = g.lessons.find(l => !isPast(l.lessonDate))
      g.nextLesson = future ? future.lessonDate : g.lessons[g.lessons.length - 1].lessonDate
    }
  }
  return Array.from(map.values())
})

function formatDay(d) {
  if (!d) return ''
  const s = typeof d === 'string' ? d.substring(0, 10) : d
  return s
}
function formatWeekday(d) {
  if (!d) return ''
  const s = typeof d === 'string' ? d.substring(0, 10) : d
  const [y, m, day] = s.split('-').map(Number)
  const date = new Date(y, m - 1, day)
  const names = ['周日','周一','周二','周三','周四','周五','周六']
  return names[date.getDay()]
}
function formatWeekdayShort(d) {
  if (!d) return ''
  const s = typeof d === 'string' ? d.substring(0, 10) : d
  const [y, m, day] = s.split('-').map(Number)
  const date = new Date(y, m - 1, day)
  const names = ['周日','周一','周二','周三','周四','周五','周六']
  return names[date.getDay()]
}
function isToday(d) {
  if (!d) return false
  const s = typeof d === 'string' ? d.substring(0, 10) : d
  const today = new Date()
  const [y, m, day] = s.split('-').map(Number)
  return today.getFullYear() === y && today.getMonth() === m - 1 && today.getDate() === day
}
function isPast(d) {
  if (!d) return false
  const s = typeof d === 'string' ? d.substring(0, 10) : d
  const [y, m, day] = s.split('-').map(Number)
  const date = new Date(y, m - 1, day)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date < today
}

function statusText(lesson) {
  if (lesson.leaveStatus === 2) return '已请假'
  if (lesson.leaveStatus === 1) return '请假中'
  if (lesson.status === 3) return '已取消'
  if (lesson.status === 4) return '已调课'
  if (isPast(lesson.lessonDate)) return '已完成'
  if (isToday(lesson.lessonDate)) return '今日'
  return '待上课'
}
function tagClass(lesson) {
  if (lesson.leaveStatus === 2) return 'tag-leave'
  if (lesson.leaveStatus === 1) return 'tag-warning'
  if (lesson.status === 3) return 'tag-muted'
  if (lesson.status === 4) return 'tag-warning'
  if (isPast(lesson.lessonDate)) return 'tag-success'
  if (isToday(lesson.lessonDate)) return 'tag-primary'
  return 'tag-info'
}

async function fetchSchedule() {
  if (!studentId.value) {
    uni.showToast({ title: '未找到学员', icon: 'none' })
    rawLessons.value = []
    return
  }
  const sid = studentId.value
  loading.value = true
  try {
    const res = await api({ url: `/api/parent/students/${sid}/schedule` })
    if (sid !== studentId.value) return
    rawLessons.value = res.data || []
  } catch (e) {
    // request.js 已对业务/HTTP 错误弹过提示（_handled），此处仅兜底未处理异常
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载课表失败'), icon: 'none' })
  } finally {
    if (sid === studentId.value) loading.value = false
  }
}

onMounted(fetchSchedule)
</script>

<style scoped>
@import '@/styles/content.css';

.course-group {
  margin: 0 24rpx 32rpx;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 12rpx 16rpx;
  cursor: pointer;
}

.group-header-main {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.group-course-name {
  font-size: 32rpx;
  font-weight: 700;
  color: #2D2A26;
  flex-shrink: 0;
  max-width: 65%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-count {
  font-size: 22rpx;
  color: #0E7490;
  background: #ECFEFF;
  padding: 2rpx 12rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.group-next {
  font-size: 22rpx;
  color: #8C7E74;
  flex-shrink: 0;
}

.group-arrow {
  width: 16rpx;
  height: 16rpx;
  border-right: 2.5rpx solid #B5ADA5;
  border-bottom: 2.5rpx solid #B5ADA5;
  transform: rotate(-135deg);
  margin-left: 12rpx;
  transition: transform 0.25s;
  flex-shrink: 0;
}

.group-arrow-down {
  transform: rotate(45deg);
}

.collapsed-hint {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 12rpx 12rpx 24rpx;
}

.collapsed-hint-date {
  font-size: 24rpx;
  color: #0E7490;
  font-weight: 500;
}

.collapsed-hint-class {
  font-size: 24rpx;
  color: #8C7E74;
}

.lesson-list {
  overflow: hidden;
}

.lesson-card {
  background: #FFF;
  border-radius: 16rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(45, 42, 38, 0.04);
}

.lesson-card-leave {
  opacity: 0.55;
  border: 2rpx dashed #C4D7DB;
}

.lesson-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4rpx;
}

.lesson-card-date {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.date-day {
  font-size: 32rpx;
  font-weight: 700;
  color: #2D2A26;
}

.date-weekday {
  font-size: 22rpx;
  color: #8C7E74;
}

.lesson-tag {
  display: inline-block;
  font-size: 22rpx;
  font-weight: 500;
  padding: 4rpx 16rpx;
  border-radius: 16rpx;
  line-height: 1.5;
  flex-shrink: 0;
}

.tag-info { background: #ECFEFF; color: #0E7490; }
.tag-primary { background: #FFF7E6; color: #D97706; }
.tag-success { background: #ECFDF5; color: #10B981; }
.tag-warning { background: #FEF3C7; color: #B45309; }
.tag-muted { background: #F0F0F0; color: #888; }
.tag-leave { background: #F0FDF4; color: #059669; border: 1rpx solid #A7F3D0; }

.lesson-divider {
  height: 1rpx;
  background: #F0ECE8;
  margin: 16rpx 0;
}

.lesson-row {
  display: flex;
  align-items: center;
  padding: 8rpx 0;
  gap: 12rpx;
}

.lesson-icon {
  font-size: 26rpx;
  width: 36rpx;
  text-align: center;
  flex-shrink: 0;
}

.lesson-label {
  font-size: 24rpx;
  color: #8C7E74;
  width: 96rpx;
  flex-shrink: 0;
}

.lesson-value {
  font-size: 26rpx;
  color: #2D2A26;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lesson-value-strong {
  font-size: 28rpx;
  color: #2D2A26;
  font-weight: 600;
  flex: 1;
}

.empty-state {
  text-align: center;
  padding: 80rpx 0;
}

.empty-tip {
  display: block;
  font-size: 24rpx;
  color: #8C7E74;
  margin-top: 12rpx;
}
</style>
