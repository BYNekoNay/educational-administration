<template>
  <view class="page-wrap">
    <text class="page-title">课堂考勤</text>
    <text class="page-sub">选择课次进行考勤</text>

    <!-- Lesson list -->
    <view v-if="lessons.length > 0" class="cell-group">
      <view
        class="cell lesson-cell"
        v-for="lesson in lessons"
        :key="lesson.id"
        @click="openAttendance(lesson)"
      >
        <view class="cell-body">
          <text class="cell-title">{{ lesson.lessonDate }}</text>
          <text class="cell-desc">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
          <text class="cell-desc">班级{{ lesson.classId }} · 教室{{ lesson.classroomId }}</text>
        </view>
        <view class="cell-footer">
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>
    <view v-if="lessons.length === 0" class="empty-state"><text>暂无课次</text></view>

    <!-- Attendance panel -->
    <view v-if="currentLesson" class="att-panel">
      <view class="att-panel-header">
        <text class="att-panel-title">课次 {{ currentLesson.lessonDate }} 考勤</text>
      </view>

      <view class="cell-group">
        <view
          class="cell student-cell"
          v-for="s in students"
          :key="s.studentId"
        >
          <view class="cell-body">
            <text class="cell-title">{{ s.studentName || `学员${s.studentId}` }}</text>
          </view>
          <view class="cell-footer">
            <picker :range="statusOpts" @change="e => setStatus(s.studentId, e.detail.value)">
              <view class="picker-label" :class="statusClass(statusMap[s.studentId] - 1)">
                {{ statusMap[s.studentId] !== undefined ? statusOpts[statusMap[s.studentId] - 1] : '点击选择' }}
              </view>
            </picker>
          </view>
        </view>
      </view>

      <view class="att-actions">
        <button class="btn-primary" @click="submitAttendance">批量提交考勤</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/utils/request'

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
    const r = await api({ url: `/api/teacher/lessons/${lesson.id}/students` })
    students.value = r.data || []
    const aR = await api({ url: `/api/teacher/lessons/${lesson.id}/attendances` })
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
    await api({ url: `/api/teacher/lessons/${currentLesson.value.id}/attendances`, method: 'POST', data: list })
    uni.showToast({ title: '考勤提交成功' })
    statusMap.value = {}
    currentLesson.value = null
  } catch (e) { uni.showToast({ title: e.data?.message || '提交失败', icon: 'none' }) }
}

onMounted(async () => {
  try {
    const r = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=50' })
    lessons.value = r.data?.records || []
  } catch (e) { uni.showToast({ title: '加载课表失败', icon: 'none' }) }
})
</script>

<style scoped>
@import '@/styles/content.css';

.lesson-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
}

.att-panel {
  margin-top: 32rpx;
}
.att-panel-header {
  padding: 24rpx 32rpx 12rpx;
}
.att-panel-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #000;
}
.student-cell {
  min-height: 88rpx;
}
.picker-label {
  font-size: 28rpx;
  color: #BEBEBE;
  padding: 8rpx 24rpx;
  background: #F7F7F7;
  border-radius: 8rpx;
  min-width: 120rpx;
  text-align: center;
}
.picker-label.status-normal {
  color: #0E7490;
  background: #ECFEFF;
}
.picker-label.status-late {
  color: #FA9D3B;
  background: #FFF8E6;
}
.picker-label.status-leave {
  color: #888;
  background: #F2F2F2;
}
.picker-label.status-absent {
  color: #FA5151;
  background: #FDEDED;
}
.att-actions {
  padding: 32rpx;
}
</style>
