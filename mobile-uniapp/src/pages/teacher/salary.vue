<template>
  <view class="page-wrap">
    <text class="page-title">我的课酬</text>
    <text class="page-sub">按月查看授课与结算明细</text>

    <view class="stats-row">
      <view class="stat-card">
        <text class="stat-value">{{ yearTotal }}</text>
        <text class="stat-label">本年合计(元)</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ list.length }}</text>
        <text class="stat-label">结算月份</text>
      </view>
    </view>

    <view class="section-header">
      <text>月度明细</text>
    </view>

    <view class="cell-group">
      <view v-for="item in list" :key="item.id" class="cell" @click="showDetail(item)">
        <view class="cell-main">
          <text class="cell-title">{{ item.salaryMonth }}</text>
          <text class="cell-desc">主讲 {{ item.lessonCount || 0 }} 节 · 代课 {{ item.substituteCount || 0 }} 节</text>
        </view>
        <view class="cell-right">
          <text class="cell-amount">¥{{ formatAmount(item.totalAmount) }}</text>
          <text class="cell-status">{{ statusText(item.status) }}</text>
        </view>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty">暂无课酬记录</view>

    <!-- 明细弹层 -->
    <view v-if="detailVisible" class="overlay" @click="detailVisible = false">
      <view class="detail-panel" @click.stop>
        <view class="detail-header">
          <text class="detail-title">{{ current && current.salaryMonth }} 课酬明细</text>
          <text class="detail-close" @click="detailVisible = false">关闭</text>
        </view>
        <scroll-view scroll-y class="detail-body">
          <view class="detail-row">
            <text class="detail-label">授课节数</text>
            <text class="detail-value">{{ (current && current.lessonCount) || 0 }} 节</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">代课节数</text>
            <text class="detail-value">{{ (current && current.substituteCount) || 0 }} 节</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">基础课酬</text>
            <text class="detail-value">¥{{ formatAmount(current && current.baseAmount) }}</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">代课课酬</text>
            <text class="detail-value">¥{{ formatAmount(current && current.substituteAmount) }}</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">奖金</text>
            <text class="detail-value">¥{{ formatAmount(current && current.bonusAmount) }}</text>
          </view>
          <view class="detail-row detail-total">
            <text class="detail-label">合计</text>
            <text class="detail-value">¥{{ formatAmount(current && current.totalAmount) }}</text>
          </view>
          <view class="detail-note">
            数据来自机构薪资结算，如有疑问请联系财务。
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '@/utils/request'

const loading = ref(false)
const list = ref([])
const detailVisible = ref(false)
const current = ref(null)

const yearTotal = computed(() => {
  const year = String(new Date().getFullYear())
  const sum = list.value
    .filter(it => String(it.salaryMonth || '').startsWith(year))
    .reduce((acc, it) => acc + Number(it.totalAmount || 0), 0)
  return sum.toFixed(2)
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const res = await api({ url: '/api/teacher/salaries', data: { pageNum: 1, pageSize: 50 } })
    list.value = (res.data && res.data.records) || []
  } catch (e) {
    if (!e || !e._handled) {
      uni.showToast({ title: '课酬加载失败', icon: 'none' })
    }
  } finally {
    loading.value = false
  }
}

function showDetail(item) {
  current.value = item
  detailVisible.value = true
}

function formatAmount(val) {
  const n = Number(val || 0)
  return n.toFixed(2)
}

function statusText(status) {
  return { 1: '待确认', 2: '已确认', 3: '已发放', 4: '已撤销' }[status] || ''
}
</script>

<style scoped>
@import '@/styles/content.css';

.stats-row {
  display: flex;
  gap: 20rpx;
  margin: 24rpx 0;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.stat-value {
  font-size: 40rpx;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #909399;
}

.cell-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.cell-amount {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
}

.cell-status {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #909399;
}

.empty {
  padding: 80rpx 0;
  text-align: center;
  color: #909399;
  font-size: 26rpx;
}

.overlay {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}

.detail-panel {
  width: 100%;
  max-height: 70vh;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 28rpx 32rpx 48rpx;
  box-sizing: border-box;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.detail-title {
  font-size: 32rpx;
  font-weight: 600;
}

.detail-close {
  font-size: 26rpx;
  color: #909399;
}

.detail-body {
  max-height: 55vh;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
  font-size: 28rpx;
}

.detail-label {
  color: #909399;
}

.detail-value {
  color: #303133;
}

.detail-total .detail-value {
  font-weight: 600;
  font-size: 32rpx;
}

.detail-note {
  margin-top: 24rpx;
  font-size: 22rpx;
  color: #c0c4cc;
  line-height: 1.6;
}
</style>
