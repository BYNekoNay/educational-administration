<template>
  <view class="page">
    <!-- Top gradient header zone -->
    <view class="header-zone">
      <!-- Custom status bar spacer -->
      <view class="status-bar" :style="{ height: statusBarHeight + 'px' }" />

      <!-- Top navigation bar -->
      <view class="nav-bar">
        <text class="nav-title">登录</text>
      </view>

      <!-- Brand area — lives inside the gradient -->
      <view class="brand-area">
        <view class="brand-logo">
          <view class="logo-inner">
            <text class="logo-text">艺</text>
          </view>
        </view>
        <text class="brand-name">艺培通</text>
        <text class="brand-desc">艺术培训管理平台</text>
      </view>
    </view>

    <!-- Warm cream body zone -->
    <view class="body-zone">
      <!-- Login form card -->
      <view class="form-card">
        <view class="input-wrap">
          <view class="input-box">
            <text class="input-label">账号</text>
            <input
              class="input-field"
              v-model="username"
              placeholder="请输入用户名"
              placeholder-class="ph"
            />
          </view>
        </view>

        <view class="divider" />

        <view class="input-wrap">
          <view class="input-box">
            <text class="input-label">密码</text>
            <input
              class="input-field"
              v-model="password"
              type="password"
              placeholder="请输入密码"
              placeholder-class="ph"
            />
          </view>
        </view>

        <button
          class="btn-login"
          :class="{ loading: loading }"
          :disabled="loading"
          @click="handleLogin"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </view>

      <!-- Demo account hint -->
      <view class="hint-area">
        <text class="hint-label">演示账号：</text>
        <text class="hint-value">parent1 / teacher1</text>
        <text class="hint-label">密码：</text>
        <text class="hint-value">123456</text>
      </view>

      <!-- Bottom branding -->
      <view class="footer">
        <text class="footer-text">艺培通 EduAdmin</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { api } from '@/utils/request'

const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 0

const username = ref('')
const password = ref('')
const loading = ref(false)

async function handleLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = await api({
      url: '/api/auth/login',
      method: 'POST',
      data: { username: username.value, password: password.value }
    })
    const { token, roleCode } = res.data
    uni.setStorageSync('token', token)
    uni.setStorageSync('userInfo', res.data)

    // 家长登录后加载学员列表
    if (roleCode === 'PARENT') {
      await loadParentStudents()
    }

    if (roleCode === 'PARENT' || roleCode === 'TEACHER') {
      uni.switchTab({ url: '/pages/home/index' })
    } else {
      uni.showToast({ title: '请使用家长或教师账号登录移动端', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: (e && e.errMsg) || '网络错误', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function loadParentStudents() {
  try {
    const res = await api({ url: '/api/parent/students' })
    uni.setStorageSync('students', res.data || [])
  } catch (e) {
    console.warn('学员列表加载失败', e)
    uni.setStorageSync('students', [])
  }
}
</script>

<style scoped>
/* ── Page ── */
.page {
  min-height: 100vh;
  background: #FAF8F5;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", "PingFang SC",
    "Microsoft YaHei", sans-serif;
}

/* ── Header gradient zone ── */
.header-zone {
  background: linear-gradient(180deg, #0E7490 0%, #0E7490 40%, #FAF8F5 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ── Status bar ── */
.status-bar {
  width: 100%;
  background: transparent;
}

/* ── Nav bar ── */
.nav-bar {
  width: 100%;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.nav-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #FFFFFF;
}

/* ── Brand area ── */
.brand-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 0 80rpx;
}
.brand-logo {
  width: 140rpx;
  height: 140rpx;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #0E7490, #06B6D4);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(14, 116, 144, 0.30);
}
.logo-inner {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-text {
  font-size: 72rpx;
  font-weight: 700;
  color: #FFFFFF;
}
.brand-name {
  font-size: 44rpx;
  font-weight: 700;
  color: #2D2A26;
  letter-spacing: 6rpx;
}
.brand-desc {
  font-size: 26rpx;
  color: #8C7E74;
  margin-top: 14rpx;
  letter-spacing: 2rpx;
}

/* ── Body zone ── */
.body-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: -40rpx;
}

/* ── Form card ── */
.form-card {
  margin: 0 40rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 0 44rpx 44rpx;
  box-shadow: 0 8rpx 32rpx rgba(45, 42, 38, 0.10);
}

.input-wrap {
  padding: 0;
}
.input-box {
  display: flex;
  align-items: center;
  height: 116rpx;
}
.input-label {
  font-size: 30rpx;
  color: #2D2A26;
  font-weight: 600;
  width: 120rpx;
  flex-shrink: 0;
}
.input-field {
  flex: 1;
  height: 88rpx;
  font-size: 30rpx;
  color: #2D2A26;
  background: #F5F0ED;
  border-radius: 18rpx;
  padding: 0 28rpx;
}
.ph {
  color: #C4B8AE;
}

.divider {
  height: 1rpx;
  background: #EDE8E3;
  margin: 0;
}

/* ── Login button ── */
.btn-login {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: linear-gradient(135deg, #0E7490, #06B6D4);
  color: #FFFFFF;
  border: none;
  border-radius: 18rpx;
  font-size: 34rpx;
  font-weight: 600;
  margin-top: 48rpx;
  letter-spacing: 8rpx;
  box-shadow: 0 4rpx 16rpx rgba(14, 116, 144, 0.25);
}
.btn-login::after {
  border: none;
}
.btn-login:active {
  opacity: 0.88;
}
.btn-login.loading {
  opacity: 0.6;
}

/* ── Hint area ── */
.hint-area {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  padding: 48rpx 40rpx 0;
  gap: 4rpx;
}
.hint-label {
  font-size: 24rpx;
  color: #A89E94;
}
.hint-value {
  font-size: 24rpx;
  color: #6B5F54;
  margin-right: 16rpx;
}

/* ── Footer ── */
.footer {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 60rpx 0 48rpx;
}
.footer-text {
  font-size: 20rpx;
  color: #C4B8AE;
  letter-spacing: 2rpx;
}
</style>
