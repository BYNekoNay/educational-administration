<template>
  <view class="page-wrap">
    <text class="page-title">课时统计</text>
    <text class="page-sub">本月教学数据概览</text>

    <!-- Stats cards -->
    <view class="stats-row">
      <view class="stat-card">
        <text class="stat-value">{{ stats.mainLessons }}</text>
        <text class="stat-label">本月授课</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ stats.subLessons }}</text>
        <text class="stat-label">代课课时</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ stats.attendanceRate }}%</text>
        <text class="stat-label">到课率</text>
      </view>
    </view>

    <!-- Monthly trend -->
    <view class="section-header">近6个月授课趋势</view>
    <view v-if="trends.length > 0" class="cell-group">
      <view
        class="cell trend-cell"
        v-for="(item, idx) in trends"
        :key="idx"
      >
        <view class="cell-body">
          <text class="cell-title">{{ item.month }}</text>
        </view>
        <view class="cell-footer">
          <text class="trend-count">{{ item.count }} 课时</text>
        </view>
      </view>
    </view>
    <view v-if="trends.length === 0" class="empty-state"><text>暂无授课记录</text></view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api } from '@/utils/request'
const stats = reactive({ mainLessons: 0, subLessons: 0, attendanceRate: 0 })
const trends = ref([])

onMounted(async () => {
  try {
    const lessonRes = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=100' })
    const allLessons = lessonRes.data.records || []
    const now = new Date()
    const monthStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`
    const monthLessons = allLessons.filter(l => l.lessonDate && l.lessonDate.startsWith(monthStr) && l.status === 2)
    stats.mainLessons = monthLessons.length
    stats.subLessons = allLessons.filter(l => l.lessonDate && l.lessonDate.startsWith(monthStr) && l.status === 2 && l.sourceLessonId).length

    // 计算到课率：取本月已完成课次，查考勤数据
    if (monthLessons.length > 0) {
      let totalRecords = 0; let presentCount = 0
      for (const lesson of monthLessons.slice(0, 5)) {
        try {
          const attRes = await api({ url: `/api/teacher/lessons/${lesson.id}/attendances` })
          const attList = attRes.data || []
          totalRecords += attList.length
          presentCount += attList.filter(a => a.status === 1).length
        } catch (e) { console.warn('考勤查询失败', e) }
      }
      stats.attendanceRate = totalRecords > 0 ? Math.round(presentCount / totalRecords * 100) : 0
    }

    // 近6个月趋势（按月份聚合）
    const trendMap = {}
    allLessons.forEach(l => {
      if (l.lessonDate && l.status === 2) {
        const m = l.lessonDate.substring(0, 7)
        trendMap[m] = (trendMap[m] || 0) + 1
      }
    })
    trends.value = Object.entries(trendMap).map(([month, count]) => ({ month, count })).slice(-6)
  } catch (e) {
    console.warn('统计加载失败', e)
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
})
</script>

<style scoped>
@import '@/styles/content.css';

.stats-row {
  display: flex;
  margin: 0 32rpx 16rpx;
  background: #FFF;
  border-radius: 16rpx;
  overflow: hidden;
}
.stat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32rpx 16rpx;
  position: relative;
}
.stat-card + .stat-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 24rpx;
  bottom: 24rpx;
  width: 1rpx;
  background: #F0F0F0;
}
.stat-card .stat-value {
  font-size: 48rpx;
  font-weight: 700;
  color: #0E7490;
}
.stat-card .stat-label {
  font-size: 24rpx;
  color: #8C7E74;
  margin-top: 8rpx;
}

.trend-cell {
  min-height: 88rpx;
}
.trend-count {
  font-size: 28rpx;
  font-weight: 600;
  color: #0E7490;
}
</style>
