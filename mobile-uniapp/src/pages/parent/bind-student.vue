<template>
  <view class="page-wrap">
    <text class="page-title">绑定学员</text>
    <text class="page-sub">用姓名与报名电话确认归属</text>

    <view class="tip-card">
      <text class="tip-text">
        请填写学员姓名与报名时登记的联系电话，两项同时匹配才能绑定；
        若匹配到多名学员，请联系机构教务人工绑定。
      </text>
    </view>

    <text class="section-header">学员信息</text>
    <view class="form-card">
      <view class="form-row">
        <text class="form-label">学员姓名</text>
        <input class="wx-input" v-model="studentName" placeholder="请输入学员真实姓名" :maxlength="50" />
      </view>

      <view class="form-row">
        <text class="form-label">联系电话</text>
        <input class="wx-input" v-model="phone" type="number" placeholder="报名时登记的 11 位手机号" :maxlength="11" />
      </view>

      <view class="form-row">
        <text class="form-label">与学员关系</text>
        <picker :range="relations" :value="relationIndex" @change="onRelationChange">
          <view class="picker-value">
            <text>{{ relations[relationIndex] }}</text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <button class="btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? '绑定中...' : '确认绑定' }}
      </button>
    </view>

    <template v-if="myStudents.length > 0">
      <text class="section-header">已绑定孩子</text>
      <view class="cell-group">
        <view v-for="item in myStudents" :key="item.id || item.studentId" class="cell">
          <view class="cell-body">
            <text class="cell-title">{{ item.name }}</text>
            <text class="cell-desc">{{ item.school || '—' }}</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, getMyStudents, refreshMyStudents } from '@/utils/request'
import { bindSuccessMessage, validateBindForm, RELATION_OPTIONS } from '@/utils/bind-student'

const relations = RELATION_OPTIONS
const studentName = ref('')
const phone = ref('')
const relationIndex = ref(0)
const submitting = ref(false)
const myStudents = ref(getMyStudents())
const fromRegister = ref(false)

function onRelationChange(e) {
  relationIndex.value = Number(e.detail.value)
}

async function submit() {
  if (submitting.value) return
  const err = validateBindForm({ studentName: studentName.value, phone: phone.value })
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await api({
      url: '/api/parent/bind-student',
      method: 'POST',
      data: {
        studentName: studentName.value.trim(),
        phone: phone.value.trim(),
        relation: relations[relationIndex.value],
      },
    })
    const list = await refreshMyStudents()
    myStudents.value = list
    uni.showToast({ title: bindSuccessMessage(list.length, fromRegister.value), icon: 'success' })
    studentName.value = ''
    phone.value = ''
    setTimeout(() => uni.switchTab({ url: '/pages/home/index' }), 800)
  } catch (e) {
    // 404/409/429 的业务文案已由 request.js 统一 toast，此处不重复弹窗
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 注册流程跳转过来时，成功提示改为「注册并绑定成功」
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  fromRegister.value = current?.options?.from === 'register'
})
</script>

<style scoped>
.page-wrap {
  min-height: 100vh;
  padding: 24rpx 28rpx 60rpx;
  background: #FAF8F5;
  box-sizing: border-box;
}
.page-title {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
  color: #2B2420;
}
.page-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #8C7E74;
}
.tip-card {
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  background: #E0F2FE;
  border-radius: 12rpx;
}
.tip-text {
  font-size: 24rpx;
  line-height: 1.6;
  color: #0E7490;
}
.section-header {
  display: block;
  margin: 32rpx 0 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #2B2420;
}
.form-card {
  padding: 8rpx 24rpx 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
}
.form-row {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  border-bottom: 1rpx solid #F0EBE5;
}
.form-row:last-of-type {
  border-bottom: none;
}
.form-label {
  width: 180rpx;
  font-size: 28rpx;
  color: #2B2420;
}
.wx-input {
  flex: 1;
  font-size: 28rpx;
  color: #2B2420;
}
.picker-value {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 28rpx;
  color: #2B2420;
}
.picker-arrow {
  width: 16rpx;
  height: 16rpx;
  border-right: 3rpx solid #B8ADA3;
  border-bottom: 3rpx solid #B8ADA3;
  transform: rotate(-45deg);
}
.btn-primary {
  margin-top: 28rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 30rpx;
  color: #FFFFFF;
  background: #0E7490;
  border-radius: 44rpx;
}
.btn-primary[disabled] {
  opacity: 0.6;
}
.cell-group {
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
}
.cell {
  padding: 24rpx;
  border-bottom: 1rpx solid #F0EBE5;
}
.cell:last-child {
  border-bottom: none;
}
.cell-body {
  display: flex;
  flex-direction: column;
}
.cell-title {
  font-size: 28rpx;
  color: #2B2420;
}
.cell-desc {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #8C7E74;
}
</style>
