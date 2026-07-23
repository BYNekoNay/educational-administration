<template>
  <view class="page-wrap">
    <text class="page-title">考勤记录</text>
    <text class="page-sub">查看历史课次考勤详情</text>

    <!-- 日期组列表 -->
    <view v-if="records.length > 0" class="cell-group">
      <view
        class="cell record-cell"
        v-for="item in records"
        :key="item.id"
        @click="viewDetail(item)"
      >
        <view class="cell-body">
          <text class="cell-title">{{ item.lessonDate }} {{ item.startTime?.slice(0,5) }}-{{ item.endTime?.slice(0,5) }}</text>
          <text class="cell-desc">{{ item.courseName || '' }}{{ item.courseName && item.className ? ' · ' : '' }}{{ item.className || '' }}</text>
        </view>
        <view class="cell-footer">
          <text class="record-summary">{{ attendanceSummary(item) }}</text>
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>
    <view v-if="records.length === 0" class="empty-state"><text>暂无考勤记录</text></view>

    <!-- 详情弹窗 -->
    <view v-if="detailVisible" class="overlay" @click="detailVisible = false">
      <view class="detail-panel" @click.stop>
        <view class="detail-header">
          <text class="detail-title">{{ detailLesson?.lessonDate }} {{ detailLesson?.startTime?.slice(0,5) }}-{{ detailLesson?.endTime?.slice(0,5) }}</text>
          <text class="detail-close" @click="detailVisible = false">✕</text>
        </view>
        <text class="detail-class">{{ detailLesson?.courseName || '' }}{{ detailLesson?.courseName && detailLesson?.className ? ' · ' : '' }}{{ detailLesson?.className || '' }}</text>
        <scroll-view v-if="detailStudents.length > 0" scroll-y class="detail-list" :style="{ maxHeight: '50vh' }">
          <view class="detail-item" v-for="s in detailStudents" :key="s.studentId">
            <text class="detail-student">{{ s.studentName || '学员' + s.studentId }}</text>
            <text class="detail-status" :class="statusStyle(s.status)">{{ statusText(s.status) }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/utils/request'

const records = ref([])
const detailVisible = ref(false)
const detailLesson = ref(null)
const detailStudents = ref([])

function statusText(s) { return { 1: '到课', 2: '迟到', 3: '请假', 4: '缺勤' }[s] || '未考勤' }
function statusStyle(s) { return { 1: 's-present', 2: 's-late', 3: 's-leave', 4: 's-absent' }[s] || '' }
function attendanceSummary(item) {
  const c = item.completed || item.lessonDate
  return c ? '已完成' : '已过期'
}

async function viewDetail(item) {
  detailLesson.value = item
  detailStudents.value = []
  detailVisible.value = true
  try {
    const aR = await api({ url: `/api/teacher/lessons/${item.id}/attendances` })
    detailStudents.value = aR.data || []
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '加载考勤详情失败', icon: 'none' })
  }
}

onMounted(async () => {
  try {
    const r = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=200' })
    const all = r.data?.records || []
    const today = new Date().toISOString().slice(0, 10)
    // 仅历史+今日已完成/已取消/已调课，不显示将来课次
    records.value = all
      .filter(l => l.lessonDate <= today)
      .filter(l => !(l.lessonDate === today && Number(l.status) === 1))
      .slice(0, 100)
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: '加载记录失败', icon: 'none' })
  }
})
</script>

<style scoped>
@import '@/styles/content.css';

.record-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
  cursor: pointer;
}
.record-cell:active {
  background: #F0F8FA;
}
.record-summary {
  font-size: 24rpx;
  color: #8C7E74;
  margin-right: 12rpx;
}

/* ─── 弹窗 ─── */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 999;
}
.detail-panel {
  width: 100%;
  max-height: 75vh;
  background: #FFF;
  border-radius: 24rpx 24rpx 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 32rpx 4rpx;
}
.detail-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #2D2A26;
}
.detail-close {
  font-size: 36rpx;
  color: #C4B8AE;
  padding: 8rpx;
}
.detail-class {
  font-size: 24rpx;
  color: #0E7490;
  padding: 0 32rpx 20rpx;
}
.detail-list {
  padding: 0 32rpx 48rpx;
}
.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #F5F0ED;
}
.detail-student {
  font-size: 28rpx;
  color: #2D2A26;
}
.detail-status {
  font-size: 24rpx;
  padding: 4rpx 16rpx;
  border-radius: 10rpx;
  font-weight: 500;
}
.s-present { background: #D1FAE5; color: #065F46; }
.s-late { background: #FEF3C7; color: #92400E; }
.s-leave { background: #F3F4F6; color: #6B7280; }
.s-absent { background: #FEE2E2; color: #991B1B; }
</style>
