<template>
  <view class="page">
    <view class="header">课时统计</view>
    <view class="cards">
      <view class="card">
        <text class="card-label">本月授课</text>
        <text class="card-value">{{ stats.mainLessons }}</text>
      </view>
      <view class="card">
        <text class="card-label">代课课时</text>
        <text class="card-value">{{ stats.subLessons }}</text>
      </view>
      <view class="card">
        <text class="card-label">到课率</text>
        <text class="card-value">{{ stats.attendanceRate }}%</text>
      </view>
    </view>
    <view class="list-title">近6个月授课趋势</view>
    <view class="list">
      <view v-for="(item, idx) in trends" :key="idx" class="list-item">
        <text>{{ item.month }}</text>
        <text class="trend-count">{{ item.count }} 课时</text>
      </view>
    </view>
    <view v-if="trends.length === 0" class="empty">暂无授课记录</view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from '@/vue-compat'
const stats = reactive({ mainLessons: 0, subLessons: 0, attendanceRate: 0 })
const trends = ref([])

onMounted(async () => {
  try {
    const res = await uni.request({ url: '/api/admin/dashboard', header: { Authorization: uni.getStorageSync('token') } })
    const d = res.data.data
    stats.mainLessons = d.cards.monthlyLessons || 0
    stats.subLessons = 0
    stats.attendanceRate = d.cards.attendanceRate || 0
    trends.value = d.charts.lessonTrend || []
  } catch (_) {}
})
</script>

<style scoped>
.page { padding: 20px; }
.header { font-size: 20px; font-weight: bold; margin-bottom: 20px; text-align: center; }
.cards { display: flex; justify-content: space-around; margin-bottom: 24px; }
.card { background: #fff; border-radius: 12px; padding: 20px; text-align: center; box-shadow: 0 2px 12px rgba(0,0,0,.08); flex: 1; margin: 0 8px; }
.card-label { font-size: 13px; color: #999; display: block; margin-bottom: 8px; }
.card-value { font-size: 28px; font-weight: bold; color: #409eff; }
.list-title { font-size: 15px; color: #666; margin-bottom: 12px; }
.list { background: #fff; border-radius: 12px; overflow: hidden; }
.list-item { display: flex; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid #f0f0f0; }
.trend-count { color: #67C23A; font-weight: bold; }
.empty { text-align: center; color: #999; padding: 40px; }
</style>
