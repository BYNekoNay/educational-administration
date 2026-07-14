<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />
    <text class="page-title">学员课表</text>
    <text class="page-sub">近期的课程安排</text>

    <view v-if="lessons.length === 0" class="empty-state"><text>暂无课次安排</text></view>

    <view class="cell-group">
      <view v-for="lesson in lessons" :key="lesson.id" class="cell lesson-cell">
        <view class="cell-body">
          <view class="lesson-header">
            <text class="lesson-date">{{ lesson.lessonDate }}</text>
            <text class="tag" :class="tagClass(lesson.status)">{{ statusText(lesson.status) }}</text>
          </view>
          <view class="lesson-details">
            <view class="lesson-detail-row">
              <text class="lesson-label">时间</text>
              <text class="lesson-value">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
            </view>
            <view class="lesson-detail-row">
              <text class="lesson-label">教师</text>
              <text class="lesson-value">ID: {{ lesson.teacherId }}</text>
            </view>
            <view class="lesson-detail-row">
              <text class="lesson-label">教室</text>
              <text class="lesson-value">{{ lesson.classroomId }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const lessons = ref([])
const studentId = ref(getCurrentStudentId())

function onStudentChange(id) {
  studentId.value = id
  fetchSchedule()
}

function statusText(s) { return {1:'待上课',2:'已完成',3:'已取消',4:'已调课'}[s] || '未知' }
function tagClass(s) { return {1:'tag-primary',2:'tag-success',3:'tag-muted',4:'tag-warning'}[s] || 'tag-muted' }

async function fetchSchedule() {
  if (!studentId.value) { uni.showToast({ title: '未找到学员', icon: 'none' }); return }
  try { const res = await api({ url: `/api/parent/students/${studentId.value}/schedule` }); lessons.value = res.data || [] }
  catch { uni.showToast({ title: '加载失败', icon: 'none' }) }
}

onMounted(fetchSchedule)
</script>

<style scoped>
@import '@/styles/content.css';

.lesson-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
}

.lesson-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.lesson-date {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}

.lesson-details {
  background: #F7F7F7;
  border-radius: 16rpx;
  padding: 16rpx 24rpx;
}

.lesson-detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10rpx 0;
}

.lesson-detail-row + .lesson-detail-row {
  border-top: 1rpx solid #F0F0F0;
}

.lesson-label {
  font-size: 24rpx;
  color: #888;
}

.lesson-value {
  font-size: 24rpx;
  color: #333;
}
</style>
