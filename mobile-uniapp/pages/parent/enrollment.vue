<template>
  <view class="container">
    <text class="title">在线报名</text>
    <view v-if="courses.length === 0" style="text-align: center; padding: 80rpx; color: #999">
      <text>暂无可用课程</text>
    </view>
    <view class="course-card" v-for="course in courses" :key="course.id">
      <view class="card-left">
        <text class="course-name">{{ course.name }}</text>
        <text class="course-info">{{ course.category }} · {{ course.totalLessons }}课时 · {{ course.lessonDuration }}分钟/节</text>
      </view>
      <view class="card-right">
        <text class="price">¥{{ course.price }}</text>
        <button class="signup-btn" @click="openSignup(course)">立即报名</button>
      </view>
    </view>

    <!-- 报名确认弹窗 -->
    <uni-popup ref="popup" type="bottom">
      <view class="popup-content">
        <text class="popup-title">确认报名</text>
        <view class="popup-info">
          <text>课程：{{ selectedCourse?.name }}</text>
          <text>分类：{{ selectedCourse?.category }}</text>
          <text>课时：{{ selectedCourse?.totalLessons }}节</text>
          <text>价格：¥{{ selectedCourse?.price }}</text>
        </view>
        <view class="popup-form">
          <text class="label">意向班级ID</text>
          <input class="input" v-model="classId" type="number" placeholder="如不确定可不填" />
        </view>
        <button class="confirm-btn" @click="handleSubmit" :loading="submitting">确认报名</button>
        <button class="cancel-btn" @click="closePopup">取消</button>
      </view>
    </uni-popup>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const BASE_URL = 'http://localhost:8080/api'
const courses = ref([])
const selectedCourse = ref(null)
const classId = ref('')
const submitting = ref(false)
const popup = ref(null)

function getToken() {
  return uni.getStorageSync('token') || ''
}

async function fetchCourses() {
  try {
    const token = getToken()
    if (!token) { uni.showToast({ title: '请先登录', icon: 'none' }); return }
    const res = await uni.request({
      url: `${BASE_URL}/parent/courses`,
      header: { Authorization: `Bearer ${token}` }
    })
    if (res.data.code === 0) courses.value = res.data.data
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
}

function openSignup(course) {
  selectedCourse.value = course
  classId.value = ''
  popup.value?.open?.() || (popup.value = { open: () => {} })
}

function closePopup() {
  selectedCourse.value = null
}

async function handleSubmit() {
  if (!selectedCourse.value) return
  submitting.value = true
  try {
    const token = getToken()
    const res = await uni.request({
      url: `${BASE_URL}/parent/enrollments`,
      method: 'POST',
      header: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {
        studentId: 1,
        courseId: selectedCourse.value.id,
        classId: classId.value ? parseInt(classId.value) : null
      }
    })
    if (res.data.code === 0) {
      uni.showToast({ title: '报名成功，等待审核', icon: 'success' })
      selectedCourse.value = null
    } else {
      uni.showToast({ title: res.data.message || '报名失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '网络错误', icon: 'none' })
  } finally { submitting.value = false }
}

onMounted(fetchCourses)
</script>

<style scoped>
.container { padding: 20rpx; }
.title { font-size: 36rpx; font-weight: bold; display: block; margin-bottom: 24rpx; }
.course-card {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.06);
}
.card-left { flex: 1; }
.course-name { font-size: 30rpx; font-weight: bold; color: #333; display: block; }
.course-info { font-size: 24rpx; color: #999; margin-top: 8rpx; display: block; }
.card-right { text-align: right; }
.price { font-size: 32rpx; font-weight: bold; color: #e74c3c; display: block; }
.signup-btn {
  margin-top: 10rpx; padding: 8rpx 24rpx; font-size: 24rpx;
  background: #409eff; color: #fff; border-radius: 8rpx; border: none;
}
.popup-content {
  background: #fff; border-radius: 24rpx 24rpx 0 0; padding: 40rpx;
}
.popup-title { font-size: 32rpx; font-weight: bold; text-align: center; display: block; margin-bottom: 24rpx; }
.popup-info { margin-bottom: 24rpx; }
.popup-info text { display: block; font-size: 26rpx; color: #666; margin-bottom: 8rpx; }
.label { font-size: 26rpx; color: #333; display: block; margin-bottom: 8rpx; }
.input { height: 80rpx; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 0 16rpx; margin-bottom: 24rpx; font-size: 26rpx; }
.confirm-btn {
  width: 100%; height: 88rpx; line-height: 88rpx; background: #409eff;
  color: #fff; border-radius: 12rpx; font-size: 30rpx; margin-bottom: 16rpx;
}
.cancel-btn {
  width: 100%; height: 88rpx; line-height: 88rpx; background: #f0f0f0;
  color: #666; border-radius: 12rpx; font-size: 30rpx;
}
</style>
