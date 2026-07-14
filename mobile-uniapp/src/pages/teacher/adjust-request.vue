<template>
  <view class="page-wrap">
    <text class="page-title">调课申请</text>
    <text class="page-sub">查看与提交调课申请</text>

    <!-- Custom tab bar -->
    <view class="adjust-tab-bar">
      <view
        class="adjust-tab-item"
        :class="{ active: activeTab === 'list' }"
        @click="switchTab('list')"
      >
        <text>我的申请</text>
      </view>
      <view
        class="adjust-tab-item"
        :class="{ active: activeTab === 'create' }"
        @click="switchTab('create')"
      >
        <text>新建申请</text>
      </view>
    </view>

    <!-- 我的申请 Tab -->
    <view v-if="activeTab === 'list'">
      <view v-if="loading" class="empty-state"><text>加载中...</text></view>
      <view v-else-if="requests.length === 0" class="empty-state"><text>暂无调课申请</text></view>

      <view
        class="request-card"
        v-for="item in requests"
        :key="item.id"
      >
        <view class="request-card-header">
          <text class="request-card-title">课次 {{ item.lessonId }}</text>
          <text class="status-tag" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
        </view>

        <view class="request-card-body">
          <view class="request-row">
            <text class="request-label">原上课时间</text>
            <text class="request-value">{{ item.originalDate }} {{ item.originalStartTime }}-{{ item.originalEndTime }}</text>
          </view>
          <view class="request-row">
            <text class="request-label">申请调整至</text>
            <text class="request-value highlight">{{ item.adjustDate }} {{ item.adjustStartTime }}-{{ item.adjustEndTime }}</text>
          </view>
          <view class="request-row">
            <text class="request-label">申请原因</text>
            <text class="request-value reason">{{ item.reason }}</text>
          </view>
          <view v-if="item.auditRemark" class="request-row">
            <text class="request-label">审核备注</text>
            <text class="request-value remark">{{ item.auditRemark }}</text>
          </view>
        </view>

        <view class="request-card-footer">
          <text class="request-time">{{ item.createTime }}</text>
        </view>
      </view>
    </view>

    <!-- 新建申请 Tab -->
    <view v-if="activeTab === 'create'" class="form-panel">
      <!-- Lesson picker -->
      <view class="form-section">
        <text class="form-label">选择课次</text>
        <picker
          :range="lessonLabels"
          @change="onLessonPick"
        >
          <view class="form-picker">
            <text :class="form.lessonId ? 'picker-text' : 'picker-placeholder'">
              {{ form.lessonId ? selectedLessonLabel : '请选择要调课的课次' }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <!-- Reason -->
      <view class="form-section">
        <text class="form-label">申请原因</text>
        <textarea
          class="wx-textarea"
          v-model="form.reason"
          placeholder="请输入调课原因"
          :maxlength="500"
        />
      </view>

      <!-- Adjust date -->
      <view class="form-section">
        <text class="form-label">调整日期</text>
        <picker mode="date" @change="onDateChange">
          <view class="form-picker">
            <text :class="form.adjustDate ? 'picker-text' : 'picker-placeholder'">
              {{ form.adjustDate || '请选择新的上课日期' }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <!-- Start time -->
      <view class="form-section">
        <text class="form-label">开始时间</text>
        <picker mode="time" @change="onStartTimeChange">
          <view class="form-picker">
            <text :class="form.adjustStartTime ? 'picker-text' : 'picker-placeholder'">
              {{ form.adjustStartTime || '请选择开始时间' }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <!-- End time -->
      <view class="form-section">
        <text class="form-label">结束时间</text>
        <picker mode="time" @change="onEndTimeChange">
          <view class="form-picker">
            <text :class="form.adjustEndTime ? 'picker-text' : 'picker-placeholder'">
              {{ form.adjustEndTime || '请选择结束时间' }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <!-- Submit -->
      <view class="form-actions">
        <button class="btn-primary" :disabled="submitting" @click="submitRequest">
          {{ submitting ? '提交中...' : '提交申请' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '@/utils/request'

const activeTab = ref('list')
const requests = ref([])
const lessons = ref([])
const loading = ref(false)
const submitting = ref(false)

const form = ref({
  lessonId: '',
  reason: '',
  adjustDate: '',
  adjustStartTime: '',
  adjustEndTime: '',
})

const lessonLabels = computed(() =>
  lessons.value.map(l => `${l.lessonDate} ${l.startTime}-${l.endTime}`)
)

const selectedLessonLabel = computed(() => {
  const l = lessons.value.find(l => l.id === form.value.lessonId)
  return l ? `${l.lessonDate} ${l.startTime}-${l.endTime}` : ''
})

function statusText(s) {
  return { 1: '待审核', 2: '已通过', 3: '已拒绝' }[s] || '未知'
}
function statusClass(s) {
  return { 1: 'status-pending', 2: 'status-approved', 3: 'status-rejected' }[s] || ''
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'list') fetchRequests()
}

async function fetchRequests() {
  loading.value = true
  try {
    const res = await api({ url: '/api/teacher/adjust-requests' })
    requests.value = res.data || []
  } catch {
    uni.showToast({ title: '加载申请失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function fetchLessons() {
  try {
    const res = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=50' })
    lessons.value = res.data?.records || []
  } catch {
    uni.showToast({ title: '加载课表失败', icon: 'none' })
  }
}

function onLessonPick(e) {
  const idx = e.detail.value
  const lesson = lessons.value[idx]
  if (lesson) form.value.lessonId = lesson.id
}

function onDateChange(e) {
  form.value.adjustDate = e.detail.value
}
function onStartTimeChange(e) {
  form.value.adjustStartTime = e.detail.value
}
function onEndTimeChange(e) {
  form.value.adjustEndTime = e.detail.value
}

async function submitRequest() {
  const f = form.value
  if (!f.lessonId) return uni.showToast({ title: '请选择课次', icon: 'none' })
  if (!f.reason.trim()) return uni.showToast({ title: '请输入申请原因', icon: 'none' })
  if (!f.adjustDate) return uni.showToast({ title: '请选择调整日期', icon: 'none' })
  if (!f.adjustStartTime) return uni.showToast({ title: '请选择开始时间', icon: 'none' })
  if (!f.adjustEndTime) return uni.showToast({ title: '请选择结束时间', icon: 'none' })

  submitting.value = true
  try {
    await api({
      url: `/api/teacher/lessons/${f.lessonId}/adjust-requests`,
      method: 'POST',
      data: {
        reason: f.reason,
        adjustDate: f.adjustDate,
        adjustStartTime: f.adjustStartTime,
        adjustEndTime: f.adjustEndTime,
      },
    })
    uni.showToast({ title: '申请提交成功' })
    // Reset form
    form.value = { lessonId: '', reason: '', adjustDate: '', adjustStartTime: '', adjustEndTime: '' }
    // Switch to list tab and reload
    activeTab.value = 'list'
    await fetchRequests()
  } catch {
    uni.showToast({ title: '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchRequests()
  fetchLessons()
})
</script>

<style scoped>
@import '@/styles/content.css';

/* ─── Custom tab bar with bottom border ─── */
.adjust-tab-bar {
  display: flex;
  margin: 0 24rpx;
  background: #FFF;
  border-radius: 28rpx 28rpx 0 0;
  border: 2rpx solid #E8E0DA;
  border-bottom: none;
  overflow: hidden;
}
.adjust-tab-item {
  flex: 1;
  text-align: center;
  padding: 28rpx 0 24rpx;
  font-size: 28rpx;
  font-weight: 400;
  color: #8C7E74;
  position: relative;
  transition: all 0.25s ease;
}
.adjust-tab-item.active {
  color: #0E7490;
  font-weight: 600;
}
.adjust-tab-item.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 64rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: #0E7490;
}

/* ─── Request cards ─── */
.request-card {
  background: #FFF;
  border-radius: 28rpx;
  margin: 24rpx 24rpx 0;
  padding: 28rpx 32rpx;
  border: 2rpx solid #E8E0DA;
  box-shadow: 0 4rpx 16rpx rgba(45, 42, 38, 0.06);
}
.request-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #F0ECE8;
}
.request-card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
}
.request-card-body {
  margin-bottom: 16rpx;
}
.request-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14rpx;
}
.request-row:last-child {
  margin-bottom: 0;
}
.request-label {
  font-size: 26rpx;
  color: #8C7E74;
  flex-shrink: 0;
  margin-right: 20rpx;
}
.request-value {
  font-size: 26rpx;
  color: #4D4139;
  font-weight: 500;
  text-align: right;
  flex: 1;
}
.request-value.highlight {
  color: #0E7490;
  font-weight: 600;
}
.request-value.reason {
  font-weight: 400;
  color: #4D4139;
  word-break: break-all;
}
.request-value.remark {
  font-weight: 400;
  color: #D97706;
  word-break: break-all;
}
.request-card-footer {
  padding-top: 12rpx;
  border-top: 1rpx solid #F0ECE8;
}
.request-time {
  font-size: 22rpx;
  color: #C4B8AE;
}

/* ─── Status tags ─── */
.status-tag {
  display: inline-block;
  font-size: 22rpx;
  font-weight: 500;
  padding: 4rpx 18rpx;
  border-radius: 20rpx;
  line-height: 1.5;
}
.status-pending {
  background: #FEF3C7;
  color: #92400E;
}
.status-approved {
  background: #D1FAE5;
  color: #065F46;
}
.status-rejected {
  background: #FFE4E6;
  color: #9F1239;
}

/* ─── Form panel ─── */
.form-panel {
  padding: 0 24rpx;
}
.form-section {
  margin-bottom: 28rpx;
}
.form-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: #4D4139;
  margin-bottom: 12rpx;
}
.form-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  background: #F5F0ED;
  border-radius: 18rpx;
  padding: 0 28rpx;
  border: 2rpx solid transparent;
  transition: border-color 0.2s;
}
.form-picker:active {
  border-color: #0E7490;
  background: #FFF;
}
.picker-text {
  font-size: 28rpx;
  color: #4D4139;
  flex: 1;
}
.picker-placeholder {
  font-size: 28rpx;
  color: #C4B8AE;
  flex: 1;
}
.picker-arrow {
  width: 14rpx;
  height: 14rpx;
  border-top: 3rpx solid #C4B8AE;
  border-right: 3rpx solid #C4B8AE;
  transform: rotate(45deg);
  margin-left: 16rpx;
  flex-shrink: 0;
}
.form-actions {
  margin-top: 48rpx;
  padding-bottom: 48rpx;
}
</style>
