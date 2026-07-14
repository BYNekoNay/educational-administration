<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />
    <text class="page-title">报名记录</text>
    <text class="page-sub">查看报名状态与审核进度</text>

    <!-- Tab filter -->
    <view class="tab-bar">
      <view
        v-for="(tab, idx) in tabs"
        :key="idx"
        class="tab-item"
        :class="{ active: activeTab === idx }"
        @click="activeTab = idx"
      >{{ tab }}</view>
    </view>

    <!-- Empty state -->
    <view v-if="filteredList.length === 0" class="empty-state">
      <text>暂无报名记录</text>
    </view>

    <!-- Enrollment cards list -->
    <view
      v-for="e in filteredList"
      :key="e.id"
      class="enrollment-card content-card"
      :class="{ 'card-pending-payment': e.status === 2 }"
    >
      <!-- Card header: course name + status tag -->
      <view class="card-header">
        <text class="card-course-name">{{ e.courseName }}</text>
        <text class="status-tag" :style="statusStyle(e.status)">{{ statusMap[e.status] || '未知' }}</text>
      </view>

      <!-- Card body -->
      <view class="card-detail">
        <view class="card-row">
          <text class="card-label">学员</text>
          <text class="card-value">{{ e.studentName }}</text>
        </view>
        <view class="card-row">
          <text class="card-label">报名时间</text>
          <text class="card-value">{{ e.createTime }}</text>
        </view>
      </view>

      <!-- Highlighted payment notice for 待缴费 -->
      <view v-if="e.status === 2" class="payment-notice">
        <text class="payment-notice-text">请联系机构完成缴费</text>
      </view>

      <!-- Audit remark -->
      <view v-if="e.auditRemark" class="audit-remark">
        <text class="remark-label">审核备注</text>
        <text class="remark-text">{{ e.auditRemark }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const list = ref([])
const activeTab = ref(0)
const tabs = ['全部', '待审核', '待缴费', '已完成', '已拒绝']
const statusMap = { 1: '待审核', 2: '待缴费', 3: '已完成', 4: '已拒绝', 5: '已失效' }

const statusColors = {
  1: { bg: '#FFFBEB', color: '#F59E0B' },
  2: { bg: '#ECFEFF', color: '#0E7490' },
  3: { bg: '#ECFDF5', color: '#10B981' },
  4: { bg: '#FEF2F2', color: '#E11D48' },
  5: { bg: '#F5F0ED', color: '#8C7E74' }
}

function statusStyle(status) {
  const c = statusColors[status]
  if (!c) return {}
  return { background: c.bg, color: c.color }
}

const filteredList = computed(() => {
  if (activeTab.value === 0) return list.value
  return list.value.filter(e => e.status === activeTab.value)
})

function onStudentChange() {
  fetchData()
}

async function fetchData() {
  try {
    const res = await api({ url: '/api/parent/enrollments?pageNum=1&pageSize=100' })
    list.value = res.data?.records || res.data || []
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
}

onMounted(fetchData)
</script>

<style scoped>
@import '@/styles/content.css';

.enrollment-card {
  margin-bottom: 24rpx;
}

.card-pending-payment {
  border: 2rpx solid #0E7490;
  box-shadow: 0 4rpx 24rpx rgba(14, 116, 144, 0.12);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.card-course-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-tag {
  display: inline-block;
  font-size: 22rpx;
  font-weight: 500;
  padding: 6rpx 20rpx;
  border-radius: 20rpx;
  line-height: 1.5;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.card-detail {
  margin-bottom: 8rpx;
}

.payment-notice {
  background: linear-gradient(135deg, #ECFEFF, #F0FDFA);
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-top: 16rpx;
  text-align: center;
}

.payment-notice-text {
  font-size: 26rpx;
  font-weight: 600;
  color: #0E7490;
  letter-spacing: 2rpx;
}

.audit-remark {
  background: #F9F6F3;
  border-radius: 16rpx;
  padding: 16rpx 24rpx;
  margin-top: 16rpx;
}

.remark-label {
  display: block;
  font-size: 22rpx;
  color: #8C7E74;
  margin-bottom: 8rpx;
}

.remark-text {
  display: block;
  font-size: 26rpx;
  color: #4D4139;
  line-height: 1.6;
}
</style>
