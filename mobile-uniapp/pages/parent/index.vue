<template>
  <view class="container">
    <text class="greeting">欢迎，{{ userInfo?.realName }}</text>
    <view class="card-grid">
      <view class="card" @click="navTo('/pages/parent/enrollment')">
        <text class="card-icon">📝</text>
        <text class="card-title">在线报名</text>
      </view>
      <view class="card" @click="navTo('/pages/parent/schedule')">
        <text class="card-icon">📅</text>
        <text class="card-title">课表查看</text>
      </view>
      <view class="card" @click="navTo('/pages/parent/learning')">
        <text class="card-icon">📊</text>
        <text class="card-title">学情查看</text>
      </view>
      <view class="card" @click="navTo('/pages/parent/lesson-account')">
        <text class="card-icon">💰</text>
        <text class="card-title">课时管理</text>
      </view>
      <view class="card" @click="navTo('/pages/parent/notices')">
        <text class="card-icon">🔔</text>
        <text class="card-title">消息通知</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const userInfo = ref(null)

onMounted(() => {
  const stored = uni.getStorageSync('userInfo')
  if (stored) userInfo.value = stored
})

function navTo(url) {
  uni.navigateTo({ url })
}
</script>

<style scoped>
.container { padding: 30rpx; }
.greeting { font-size: 36rpx; font-weight: bold; margin-bottom: 40rpx; display: block; }
.card-grid { display: flex; flex-wrap: wrap; gap: 20rpx; }
.card {
  width: calc(50% - 10rpx);
  background: #fff;
  border-radius: 16rpx;
  padding: 40rpx 20rpx;
  text-align: center;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06);
}
.card-icon { font-size: 48rpx; display: block; margin-bottom: 12rpx; }
.card-title { font-size: 28rpx; color: #333; }
</style>