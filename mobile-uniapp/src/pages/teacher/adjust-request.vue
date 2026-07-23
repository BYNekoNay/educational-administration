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
          <text class="request-card-title">{{ item.courseName || '未知课程' }} · {{ item.className || '-' }}</text>
          <text class="status-tag" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
        </view>

        <view class="request-card-body">
          <view class="request-row">
            <text class="request-label">原课次</text>
            <text class="request-value">{{ item.lessonDate }} {{ item.startTime?.slice(0,5) }}-{{ item.endTime?.slice(0,5) }}</text>
          </view>
          <view class="request-row" v-if="item.periodName">
            <text class="request-label">时段</text>
            <text class="request-value">{{ item.periodName }}</text>
          </view>
          <view class="request-row">
            <text class="request-label">调至</text>
            <text class="request-value highlight">{{ formatExpectTime(item.expectTime) }}</text>
          </view>
          <view class="request-row">
            <text class="request-label">原因</text>
            <text class="request-value reason">{{ item.reason }}</text>
          </view>
          <view v-if="item.auditRemark" class="request-row">
            <text class="request-label">备注</text>
            <text class="request-value remark">{{ item.auditRemark }}</text>
          </view>
        </view>

        <view class="request-card-footer">
          <text class="request-time">{{ formatTime(item.createTime) }}</text>
        </view>
      </view>
    </view>

    <!-- 新建申请 Tab -->
    <view v-if="activeTab === 'create'" class="form-panel">
      <!-- 两级选择：班级 → 课次 -->
      <view class="form-section">
        <text class="form-label">选择班级</text>
        <picker :range="classLabels" @change="onClassPick">
          <view class="form-picker">
            <text :class="form.classIdx >= 0 ? 'picker-text' : 'picker-placeholder'">
              {{ form.classIdx >= 0 ? classLabels[form.classIdx] : '请选择班级' }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <view class="form-section" v-if="form.classIdx >= 0">
        <text class="form-label">选择课次</text>
        <picker :range="filteredLessonLabels" @change="onLessonPick">
          <view class="form-picker">
            <text :class="form.lessonIdx >= 0 ? 'picker-text' : 'picker-placeholder'">
              {{ form.lessonIdx >= 0 ? filteredLessonLabels[form.lessonIdx] : '请选择具体课次' }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <!-- 选中课次后才显示调课信息 -->
      <view v-if="form.classIdx >= 0 && form.lessonId" class="step2">

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

      <!-- 当日占用提示 -->
      <view v-if="form.adjustDate && occupiedHint" class="occupied-hint">
        <text>{{ occupiedHint }}</text>
      </view>

      <!-- Period picker (仅空闲时段) -->
      <view class="form-section">
        <text class="form-label">调整时段</text>
        <picker
          :range="availablePeriodLabels"
          @change="onPeriodChange"
          :disabled="availablePeriodLabels.length === 0"
        >
          <view class="form-picker">
            <text :class="form.periodIdx >= 0 ? 'picker-text' : 'picker-placeholder'">
              {{ availablePeriodLabels.length === 0 ? '当日无空闲时段' : (form.periodIdx >= 0 ? availablePeriodLabels[form.periodIdx] : '请选择上课时段') }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <!-- Submit -->
      <view class="form-actions">
        <button class="btn-primary" :disabled="submitting || availablePeriodLabels.length === 0" @click="submitRequest">
          {{ submitting ? '提交中...' : '提交申请' }}
        </button>
      </view>

      </view><!-- /step2 -->
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { api } from '@/utils/request'

const activeTab = ref('list')
const requests = ref([])
const lessons = ref([])
const periods = ref([])
const loading = ref(false)
const submitting = ref(false)

const form = ref({
  lessonId: '',
  reason: '',
  adjustDate: '',
  periodIdx: -1,
  classIdx: -1,
  lessonIdx: -1,
})

// 班级列表（从 lessons 中按 classId 去重）
const classOptions = computed(() => {
  const seen = new Set()
  const result = []
  for (const l of lessons.value) {
    if (seen.has(l.classId)) continue
    seen.add(l.classId)
    result.push({ classId: l.classId, className: l.className || '', courseName: l.courseName || '', count: 0 })
  }
  // 计数
  for (const r of result) {
    r.count = lessons.value.filter(l => l.classId === r.classId).length
  }
  return result
})

const classLabels = computed(() =>
  classOptions.value.map(c => `${c.courseName} · ${c.className}  (${c.count}节)`)
)

// 当前选中班级的课次
const filteredLessons = computed(() => {
  if (form.value.classIdx < 0) return []
  const cls = classOptions.value[form.value.classIdx]
  return cls ? lessons.value.filter(l => l.classId === cls.classId) : []
})

const filteredLessonLabels = computed(() =>
  filteredLessons.value.map(l => {
    const weekNames = ['日','一','二','三','四','五','六']
    const d = new Date(l.lessonDate)
    const wd = weekNames[d.getDay()]
    return `${l.lessonDate} 周${wd} ${l.startTime.slice(0,5)}-${l.endTime.slice(0,5)}`
  })
)

function statusText(s) {
  return { 1: '待审核', 2: '已通过', 3: '已拒绝' }[s] || '未知'
}
function statusClass(s) {
  return { 1: 'status-pending', 2: 'status-approved', 3: 'status-rejected' }[s] || ''
}
// 格式化后端返回的 expectTime（如 2026-07-20T14:00:00）为 'YYYY-MM-DD HH:mm'
function formatExpectTime(t) {
  if (!t) return '待定'
  const s = String(t).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

// 格式化 createTime 为友好展示（今天/昨天/完整日期）
function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  const now = new Date()
  const dateStr = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
  const nowStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
  const yesterday = new Date(now.getTime() - 86400000)
  const yesStr = `${yesterday.getFullYear()}-${String(yesterday.getMonth()+1).padStart(2,'0')}-${String(yesterday.getDate()).padStart(2,'0')}`
  if (dateStr === nowStr) return `今天 ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
  if (dateStr === yesStr) return `昨天 ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'list') fetchRequests()
}

async function fetchRequests() {
  loading.value = true
  try {
    // 后端分页默认 pageSize=10，不传会静默截断历史申请
    const res = await api({ url: '/api/teacher/adjust-requests?pageNum=1&pageSize=50' })
    requests.value = res.data?.records || []
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '加载申请失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function fetchLessons() {
  try {
    const res = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=50' })
    // 后端仅允许待上课(status=1)课次发起调课，已完成/已调课课次申请必被 409 拒绝，这里提前过滤
    lessons.value = (res.data?.records || []).filter(l => Number(l.status) === 1)
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '加载课表失败', icon: 'none' })
  }
}

const periodLabels = computed(() =>
  periods.value.map(p => `${p.name} ${p.startTime?.slice(0,5)}-${p.endTime?.slice(0,5)}`)
)

// 当日已被该教师占用的时段 ID 集合
const occupiedPeriodIds = ref(new Set())
const occupiedHint = ref('')

// 可用时段（排除已占用）
const availablePeriods = computed(() =>
  periods.value.filter(p => !occupiedPeriodIds.value.has(p.id))
)
const availablePeriodLabels = computed(() =>
  availablePeriods.value.map(p => `${p.name} ${p.startTime?.slice(0,5)}-${p.endTime?.slice(0,5)}`)
)

// 监听调整日期：查询当日教师已占时段
watch(() => form.value.adjustDate, async (date) => {
  occupiedPeriodIds.value = new Set()
  occupiedHint.value = ''
  form.value.periodIdx = -1
  if (!date) return
  try {
    const res = await api({ url: `/api/teacher/lessons?pageNum=1&pageSize=100&dateFrom=${date}&dateTo=${date}` })
    const records = res.data?.records || []
    const set = new Set()
    const names = []
    for (const l of records) {
      if (l.periodId) {
        set.add(l.periodId)
        const p = periods.value.find(pp => pp.id === l.periodId)
        if (p) names.push(p.name)
      }
    }
    occupiedPeriodIds.value = set
    if (names.length > 0) {
      occupiedHint.value = `当日已占：${names.join('、')}`
    }
  } catch (e) { /* 静默 */ }
})

function onClassPick(e) {
  form.value.classIdx = e.detail.value
  form.value.lessonIdx = -1
  form.value.lessonId = ''
}

function onLessonPick(e) {
  form.value.lessonIdx = e.detail.value
  const lesson = filteredLessons.value[form.value.lessonIdx]
  if (lesson) form.value.lessonId = lesson.id
}

function onDateChange(e) {
  form.value.adjustDate = e.detail.value
}
function onPeriodChange(e) {
  form.value.periodIdx = e.detail.value
}

async function submitRequest() {
  const f = form.value
  if (!f.lessonId) return uni.showToast({ title: '请选择课次', icon: 'none' })
  if (!f.reason.trim()) return uni.showToast({ title: '请输入申请原因', icon: 'none' })
  if (!f.adjustDate) return uni.showToast({ title: '请选择调整日期', icon: 'none' })
  if (f.periodIdx < 0) return uni.showToast({ title: '请选择上课时段', icon: 'none' })

  const period = availablePeriods.value[f.periodIdx]
  if (!period) return uni.showToast({ title: '时段数据异常', icon: 'none' })

  submitting.value = true
  try {
    await api({
      url: `/api/teacher/lessons/${f.lessonId}/adjust-requests`,
      method: 'POST',
      data: {
        reason: f.reason,
        expectTime: `${f.adjustDate}T${period.startTime}`,
      },
    })
    uni.showToast({ title: '申请提交成功' })
    // Reset form
    form.value = { lessonId: '', reason: '', adjustDate: '', periodIdx: -1, classIdx: -1, lessonIdx: -1 }
    // Switch to list tab and reload
    activeTab.value = 'list'
    await Promise.all([fetchRequests(), fetchLessons()])
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

async function fetchPeriods() {
  try {
    const res = await api({ url: '/api/edu/periods' })
    periods.value = res.data || []
  } catch (e) {
    // 时段加载失败不阻塞其他功能
  }
}

onMounted(() => {
  fetchRequests()
  fetchLessons()
  fetchPeriods()
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

.occupied-hint {
  margin: 0 0 20rpx 0;
  padding: 16rpx 24rpx;
  background: #FEF3C7;
  border-radius: 12rpx;
  font-size: 24rpx;
  color: #92400E;
}
</style>
