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
            <text class="tag" :class="tagClass(item.category)">{{ typeLabel(item.category) }}</text>
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
          <text class="dialog-time">{{ detail.publishTime || detail.createTime }}</text>
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
  const map = {
    1: '公告', 2: '调课', 3: '上课提醒', 4: '课时提醒', 5: '报名提醒', 6: '考级',
    SCHEDULE_CHANGE: '调课', CLASS_REMINDER: '上课提醒',
    EXAM_NOTICE: '考级', ANNOUNCEMENT: '公告', LESSON_EXPIRY: '到期提醒'
  }
  return map[t] || '通知'
}
function tagClass(t) {
  const map = {
    1: 'tag-success', 2: 'tag-warning', 3: 'tag-primary', 4: 'tag-warning', 5: 'tag-info', 6: 'tag-info',
    SCHEDULE_CHANGE: 'tag-warning', CLASS_REMINDER: 'tag-primary',
    EXAM_NOTICE: 'tag-info', ANNOUNCEMENT: 'tag-success', LESSON_EXPIRY: 'tag-warning'
  }
  return map[t] || 'tag-muted'
}

async function showDetail(item) {
  detail.value = item
  if (item.source === 'notification' && item.isRead === 0) {
    try {
      await api({ url: `/api/notifications/${item.id}/read`, method: 'PUT' })
      item.isRead = 1
    } catch (e) {}
  }
}

onMounted(async () => {
  try {
    const stored = uni.getStorageSync('userInfo') || {}
    const info = typeof stored === 'string' ? JSON.parse(stored) : stored
    const requests = [api({ url: '/api/notifications?pageNum=1&pageSize=50' })]
    if (info.roleCode === 'PARENT') requests.push(api({ url: '/api/parent/notices' }))
    const results = await Promise.all(requests)
    const personal = (results[0].data?.records || []).map(item => ({
      ...item, category: item.type, source: 'notification', publishTime: item.createTime
    }))
    const announcements = (results[1]?.data || []).map(item => ({
      ...item, category: item.noticeType || 'ANNOUNCEMENT', source: 'announcement'
    }))
    notices.value = [...personal, ...announcements].sort((a, b) =>
      String(b.publishTime || b.createTime || '').localeCompare(String(a.publishTime || a.createTime || ''))
    )
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
