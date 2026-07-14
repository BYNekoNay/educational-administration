<template>
  <view class="page-wrap">
    <text class="page-title">消息通知</text>
    <text class="page-sub">调课与公告提醒</text>

    <view v-if="notices.length === 0" class="empty-state"><text>暂无通知</text></view>

    <view v-else class="cell-group">
      <view
        v-for="item in notices"
        :key="item.id"
        class="cell"
        @click="showDetail(item)"
      >
        <view class="cell-body">
          <view class="notice-title-row">
            <text class="cell-title">{{ item.title }}</text>
            <text class="tag" :class="tagClass(item.noticeType)">{{ typeLabel(item.noticeType) }}</text>
          </view>
          <text class="cell-desc">{{ item.publishTime || item.createTime }}</text>
        </view>
        <view class="cell-footer">
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>

    <!-- Detail dialog -->
    <view v-if="detail" class="dialog-mask" @click="detail = null">
      <view class="dialog" @click.stop>
        <text class="dialog-title">{{ detail.title }}</text>
        <view class="dialog-content">
          <text>{{ detail.content }}</text>
          <text class="dialog-time">{{ detail.publishTime }}</text>
        </view>
        <view class="dialog-footer">
          <button class="dialog-btn dialog-btn-confirm" @click="detail = null">知道了</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/utils/request'

const notices = ref([])
const detail = ref(null)

function typeLabel(t) {
  const map = { 1: '调课', 2: '上课提醒', 3: '考级', 4: '公告' }
  return map[t] || '通知'
}
function tagClass(t) {
  const map = { 1: 'tag-warning', 2: 'tag-primary', 3: 'tag-info', 4: 'tag-success' }
  return map[t] || 'tag-muted'
}

function showDetail(item) { detail.value = item }

onMounted(async () => {
  try {
    const res = await api({ url: '/api/notices?pageNum=1&pageSize=50' })
    notices.value = res.data.records || []
  } catch (e) {
    console.warn('通知加载失败', e)
  }
})
</script>

<style scoped>
@import '@/styles/content.css';

.notice-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.dialog-content {
  text-align: left;
}

.dialog-content text {
  display: block;
}

.dialog-time {
  font-size: 24rpx;
  color: #BEBEBE;
  margin-top: 24rpx;
}
</style>
