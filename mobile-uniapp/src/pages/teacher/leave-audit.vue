<template>
  <view class="page-wrap">
    <text class="page-title">请假审批</text>
    <text class="page-sub">本班学员请假处理</text>

    <view v-if="loading" class="empty-state"><text>加载中...</text></view>
    <view v-else-if="requests.length === 0" class="empty-state">
      <text>暂无请假申请</text>
      <text class="empty-tip">本班学员提交请假后会出现在这里</text>
    </view>

    <view v-else class="cell-group">
      <view
        v-for="item in requests"
        :key="item.id"
        class="cell record-cell"
        :class="{ 'record-done': !canAudit(item.status) }"
      >
        <view class="cell-body">
          <view class="record-header">
            <view class="record-student">
              <text class="record-name">{{ item.studentName || '学员' }}</text>
              <text class="record-date">{{ item.lessonDate }}</text>
            </view>
            <text class="tag" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
          </view>

          <text class="record-reason">{{ item.reason || '未填写请假原因' }}</text>

          <text v-if="item.auditRemark" class="record-remark">审批备注：{{ item.auditRemark }}</text>

          <view v-if="canAudit(item.status)" class="record-actions">
            <button class="btn-approve" :disabled="auditingId === item.id" @click="handleAudit(item, 2)">通过</button>
            <button class="btn-reject" :disabled="auditingId === item.id" @click="openReject(item)">驳回</button>
          </view>
        </view>
      </view>
    </view>

    <!-- 驳回备注弹窗 -->
    <view v-if="rejectTarget" class="dialog-mask" @click="closeReject">
      <view class="dialog" @click.stop>
        <text class="dialog-title">驳回请假</text>
        <text class="dialog-sub">学员：{{ rejectTarget.studentName }} · {{ rejectTarget.lessonDate }}</text>
        <textarea
          class="wx-textarea"
          v-model="rejectRemark"
          placeholder="请输入驳回原因（≤200字）"
          :maxlength="200"
        />
        <view class="dialog-footer">
          <button class="dialog-btn" @click="closeReject">取消</button>
          <button class="dialog-btn dialog-btn-danger" :disabled="rejectSubmitting" @click="submitReject">确认驳回</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'
import { canAudit, leaveStatusClass, leaveStatusText } from '@/utils/leave-status'

const requests = ref([])
const loading = ref(false)
const auditingId = ref(null)

const rejectTarget = ref(null)
const rejectRemark = ref('')
const rejectSubmitting = ref(false)

function statusText(s) { return leaveStatusText(s) }
function statusClass(s) { return leaveStatusClass(s) }

async function fetchRequests() {
  loading.value = true
  try {
    const res = await api({ url: '/api/teacher/leave-requests?pageNum=1&pageSize=20' })
    requests.value = (res.data && res.data.records) || (res.data || [])
  } catch (e) {
    // 业务/HTTP 错误 api() 已弹 toast（_handled），此处仅兜底未处理异常
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载失败'), icon: 'none' })
  } finally {
    loading.value = false
  }
}

function openReject(item) {
  if (!canAudit(item.status)) return
  rejectTarget.value = item
  rejectRemark.value = ''
}

function closeReject() {
  if (rejectSubmitting.value) return
  rejectTarget.value = null
  rejectRemark.value = ''
}

async function submitReject() {
  const target = rejectTarget.value
  if (!target) return
  if (!rejectRemark.value.trim()) {
    uni.showToast({ title: '请输入驳回原因', icon: 'none' })
    return
  }
  rejectSubmitting.value = true
  try {
    const res = await api({
      url: `/api/teacher/leave-requests/${target.id}/audit`,
      method: 'PUT',
      data: { status: 3, remark: rejectRemark.value.trim() },
    })
    const updated = res.data || {}
    // 局部更新列表项状态，无需整页刷新
    target.status = updated.status != null ? updated.status : 3
    if (updated.auditRemark != null) target.auditRemark = updated.auditRemark
    uni.showToast({ title: '已驳回', icon: 'success' })
    rejectTarget.value = null
    rejectRemark.value = ''
  } catch (e) {
    // 409 已处理等错误 api() 已弹 toast（_handled）
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '操作失败'), icon: 'none' })
  } finally {
    rejectSubmitting.value = false
  }
}

async function handleAudit(item, status) {
  if (!canAudit(item.status)) return
  auditingId.value = item.id
  try {
    const res = await api({
      url: `/api/teacher/leave-requests/${item.id}/audit`,
      method: 'PUT',
      data: { status, remark: status === 2 ? '同意' : '' },
    })
    const updated = res.data || {}
    item.status = updated.status != null ? updated.status : status
    if (updated.auditRemark != null) item.auditRemark = updated.auditRemark
    uni.showToast({ title: status === 2 ? '已通过' : '已驳回', icon: 'success' })
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '操作失败'), icon: 'none' })
  } finally {
    auditingId.value = null
  }
}

onMounted(fetchRequests)
// tab 外来回导航时刷新最新待办
onShow(() => { fetchRequests() })
</script>

<style scoped>
@import '@/styles/content.css';

.record-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
}
.record-done { opacity: 0.92; }

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.record-student {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  flex: 1;
  min-width: 0;
}
.record-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
}
.record-date {
  font-size: 24rpx;
  color: #8C7E74;
}
.record-reason {
  display: block;
  font-size: 26rpx;
  color: #4D4139;
  line-height: 1.5;
}
.record-remark {
  display: block;
  font-size: 24rpx;
  color: #8C7E74;
  margin-top: 8rpx;
}

.record-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
}
.btn-approve, .btn-reject {
  flex: 1;
  font-size: 28rpx;
  border-radius: 16rpx;
  padding: 16rpx 0;
  line-height: 1.4;
  border: none;
}
.btn-approve {
  background: #10B981;
  color: #FFF;
}
.btn-reject {
  background: #FFF;
  color: #E54848;
  border: 2rpx solid #F3C2C2;
}
.btn-approve[disabled], .btn-reject[disabled] {
  opacity: 0.6;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.dialog {
  width: 560rpx;
  background: #FFF;
  border-radius: 24rpx;
  padding: 36rpx 32rpx;
}
.dialog-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #2D2A26;
}
.dialog-sub {
  display: block;
  font-size: 24rpx;
  color: #8C7E74;
  margin-top: 8rpx;
  margin-bottom: 20rpx;
}
.wx-textarea {
  width: 100%;
  height: 160rpx;
  background: #F5F0ED;
  border-radius: 16rpx;
  padding: 20rpx;
  font-size: 26rpx;
  box-sizing: border-box;
}
.dialog-footer {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}
.dialog-btn {
  flex: 1;
  font-size: 28rpx;
  border-radius: 16rpx;
  padding: 16rpx 0;
  line-height: 1.4;
  background: #F0ECE8;
  color: #4D4139;
  border: none;
}
.dialog-btn-danger {
  background: #E54848;
  color: #FFF;
}
.dialog-btn[disabled] { opacity: 0.6; }
</style>
