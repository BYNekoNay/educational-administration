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
          <text class="cell-title">{{ lesson.lessonDate }}  {{ lesson.startTime?.slice(0,5) }}-{{ lesson.endTime?.slice(0,5) }}</text>
          <text class="cell-desc">{{ lesson.courseName || '' }}{{ lesson.courseName && lesson.className ? ' · ' : '' }}{{ lesson.className || '' }}</text>
        </view>
        <view class="cell-footer">
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>
    <view v-if="lessons.length === 0" class="empty-state"><text>暂无课次</text></view>

    <!-- 考勤弹窗 -->
    <view v-if="currentLesson" class="overlay" @click="currentLesson = null">
      <view class="att-panel" @click.stop>
        <view class="att-panel-header">
          <text class="att-panel-title">{{ currentLesson.lessonDate }} {{ currentLesson.startTime?.slice(0,5) }}-{{ currentLesson.endTime?.slice(0,5) }}</text>
          <text class="att-panel-close" @click="currentLesson = null">✕</text>
        </view>
        <text class="att-panel-class">{{ currentLesson.courseName || '' }}{{ currentLesson.courseName && currentLesson.className ? ' · ' : '' }}{{ currentLesson.className || '' }}</text>

        <view v-if="students.length === 0" class="att-loading">学员加载中...</view>
        <scroll-view v-else scroll-y class="att-list" :style="{ maxHeight: '50vh' }">
          <view class="cell student-cell" v-for="s in students" :key="s.studentId">
            <view class="cell-body">
              <text class="cell-title">{{ s.studentName || '学员' + s.studentId }}</text>
            </view>
            <view class="cell-footer">
              <picker :range="statusOpts" @change="e => setStatus(s.studentId, e.detail.value)">
                <view class="picker-label" :class="statusClass(statusMap[s.studentId] - 1)">
                  {{ statusMap[s.studentId] !== undefined ? statusOpts[statusMap[s.studentId] - 1] : '点击选择' }}
                </view>
              </picker>
            </view>
          </view>
        </scroll-view>

        <view class="att-actions">
          <button class="btn-primary" @click="submitAttendance">批量提交考勤</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'

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
  // 先清空旧数据，避免上一课次的学员/考勤状态残留显示
  students.value = []
  statusMap.value = {}
  const lid = lesson.id
  try {
    const r = await api({ url: `/api/teacher/lessons/${lid}/students` })
    // 竞态守卫：请求期间用户可能已切换课次，过期响应不得覆盖当前面板
    if (!currentLesson.value || currentLesson.value.id !== lid) return
    students.value = r.data || []
    const aR = await api({ url: `/api/teacher/lessons/${lid}/attendances` })
    if (!currentLesson.value || currentLesson.value.id !== lid) return
    const map = {}
    if (aR.data) aR.data.forEach(a => map[a.studentId] = a.status)
    statusMap.value = map
  } catch (e) {
    if (!currentLesson.value || currentLesson.value.id !== lid) return
    if (!e || !e._handled) uni.showToast({ title: '加载失败', icon: 'none' })
  }
}

async function submitAttendance() {
  // 校验：必须为每个学员显式选择考勤状态，避免未操作学员被静默记为缺勤
  const unselected = (students.value || []).filter(s => !statusMap.value[s.studentId])
  if (unselected.length > 0) {
    uni.showToast({ title: `请为所有学员选择考勤状态（还有 ${unselected.length} 人未选）`, icon: 'none' })
    return
  }
  const lid = currentLesson.value.id
  const list = (students.value || []).map(s => ({
    studentId: s.studentId,
    status: statusMap.value[s.studentId],
    lessonId: lid,
    deductLessons: statusMap.value[s.studentId] === 1 ? 1 : (statusMap.value[s.studentId] === 2 ? 0.5 : 0)
  }))
  try {
    await api({ url: `/api/teacher/lessons/${lid}/attendances`, method: 'POST', data: list })
    // 提交期间教师可能已打开其他课次面板，仅当仍停留在本课次时才清空，避免误清新课次状态
    if (currentLesson.value?.id === lid) {
      uni.showToast({ title: '考勤提交成功' })
      statusMap.value = {}
      currentLesson.value = null
    }
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '提交失败'), icon: 'none' })
  }
}

onMounted(async () => {
  try {
    const r = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=200' })
    const all = r.data?.records || []
    const today = new Date().toISOString().slice(0, 10)
    // 仅显示今日待上课(status=1)课次，历史课次请到"考勤记录"
    lessons.value = all.filter(l => l.lessonDate === today && Number(l.status) === 1)
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载课表失败'), icon: 'none' })
  }
})
</script>

<style scoped>
@import '@/styles/content.css';

.lesson-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
  cursor: pointer;
}
.lesson-cell:active {
  background: #F0F8FA;
}

/* ─── 弹窗 ─── */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 999;
}
.att-panel {
  width: 100%;
  max-height: 85vh;
  background: #FFF;
  border-radius: 24rpx 24rpx 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.att-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 32rpx 4rpx;
}
.att-panel-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #2D2A26;
}
.att-panel-close {
  font-size: 36rpx;
  color: #C4B8AE;
  padding: 8rpx;
}
.att-panel-class {
  font-size: 24rpx;
  color: #0E7490;
  padding: 0 32rpx 20rpx;
}
.att-loading {
  padding: 80rpx 0;
  text-align: center;
  color: #8C7E74;
  font-size: 26rpx;
}
.att-list {
  padding: 0 32rpx;
}
.student-cell {
  min-height: 88rpx;
  margin-bottom: 2rpx;
  background: #FFF;
  border-bottom: 1rpx solid #F5F0ED;
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
  padding: 24rpx 32rpx 48rpx;
}
</style>
