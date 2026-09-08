<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />
    <text class="page-title">学情查看</text>

    <!-- Tab bar -->
    <view class="tab-bar">
      <text class="tab-item" :class="{ active: tab === 0 }" @click="tab = 0">考勤</text>
      <text class="tab-item" :class="{ active: tab === 1 }" @click="tab = 1">作业</text>
      <text class="tab-item" :class="{ active: tab === 2 }" @click="tab = 2">评语</text>
    </view>

    <!-- 考勤 -->
    <template v-if="tab === 0">
      <view v-if="attendances.length === 0" class="empty-state"><text>暂无考勤记录</text></view>
      <view v-else class="cell-group">
        <view v-for="a in attendances" :key="a.id" class="cell">
          <view class="cell-body">
            <text class="cell-title">{{ a.lessonInfo || ('课次' + a.lessonId) }} · {{ attStatus(a.status) }}</text>
            <text class="cell-desc">扣课时: {{ a.deductLessons || 0 }} · {{ a.checkTime }}</text>
          </view>
          <view class="cell-footer">
            <text class="tag" :class="attTagClass(a.status)">{{ attStatus(a.status) }}</text>
          </view>
        </view>
      </view>
    </template>

    <!-- 作业 -->
    <template v-if="tab === 1">
      <view v-if="homeworks.length === 0" class="empty-state"><text>暂无作业</text></view>
      <view v-else class="cell-group">
        <view v-for="h in homeworks" :key="h.id" class="cell">
          <view class="cell-body">
            <text class="cell-title">{{ h.content }}</text>
            <image v-if="h.attachmentDisplayUrl" :src="h.attachmentDisplayUrl" mode="aspectFit" class="homework-image" />
            <text class="cell-desc">{{ h.createTime }}</text>
          </view>
        </view>
      </view>
    </template>

    <!-- 评语 -->
    <template v-if="tab === 2">
      <view v-if="records.length === 0" class="empty-state"><text>暂无评语</text></view>
      <view v-else class="cell-group">
        <view v-for="r in records" :key="r.id" class="cell">
          <view class="cell-body">
            <text class="cell-title">老师评语: {{ r.teacherComment || '无' }}</text>
            <view class="comment-meta">
              <text v-if="r.growthTag" class="tag tag-primary">#{{ r.growthTag }}</text>
              <text class="cell-desc">{{ r.createTime }}</text>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, downloadProtectedFile, getCurrentStudentId } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const tab = ref(0)
const attendances = ref([])
const homeworks = ref([])
const records = ref([])
const studentId = ref(getCurrentStudentId())

function onStudentChange(id) {
  studentId.value = id
  loadData()
}

function attStatus(s) { return { 1: '到课', 2: '迟到', 3: '请假', 4: '缺勤' }[s] || s }
function attTagClass(s) { return { 1: 'tag-success', 2: 'tag-warning', 3: 'tag-info', 4: 'tag-danger' }[s] || 'tag-muted' }

async function loadData() {
  if (!studentId.value) return
  const sid = studentId.value
  try {
    const [aR, lR, hR] = await Promise.all([
      api({ url: `/api/parent/students/${sid}/attendance?pageNum=1&pageSize=100` }),
      api({ url: `/api/parent/students/${sid}/learning-records` }),
      api({ url: `/api/parent/students/${sid}/homeworks` })
    ])
    if (sid !== studentId.value) return
    attendances.value = (aR.data && aR.data.records) || (aR.data || [])
    records.value = lR.data || []
    const hws = await Promise.all((hR.data || []).map(async item => ({
      ...item,
      attachmentDisplayUrl: item.attachmentUrl
        ? await downloadProtectedFile(item.attachmentUrl).catch(() => '')
        : ''
    })))
    if (sid !== studentId.value) return
    homeworks.value = hws
  } catch (e) {
    // request.js 已对业务/HTTP 错误弹过提示（_handled），此处仅兜底未处理异常
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载失败'), icon: 'none' })
  }
}

onMounted(loadData)
</script>

<style scoped>
@import '@/styles/content.css';

.comment-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 8rpx;
}
.homework-image { width: 100%; height: 300rpx; margin: 16rpx 0; border-radius: 8rpx; }
</style>
