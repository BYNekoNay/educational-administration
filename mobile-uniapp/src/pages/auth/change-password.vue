<template>
  <view class="cp-wrap">
    <view class="cp-card">
      <text class="cp-title">修改密码</text>
      <text class="cp-tip">为保障账号安全，请先设置新密码后再继续使用</text>

      <view class="cp-field">
        <text class="cp-label">原密码</text>
        <input v-model="form.oldPassword" password placeholder="请输入当前密码" class="cp-input" />
      </view>
      <view class="cp-field">
        <text class="cp-label">新密码</text>
        <input v-model="form.newPassword" password placeholder="至少 6 位，需含字母和数字" class="cp-input" />
      </view>
      <view class="cp-field">
        <text class="cp-label">确认新密码</text>
        <input v-model="form.confirm" password placeholder="再次输入新密码" class="cp-input" />
      </view>

      <button class="cp-btn" :loading="saving" @click="submit">确认修改</button>

      <text class="cp-note">
        修改成功后其他设备将退出登录，需要用新密码重新登录。
      </text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { api } from '@/utils/request'

const saving = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })

async function submit() {
  if (!form.oldPassword) {
    uni.showToast({ title: '请输入原密码', icon: 'none' })
    return
  }
  if (!form.newPassword || form.newPassword.length < 6 || !/[a-zA-Z]/.test(form.newPassword) || !/[0-9]/.test(form.newPassword)) {
    uni.showToast({ title: '新密码需至少 6 位且同时包含字母和数字', icon: 'none' })
    return
  }
  if (form.newPassword !== form.confirm) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await api({
      url: '/api/auth/password',
      method: 'PUT',
      data: { oldPassword: form.oldPassword, newPassword: form.newPassword },
    })
    uni.showToast({ title: '密码已更新，请重新登录', icon: 'none' })
    setTimeout(() => {
      uni.removeStorageSync('token')
      uni.reLaunch({ url: '/pages/login/login' })
    }, 800)
  } catch (e) {
    // request.js 已 toast 过则不再重复提示
    if (!e || !e._handled) {
      uni.showToast({ title: '修改失败，请重试', icon: 'none' })
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.cp-wrap {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 48rpx 32rpx;
  box-sizing: border-box;
}

.cp-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 40rpx 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
}

.cp-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #303133;
}

.cp-tip {
  margin-top: 12rpx;
  margin-bottom: 32rpx;
  font-size: 24rpx;
  color: #909399;
  line-height: 1.6;
}

.cp-field {
  margin-bottom: 28rpx;
}

.cp-label {
  display: block;
  font-size: 26rpx;
  color: #606266;
  margin-bottom: 12rpx;
}

.cp-input {
  height: 84rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
}

.cp-btn {
  margin-top: 16rpx;
  background: #409eff;
  color: #fff;
  font-size: 30rpx;
  border-radius: 12rpx;
  height: 88rpx;
  line-height: 88rpx;
}

.cp-note {
  margin-top: 24rpx;
  font-size: 22rpx;
  color: #c0c4cc;
  line-height: 1.6;
}
</style>
