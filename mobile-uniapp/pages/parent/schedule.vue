<template>
  <view class="container">
    <text class="title">学员课表</text>
    <view v-if="lessons.length===0" class="empty"><text>暂无课次安排</text></view>
    <view v-for="lesson in lessons" :key="lesson.id" class="lesson-card">
      <view class="lesson-date">{{ lesson.lessonDate }} {{ lesson.startTime }}-{{ lesson.endTime }}</view>
      <view class="lesson-info">教师ID: {{ lesson.teacherId }} | 教室: {{ lesson.classroomId }}</view>
      <view class="lesson-status">
        <text :class="statusClass(lesson.status)">{{ statusText(lesson.status) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, getDefaultStudentId } from '@/utils/request'

const lessons = ref([])
const studentId = ref(getDefaultStudentId())

function statusText(s: number) { return {1:'待上课',2:'已完成',3:'已取消',4:'已调课'}[s] || '未知' }
function statusClass(s: number) { return {1:'s-upcoming',2:'s-done',3:'s-cancel',4:'s-moved'}[s] || '' }

async function fetchSchedule() {
  if (!studentId.value) { uni.showToast({ title: '未找到学员', icon: 'none' }); return }
  try {
    const res = await api({ url: `/api/parent/students/${studentId.value}/schedule` })
    lessons.value = res.data || []
  } catch { uni.showToast({ title: '加载失败', icon: 'none' }) }
}

onMounted(fetchSchedule)
</script>

<style scoped>
.container { padding: 20rpx; }
.title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 24rpx; }
.empty { text-align: center; padding: 80rpx; color: #999; }
.lesson-card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.06); }
.lesson-date { font-size: 28rpx; font-weight: bold; color: #333; }
.lesson-info { font-size: 24rpx; color: #999; margin-top: 8rpx; }
.lesson-status { margin-top: 8rpx; }
.s-upcoming { color: #e67e22; font-size: 24rpx; }
.s-done { color: #27ae60; font-size: 24rpx; }
.s-cancel { color: #999; font-size: 24rpx; text-decoration: line-through; }
.s-moved { color: #e74c3c; font-size: 24rpx; }
</style>
