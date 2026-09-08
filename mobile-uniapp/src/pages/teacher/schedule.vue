<template>
  <view class="page-wrap">
    <text class="page-title">我的课表</text>
    <text class="page-sub">全部授课安排（按日期分组）</text>

    <view v-if="loading" class="empty-state"><text>加载中...</text></view>
    <view v-else-if="groups.length === 0" class="empty-state">
      <text>暂无课次安排</text>
      <text class="empty-tip">排课后会自动显示在这里</text>
    </view>

    <view v-else>
      <view
        v-for="g in groups"
        :key="g.date"
        class="day-group"
        :class="{ 'day-group-today': g.date === todayStr }"
      >
        <view class="day-header">
          <text class="day-date">{{ g.date }}</text>
          <text class="day-weekday">{{ weekday(g.date) }}</text>
          <text v-if="g.date === todayStr" class="day-today-badge">今天</text>
        </view>

        <view class="cell-group">
          <view
            v-for="lesson in g.lessons"
            :key="lesson.id"
            class="cell schedule-card"
            :class="{ 'schedule-pending': lesson.status === 1 }"
            @click="onLessonClick(lesson)"
          >
            <view class="cell-body">
              <view class="schedule-top">
                <text class="schedule-course">{{ lesson.courseName || '课程' }}</text>
                <text class="tag" :class="tagClass(lesson.status)">{{ statusText(lesson.status) }}</text>
              </view>
              <view class="schedule-info">
                <text class="schedule-row">🕐 {{ lesson.startTime }} - {{ lesson.endTime }}</text>
              </view>
              <view class="schedule-info">
                <text class="schedule-row">📚 {{ lesson.className || '—' }}</text>
                <text class="schedule-row">🏫 {{ lesson.classroomName || '—' }}</text>
              </view>
            </view>
            <view v-if="lesson.status === 1" class="cell-footer">
              <view class="cell-arrow"></view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'
import { groupLessonsByDate, isToday, todayPlusDays } from '@/utils/schedule-summary'

const loading = ref(false)
const lessons = ref([])
const todayStr = todayPlusDays(0)

const groups = computed(() => {
  // 最新日期在前
  return groupLessonsByDate(lessons.value).slice().reverse()
})

function weekday(d) {
  if (!d) return ''
  const [y, m, day] = String(d).substring(0, 10).split('-').map(Number)
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[new Date(y, m - 1, day).getDay()]
}
function statusText(s) { return { 1: '待上课', 2: '已完成', 3: '已取消', 4: '已调课' }[s] || '未知' }
function tagClass(s) { return { 1: 'tag-primary', 2: 'tag-success', 3: 'tag-muted', 4: 'tag-warning' }[s] || 'tag-muted' }

async function fetchSchedule() {
  loading.value = true
  try {
    const res = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=200' })
    // 后端已按当前教师过滤，无需客户端再按 teacherId 过滤（类型不一致时会误清空列表）
    lessons.value = (res.data && res.data.records) || []
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载失败'), icon: 'none' })
  } finally {
    loading.value = false
  }
}

// 仅"待上课"课次可跳转考勤
function onLessonClick(lesson) {
  if (lesson.status !== 1) return
  uni.navigateTo({ url: '/pages/teacher/attendance' })
}

onMounted(fetchSchedule)
</script>

<style scoped>
@import '@/styles/content.css';

.schedule-card {
  min-height: auto;
  padding: 24rpx 32rpx;
}
.schedule-pending {
  cursor: pointer;
}
.schedule-pending:active {
  background: #F0F8FA;
}
.schedule-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.schedule-course {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
}
.schedule-info {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx 32rpx;
  margin-top: 6rpx;
}
.schedule-row {
  font-size: 24rpx;
  color: #8C7E74;
}

.day-group-today {
  background: linear-gradient(180deg, rgba(14,116,144,0.06), rgba(14,116,144,0));
  border-radius: 24rpx;
  margin: 0 12rpx;
  padding-top: 8rpx;
}
.day-header {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  padding: 16rpx 28rpx 12rpx;
}
.day-date {
  font-size: 28rpx;
  font-weight: 600;
  color: #0E7490;
}
.day-weekday {
  font-size: 24rpx;
  color: #8C7E74;
}
.day-today-badge {
  font-size: 20rpx;
  color: #D97706;
  background: #FFF7E6;
  padding: 2rpx 12rpx;
  border-radius: 12rpx;
}

.tag-primary { background: #FFF7E6; color: #D97706; }
.tag-success { background: #ECFDF5; color: #10B981; }
.tag-warning { background: #FEF3C7; color: #B45309; }
.tag-muted { background: #F0F0F0; color: #888; }
.tag { display: inline-block; font-size: 22rpx; font-weight: 500; padding: 4rpx 16rpx; border-radius: 16rpx; flex-shrink: 0; }

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
