<template>
  <view class="page-wrap">
    <!-- Warm gradient header -->
    <view class="header-gradient">
      <view class="page-title-area">
        <text class="page-title">我的课表</text>
        <text class="page-subtitle">{{ isParent ? '查看孩子的课程安排' : '查看我的授课安排' }}</text>
      </view>
    </view>

    <!-- Student switcher (parent only) -->
    <StudentSwitcher v-if="isParent" @change="onStudentChange" />

    <!-- 查看完整课表 -->
    <view class="quick-actions">
      <view class="action-card" @click="goSchedule">
        <text class="action-emoji">📅</text>
        <text class="action-label">查看完整课表</text>
        <text class="action-desc">{{ isParent ? '近期课程安排与调课信息' : '我的授课排班详情' }}</text>
      </view>
    </view>

    <view v-if="loading" class="empty-state"><text>加载中...</text></view>

    <template v-else>
      <!-- 今日概览 -->
      <view class="section-header">今日概览</view>
      <view class="cell-group">
        <view v-if="todayLessons.length === 0" class="cell">
          <view class="cell-body" style="text-align: center; padding: 40rpx 0;">
            <text class="cell-title" style="color: var(--text-secondary, #8C7E74)">今天没有课程安排</text>
          </view>
        </view>
        <view
          v-for="lesson in todayLessons"
          :key="lesson.id"
          class="cell lesson-cell"
          :class="{ 'lesson-cell-leave': lesson.leaveStatus === 2 }"
          @click="goSchedule"
        >
          <view class="cell-body">
            <view class="lesson-top">
              <text class="lesson-course">{{ lesson.courseName || '课程' }}</text>
              <text class="tag" :class="tagClass(lesson)">{{ statusText(lesson) }}</text>
            </view>
            <text class="lesson-meta">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
            <text class="lesson-sub">{{ lesson.className || '班级' }} · {{ lesson.classroomName || '教室' }}</text>
          </view>
        </view>
      </view>

      <!-- 未来7天 -->
      <view v-if="upcomingGroups.length > 0" class="section-header">未来 7 天</view>
      <view v-for="g in upcomingGroups" :key="g.date" class="day-group">
        <view class="day-header">
          <text class="day-date">{{ g.date }}</text>
          <text class="day-weekday">{{ weekday(g.date) }}</text>
        </view>
        <view class="cell-group">
          <view
            v-for="lesson in g.lessons"
            :key="lesson.id"
            class="cell lesson-cell"
            :class="{ 'lesson-cell-leave': lesson.leaveStatus === 2 }"
            @click="goSchedule"
          >
            <view class="cell-body">
              <view class="lesson-top">
                <text class="lesson-course">{{ lesson.courseName || '课程' }}</text>
                <text class="tag" :class="tagClass(lesson)">{{ statusText(lesson) }}</text>
              </view>
              <text class="lesson-meta">{{ lesson.startTime }} - {{ lesson.endTime }}</text>
              <text class="lesson-sub">{{ lesson.className || '班级' }} · {{ lesson.classroomName || '教室' }}</text>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import StudentSwitcher from '@/components/StudentSwitcher.vue'
import { api, getCurrentStudentId } from '@/utils/request'
import { getErrorMessage } from '@/utils/error'
import { filterUpcoming, groupLessonsByDate, isToday, todayPlusDays } from '@/utils/schedule-summary'

const isParent = ref(true)
const loading = ref(false)
const studentId = ref(getCurrentStudentId())
// 窗口内全部课次（今日 ~ 今日+7）
const lessons = ref([])

function onStudentChange(id) {
  studentId.value = id
  lessons.value = []
  loadSchedule()
}

const todayLessons = computed(() => lessons.value.filter(l => isToday(l.lessonDate)))
const upcomingGroups = computed(() => groupLessonsByDate(lessons.value).filter(g => g.date !== todayPlusDays(0)))

function weekday(d) {
  if (!d) return ''
  const [y, m, day] = String(d).substring(0, 10).split('-').map(Number)
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[new Date(y, m - 1, day).getDay()]
}
function isPast(d) {
  if (!d) return false
  const [y, m, day] = String(d).substring(0, 10).split('-').map(Number)
  const date = new Date(y, m - 1, day)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date < today
}
function statusText(lesson) {
  if (lesson.leaveStatus === 2) return '已请假'
  if (lesson.leaveStatus === 1) return '请假中'
  if (lesson.status === 3) return '已取消'
  if (lesson.status === 4) return '已调课'
  if (isPast(lesson.lessonDate)) return '已完成'
  if (isToday(lesson.lessonDate)) return '今日'
  return '待上课'
}
function tagClass(lesson) {
  if (lesson.leaveStatus === 2) return 'tag-leave'
  if (lesson.leaveStatus === 1) return 'tag-warning'
  if (lesson.status === 3) return 'tag-muted'
  if (lesson.status === 4) return 'tag-warning'
  if (isPast(lesson.lessonDate)) return 'tag-success'
  if (isToday(lesson.lessonDate)) return 'tag-primary'
  return 'tag-info'
}

