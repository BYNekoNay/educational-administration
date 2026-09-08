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
    <view v-if="isParent" class="switcher-section">
      <text class="section-header">我的孩子</text>
      <StudentSwitcher :key="switcherKey" @change="onStudentChange" />
    </view>

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

    <!-- 退出登录 -->
    <view class="cell-group" style="margin-top: 24rpx">
      <view class="cell" @click="handleLogout">
        <view class="cell-icon" style="background: #FFEBEE">
          <text>🚪</text>
        </view>
        <view class="cell-body">
          <text class="cell-title" style="color: #E53935">退出登录</text>
          <text class="cell-desc">返回登录页面</text>
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
import { onShow } from '@dcloudio/uni-app'
import StudentSwitcher from '@/components/StudentSwitcher.vue'
import { api, getMyStudents } from '@/utils/request'
import { formatBadge, shouldShowBadge } from '@/utils/unread-count'

const userInfo = ref(null)
const isParent = ref(true)
// 学员列表刷新后递增至 key，强制 StudentSwitcher 重新读取
const switcherKey = ref(0)

const parentMenus = [
  { path: '/pages/parent/enrollment',         emoji: '📋', label: '课程报名', desc: '浏览课程并报名', bg: '#E0F7FA' },
  { path: '/pages/parent/enrollment-history',  emoji: '📄', label: '报名记录', desc: '报名状态与审核进度', bg: '#FFF8E1' },
  { path: '/pages/parent/payments',            emoji: '💰', label: '缴费记录', desc: '查看缴费历史', bg: '#E8F5E9' },
  { path: '/pages/parent/learning',            emoji: '📚', label: '学情查看', desc: '考勤、作业与老师评语', bg: '#E0F2FE' },
  { path: '/pages/parent/lesson-account',      emoji: '📊', label: '课时账户', desc: '余额与消费明细', bg: '#FCE4EC' },
  { path: '/pages/parent/leave-request',       emoji: '✋', label: '请假申请', desc: '提交请假并查看记录', bg: '#F3E5F5' },
  { path: '/pages/parent/refund',               emoji: '💸', label: '退费申请', desc: '申请退费并查看记录', bg: '#FFEBEE' },
  { path: '/pages/parent/notices',             emoji: '🔔', label: '消息中心', desc: '调课与公告提醒', bg: '#FFF3E0' },
]

const teacherMenus = [
  { path: '/pages/teacher/attendance',    emoji: '✅', label: '课堂考勤', desc: '当日学员出勤', bg: '#E8F5E9' },
  { path: '/pages/teacher/attendance-records', emoji: '📋', label: '考勤记录', desc: '历史考勤查看', bg: '#F0FDF4' },
  { path: '/pages/teacher/learning',      emoji: '📝', label: '学情管理', desc: '作业与成长点评', bg: '#FFF3E0' },
  { path: '/pages/teacher/adjust-request',emoji: '🔄', label: '调课申请', desc: '申请调课与查看进度', bg: '#E0F7FA' },
  { path: '/pages/teacher/statistics',    emoji: '📊', label: '课时统计', desc: '本月授课数据', bg: '#F3E5F5' },
  { path: '/pages/teacher/leave-audit',   emoji: '✋', label: '请假审批', desc: '本班学员请假处理', bg: '#F3E5F5' },
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

function handleLogout() {
  uni.showModal({
    title: '退出确认',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        uni.removeStorageSync('token')
        uni.removeStorageSync('userInfo')
        uni.removeStorageSync('students')
        uni.reLaunch({ url: '/pages/login/login' })
      }
    }
  })
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

// 首页是 tabBar 页面，onMounted 只执行一次；
// 登录时学员列表加载失败会提示"可稍后在首页重试"，这里在每次显示时补偿加载
onShow(() => {
  refreshUnreadBadge()
  let storedUser = null
  try {
    storedUser = uni.getStorageSync('userInfo')
    if (typeof storedUser === 'string') storedUser = JSON.parse(storedUser)
  } catch (e) {}
  if ((storedUser?.roleCode || '') !== 'PARENT') return
  if (getMyStudents().length > 0) return
  api({ url: '/api/parent/students' }).then(res => {
    const list = res.data || []
    uni.setStorageSync('students', list)
    if (list.length) switcherKey.value++
  }).catch(() => {
    // 静默重试，不打扰用户
  })
})

// 刷新"消息"Tab 红点（全角色通用端点）。异常时保留上次角标值，不阻断页面。
async function refreshUnreadBadge() {
  try {
    const res = await api({ url: '/api/notifications/unread-count' })
    const n = Number(res.data) || 0
    if (shouldShowBadge(n)) {
      uni.setTabBarBadge({ index: 2, text: formatBadge(n) })
    } else {
      uni.removeTabBarBadge({ index: 2 })
    }
  } catch (e) {
    // 静默失败：保留上一次的角标值
    if (!e || !e._handled) console.warn('未读消息角标刷新失败', e)
  }
}
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
