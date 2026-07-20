<template>
  <view class="page-wrap">
    <!-- Warm gradient header -->
    <view class="header-gradient">
      <view class="page-title-area">
        <text class="page-title">消息中心</text>
        <text class="page-subtitle">调课通知与机构公告</text>
      </view>
    </view>

    <!-- Quick nav -->
    <view class="quick-actions">
      <view class="action-card" @click="goNotices">
        <text class="action-emoji">🔔</text>
        <text class="action-label">查看全部消息</text>
        <text class="action-desc">调课提醒、机构公告、系统通知</text>
      </view>
    </view>

    <!-- Recent notices preview -->
    <view class="section-header">最近消息</view>
    <view class="cell-group">
      <view class="cell" v-if="recentNotices.length === 0">
        <view class="cell-body" style="text-align: center; padding: 40rpx 0;">
          <text class="cell-title" style="color: var(--text-secondary, #8C7E74)">暂无新消息</text>
        </view>
      </view>
      <view class="cell" v-for="item in recentNotices" :key="item.id" @click="goNotices">
        <view class="cell-body">
          <text class="cell-title">{{ item.title }}</text>
          <text class="cell-desc">{{ item.createTime || item.publishTime }}</text>
        </view>
        <view class="cell-footer"><view class="cell-arrow"></view></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/utils/request'

const recentNotices = ref([])

function goNotices() {
  uni.navigateTo({ url: '/pages/parent/notices' })
}

onMounted(async () => {
  try {
    const res = await api({ url: '/api/notifications?pageNum=1&pageSize=3' })
    recentNotices.value = res.data?.records || []
  } catch (e) {
    recentNotices.value = []
  }
})
</script>

<style scoped>
@import '@/styles/content.css';

.header-gradient {
  background: linear-gradient(135deg, #0E7490, #06B6D4);
  border-radius: 0 0 48rpx 48rpx;
  padding: 0 0 32rpx;
  min-height: 200rpx;
}

.page-title-area {
  padding: 48rpx 32rpx 24rpx;
}

.page-title {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.page-subtitle {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 8rpx;
}

.quick-actions {
  padding: 24rpx 24rpx 0;
}

.action-card {
  background: var(--bg-card, #FFFFFF);
  border-radius: 28rpx;
  padding: 40rpx 32rpx;
  text-align: center;
  box-shadow: var(--shadow-card, 0 4rpx 20rpx rgba(45,42,38,0.06));
  border: 1rpx solid var(--border, #E8E0DA);
}

.action-emoji {
  display: block;
  font-size: 64rpx;
  margin-bottom: 16rpx;
}

.action-label {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary, #2D2A26);
  margin-bottom: 8rpx;
}

.action-desc {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary, #8C7E74);
}
</style>
