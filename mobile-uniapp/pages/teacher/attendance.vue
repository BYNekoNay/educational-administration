<template>
  <view class="container">
    <text class="title">课堂考勤</text>

    <view class="lesson-list">
      <view v-for="lesson in lessons" :key="lesson.id" class="lesson-card" @click="openAttendance(lesson)">
        <text class="lesson-date">{{ lesson.lessonDate }}</text>
        <text class="lesson-time">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
        <text class="lesson-badge">班级{{ lesson.classId }} · 教室{{ lesson.classroomId }}</text>
      </view>
    </view>

    <view v-if="currentLesson" class="attendance-panel">
      <text class="sub-title">课次 {{ currentLesson.lessonDate }} 考勤</text>
      <view v-for="s in students" :key="s.studentId" class="student-row">
        <text class="student-id">学员{{ s.studentId }}</text>
        <picker :range="statusOpts" @change="e => setStatus(s.studentId, e.detail.value)">
          <text :class="statusClass(statusMap[s.studentId])">{{ statusMap[s.studentId] !== undefined ? statusOpts[statusMap[s.studentId]] : '点击选择' }}</text>
        </picker>
      </view>
      <button class="submit-btn" @click="submitAttendance">批量提交考勤</button>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
const token = uni.getStorageSync('token')
const baseUrl = 'http://localhost:8080'
function api(url, opt = {}) {
  return uni.request({ url: baseUrl + url, header: { Authorization: 'Bearer ' + token }, ...opt }).then(r => r.data)
}

const lessons = ref([])
const currentLesson = ref(null)
const students = ref([])
const statusMap = ref({})
const statusOpts = ['到课', '迟到', '请假', '缺勤']

function statusClass(i) {
  if (i === 0) return 'status-normal'
  if (i === 1) return 'status-late'
  if (i === 2) return 'status-leave'
  if (i === 3) return 'status-absent'
  return ''
}
function setStatus(sid, idx) { statusMap.value = { ...statusMap.value, [sid]: idx + 1 } }

async function openAttendance(lesson) {
  currentLesson.value = lesson
  try {
    const r = await api(`/api/teacher/lessons/${lesson.id}/students`)
    students.value = r.data || []
    const aR = await api(`/api/teacher/lessons/${lesson.id}/attendances`)
    const map = {}
    if (aR.data) aR.data.forEach(a => map[a.studentId] = a.status)
    statusMap.value = map
  } catch (e) { uni.showToast({ title: '加载失败', icon: 'none' }) }
}

async function submitAttendance() {
  const list = (students.value || []).map(s => ({
    studentId: s.studentId,
    status: statusMap.value[s.studentId] || 4,
    lessonId: currentLesson.value.id,
    deductLessons: statusMap.value[s.studentId] === 1 ? 1 : (statusMap.value[s.studentId] === 2 ? 0.5 : 0)
  }))
  try {
    await api(`/api/teacher/lessons/${currentLesson.value.id}/attendances`, { method: 'POST', data: list })
    uni.showToast({ title: '考勤提交成功' })
    statusMap.value = {}
    currentLesson.value = null
  } catch (e) { uni.showToast({ title: e.data?.message || '提交失败', icon: 'none' }) }
}

onMounted(async () => {
  try {
    const r = await api('/api/teacher/lessons?pageNum=1&pageSize=50')
    lessons.value = r.data?.records || []
  } catch (e) { uni.showToast({ title: '加载课表失败', icon: 'none' }) }
})
</script>

<style scoped>
.container { padding: 20rpx }
.title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 20rpx }
.lesson-list { display: flex; flex-direction: column; gap: 16rpx }
.lesson-card { background: #f5f7fa; padding: 24rpx; border-radius: 12rpx }
.lesson-date { font-size: 30rpx; font-weight: bold; display: block }
.lesson-time { font-size: 26rpx; color: #666; margin-top: 8rpx }
.lesson-badge { font-size: 24rpx; color: #999; margin-top: 4rpx }
.attendance-panel { margin-top: 30rpx }
.sub-title { font-size: 30rpx; font-weight: bold; margin-bottom: 20rpx; display: block }
.student-row { display: flex; justify-content: space-between; padding: 20rpx; background: #fff; border-bottom: 1rpx solid #eee }
.student-id { font-size: 28rpx }
.status-normal { color: #07c160 }
.status-late { color: #f0ad4e }
.status-leave { color: #999 }
.status-absent { color: #e74c3c }
.submit-btn { margin-top: 30rpx; background: #07c160; color: #fff }
</style>
