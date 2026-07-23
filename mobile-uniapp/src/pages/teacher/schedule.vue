<template>
  <view class="page-wrap">
    <text class="page-title">我的课表</text>
    <text class="page-sub">即将进行的课程</text>

    <view v-if="lessons.length===0" class="empty-state"><text>暂无课次安排</text></view>

    <view class="cell-group" v-if="lessons.length > 0">
      <view
        class="cell schedule-cell"
        v-for="lesson in lessons"
        :key="lesson.id"
      >
        <view class="cell-body">
          <view class="schedule-top">
            <text class="schedule-date">{{ lesson.lessonDate }}</text>
            <text class="tag" :class="tagClass(lesson.status)">{{ statusText(lesson.status) }}</text>
          </view>
          <view class="schedule-meta">
            <text class="schedule-time">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
          </view>
          <view class="schedule-info">
            <text class="schedule-detail">班级ID: {{ lesson.classId }}</text>
            <text class="schedule-detail">教室ID: {{ lesson.classroomId }}</text>
          </view>
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

function statusText(s) { return {1:'待上课',2:'已完成',3:'已取消',4:'已调课'}[s] || '未知' }
function tagClass(s) { return {1:'tag-primary',2:'tag-success',3:'tag-muted',4:'tag-warning'}[s] || 'tag-muted' }

async function fetchSchedule() {
  try {
    const res = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=50' })
    // 后端已按当前教师过滤，无需客户端再按 teacherId 过滤（类型不一致时会误清空列表）
    lessons.value = res.data?.records || []
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载失败'), icon: 'none' })
  }
}

onMounted(fetchSchedule)
</script>

<style scoped>
@import '@/styles/content.css';

.schedule-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
}
.schedule-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.schedule-date {
  font-size: 30rpx;
  font-weight: 600;
  color: #000;
}
.schedule-meta {
  margin-bottom: 8rpx;
}
.schedule-time {
  font-size: 28rpx;
  color: #333;
}
.schedule-info {
  display: flex;
  gap: 24rpx;
}
.schedule-detail {
  font-size: 24rpx;
  color: #888;
}
</style>
