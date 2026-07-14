<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />

    <!-- Summary header -->
    <view class="summary-bar">
      <view class="summary-item">
        <text class="summary-value">{{ totalRemaining }}</text>
        <text class="summary-label">剩余总课时</text>
      </view>
      <view class="summary-divider"></view>
      <view class="summary-item">
        <text class="summary-value">{{ accounts.length }}</text>
        <text class="summary-label">在学课程</text>
      </view>
    </view>

    <!-- Account cards with progress -->
    <text class="section-header">课时账户</text>

    <view v-if="accounts.length === 0" class="empty-state">
      <text class="empty-icon">📭</text>
      <text class="empty-text">暂无课时账户</text>
    </view>

    <view v-for="a in accounts" :key="a.id" class="account-card">
      <!-- Card header: course name + expiry -->
      <view class="card-top">
        <view class="course-info">
          <text class="course-name">{{ a.courseName || '课程 #' + a.courseId }}</text>
          <text class="expire-tag" :class="{ 'expire-warn': isExpiringSoon(a) }">
            {{ a.expireDate ? '有效期至 ' + a.expireDate : '永久有效' }}
          </text>
        </view>
      </view>

      <!-- Progress bar section -->
      <view class="progress-section">
        <view class="progress-header">
          <text class="progress-label">消耗进度</text>
          <text class="progress-percent" :class="progressColorClass(a)">{{ calcPercent(a) }}%</text>
        </view>
        <view class="progress-track">
          <view
            class="progress-fill"
            :class="progressBarClass(a)"
            :style="{ width: calcPercent(a) + '%' }"
          ></view>
        </view>
        <view class="progress-meta">
          <text class="meta-consumed">已消耗 {{ calcConsumed(a) }} 课时</text>
          <text class="meta-total">共 {{ a.totalLessons }} 课时</text>
        </view>
      </view>

      <!-- Remaining stat -->
      <view class="remaining-row">
        <view class="remaining-info">
          <text class="remaining-value" :class="remainingColorClass(a)">{{ a.remainingLessons }}</text>
          <text class="remaining-label">剩余课时</text>
        </view>
        <view class="remaining-badge" v-if="a.remainingLessons <= 0">
          <text class="badge-text">已用完</text>
        </view>
        <view class="remaining-badge badge-low" v-else-if="a.remainingLessons <= 3">
          <text class="badge-text">即将用完</text>
        </view>
        <view class="remaining-badge badge-ok" v-else>
          <text class="badge-text">正常</text>
        </view>
      </view>
    </view>

    <!-- Flow records -->
    <text class="section-header">课时流水</text>

    <view v-if="flows.length === 0" class="empty-state">
      <text class="empty-icon">📋</text>
      <text class="empty-text">暂无流水记录</text>
    </view>

    <view v-else class="cell-group">
      <view v-for="f in flows" :key="f.id" class="cell">
        <view class="cell-body">
          <text class="cell-title">{{ f.remark || '无备注' }}</text>
          <text class="cell-desc">余额 {{ f.afterBalance }}</text>
        </view>
        <view class="cell-footer">
          <text class="tag" :class="f.changeType === 1 ? 'tag-success' : 'tag-danger'">
            {{ f.changeType === 1 ? '+' + f.changeAmount : '-' + f.changeAmount }}
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const accounts = ref([])
const flows = ref([])
const studentId = ref(getCurrentStudentId())

const totalRemaining = computed(() => {
  return accounts.value.reduce((sum, a) => sum + (Number(a.remainingLessons) || 0), 0)
})

function calcConsumed(a) {
  const total = Number(a.totalLessons) || 0
  const remaining = Number(a.remainingLessons) || 0
  return Math.max(0, total - remaining)
}

function calcPercent(a) {
  const total = Number(a.totalLessons) || 0
  if (total === 0) return 0
  const consumed = calcConsumed(a)
  return Math.min(100, Math.round((consumed / total) * 100))
}

