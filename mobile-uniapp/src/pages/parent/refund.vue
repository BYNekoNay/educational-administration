<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />

    <!-- 可退费报名列表 -->
    <text class="section-header">可退费报名</text>

    <view v-if="available.length === 0" class="empty-state">
      <text class="empty-icon">📭</text>
      <text class="empty-text">暂无可退费的报名</text>
    </view>

    <view v-for="item in available" :key="item.enrollmentId" class="refund-card">
      <view class="card-top">
        <text class="course-name">{{ item.courseName }}</text>
        <text class="paid-amount">¥{{ formatAmount(item.paidAmount) }}</text>
      </view>
      <view class="card-detail">
        <view class="detail-row">
          <text class="detail-label">学员</text>
          <text class="detail-value">{{ item.studentName }}</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">剩余课时</text>
          <text class="detail-value highlight">{{ item.remainingLessons }} 课时</text>
        </view>
      </view>
      <view class="card-action">
        <button class="refund-btn" @click="showConfirm(item)">申请退费</button>
      </view>
    </view>

    <!-- 退费记录 -->
    <text class="section-header" style="margin-top: 40rpx">退费记录</text>

    <view v-if="records.length === 0" class="empty-state">
      <text class="empty-icon">📝</text>
      <text class="empty-text">暂无退费记录</text>
    </view>

    <view v-for="r in records" :key="r.id" class="record-card">
      <view class="card-top">
        <text class="course-name">{{ r.courseName || '-' }}</text>
        <text class="status-tag" :class="statusClass(r.status)">{{ statusText(r.status) }}</text>
      </view>
      <view class="card-detail">
        <view class="detail-row">
          <text class="detail-label">学员</text>
          <text class="detail-value">{{ r.studentName }}</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">退费金额</text>
          <text class="detail-value refund-amount">¥{{ formatAmount(r.amount) }}</text>
        </view>
        <view class="detail-row">
          <text class="detail-label">申请时间</text>
          <text class="detail-value">{{ formatTime(r.createTime) }}</text>
        </view>
      </view>
    </view>

    <!-- 确认弹窗 -->
    <view v-if="confirmVisible" class="modal-mask" @click="confirmVisible = false">
      <view class="modal-card" @click.stop>
        <text class="modal-title">确认退费申请</text>
        <view class="modal-body">
          <view class="modal-row">
            <text class="modal-label">课程</text>
            <text class="modal-value">{{ confirmItem.courseName }}</text>
          </view>
          <view class="modal-row">
            <text class="modal-label">学员</text>
            <text class="modal-value">{{ confirmItem.studentName }}</text>
          </view>
          <view class="modal-row">
            <text class="modal-label">剩余课时</text>
            <text class="modal-value">{{ confirmItem.remainingLessons }} 课时</text>
          </view>
        </view>
        <view class="modal-tip">
          退费金额将在财务审核后确定，审核通过后课时将自动回退。
        </view>
        <view class="modal-btns">
          <button class="modal-btn cancel" @click="confirmVisible = false">取消</button>
          <button class="modal-btn submit" :loading="submitting" @click="doRefund">确认申请</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const available = ref([])
const records = ref([])
const studentId = ref(getCurrentStudentId())
const confirmVisible = ref(false)
const confirmItem = ref({})
const submitting = ref(false)

function statusText(s) {
  const map = { 1: '待审核', 2: '已通过', 3: '已驳回' }
  return map[s] || '未知'
}

function statusClass(s) {
  const map = { 1: 'status-pending', 2: 'status-approved', 3: 'status-rejected' }
  return map[s] || ''
}

