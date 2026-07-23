<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />
    <text class="page-title">请假申请</text>
    <text class="page-sub">提交请假并查看记录</text>

    <!-- ─── 新建请假表单 ─── -->
    <text class="section-header">新建请假</text>
    <view class="form-card">
      <view class="form-row">
        <text class="form-label">请假日期</text>
        <picker mode="date" :value="form.lessonDate" :start="today" @change="onDateChange">
          <view class="date-picker">
            <text :class="form.lessonDate ? 'date-text' : 'date-placeholder'">
              {{ form.lessonDate || '请选择日期' }}
            </text>
            <view class="picker-arrow"></view>
          </view>
        </picker>
      </view>

      <view class="form-row">
        <text class="form-label">请假原因</text>
        <textarea
          class="wx-textarea"
          v-model="form.reason"
          placeholder="请输入请假原因"
          :maxlength="200"
        />
      </view>

      <button
        class="btn-primary"
        :disabled="submitting"
        @click="handleSubmit"
      >
        {{ submitting ? '提交中...' : '提交申请' }}
      </button>
    </view>

    <!-- ─── 请假记录 ─── -->
    <text class="section-header">请假记录</text>

    <view v-if="records.length === 0" class="empty-state">
      <text>暂无请假记录</text>
    </view>

    <view v-else class="cell-group">
      <view v-for="item in records" :key="item.id" class="cell record-cell">
        <view class="cell-body">
          <view class="record-header">
            <text class="record-date">{{ item.lessonDate }}</text>
            <text class="tag" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
          </view>
          <text class="record-reason">{{ truncate(item.reason, 40) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const studentId = ref(getCurrentStudentId())
const records = ref([])
const submitting = ref(false)

// 日期选择器最早可选今天（历史课次不可请假）
const _now = new Date()
const today = `${_now.getFullYear()}-${String(_now.getMonth() + 1).padStart(2, '0')}-${String(_now.getDate()).padStart(2, '0')}`

const form = reactive({
  lessonDate: '',
  reason: '',
})

function onStudentChange(id) {
  studentId.value = id
  fetchRecords()
}

function onDateChange(e) {
  form.lessonDate = e.detail.value
}

function statusText(s) {
  return { 1: '待审核', 2: '已通过', 3: '已拒绝' }[s] || '未知'
}

function statusClass(s) {
  return { 1: 'tag-warning', 2: 'tag-success', 3: 'tag-danger' }[s] || 'tag-muted'
}

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.slice(0, len) + '...' : str
}

async function fetchRecords() {
  const sid = studentId.value
  try {
    const res = await api({ url: `/api/parent/leave-requests${sid ? '?studentId=' + sid : ''}` })
    if (sid !== studentId.value) return
    records.value = res.data || []
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '加载失败', icon: 'none' })
  }
}

async function handleSubmit() {
  if (!studentId.value) {
    uni.showToast({ title: '未找到学员', icon: 'none' })
    return
  }
  if (!form.lessonDate) {
    uni.showToast({ title: '请选择请假日期', icon: 'none' })
    return
  }
  if (!form.reason.trim()) {
    uni.showToast({ title: '请输入请假原因', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    await api({
      url: `/api/parent/leave-requests?studentId=${studentId.value}&lessonDate=${form.lessonDate}&reason=${encodeURIComponent(form.reason.trim())}`,
      method: 'POST',
    })
    uni.showToast({ title: '提交成功', icon: 'success' })
    form.lessonDate = ''
    form.reason = ''
    fetchRecords()
  } catch (e) {
    // 业务错误 api() 已弹 toast（_handled），网络错误需此处提示
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '提交失败'), icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onMounted(fetchRecords)
</script>

<style scoped>
@import '@/styles/content.css';

/* ─── 表单卡片 ─── */
.form-card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 32rpx;
  margin: 0 24rpx 8rpx;
  box-shadow: 0 4rpx 20rpx rgba(45, 42, 38, 0.06);
}

.form-row {
  margin-bottom: 28rpx;
}

.form-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: #4D4139;
  margin-bottom: 14rpx;
}

/* ─── 日期选择器 ─── */
.date-picker {
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

.date-picker:active {
  border-color: #0E7490;
  background: #FFFFFF;
}

.date-text {
  font-size: 28rpx;
  color: #4D4139;
}

.date-placeholder {
  font-size: 28rpx;
  color: #C4B8AE;
}

.picker-arrow {
  width: 14rpx;
  height: 14rpx;
  border-right: 3rpx solid #8C7E74;
  border-bottom: 3rpx solid #8C7E74;
  transform: rotate(45deg);
  flex-shrink: 0;
}

/* ─── 记录列表 ─── */
.record-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10rpx;
}

.record-date {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
}

.record-reason {
  font-size: 24rpx;
  color: #8C7E74;
  display: block;
  line-height: 1.5;
}
</style>