function isExpiringSoon(a) {
  if (!a.expireDate) return false
  const expire = new Date(a.expireDate)
  const now = new Date()
  const diff = (expire - now) / (1000 * 60 * 60 * 24)
  return diff <= 30 && diff > 0
}

function progressColorClass(a) {
  const pct = calcPercent(a)
  if (pct >= 90) return 'percent-danger'
  if (pct >= 70) return 'percent-warning'
  return 'percent-normal'
}

function progressBarClass(a) {
  const pct = calcPercent(a)
  if (pct >= 90) return 'bar-danger'
  if (pct >= 70) return 'bar-warning'
  return 'bar-normal'
}

function remainingColorClass(a) {
  const r = Number(a.remainingLessons) || 0
  if (r <= 0) return 'remain-empty'
  if (r <= 3) return 'remain-low'
  return 'remain-ok'
}

function onStudentChange(id) {
  studentId.value = id
  loadData()
}

async function loadData() {
  if (!studentId.value) return
  try {
    const [aR, fR] = await Promise.all([
      api({ url: `/api/finance/parent/students/${studentId.value}/lesson-account` }),
      api({ url: `/api/finance/parent/students/${studentId.value}/lesson-flows` })
    ])
    accounts.value = aR.data || []
    flows.value = fR.data || []
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

/* ─── Account Card ─── */
.account-card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  border-radius: 28rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(45, 42, 38, 0.06);
  border: 1rpx solid #E8E0DA;
}

.card-top {
  margin-bottom: 28rpx;
}

.course-name {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #2D2A26;
  margin-bottom: 8rpx;
}

.expire-tag {
  display: inline-block;
  font-size: 22rpx;
  color: #8C7E74;
  background: #F5F0ED;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}

.expire-warn {
  color: #D97706;
  background: #FEF3C7;
}

/* ─── Progress Bar ─── */
.progress-section {
  margin-bottom: 28rpx;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.progress-label {
  font-size: 24rpx;
  color: #8C7E74;
}

.progress-percent {
  font-size: 28rpx;
  font-weight: 700;
}

.percent-normal { color: #0E7490; }
.percent-warning { color: #D97706; }
.percent-danger { color: #DC2626; }

.progress-track {
  height: 16rpx;
  background: #F0ECE8;
  border-radius: 8rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 8rpx;
  transition: width 0.6s cubic-bezier(0.25, 1, 0.5, 1);
  min-width: 4rpx;
}

.bar-normal {
  background: linear-gradient(90deg, #0E7490, #06B6D4);
}

.bar-warning {
  background: linear-gradient(90deg, #D97706, #F59E0B);
}

.bar-danger {
  background: linear-gradient(90deg, #DC2626, #F87171);
}

.progress-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 10rpx;
}

.meta-consumed {
  font-size: 22rpx;
  color: #8C7E74;
}

.meta-total {
  font-size: 22rpx;
  color: #8C7E74;
}

/* ─── Remaining Row ─── */
.remaining-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 24rpx;
  border-top: 1rpx solid #F0ECE8;
}

.remaining-info {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.remaining-value {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1;
}

.remain-ok { color: #0E7490; }
.remain-low { color: #D97706; }
.remain-empty { color: #999; }

.remaining-label {
  font-size: 24rpx;
  color: #8C7E74;
}

/* ─── Status Badge ─── */
.remaining-badge {
  padding: 6rpx 20rpx;
  border-radius: 20rpx;
}

.badge-ok {
  background: #E0F5F9;
}
.badge-ok .badge-text {
  color: #0E7490;
  font-size: 22rpx;
  font-weight: 500;
}

.badge-low {
  background: #FEF3C7;
}
.badge-low .badge-text {
  color: #D97706;
  font-size: 22rpx;
  font-weight: 500;
}

.remaining-badge:first-of-type {
  background: #F5F0ED;
}
.remaining-badge:first-of-type .badge-text {
  color: #999;
  font-size: 22rpx;
  font-weight: 500;
}
</style>