function formatAmount(v) {
  return (Number(v) || 0).toFixed(2)
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

function showConfirm(item) {
  confirmItem.value = item
  confirmVisible.value = true
}

async function doRefund() {
  submitting.value = true
  try {
    await api({
      url: '/api/parent/refunds',
      method: 'POST',
      data: { enrollmentId: confirmItem.value.enrollmentId }
    })
    uni.showToast({ title: '退费申请已提交', icon: 'success' })
    confirmVisible.value = false
    loadData()
  } catch (e) {
    const msg = e.data?.message || e.errMsg || '提交失败'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

async function loadData() {
  if (!studentId.value) return
  try {
    const [availRes, recRes] = await Promise.all([
      api({ url: `/api/parent/refunds/available?studentId=${studentId.value}` }),
      api({ url: `/api/parent/refunds?studentId=${studentId.value}&pageNum=1&pageSize=50` })
    ])
    available.value = availRes.data || []
    records.value = recRes.data?.records || []
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
}

onMounted(loadData)
</script>

<style scoped>
/* 复用 content.css 基础样式（.section-header, .page-wrap 等） */
@import '@/styles/content.css';

/* ─── Empty State ─── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 0;
}
.empty-icon { font-size: 64rpx; margin-bottom: 16rpx; }
.empty-text { font-size: 28rpx; color: #8C7E74; }

/* ─── Refund Card ─── */
.refund-card {
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

.paid-amount {
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

.detail-label { font-size: 24rpx; color: #8C7E74; }
.detail-value { font-size: 24rpx; color: #4D4139; font-weight: 500; }
.highlight { color: #0E7490; font-weight: 600; }

/* ─── Action Button ─── */
.card-action {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #F0ECE8;
}

.refund-btn {
  width: 100%;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #FFFFFF;
  background: linear-gradient(135deg, #E65100, #F57C00);
  border: none;
  border-radius: 16rpx;
  text-align: center;
}

.refund-btn::after { border: none; }

/* ─── Record Card ─── */
.record-card {
  background: #FFFFFF;
  margin: 16rpx 24rpx;
  border-radius: 28rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(45, 42, 38, 0.06);
  border: 1rpx solid #E8E0DA;
}

.status-tag {
  font-size: 22rpx;
  font-weight: 600;
  padding: 6rpx 16rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.status-pending   { background: #FFF3E0; color: #E65100; }
.status-approved  { background: #E8F5E9; color: #2E7D32; }
.status-rejected  { background: #FFEBEE; color: #C62828; }

.refund-amount { color: #C62828; font-weight: 700; font-size: 26rpx; }

/* ─── Modal ─── */
.modal-mask {
  position: fixed; inset: 0; z-index: 999;
  background: rgba(0,0,0,0.45);
  display: flex; align-items: flex-end;
}

.modal-card {
  width: 100%; background: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  padding: 48rpx 32rpx 32rpx;
}

.modal-title {
  font-size: 36rpx; font-weight: 700; color: #2D2A26;
  display: block; text-align: center; margin-bottom: 32rpx;
}

.modal-body {
  display: flex; flex-direction: column; gap: 20rpx;
  padding: 0 16rpx; margin-bottom: 24rpx;
}

.modal-row { display: flex; justify-content: space-between; align-items: center; }
.modal-label { font-size: 28rpx; color: #8C7E74; }
.modal-value { font-size: 28rpx; color: #2D2A26; font-weight: 600; }

.modal-tip {
  font-size: 24rpx; color: #8C7E74; line-height: 1.6;
  padding: 20rpx 16rpx; background: #FFF8E1;
  border-radius: 16rpx; margin: 0 0 32rpx 0;
}

.modal-btns { display: flex; gap: 20rpx; }
.modal-btn {
  flex: 1; height: 88rpx; line-height: 88rpx;
  font-size: 30rpx; font-weight: 600;
  border-radius: 20rpx; border: none; text-align: center;
}

.modal-btn.cancel { background: #F5F0EB; color: #8C7E74; }
.modal-btn.submit {
  color: #FFFFFF;
  background: linear-gradient(135deg, #0E7490, #06B6D4);
}

.modal-btn::after { border: none; }
</style>
