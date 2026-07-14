<template>
  <view class="page-wrap">
    <!-- Warm gradient header -->
    <view class="header-gradient">
      <view class="greeting-area">
        <view class="avatar">
          <text class="avatar-text">{{ avatarLetter }}</text>
        </view>
        <view class="greeting-info">
          <text class="greeting-text">{{ greetingText }}</text>
          <text class="greeting-name">{{ displayName }}</text>
        </view>
      </view>
    </view>

    <!-- Student switcher (parent only) -->
    <StudentSwitcher v-if="isParent" @change="onStudentChange" />

    <!-- Menu list -->
    <text class="section-header">{{ isParent ? '常用功能' : '教学工具' }}</text>
    <view class="cell-group">
      <view
        class="cell"
        v-for="item in menus"
        :key="item.path"
        @click="navTo(item.path)"
      >
        <view class="cell-icon" :style="{ background: item.bg }">
          <text>{{ item.emoji }}</text>
        </view>
        <view class="cell-body">
          <text class="cell-title">{{ item.label }}</text>
          <text class="cell-desc">{{ item.desc }}</text>
        </view>
        <view class="cell-footer">
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const userInfo = ref(null)
const isParent = ref(true)

const parentMenus = [
  { path: '/pages/parent/enrollment',         emoji: '📋', label: '课程报名', desc: '浏览课程并报名', bg: '#E0F7FA' },
  { path: '/pages/parent/enrollment-history',  emoji: '📄', label: '报名记录', desc: '报名状态与审核进度', bg: '#FFF8E1' },
  { path: '/pages/parent/payments',            emoji: '💰', label: '缴费记录', desc: '查看缴费历史', bg: '#E8F5E9' },
  { path: '/pages/parent/lesson-account',      emoji: '📊', label: '课时账户', desc: '余额与消费明细', bg: '#FCE4EC' },
  { path: '/pages/parent/leave-request',       emoji: '✋', label: '请假申请', desc: '提交请假并查看记录', bg: '#F3E5F5' },
  { path: '/pages/parent/notices',             emoji: '🔔', label: '消息中心', desc: '调课与公告提醒', bg: '#FFF3E0' },
]

const teacherMenus = [
  { path: '/pages/teacher/attendance',    emoji: '✅', label: '课堂考勤', desc: '记录学员出勤', bg: '#E8F5E9' },
  { path: '/pages/teacher/learning',      emoji: '📝', label: '学情管理', desc: '作业与成长点评', bg: '#FFF3E0' },
  { path: '/pages/teacher/adjust-request',emoji: '🔄', label: '调课申请', desc: '申请调课与查看进度', bg: '#E0F7FA' },
  { path: '/pages/teacher/statistics',    emoji: '📊', label: '课时统计', desc: '本月授课数据', bg: '#F3E5F5' },
]

const menus = computed(() => isParent.value ? parentMenus : teacherMenus)

const avatarLetter = computed(() => {
  if (!userInfo.value) return isParent.value ? '家' : '师'
  return (userInfo.value.realName || (isParent.value ? '家' : '师'))[0]
})

const displayName = computed(() => {
  if (!userInfo.value) return isParent.value ? '家长' : '老师'
  return userInfo.value.realName || (isParent.value ? '家长' : '老师')
})

const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

function onStudentChange() {}

function navTo(url) {
  uni.navigateTo({ url })
}

onMounted(() => {
  try {
    const stored = uni.getStorageSync('userInfo')
    if (stored) {
      userInfo.value = typeof stored === 'string' ? JSON.parse(stored) : stored
    }
  } catch (e) {}
  const roleCode = userInfo.value?.roleCode || ''
  isParent.value = roleCode !== 'TEACHER'
})
</script>

<style scoped>
@import '@/styles/content.css';

.header-gradient {
  background: linear-gradient(135deg, #0E7490, #06B6D4);
  border-radius: 0 0 48rpx 48rpx;
  padding: 0 0 32rpx;
  min-height: 280rpx;
}

.greeting-area {
  display: flex;
  align-items: center;
  padding: 48rpx 32rpx 24rpx;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 48rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
  flex-shrink: 0;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.10);
}

.avatar-text {
  font-size: 40rpx;
  font-weight: 700;
  color: #0E7490;
}

.greeting-info {
  flex: 1;
}

.greeting-text {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.78);
}

.greeting-name {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #FFFFFF;
  margin-top: 4rpx;
}
</style>
