<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />

    <!-- Summary bar: total amount + total lessons -->
    <view class="summary-bar">
      <view class="summary-item">
        <text class="summary-value">¥{{ totalAmount }}</text>
        <text class="summary-label">累计缴费</text>
      </view>
      <view class="summary-divider"></view>
      <view class="summary-item">
        <text class="summary-value">{{ totalLessons }}</text>
        <text class="summary-label">购买课时</text>
      </view>
    </view>

    <!-- Payment cards -->
    <text class="section-header">缴费记录</text>

    <view v-if="payments.length === 0" class="empty-state">
      <text class="empty-icon">📭</text>
      <text class="empty-text">暂无缴费记录</text>
    </view>

    <view v-for="p in payments" :key="p.id" class="payment-card">
      <view class="card-top">
        <text class="course-name">{{ p.courseName || '课程 #' + p.courseId }}</text>
        <text class="pay-amount">¥{{ formatAmount(p.amount) }}</text>
      </view>
      <view class="card-detail">
        <view class="detail-row">
          <text class="detail-label">学生</text>
          <text class="detail-value">{{ p.studentName }}</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">课时</text>
          <text class="detail-value">{{ p.lessonCount }} 课时</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">支付方式</text>
          <text class="tag" :class="payTypeClass(p.payType)">{{ payTypeLabel(p.payType) }}</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">缴费时间</text>
          <text class="detail-value">{{ formatTime(p.payTime) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const payments = ref([])
const studentId = ref(getCurrentStudentId())

const totalAmount = computed(() => {
  return payments.value
    .reduce((sum, p) => sum + (Number(p.amount) || 0), 0)
    .toFixed(2)
})

const totalLessons = computed(() => {
  return payments.value.reduce((sum, p) => sum + (Number(p.lessonCount) || 0), 0)
})

function payTypeLabel(type) {
  const map = { 1: '现金', 2: '模拟支付', 3: '其他' }
  return map[type] || '未知'
}

function payTypeClass(type) {
  const map = { 1: 'tag-success', 2: 'tag-primary', 3: 'tag-muted' }
  return map[type] || 'tag-muted'
}

function formatAmount(amount) {
  return (Number(amount) || 0).toFixed(2)
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function onStudentChange(id) {
  studentId.value = id
  loadData()
}

async function loadData() {
  if (!studentId.value) return
  try {
    const res = await api({ url: `/api/parent/payments?studentId=${studentId.value}` })
    payments.value = res.data || []
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
}

onMounted(loadData)
</script>

<style scoped>
@import '@/styles/content.css';

/* ─── Summary Bar ─── */
.summary-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 24rpx 24rpx 0;
  padding: 32rpx 0;
  background: linear-gradient(135deg, #0E7490, #06B6D4);
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(14, 116, 144, 0.2);
}

.summary-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.summary-value {
  font-size: 48rpx;
  font-weight: 700;
  color: #FFFFFF;
  line-height: 1.2;
}

.summary-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 6rpx;
}

.summary-divider {
  width: 2rpx;
  height: 56rpx;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 2rpx;
}

/* ─── Empty State ─── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 0;
}

.empty-icon {
  font-size: 64rpx;
  margin-bottom: 16rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #8C7E74;
}

/* ─── Payment Card ─── */
.payment-card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  border-radius: 28rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(45, 42, 38, 0.06);
  border: 1rpx solid #E8E0DA;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid #F0ECE8;
}

.course-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #2D2A26;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pay-amount {
  font-size: 40rpx;
  font-weight: 700;
  color: #0E7490;
  flex-shrink: 0;
  margin-left: 16rpx;
}

/* ─── Detail Rows ─── */
.card-detail {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-label {
  font-size: 24rpx;
  color: #8C7E74;
}

.detail-value {
  font-size: 24rpx;
  color: #4D4139;
  font-weight: 500;
}
</style>
