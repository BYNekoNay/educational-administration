<template>
  <view class="container">
    <text class="title">课时余额</text>
    <view v-if="accounts.length" class="account-list">
      <view v-for="a in accounts" :key="a.id" class="account-card">
        <text class="account-course">课程{{ a.courseId }}</text>
        <text class="account-remaining">剩余: {{ a.remainingLessons }} / 总: {{ a.totalLessons }}</text>
        <text class="account-expire">有效期: {{ a.expireDate }}</text>
      </view>
    </view>
    <text v-if="!accounts.length" class="empty">暂无课时账户</text>

    <text class="sub-title" style="margin-top:40rpx">课时流水</text>
    <view v-for="f in flows" :key="f.id" class="flow-card">
      <text :class="f.changeType === 1 ? 'flow-in' : 'flow-out'">
        {{ f.changeType === 1 ? '+' : '' }}{{ f.changeAmount }}
      </text>
      <text class="flow-balance">余额: {{ f.afterBalance }}</text>
      <text class="flow-remark">{{ f.remark || '' }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, getDefaultStudentId } from '@/utils/request'

const accounts = ref<any[]>([])
const flows = ref<any[]>([])
const studentId = ref(getDefaultStudentId())

onMounted(async () => {
  if (!studentId.value) return
  try {
    const aR = await api({ url: `/api/parent/students/${studentId.value}/lesson-account` })
    accounts.value = aR.data || []
  } catch {}
})
</script>

<style scoped>
.container { padding: 20rpx }
.title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 20rpx }
.sub-title { font-size: 30rpx; font-weight: bold; margin-bottom: 16rpx; display: block }
.account-list { display: flex; flex-direction: column; gap: 16rpx }
.account-card { background: #e8f5e9; padding: 24rpx; border-radius: 12rpx }
.account-course { font-size: 30rpx; font-weight: bold; display: block }
.account-remaining { font-size: 28rpx; color: #07c160; margin-top: 8rpx }
.account-expire { font-size: 24rpx; color: #999; margin-top: 4rpx }
.flow-card { background: #f5f7fa; padding: 20rpx; border-radius: 8rpx; margin-bottom: 12rpx }
.flow-in { font-size: 30rpx; font-weight: bold; color: #07c160 }
.flow-out { font-size: 30rpx; font-weight: bold; color: #e74c3c }
.flow-balance { font-size: 26rpx; color: #666; margin-left: 20rpx }
.flow-remark { font-size: 24rpx; color: #999; display: block; margin-top: 4rpx }
.empty { font-size: 28rpx; color: #999; text-align: center; margin-top: 80rpx }
</style>
