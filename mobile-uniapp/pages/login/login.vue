<template>
  <view class="login-page">
    <view class="login-header">
      <text class="title">教务管理系统</text>
      <text class="subtitle">艺术培训机构全流程管理平台</text>
    </view>
    <view class="login-form">
      <input class="input" v-model="username" placeholder="用户名" />
      <input class="input" v-model="password" type="password" placeholder="密码" />
      <button class="login-btn" @click="handleLogin" :loading="loading">登 录</button>
    </view>
    <view class="login-tip">
      <text>演示账号：admin / teacher1 / parent1</text>
      <text>密码：123456</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { api } from '@/utils/request'

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

    if (roleCode === 'PARENT') {
      uni.reLaunch({ url: '/pages/parent/index' })
    } else if (roleCode === 'TEACHER') {
      uni.reLaunch({ url: '/pages/teacher/index' })
    } else {
      uni.showToast({ title: '请使用家长或教师账号登录移动端', icon: 'none' })
    }
  } catch (e: any) {
    uni.showToast({ title: e.errMsg || '网络错误', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function loadParentStudents() {
  try {
    const res = await api({ url: '/api/parent/students' })
    uni.setStorageSync('students', res.data || [])
  } catch {
    uni.setStorageSync('students', [])
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40rpx;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-header {
  margin-bottom: 60rpx;
  text-align: center;
}
.title {
  font-size: 48rpx;
  font-weight: bold;
  color: #fff;
  display: block;
}
.subtitle {
  font-size: 28rpx;
  color: rgba(255,255,255,0.8);
  margin-top: 16rpx;
  display: block;
}
.login-form {
  width: 100%;
  background: #fff;
  border-radius: 20rpx;
  padding: 60rpx 40rpx;
}
.input {
  width: 100%;
  height: 88rpx;
  border: 1rpx solid #e0e0e0;
  border-radius: 12rpx;
  padding: 0 24rpx;
  margin-bottom: 24rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}
.login-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #667eea;
  color: #fff;
  border-radius: 12rpx;
  font-size: 32rpx;
  margin-top: 20rpx;
}
.login-tip {
  margin-top: 40rpx;
  text-align: center;
  color: rgba(255,255,255,0.7);
  font-size: 22rpx;
}
.login-tip text {
  display: block;
  margin-bottom: 8rpx;
}
</style>
