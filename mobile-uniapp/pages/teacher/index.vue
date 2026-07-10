<template>
  <view class="container">
    <text class="greeting">欢迎，{{ userInfo?.realName }} 老师</text>
    <view class="card-grid">
      <view class="card" @click="navTo('/pages/teacher/schedule')">
        <text class="card-icon">📅</text>
        <text class="card-title">课程排班</text>
      </view>
      <view class="card" @click="navTo('/pages/teacher/attendance')">
        <text class="card-icon">✅</text>
        <text class="card-title">课堂考勤</text>
      </view>
      <view class="card" @click="navTo('/pages/teacher/learning')">
        <text class="card-icon">📝</text>
        <text class="card-title">学情管理</text>
      </view>
      <view class="card" @click="navTo('/pages/teacher/statistics')">
        <text class="card-icon">📊</text>
        <text class="card-title">课时统计</text>
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