async function loadSchedule() {
  loading.value = true
  try {
    const from = todayPlusDays(0)
    const to = todayPlusDays(7)
    let list = []
    if (isParent.value) {
      const sid = studentId.value
      if (!sid) {
        lessons.value = []
        return
      }
      const res = await api({ url: `/api/parent/students/${sid}/schedule` })
      if (sid !== studentId.value) return
      list = res.data || []
    } else {
      const res = await api({
        url: '/api/teacher/lessons',
        params: { dateFrom: from, dateTo: to, pageNum: 1, pageSize: 100 },
      })
      list = (res.data && res.data.records) || []
    }
    // 仅保留今日 ~ 未来 7 天窗口内的课次
    lessons.value = filterUpcoming(list, from, to)
  } catch (e) {
    // 业务/HTTP 错误 api() 已弹 toast（_handled），此处仅兜底未处理异常
    if (!e || !e._handled) uni.showToast({ title: getErrorMessage(e, '加载失败'), icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goSchedule() {
  const url = isParent.value ? '/pages/parent/schedule' : '/pages/teacher/schedule'
  uni.navigateTo({ url })
}

onMounted(() => {
  try {
    const stored = uni.getStorageSync('userInfo')
    const info = typeof stored === 'string' ? JSON.parse(stored) : stored
    isParent.value = info?.roleCode !== 'TEACHER'
  } catch (e) {}
})
// tabBar 页切走再切回不会重建，用 onShow 保证每次显示都刷新
onShow(() => { loadSchedule() })
</script>

<style scoped>
@import '@/styles/content.css';

.header-gradient {
  background: linear-gradient(135deg, #0E7490, #06B6D4);
  border-radius: 0 0 48rpx 48rpx;
  padding: 0 0 32rpx;
  min-height: 200rpx;
}
.page-title-area {
  padding: 48rpx 32rpx 24rpx;
}
.page-title {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  color: #FFFFFF;
}
.page-subtitle {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 8rpx;
}
.quick-actions {
  padding: 24rpx 24rpx 0;
}
.action-card {
  background: var(--bg-card, #FFFFFF);
  border-radius: 28rpx;
  padding: 40rpx 32rpx;
  text-align: center;
  box-shadow: var(--shadow-card, 0 4rpx 20rpx rgba(45,42,38,0.06));
  border: 1rpx solid var(--border, #E8E0DA);
}
.action-emoji {
  display: block;
  font-size: 64rpx;
  margin-bottom: 16rpx;
}
.action-label {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary, #2D2A26);
  margin-bottom: 8rpx;
}
.action-desc {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary, #8C7E74);
}

.lesson-cell { min-height: auto; padding: 24rpx 32rpx; }
.lesson-cell-leave { opacity: 0.6; }
.lesson-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}
.lesson-course {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
}
.lesson-meta {
  display: block;
  font-size: 26rpx;
  color: #4D4139;
  margin-bottom: 4rpx;
}
.lesson-sub {
  display: block;
  font-size: 24rpx;
  color: #8C7E74;
}

.day-group { margin-top: 8rpx; }
.day-header {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  padding: 8rpx 32rpx 12rpx;
}
.day-date {
  font-size: 26rpx;
  font-weight: 600;
  color: #0E7490;
}
.day-weekday {
  font-size: 22rpx;
  color: #8C7E74;
}

/* tag 样式沿用 content.css；此处补充 tag-leave/tag-info 兜底 */
.tag-leave { background: #F0FDF4; color: #059669; }
.tag-info { background: #ECFEFF; color: #0E7490; }
.tag-primary { background: #FFF7E6; color: #D97706; }
.tag-success { background: #ECFDF5; color: #10B981; }
.tag-warning { background: #FEF3C7; color: #B45309; }
.tag-muted { background: #F0F0F0; color: #888; }
.tag { display: inline-block; font-size: 22rpx; font-weight: 500; padding: 4rpx 16rpx; border-radius: 16rpx; }

.empty-state {
  text-align: center;
  padding: 80rpx 0;
}
</style>
