<template>
  <view class="container">
    <text class="title">学情查看</text>

    <view class="tab-bar">
      <text :class="tab === 0 ? 'tab-active' : ''" @click="tab = 0">考勤</text>
      <text :class="tab === 1 ? 'tab-active' : ''" @click="tab = 1">作业</text>
      <text :class="tab === 2 ? 'tab-active' : ''" @click="tab = 2">评语</text>
    </view>

    <!-- 考勤 -->
    <view v-if="tab === 0 && attendances.length">
      <view v-for="a in attendances" :key="a.id" class="card">
        <text class="card-label">课次{{ a.lessonId }} · {{ attStatus(a.status) }}</text>
        <text class="card-sub">扣课时: {{ a.deductLessons || 0 }}</text>
        <text class="card-time">{{ a.checkTime }}</text>
      </view>
    </view>
    <text v-if="tab === 0 && !attendances.length" class="empty">暂无考勤记录</text>

    <!-- 作业 -->
    <view v-if="tab === 1 && homeworks.length">
      <view v-for="h in homeworks" :key="h.id" class="card">
        <text class="card-label">{{ h.content }}</text>
        <text class="card-time">{{ h.createTime }}</text>
      </view>
    </view>
    <text v-if="tab === 1 && !homeworks.length" class="empty">暂无作业</text>

    <!-- 评语 -->
    <view v-if="tab === 2 && records.length">
      <view v-for="r in records" :key="r.id" class="card">
        <text class="card-label">老师评语: {{ r.teacherComment || '无' }}</text>
        <text v-if="r.growthTag" class="card-tag">#{{ r.growthTag }}</text>
        <text class="card-time">{{ r.createTime }}</text>
      </view>
    </view>
    <text v-if="tab === 2 && !records.length" class="empty">暂无评语</text>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, getDefaultStudentId } from '@/utils/request'

const tab = ref(0)
const attendances = ref<any[]>([])
const homeworks = ref<any[]>([])
const records = ref<any[]>([])
const studentId = ref(getDefaultStudentId())

function attStatus(s: number) { return { 1: '到课', 2: '迟到', 3: '请假', 4: '缺勤' }[s] || s }

async function loadData() {
  if (!studentId.value) return
  try {
    const aR = await api({ url: `/api/parent/students/${studentId.value}/attendance` })
    attendances.value = aR.data || []
    const lR = await api({ url: `/api/parent/students/${studentId.value}/learning-records` })
    records.value = lR.data || []
  } catch {}
}

onMounted(loadData)
</script>

<style scoped>
.container { padding: 20rpx }
.title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 20rpx }
.tab-bar { display: flex; gap: 32rpx; margin-bottom: 24rpx }
.tab-bar text { font-size: 28rpx; color: #999; padding: 8rpx 0 }
.tab-active { color: #07c160; border-bottom: 4rpx solid #07c160; font-weight: bold }
.card { background: #f5f7fa; padding: 20rpx; border-radius: 12rpx; margin-bottom: 16rpx }
.card-label { font-size: 28rpx; font-weight: bold; display: block }
.card-sub { font-size: 26rpx; color: #666; margin-top: 6rpx }
.card-time { font-size: 24rpx; color: #999; margin-top: 4rpx }
.card-tag { font-size: 24rpx; color: #07c160; margin-top: 4rpx }
.empty { font-size: 28rpx; color: #999; text-align: center; margin-top: 80rpx }
</style>
