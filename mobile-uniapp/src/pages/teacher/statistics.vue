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
      <view class="stat-card">
        <text class="stat-value">{{ stats.completionRate }}%</text>
        <text class="stat-label">完成率</text>
      </view>
    </view>

    <!-- Monthly trend -->
    <view class="section-header">近6个月授课趋势</view>
    <view v-if="trends.length > 0" class="cell-group">
      <view
        class="cell trend-cell"
        v-for="(item, idx) in trends"
        :key="idx"
        @click="showMonthDetail(item)"
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

    <!-- 月份详情弹窗 -->
    <view v-if="detailVisible" class="overlay" @click="detailVisible = false">
      <view class="detail-panel" @click.stop>
        <view class="detail-header">
          <text class="detail-title">{{ detailMonth }} 授课明细</text>
          <text class="detail-close" @click="detailVisible = false">✕</text>
        </view>
        <view v-if="detailLoading" class="detail-loading"><text>加载中...</text></view>
        <view v-else-if="detailLessons.length === 0" class="empty-state"><text>该月无授课记录</text></view>
        <scroll-view v-else scroll-y class="detail-list" :style="{ maxHeight: detailMaxHeight }">
          <view class="detail-item" v-for="l in detailLessons" :key="l.id">
            <view class="detail-item-top">
              <text class="detail-date">{{ fmtLessonDate(l.lessonDate) }}</text>
              <text class="detail-time">{{ l.startTime?.slice(0,5) }}-{{ l.endTime?.slice(0,5) }}</text>
              <text class="detail-status" :class="statusClass(l.status)">{{ statusLabel(l.status) }}</text>
            </view>
            <text class="detail-class">{{ l.courseName || '' }}{{ l.courseName && l.className ? ' · ' : '' }}{{ l.className || '' }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api } from '@/utils/request'
const stats = reactive({ mainLessons: 0, subLessons: 0, attendanceRate: 0, completionRate: 0 })
const trends = ref([])

// 弹窗状态
const detailVisible = ref(false)
const detailMonth = ref('')
const detailLessons = ref([])
const detailLoading = ref(false)
const detailMaxHeight = '60vh'

onMounted(async () => {
  try {
    const overviewRes = await api({ url: '/api/teacher/statistics' })
    const overview = overviewRes.data || {}
    stats.mainLessons = overview.thisMonth?.lessonCount || 0
    stats.subLessons = overview.substituteCount || 0
    stats.attendanceRate = Math.round((overview.attendanceRate || 0) * 100)
    stats.completionRate = Math.round((overview.completionRate || 0) * 100)
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '统计数据加载失败', icon: 'none' })
  }
  try {
    const monthlyRes = await api({ url: '/api/teacher/statistics/monthly' })
    trends.value = (monthlyRes.data || []).slice(-6).map(item => ({
      month: item.month,
      count: item.lessonCount || 0
    }))
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '趋势数据加载失败', icon: 'none' })
  }
})

function fmtLessonDate(d) {
  if (!d) return '-'
  const parts = String(d).split('-')
  return parts.length === 3 ? `${parseInt(parts[1])}/${parseInt(parts[2])}` : String(d)
}

function statusLabel(s) {
  return { 1: '待上课', 2: '已完成', 3: '已取消', 4: '已调课' }[s] || ''
}
function statusClass(s) {
  return { 1: 's-pending', 2: 's-done', 3: 's-cancel', 4: 's-adjust' }[s] || ''
}

async function showMonthDetail(item) {
  detailMonth.value = item.month  // e.g. "2026-07"
  detailVisible.value = true
  detailLoading.value = true
  detailLessons.value = []
  try {
    const [y, m] = item.month.split('-')
    const dateFrom = `${y}-${m}-01`
    const lastDay = new Date(parseInt(y), parseInt(m), 0).getDate()
    const dateTo = `${y}-${m}-${String(lastDay).padStart(2, '0')}`
    const res = await api({ url: `/api/teacher/lessons?pageNum=1&pageSize=100&dateFrom=${dateFrom}&dateTo=${dateTo}` })
    detailLessons.value = (res.data?.records || []).filter(l => Number(l.status) !== 3)
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    detailLoading.value = false
  }
}
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
  cursor: pointer;
}
.trend-cell:active {
  background: #F0F8FA;
}
.trend-count {
  font-size: 28rpx;
  font-weight: 600;
  color: #0E7490;
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
.detail-panel {
  width: 100%;
  max-height: 80vh;
  background: #FFF;
  border-radius: 24rpx 24rpx 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 32rpx 20rpx;
  border-bottom: 1rpx solid #F0F0F0;
}
.detail-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #2D2A26;
}
.detail-close {
  font-size: 36rpx;
  color: #C4B8AE;
  padding: 8rpx;
}
.detail-loading, .empty-state {
  padding: 80rpx 0;
  text-align: center;
  color: #8C7E74;
  font-size: 26rpx;
}
.detail-list {
  padding: 0 32rpx 48rpx;
}
.detail-item {
  padding: 24rpx 0;
  border-bottom: 1rpx solid #F5F0ED;
}
.detail-item:last-child {
  border-bottom: none;
}
.detail-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6rpx;
}
.detail-date {
  font-size: 26rpx;
  font-weight: 600;
  color: #2D2A26;
}
.detail-time {
  font-size: 24rpx;
  color: #8C7E74;
}
.detail-class {
  font-size: 24rpx;
  color: #0E7490;
}

.detail-status {
  font-size: 22rpx;
  padding: 2rpx 12rpx;
  border-radius: 10rpx;
  font-weight: 500;
}
.s-pending { background: #FEF3C7; color: #92400E; }
.s-done { background: #D1FAE5; color: #065F46; }
.s-cancel { background: #F3F4F6; color: #6B7280; }
.s-adjust { background: #E0F2FE; color: #0369A1; }
</style>
