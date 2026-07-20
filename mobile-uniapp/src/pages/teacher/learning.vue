<template>
  <view class="page-wrap">
    <text class="page-title">学情管理</text>
    <text class="page-sub">作业发布与成长点评</text>

    <!-- Lesson list -->
    <view v-if="lessons.length > 0" class="cell-group">
      <view
        class="cell lesson-cell"
        v-for="lesson in lessons"
        :key="lesson.id"
        @click="openLesson(lesson)"
      >
        <view class="cell-body">
          <text class="cell-title">{{ lesson.lessonDate }}</text>
          <text class="cell-desc">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
          <text class="cell-desc">班级{{ lesson.classId }}</text>
        </view>
        <view class="cell-footer">
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>
    <view v-if="lessons.length === 0" class="empty-state"><text>暂无课次</text></view>

    <!-- Management panel -->
    <view v-if="currentLesson" class="mgmt-panel">
      <view class="mgmt-header">
        <text class="mgmt-title">{{ currentLesson.lessonDate }} 管理</text>
      </view>

      <view class="tab-bar">
        <text :class="['tab-item', tab === 0 ? 'active' : '']" @click="tab = 0">作业</text>
        <text :class="['tab-item', tab === 1 ? 'active' : '']" @click="tab = 1">评语</text>
      </view>

      <!-- Homework panel -->
      <view v-if="tab === 0" class="panel-body">
        <view v-if="homeworks.length === 0" class="empty-state"><text>暂无已发布作业</text></view>
        <view v-for="h in homeworks" :key="h.id" class="hw-item">
          <text class="hw-content">{{ h.content }}</text>
          <image v-if="h.attachmentDisplayUrl" :src="h.attachmentDisplayUrl" mode="aspectFit" class="hw-attachment" />
          <text class="hw-time">{{ h.createTime }}</text>
        </view>
        <view class="input-section">
          <textarea v-model="hwContent" placeholder="输入作业内容..." class="wx-textarea" />
          <button class="btn-secondary attachment-btn" @click="chooseAttachment">{{ attachmentUrl ? '已选择附件' : '上传作业图片' }}</button>
        </view>
        <view class="panel-actions">
          <button class="btn-primary" @click="publishHomework">发布作业</button>
        </view>
      </view>

      <!-- Comment panel -->
      <view v-if="tab === 1" class="panel-body">
        <view v-if="students.length === 0" class="empty-state"><text>暂无学员</text></view>
        <view class="cell-group" v-if="students.length > 0">
          <view class="cell student-section" v-for="s in students" :key="s.studentId">
            <view class="student-section-inner">
              <text class="student-label">{{ s.studentName || `学员${s.studentId}` }}</text>
              <view class="input-row">
                <input v-model="comments[s.studentId]" placeholder="输入评语..." class="wx-input" />
              </view>
              <view class="input-row">
                <input v-model="tags[s.studentId]" placeholder="成长标签..." class="wx-input" />
              </view>
            </view>
          </view>
        </view>
        <view class="panel-actions">
          <button class="btn-primary" @click="submitRecords">批量保存评语</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, downloadProtectedFile, uploadFile } from '@/utils/request'

const lessons = ref([])
const currentLesson = ref(null)
const tab = ref(0)
const homeworks = ref([])
const hwContent = ref('')
const attachmentUrl = ref('')
const students = ref([])
const comments = ref({})
const tags = ref({})

async function openLesson(lesson) {
  currentLesson.value = lesson
  try {
    const [hR, sR] = await Promise.all([
      api({ url: `/api/teacher/homeworks/${lesson.id}` }),
      api({ url: `/api/teacher/lessons/${lesson.id}/students` })
    ])
    homeworks.value = await attachProtectedUrls(hR.data || [])
    students.value = sR.data || []
  } catch (e) { uni.showToast({ title: '加载失败', icon: 'none' }) }
}

async function attachProtectedUrls(items) {
  return Promise.all(items.map(async item => ({
    ...item,
    attachmentDisplayUrl: item.attachmentUrl
      ? await downloadProtectedFile(item.attachmentUrl).catch(() => '')
      : ''
  })))
}

async function publishHomework() {
  if (!hwContent.value && !attachmentUrl.value) return uni.showToast({ title: '请输入作业内容或上传附件', icon: 'none' })
  try {
    await api({
      url: `/api/teacher/lessons/${currentLesson.value.id}/homeworks`,
      method: 'POST',
      data: { content: hwContent.value, attachmentUrl: attachmentUrl.value || null }
    })
    uni.showToast({ title: '发布成功' })
    hwContent.value = ''
    attachmentUrl.value = ''
    const r = await api({ url: `/api/teacher/homeworks/${currentLesson.value.id}` })
    homeworks.value = await attachProtectedUrls(r.data || [])
  } catch (e) { uni.showToast({ title: '发布失败', icon: 'none' }) }
}

function chooseAttachment() {
  uni.chooseImage({
    count: 1,
    success: async (result) => {
      try {
        attachmentUrl.value = await uploadFile(result.tempFilePaths[0])
        uni.showToast({ title: '附件上传成功', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: e.message || '附件上传失败', icon: 'none' })
      }
    }
  })
}

async function submitRecords() {
  const list = (students.value || []).map(s => ({
    studentId: s.studentId,
    teacherComment: comments.value[s.studentId] || '',
    growthTag: tags.value[s.studentId] || ''
  })).filter(r => r.teacherComment || r.growthTag)
  if (!list.length) return uni.showToast({ title: '请输入评语或标签', icon: 'none' })
  try {
    await api({ url: `/api/teacher/lessons/${currentLesson.value.id}/learning-records`, method: 'POST', data: list })
    uni.showToast({ title: '保存成功' })
    comments.value = {}
    tags.value = {}
  } catch (e) { uni.showToast({ title: '保存失败', icon: 'none' }) }
}

onMounted(async () => {
  try {
    const r = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=50' })
    lessons.value = r.data?.records || []
  } catch (e) { uni.showToast({ title: '加载课表失败', icon: 'none' }) }
})
</script>

<style scoped>
@import '@/styles/content.css';

.lesson-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
}

.mgmt-panel {
  margin-top: 32rpx;
}
.mgmt-header {
  padding: 24rpx 32rpx 0;
}
.mgmt-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #000;
}

.panel-body {
  padding: 0 32rpx;
}

/* Homework items */
.hw-item {
  background: #F7F7F7;
  padding: 24rpx;
  border-radius: 8rpx;
  margin-bottom: 16rpx;
}
.hw-content {
  font-size: 28rpx;
  color: #333;
  display: block;
  line-height: 1.5;
}
.hw-time {
  font-size: 24rpx;
  color: #888;
  margin-top: 12rpx;
  display: block;
}
.hw-attachment { width: 100%; height: 300rpx; margin-top: 16rpx; border-radius: 8rpx; }
.attachment-btn { margin-top: 16rpx; }

.input-section {
  margin-top: 24rpx;
}

.panel-actions {
  margin-top: 32rpx;
  padding-bottom: 16rpx;
}

/* Student comment sections */
.student-section {
  min-height: auto;
  padding: 24rpx 32rpx;
}
.student-section-inner {
  width: 100%;
}
.student-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 16rpx;
}
.input-row {
  margin-bottom: 16rpx;
}
.input-row:last-child {
  margin-bottom: 0;
}
</style>
