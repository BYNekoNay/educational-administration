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
          <text class="cell-title">{{ lesson.lessonDate }} {{ lesson.startTime?.slice(0,5) }}-{{ lesson.endTime?.slice(0,5) }}</text>
          <text class="cell-desc">{{ lesson.courseName || '' }}{{ lesson.courseName && lesson.className ? ' · ' : '' }}{{ lesson.className || '' }}</text>
        </view>
        <view class="cell-footer">
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>
    <view v-if="lessons.length === 0" class="empty-state"><text>暂无课次</text></view>

    <!-- 弹窗 -->
    <view v-if="currentLesson" class="overlay" @click="currentLesson = null">
      <view class="mgmt-panel" @click.stop>
        <view class="mgmt-header">
          <text class="mgmt-title">{{ currentLesson.lessonDate }} {{ currentLesson.startTime?.slice(0,5) }}-{{ currentLesson.endTime?.slice(0,5) }}</text>
          <text class="mgmt-close" @click="currentLesson = null">✕</text>
        </view>
        <text class="mgmt-class">{{ currentLesson.courseName || '' }}{{ currentLesson.courseName && currentLesson.className ? ' · ' : '' }}{{ currentLesson.className || '' }}</text>

        <view class="tab-bar">
          <text :class="['tab-item', tab === 0 ? 'active' : '']" @click="tab = 0">作业</text>
          <text :class="['tab-item', tab === 1 ? 'active' : '']" @click="tab = 1">评语</text>
        </view>

        <!-- Homework -->
        <scroll-view v-if="tab === 0" scroll-y class="panel-body" :style="{ maxHeight: '45vh' }">
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
        </scroll-view>

        <!-- Comments -->
        <scroll-view v-if="tab === 1" scroll-y class="panel-body" :style="{ maxHeight: '45vh' }">
          <view v-if="students.length === 0" class="empty-state"><text>暂无学员</text></view>
          <view v-if="students.length > 0">
            <view class="student-section" v-for="s in students" :key="s.studentId">
              <text class="student-label">{{ s.studentName || '学员' + s.studentId }}</text>
              <view class="input-row">
                <input v-model="comments[s.studentId]" placeholder="输入评语..." class="wx-input" />
              </view>
              <view class="input-row">
                <input v-model="tags[s.studentId]" placeholder="成长标签..." class="wx-input" />
              </view>
            </view>
          </view>
          <view class="panel-actions">
            <button class="btn-primary" @click="submitRecords">批量保存评语</button>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, downloadProtectedFile, uploadFile } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'

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
  comments.value = {}
  tags.value = {}
  students.value = []
  homeworks.value = []
  tab.value = 0
  const lessonId = lesson.id
  try {
    const [hR, sR, rR] = await Promise.all([
      api({ url: `/api/teacher/homeworks/${lessonId}` }),
      api({ url: `/api/teacher/lessons/${lessonId}/students` }),
      api({ url: `/api/teacher/lessons/${lessonId}/learning-records` })
    ])
    if (currentLesson.value?.id !== lessonId) return
    const prefills = {}
    const preTags = {}
    ;(rR.data || []).forEach(r => {
      if (r.studentId == null) return
      prefills[r.studentId] = r.teacherComment || ''
      preTags[r.studentId] = r.growthTag || ''
    })
    comments.value = prefills
    tags.value = preTags
    homeworks.value = await attachProtectedUrls(hR.data || [])
    if (currentLesson.value?.id !== lessonId) return
    students.value = sR.data || []
  } catch (e) {
    if (currentLesson.value?.id !== lessonId) return
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载失败'), icon: 'none' })
  }
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
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '发布失败'), icon: 'none' })
  }
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
    const res = await api({ url: `/api/teacher/lessons/${currentLesson.value.id}/learning-records`, method: 'POST', data: list })
    const created = (res.data || []).length
    const skipped = list.length - created
    if (created > 0 && skipped > 0) {
      uni.showToast({ title: `新增${created}条评语；${skipped}条已存在，未覆盖`, icon: 'none' })
    } else if (created === 0) {
      uni.showToast({ title: '评语均已存在，未保存新内容（暂不支持修改）', icon: 'none' })
    } else {
      uni.showToast({ title: '保存成功' })
    }
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '保存失败'), icon: 'none' })
  }
}

onMounted(async () => {
  try {
    const r = await api({ url: '/api/teacher/lessons?pageNum=1&pageSize=200' })
    const all = r.data?.records || []
    const today = new Date().toISOString().slice(0, 10)
    // 今日待上课 + 历史已完成
    lessons.value = all
      .filter(l => l.lessonDate <= today)
      .filter(l => Number(l.status) === 1 || Number(l.status) === 2)
      .slice(0, 50)
  } catch (e) {
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载课表失败'), icon: 'none' })
  }
})
</script>

<style scoped>
@import '@/styles/content.css';

.lesson-cell {
  min-height: auto;
  padding: 24rpx 32rpx;
  cursor: pointer;
}
.lesson-cell:active { background: #F0F8FA; }

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 999;
}
.mgmt-panel {
  width: 100%;
  max-height: 85vh;
  background: #FFF;
  border-radius: 24rpx 24rpx 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.mgmt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx 32rpx 4rpx;
}
.mgmt-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #2D2A26;
}
.mgmt-close {
  font-size: 36rpx;
  color: #C4B8AE;
  padding: 8rpx;
}
.mgmt-class {
  font-size: 24rpx;
  color: #0E7490;
  padding: 0 32rpx 8rpx;
}

.panel-body { padding: 0 32rpx; }

.hw-item {
  background: #F7F7F7;
  padding: 24rpx;
  border-radius: 8rpx;
  margin-bottom: 16rpx;
}
.hw-content { font-size: 28rpx; color: #333; display: block; line-height: 1.5; }
.hw-time { font-size: 24rpx; color: #888; margin-top: 12rpx; display: block; }
.hw-attachment { width: 100%; height: 300rpx; margin-top: 16rpx; border-radius: 8rpx; }
.attachment-btn { margin-top: 16rpx; }
.input-section { margin-top: 24rpx; }
.panel-actions { margin-top: 32rpx; padding-bottom: 48rpx; }

.student-section { padding: 20rpx 0; border-bottom: 1rpx solid #F5F0ED; }
.student-label { font-size: 28rpx; font-weight: 600; color: #2D2A26; display: block; margin-bottom: 12rpx; }
.input-row { margin-bottom: 12rpx; }
.input-row:last-child { margin-bottom: 0; }
</style>
