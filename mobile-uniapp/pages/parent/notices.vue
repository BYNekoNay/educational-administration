<template>
  <view class="page">
    <view class="header">消息通知</view>
    <view class="list">
      <view v-for="item in notices" :key="item.id" class="notice-item" @click="showDetail(item)">
        <view class="notice-top">
          <text class="notice-title">{{ item.title }}</text>
          <text class="notice-tag" :class="tagClass(item.noticeType)">{{ typeLabel(item.noticeType) }}</text>
        </view>
        <text class="notice-time">{{ item.publishTime || item.createTime }}</text>
      </view>
    </view>
    <view v-if="notices.length === 0" class="empty">暂无通知</view>

    <view v-if="detail" class="overlay" @click="detail = null">
      <view class="detail-card" @click.stop>
        <text class="detail-title">{{ detail.title }}</text>
        <text class="detail-content">{{ detail.content }}</text>
        <text class="detail-time">{{ detail.publishTime }}</text>
        <button class="close-btn" @click="detail = null">关闭</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '@/utils/request'

const notices = ref<any[]>([])
const detail = ref<any>(null)

function typeLabel(t: number) {
  const map: Record<number, string> = { 1: '调课', 2: '上课提醒', 3: '考级', 4: '公告' }
  return map[t] || '通知'
}
function tagClass(t: number) {
  const map: Record<number, string> = { 1: 'tag-warn', 2: 'tag-primary', 3: 'tag-info', 4: 'tag-default' }
  return map[t] || 'tag-default'
}

function showDetail(item: any) { detail.value = item }

onMounted(async () => {
  try {
    const res = await api({ url: '/api/notices?pageNum=1&pageSize=50' })
    notices.value = res.data.records || []
  } catch {}
})
</script>

<style scoped>
.page { padding: 20px; }
.header { font-size: 20px; font-weight: bold; text-align: center; margin-bottom: 20px; }
.list { background: #fff; border-radius: 12px; overflow: hidden; }
.notice-item { padding: 16px; border-bottom: 1px solid #f0f0f0; }
.notice-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.notice-title { font-size: 15px; font-weight: 500; flex: 1; }
.notice-time { font-size: 12px; color: #999; }
.notice-tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; }
.tag-warn { background: #fdf6ec; color: #e6a23c; }
.tag-primary { background: #ecf5ff; color: #409eff; }
.tag-info { background: #f4f4f5; color: #909399; }
.tag-default { background: #f0f9eb; color: #67c23a; }
.empty { text-align: center; color: #999; padding: 40px; }
.overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.5); display: flex; justify-content: center; align-items: center; z-index: 999; }
.detail-card { background: #fff; border-radius: 12px; padding: 24px; width: 85%; max-width: 360px; }
.detail-title { font-size: 18px; font-weight: bold; display: block; margin-bottom: 16px; }
.detail-content { font-size: 15px; color: #333; display: block; margin-bottom: 16px; line-height: 1.6; }
.detail-time { font-size: 12px; color: #999; display: block; margin-bottom: 16px; }
.close-btn { background: #409eff; color: #fff; border: none; border-radius: 6px; padding: 8px 0; font-size: 14px; text-align: center; }
</style>
