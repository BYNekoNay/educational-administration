<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />
    <text class="page-title">在线报名</text>
    <text class="page-sub">浏览并选择课程</text>

    <text class="section-header">可报名课程</text>

    <view v-if="courses.length === 0" class="empty-state"><text>暂无可用课程</text></view>

    <view class="cell-group">
      <view
        v-for="course in courses"
        :key="course.id"
        class="cell"
        @click="openSignup(course)"
      >
        <view class="cell-body">
          <view class="course-top">
            <text class="course-name">{{ course.name }}</text>
            <text class="course-price">¥{{ course.price }}</text>
          </view>
          <view class="course-meta">
            <text class="tag tag-info">{{ course.category }}</text>
            <text class="course-info">{{ course.totalLessons }}课时 · {{ course.lessonDuration }}分钟/节</text>
          </view>
        </view>
        <view class="cell-footer">
          <view class="cell-arrow"></view>
        </view>
      </view>
    </view>

    <!-- Action Sheet for enrollment confirmation -->
    <view v-if="showSheet" class="action-sheet-mask" @click="closePopup"></view>
    <view v-if="showSheet" class="action-sheet">
      <view class="action-sheet-header">确认报名</view>
      <view class="sheet-body">
        <view class="sheet-info">
          <text class="sheet-label">课程</text>
          <text class="sheet-value">{{ selectedCourse?.name }}</text>
        </view>
        <view class="sheet-info">
          <text class="sheet-label">课时</text>
          <text class="sheet-value">{{ selectedCourse?.totalLessons }}节 · ¥{{ selectedCourse?.price }}</text>
        </view>
        <view class="sheet-input-wrap">
          <input
            class="wx-input"
            v-model="classId"
            type="number"
            placeholder="意向班级ID（选填）"
          />
        </view>
        <button class="btn-primary" @click="handleSubmit" :disabled="submitting">
          {{ submitting ? '提交中...' : '确认报名' }}
        </button>
      </view>
      <view class="action-sheet-cancel" @click="closePopup">取消</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api, getCurrentStudentId } from '@/utils/request'
import StudentSwitcher from '@/components/StudentSwitcher.vue'

const courses = ref([])
const selectedCourse = ref(null)
const classId = ref('')
const submitting = ref(false)
const showSheet = ref(false)
const studentId = ref(getCurrentStudentId())

function onStudentChange(id) {
  studentId.value = id
}

async function fetchCourses() {
  try { const res = await api({ url: '/api/parent/courses' }); courses.value = res.data || [] }
  catch { uni.showToast({ title: '加载失败', icon: 'none' }) }
}

function openSignup(course) { selectedCourse.value = course; classId.value = ''; showSheet.value = true }
function closePopup() { selectedCourse.value = null; showSheet.value = false }

async function handleSubmit() {
  if (!selectedCourse.value) return
  if (!studentId.value) { uni.showToast({ title: '未找到学员', icon: 'none' }); return }
  submitting.value = true
  try {
    await api({ url: '/api/parent/enrollments', method: 'POST', data: { studentId: studentId.value, courseId: selectedCourse.value.id, classId: classId.value ? parseInt(classId.value) : null } })
    uni.showToast({ title: '报名成功，等待审核', icon: 'success' }); selectedCourse.value = null; showSheet.value = false
  } catch { uni.showToast({ title: '报名失败', icon: 'none' }) }
  finally { submitting.value = false }
}

onMounted(fetchCourses)
</script>

<style scoped>
@import '@/styles/content.css';

.course-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.course-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
  flex: 1;
}

.course-price {
  font-size: 34rpx;
  font-weight: 700;
  color: #FA5151;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.course-meta {
  display: flex;
  align-items: center;
}

.course-info {
  font-size: 24rpx;
  color: #888;
  margin-left: 16rpx;
}

.sheet-body {
  background: #FFF;
  padding: 32rpx;
}

.sheet-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.sheet-label {
  font-size: 28rpx;
  color: #888;
}

.sheet-value {
  font-size: 28rpx;
  color: #333;
  font-weight: 500;
}

.sheet-input-wrap {
  margin: 24rpx 0;
}
</style>
