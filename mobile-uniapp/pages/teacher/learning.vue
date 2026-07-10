<template>
  <view class="container">
    <text class="title">学情管理</text>

    <view class="lesson-list">
      <view v-for="lesson in lessons" :key="lesson.id" class="lesson-card" @click="openLesson(lesson)">
        <text class="lesson-date">{{ lesson.lessonDate }}</text>
        <text class="lesson-time">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
        <text class="lesson-badge">班级{{ lesson.classId }}</text>
      </view>
    </view>

    <view v-if="currentLesson" class="panel">
      <text class="sub-title">{{ currentLesson.lessonDate }} 管理</text>

      <view class="tab-bar">
        <text :class="tab === 0 ? 'tab-active' : ''" @click="tab = 0">作业</text>
        <text :class="tab === 1 ? 'tab-active' : ''" @click="tab = 1">评语</text>
      </view>

      <!-- 作业面板 -->
      <view v-if="tab === 0">
        <view v-for="h in homeworks" :key="h.id" class="item-card">
          <text class="item-content">{{ h.content }}</text>
          <text class="item-time">{{ h.createTime }}</text>
        </view>
        <textarea v-model="hwContent" placeholder="输入作业内容..." class="textarea" />
        <button class="submit-btn" @click="publishHomework">发布作业</button>
      </view>

      <!-- 学情评语面板 -->
      <view v-if="tab === 1">
        <view v-for="s in students" :key="s.studentId" class="student-section">
          <text class="student-label">学员{{ s.studentId }}</text>
          <input v-model="comments[s.studentId]" placeholder="输入评语..." class="input" />
          <input v-model="tags[s.studentId]" placeholder="成长标签..." class="input" />
        </view>
        <button class="submit-btn" @click="submitRecords">批量保存评语</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
const token = uni.getStorageSync('token')
const baseUrl = 'http://localhost:8080'
function api(url, opt = {}) {
  return uni.request({ url: baseUrl + url, header: { Authorization: 'Bearer ' + token }, ...opt }).then(r => r.data)
}

const lessons = ref([])
const currentLesson = ref(null)
const tab = ref(0)
const homeworks = ref([])
const hwContent = ref('')
const students = ref([])
const comments = ref({})
const tags = ref({})

async function openLesson(lesson) {
  currentLesson.value = lesson
  try {
    const [hR, sR] = await Promise.all([
      api(`/api/teacher/homeworks/${lesson.id}`),
      api(`/api/teacher/lessons/${lesson.id}/students`)
    ])
    homeworks.value = hR.data || []
    students.value = sR.data || []
  } catch (e) { uni.showToast({ title: '加载失败', icon: 'none' }) }
}

async function publishHomework() {
  if (!hwContent.value) return uni.showToast({ title: '请输入作业内容', icon: 'none' })
  try {
    await api(`/api/teacher/lessons/${currentLesson.value.id}/homeworks`, {
      method: 'POST', data: { content: hwContent.value }
    })
    uni.showToast({ title: '发布成功' })
    hwContent.value = ''
    const r = await api(`/api/teacher/homeworks/${currentLesson.value.id}`)
    homeworks.value = r.data || []
  } catch (e) { uni.showToast({ title: '发布失败', icon: 'none' }) }
}

async function submitRecords() {
  const list = (students.value || []).map(s => ({
    studentId: s.studentId,
    teacherComment: comments.value[s.studentId] || '',
    growthTag: tags.value[s.studentId] || ''
  })).filter(r => r.teacherComment || r.growthTag)
  if (!list.length) return uni.showToast({ title: '请输入评语或标签', icon: 'none' })
  try {
    await api(`/api/teacher/lessons/${currentLesson.value.id}/learning-records`, { method: 'POST', data: list })
    uni.showToast({ title: '保存成功' })
    comments.value = {}
    tags.value = {}
  } catch (e) { uni.showToast({ title: '保存失败', icon: 'none' }) }
}

onMounted(async () => {
  try {
    const r = await api('/api/teacher/lessons?pageNum=1&pageSize=50')
    lessons.value = r.data?.records || []
  } catch (e) { uni.showToast({ title: '加载课表失败', icon: 'none' }) }
})
</script>

<style scoped>
.container { padding: 20rpx }
.title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 20rpx }
.lesson-list { display: flex; flex-direction: column; gap: 16rpx }
.lesson-card { background: #f5f7fa; padding: 24rpx; border-radius: 12rpx }
.lesson-date { font-size: 30rpx; font-weight: bold }
.lesson-time, .lesson-badge { font-size: 26rpx; color: #666; margin-top: 4rpx }
.panel { margin-top: 30rpx }
.sub-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 16rpx }
.tab-bar { display: flex; gap: 32rpx; margin-bottom: 24rpx }
.tab-bar text { font-size: 28rpx; color: #999; padding: 8rpx 0 }
.tab-active { color: #07c160; border-bottom: 4rpx solid #07c160; font-weight: bold }
.item-card { background: #f5f7fa; padding: 20rpx; border-radius: 8rpx; margin-bottom: 12rpx }
.item-content { font-size: 28rpx; display: block }
.item-time { font-size: 24rpx; color: #999; margin-top: 8rpx }
.student-section { margin-bottom: 16rpx; background: #fff; padding: 16rpx; border-radius: 8rpx }
.student-label { font-size: 28rpx; font-weight: bold; margin-bottom: 8rpx; display: block }
.textarea, .input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 16rpx; font-size: 28rpx; margin-top: 12rpx; width: 100%; box-sizing: border-box }
.submit-btn { margin-top: 24rpx; background: #07c160; color: #fff }
</style>
