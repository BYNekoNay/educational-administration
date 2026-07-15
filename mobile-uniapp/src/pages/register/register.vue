<template>
  <view class="page">
    <!-- Top gradient header zone -->
    <view class="header-zone">
      <view class="status-bar" :style="{ height: statusBarHeight + 'px' }" />

      <view class="nav-bar">
        <text class="nav-back" @click="goBack">‹</text>
        <text class="nav-title">家长注册</text>
      </view>

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
      <view class="form-card">
        <view class="input-wrap">
          <view class="input-box">
            <text class="input-label">账号</text>
            <input
              class="input-field"
              v-model="username"
              placeholder="用于登录的用户名"
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
              placeholder="6-32 位登录密码"
              placeholder-class="ph"
            />
          </view>
        </view>
        <view class="divider" />

        <view class="input-wrap">
          <view class="input-box">
            <text class="input-label">确认</text>
            <input
              class="input-field"
              v-model="confirmPassword"
              type="password"
              placeholder="再次输入密码"
              placeholder-class="ph"
            />
          </view>
        </view>
        <view class="divider" />

        <view class="input-wrap">
          <view class="input-box">
            <text class="input-label">姓名</text>
            <input
              class="input-field"
              v-model="realName"
              placeholder="家长真实姓名"
              placeholder-class="ph"
            />
          </view>
        </view>
        <view class="divider" />

        <view class="input-wrap">
          <view class="input-box">
            <text class="input-label">手机</text>
            <input
              class="input-field"
              v-model="phone"
              type="number"
              placeholder="11 位手机号"
              placeholder-class="ph"
            />
          </view>
        </view>

        <button
          class="btn-register"
          :class="{ loading: loading }"
          :disabled="loading"
          @click="handleRegister"
        >
          {{ loading ? '注册中...' : '注 册' }}
        </button>

        <view class="tip">注册即创建「学员家长」账号，可直接登录使用</view>
      </view>

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
const confirmPassword = ref('')
const realName = ref('')
const phone = ref('')
const loading = ref(false)

function goBack() {
  uni.navigateBack()
}

async function handleRegister() {
  if (!username.value || !password.value || !confirmPassword.value || !realName.value) {
    uni.showToast({ title: '请填写账号、密码与姓名', icon: 'none' })
    return
  }
  if (password.value !== confirmPassword.value) {
    uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
    return
  }
  if (password.value.length < 6) {
    uni.showToast({ title: '密码至少 6 位', icon: 'none' })
    return
  }
  if (phone.value && !/^1[3-9]\d{9}$/.test(phone.value)) {
    uni.showToast({ title: '手机号格式不正确', icon: 'none' })
    return
  }

  loading.value = true
  try {
    const res = await api({
      url: '/api/auth/register',
      method: 'POST',
      data: {
        username: username.value,
        password: password.value,
        realName: realName.value,
        phone: phone.value || undefined
      }
    })

    const { token, roleCode } = res.data
    uni.setStorageSync('token', token)
    uni.setStorageSync('userInfo', res.data)

    // 家长注册后加载学员列表（与管理端登录逻辑一致）
    if (roleCode === 'PARENT') {
      await loadParentStudents()
    }

    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/home/index' })
    }, 600)
  } catch (e) {
    uni.showToast({ title: (e && e.errMsg) || '注册失败', icon: 'none' })
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
.status-bar {
  width: 100%;
  background: transparent;
}
.nav-bar {
  width: 100%;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: transparent;
}
.nav-back {
  position: absolute;
  left: 24rpx;
  font-size: 52rpx;
  color: #FFFFFF;
  line-height: 1;
}
.nav-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #FFFFFF;
}
.brand-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0 80rpx;
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
.btn-register {
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
.btn-register::after {
  border: none;
}
.btn-register:active {
  opacity: 0.88;
}
.btn-register.loading {
  opacity: 0.6;
}
.tip {
  margin-top: 24rpx;
  text-align: center;
  font-size: 24rpx;
  color: #A89E94;
}
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
