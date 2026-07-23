<template>
  <view class="page-wrap">
    <!-- Warm gradient header -->
    <view class="header-gradient">
      <view class="page-title-area">
        <text class="page-title">我的课表</text>
        <text class="page-subtitle">{{ isParent ? '查看孩子的课程安排' : '查看我的授课安排' }}</text>
      </view>
    </view>

    <!-- Student switcher (parent only) -->
    <StudentSwitcher v-if="isParent" @change="onStudentChange" />

    <!-- Schedule content placeholder - redirects to existing schedule pages -->
    <view class="quick-actions">
      <view class="action-card" @click="goSchedule">
        <text class="action-emoji">📅</text>
        <text class="action-label">查看完整课表</text>
        <text class="action-desc">{{ isParent ? '近期课程安排与调课信息' : '我的授课排班详情' }}</text>
      </view>
    </view>

    <!-- Today's summary -->
    <view class="section-header">今日概览</view>
    <view class="cell-group">
      <view class="cell" v-if="todayCount === 0">
        <view class="cell-body" style="text-align: center; padding: 40rpx 0;">
          <text class="cell-title" style="color: var(--text-secondary, #8C7E74)">今天没有课程安排</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const isParent = ref(true)
const todayCount = ref(0)

function onStudentChange() {}

function goSchedule() {
  const url = isParent.value ? '/pages/parent/schedule' : '/pages/teacher/schedule'
  uni.navigateTo({ url })
}

onMounted(() => {
  try {
    const stored = uni.getStorageSync('userInfo')
    const info = typeof stored === 'string' ? JSON.parse(stored) : stored
    isParent.value = info?.roleCode !== 'TEACHER'
  } catch (e) {}
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
