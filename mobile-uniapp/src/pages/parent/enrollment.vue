<template>
  <view class="page-wrap">
    <StudentSwitcher @change="onStudentChange" />
    <text class="page-title">在线报名</text>
    <text class="page-sub">浏览课程并选择合适的开班</text>

    <text class="section-header">可报名课程</text>

    <view v-if="snapshotLoading" class="empty-state"><text>课程加载中...</text></view>
    <view v-else-if="snapshotLoadFailed" class="empty-state">
      <text>课程加载失败</text>
      <button class="retry-button" size="mini" @click="fetchEnrollmentSnapshot">重新加载</button>
    </view>
    <view v-else-if="!studentId" class="empty-state"><text>暂无关联学员</text></view>
    <view v-else-if="courses.length === 0" class="empty-state"><text>暂无可用课程</text></view>

    <view v-else class="cell-group">
      <view
        v-for="course in courses"
        :key="course.id"
        class="course-card"
        :class="{ 'course-card-active': expandedCourseId === course.id, 'course-card-enrolled': enrolledCourseIds.has(course.id) }"
      >
        <view class="cell" @click="toggleCourse(course)">
          <view class="cell-body">
            <view class="course-top">
              <view class="course-name-wrap">
                <view class="course-name-row">
                  <text class="course-name">{{ course.name }}</text>
                  <text v-if="enrolledCourseIds.has(course.id)" class="badge-enrolled">已报名</text>
                </view>
                <text v-if="expandedCourseId === course.id" class="expand-tip">点击收起</text>
              </view>
              <text class="course-price">¥{{ course.price }}</text>
            </view>
            <view class="course-meta">
              <text class="tag tag-info">{{ course.category }}</text>
              <text class="course-info">{{ course.totalLessons }}课时 · {{ course.lessonDuration }}分钟/节</text>
            </view>
            <view class="course-extra">
              <text class="extra-item">🎯 {{ getCategoryDesc(course.category) }}</text>
              <text class="extra-item" v-if="classCountMap[course.id] !== undefined">
                📚 {{ classCountMap[course.id] }} 个开班可选
              </text>
            </view>
          </view>
          <view class="cell-footer">
            <view class="cell-arrow" :class="{ 'cell-arrow-up': expandedCourseId === course.id }"></view>
          </view>
        </view>

        <!-- 展开：班级列表 -->
        <view v-if="expandedCourseId === course.id" class="class-list">
          <view v-if="!classList.length" class="class-loading"><text>暂无可报名班级</text></view>
          <view
            v-for="cls in classList"
            :key="cls.id"
            class="class-item"
            :class="{ 'class-item-full': isClassFull(cls), 'class-item-conflict': isClassConflict(cls) }"
            @click="selectClass(cls)"
          >
            <view class="class-row1">
              <text class="class-name">{{ cls.className }}</text>
              <text class="class-spots" :class="{ 'class-spots-full': isClassFull(cls), 'class-spots-conflict': isClassConflict(cls) }">
                {{ isClassConflict(cls) ? '⛔ 冲突' : spotsText(cls) }}
              </text>
            </view>
            <view class="class-row2">
              <text class="class-meta">👨‍🏫 {{ cls.teacherName }}</text>
              <text class="class-meta">🕐 {{ cls.scheduleSummary }}</text>
            </view>
            <view class="class-row3">
              <text v-if="cls.startDate" class="class-start">📅 {{ formatDate(cls.startDate) }} 开课</text>
              <text v-if="isClassFull(cls)" class="class-full-tip">已满</text>
              <text v-else-if="isClassConflict(cls)" class="class-conflict-desc">{{ getConflictText(cls) }}</text>
              <!-- maxStudentCount=0 表示不限名额（后端 null→0），直接相减会显示负数 -->
              <text v-else class="class-remaining">{{ cls.maxStudentCount > 0 ? '剩 ' + (cls.maxStudentCount - cls.currentStudentCount) + ' 个名额' : '名额不限' }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 报名确认弹层 -->
    <view v-if="showSheet" class="action-sheet-mask" @click="closePopup"></view>
    <view v-if="showSheet" class="action-sheet">
      <view class="action-sheet-header">确认报名</view>
      <view class="sheet-body">
        <view class="sheet-info">
          <text class="sheet-label">学员</text>
          <text class="sheet-value">{{ studentName }}</text>
        </view>
        <view class="sheet-info">
          <text class="sheet-label">课程</text>
          <text class="sheet-value">{{ selectedCourse?.name }}</text>
        </view>
        <view class="sheet-info" v-if="selectedClass">
          <text class="sheet-label">班级</text>
          <text class="sheet-value">{{ selectedClass.className }}</text>
        </view>
        <view class="sheet-info" v-if="selectedClass">
          <text class="sheet-label">教师</text>
          <text class="sheet-value">{{ selectedClass.teacherName }}</text>
        </view>
        <view class="sheet-info" v-if="selectedClass">
          <text class="sheet-label">课次</text>
          <text class="sheet-value">{{ selectedClass.scheduleSummary }}</text>
        </view>
        <view v-if="selectedClass && isClassConflict(selectedClass)" class="sheet-warning">
          <text class="sheet-warning-text">⚠️ {{ getConflictText(selectedClass) }}，报名将被拦截</text>
        </view>
        <view class="sheet-info">
          <text class="sheet-label">课时/价格</text>
          <text class="sheet-value">{{ selectedCourse?.totalLessons }}节 · ¥{{ selectedCourse?.price }}</text>
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
import { api, getCurrentStudentId, getMyStudents } from '@/utils/request'
import StudentSwitcher from '@/components/StudentSwitcher.vue'
import {
  createSnapshotRequestGuard,
  loadEnrollmentSnapshot,
} from './enrollment-snapshot'

const courses = ref([])
const classCountMap = ref({})      // courseId -> 开班数
const enrolledCourseIds = ref(new Set()) // 已报名的课程 ID 集合
const classesByCourseId = ref({})
const conflictsByCourseId = ref({})
const expandedCourseId = ref(null)
const classList = ref([])
const selectedCourse = ref(null)
const selectedClass = ref(null)
const submitting = ref(false)
const showSheet = ref(false)
const studentId = ref(getCurrentStudentId())
const studentName = ref('')
const snapshotVersionToken = ref('')
const snapshotExpiresAt = ref(null)
const snapshotLoading = ref(false)
const snapshotLoadFailed = ref(false)
const snapshotRequestGuard = createSnapshotRequestGuard()
/** classId → { className, conflictClassName, description } 时间冲突信息 */
const conflictMap = ref({})

const CATEGORY_DESC = {
  '美术': '培养绘画与审美能力，激发艺术创造力',
  '钢琴': '系统学习键盘演奏与乐理，提升音乐素养',
  '舞蹈': '塑造体态与节奏感，培养艺术表现力',
  '书法': '练习毛笔/硬笔书法，传承中华传统文化',
  '声乐': '训练发声技巧与歌曲演唱',
  '乐器': '器乐演奏入门到进阶',
}

function getCategoryDesc(cat) {
  return CATEGORY_DESC[cat] || '系统化教学，循序渐近提升专业能力'
}

function isClassFull(cls) {
  return cls.maxStudentCount > 0 && cls.currentStudentCount >= cls.maxStudentCount
}

/** 名额文案：maxStudentCount=0 为不限名额，不能渲染成 "3/0" */
function spotsText(cls) {
  return cls.maxStudentCount > 0 ? `${cls.currentStudentCount}/${cls.maxStudentCount}` : `${cls.currentStudentCount}人已报`
}

/** 班级是否与已报名班级有时间冲突 */
function isClassConflict(cls) {
  return !!conflictMap.value[cls.id]
}

/** 获取冲突描述文本 */
function getConflictText(cls) {
  const c = conflictMap.value[cls.id]
  return c ? `⛔ 与[${c.conflictClassName}]时间冲突：${c.description}` : ''
}

function formatDate(d) {
  if (!d) return ''
  // 处理 "YYYY-MM-DD" 或 LocalDate 字符串
  return typeof d === 'string' ? d.substring(0, 10) : d
}

function onStudentChange(id) {
  studentId.value = id
  refreshStudentName()
  clearSnapshotState()
  fetchEnrollmentSnapshot()
}

function refreshStudentName() {
  const all = getMyStudents()
  const s = all.find(x => (x.id || x.studentId) === studentId.value)
  studentName.value = s?.name || '未选择'
}

function clearSnapshotState() {
  courses.value = []
  classCountMap.value = {}
  enrolledCourseIds.value = new Set()
  classesByCourseId.value = {}
  conflictsByCourseId.value = {}
  snapshotVersionToken.value = ''
  snapshotExpiresAt.value = null
  expandedCourseId.value = null
  classList.value = []
  conflictMap.value = {}
  closePopup()
}

function applySnapshotState(state) {
  courses.value = state.courses
  classCountMap.value = state.classCountMap
  enrolledCourseIds.value = state.enrolledCourseIds
  classesByCourseId.value = state.classesByCourseId
  conflictsByCourseId.value = state.conflictsByCourseId
  snapshotVersionToken.value = state.versionToken
  snapshotExpiresAt.value = state.snapshotExpiresAt
}

async function fetchEnrollmentSnapshot() {
  const sid = studentId.value
  clearSnapshotState()
  snapshotLoadFailed.value = false
  if (!sid) {
    snapshotRequestGuard.invalidate()
    snapshotLoading.value = false
    return
  }
  snapshotLoading.value = true

  const result = await loadEnrollmentSnapshot({
    studentId: sid,
    guard: snapshotRequestGuard,
    fetchSnapshot: currentStudentId => api({
      url: `/api/parent/enrollments/snapshot?studentId=${currentStudentId}`,
    }),
    getCurrentStudentId: () => studentId.value,
    onSuccess: state => applySnapshotState(state),
    onError: error => {
      snapshotLoadFailed.value = true
      if (!error || !error._handled) {
        uni.showToast({ title: '课程加载失败', icon: 'none' })
      }
    },
  })
  if (result.status !== 'stale') {
    snapshotLoading.value = false
  }
}

function toggleCourse(course) {
  if (enrolledCourseIds.value.has(course.id)) {
    uni.showToast({ title: '已报名该课程，请查看报名记录', icon: 'none' })
    return
  }
  if (expandedCourseId.value === course.id) {
    expandedCourseId.value = null
    classList.value = []
    return
  }
  expandedCourseId.value = course.id
  classList.value = classesByCourseId.value[course.id] || []
  conflictMap.value = conflictsByCourseId.value[course.id] || {}
}

function selectClass(cls) {
  if (isClassFull(cls)) {
    uni.showToast({ title: '该班级已满，请选择其他班级', icon: 'none' })
    return
  }
  if (isClassConflict(cls)) {
    uni.showToast({ title: getConflictText(cls), icon: 'none', duration: 3000 })
    return
  }
  selectedCourse.value = courses.value.find(c => c.id === expandedCourseId.value)
  selectedClass.value = cls
  showSheet.value = true
}

function closePopup() {
  selectedCourse.value = null
  selectedClass.value = null
  showSheet.value = false
}

async function handleSubmit() {
  if (!selectedCourse.value || !selectedClass.value) return
  if (!studentId.value) { uni.showToast({ title: '未找到学员', icon: 'none' }); return }
  const expiresAt = snapshotExpiresAt.value ? Date.parse(snapshotExpiresAt.value) : NaN
  if (!snapshotVersionToken.value || (Number.isFinite(expiresAt) && expiresAt <= Date.now())) {
    uni.showToast({ title: '报名信息已过期，正在刷新', icon: 'none' })
    await fetchEnrollmentSnapshot()
    return
  }
  const sid = studentId.value
  const snapshotVersion = snapshotVersionToken.value
  submitting.value = true
  try {
    await api({
      url: '/api/parent/enrollments',
      method: 'POST',
      header: { 'If-Match': snapshotVersion },
      data: {
        studentId: sid,
        courseId: selectedCourse.value.id,
        classId: selectedClass.value.id,
      },
    })
    uni.showToast({ title: '报名成功，等待审核', icon: 'success' })
    closePopup()
    await fetchEnrollmentSnapshot()
  } catch (e) {
    // api() 已对业务/网络错误弹过具体提示（_handled），此处仅兜底未处理异常
    if (!e || !e._handled) uni.showToast({ title: (e && e.message) || '报名失败', icon: 'none' })
    if (sid === studentId.value && /快照|刷新/.test(e?.message || '')) {
      await fetchEnrollmentSnapshot()
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  refreshStudentName()
  fetchEnrollmentSnapshot()
})
</script>

<style scoped>
@import '@/styles/content.css';

.course-card {
  background: #FFF;
  border-radius: 16rpx;
  margin: 0 24rpx 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(45, 42, 38, 0.04);
  transition: all 0.2s;
}

.retry-button {
  margin-top: 20rpx;
  color: #0E7490;
  border-color: #0E7490;
  background: #FFF;
}
.course-card-enrolled {
  background: #F8FAF8;
  border: 2rpx solid #DFE6DF;
}
.course-card-enrolled .cell {
  opacity: 0.7;
  pointer-events: auto;  /* 允许点击展开但会被 toggleCourse 提示拦截 */
}
.course-card-active {
  box-shadow: 0 4rpx 20rpx rgba(14, 116, 144, 0.12);
}

.cell {
  display: flex;
  align-items: center;
  padding: 24rpx 28rpx;
}

.course-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12rpx;
}

.course-name-wrap {
  flex: 1;
  min-width: 0;
}

.course-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.course-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
  line-height: 1.4;
}

.badge-enrolled {
  font-size: 22rpx;
  font-weight: 600;
  color: #0E7490;
  background: #ECFEFF;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
  border: 1rpx solid #A5E2EB;
}

.expand-tip {
  display: block;
  font-size: 22rpx;
  color: #0E7490;
  margin-top: 4rpx;
}

.course-price {
  font-size: 36rpx;
  font-weight: 700;
  color: #FA5151;
  flex-shrink: 0;
  margin-left: 16rpx;
  line-height: 1.2;
}

.course-meta {
  display: flex;
  align-items: center;
  margin-bottom: 8rpx;
}

.course-info {
  font-size: 24rpx;
  color: #8C7E74;
  margin-left: 16rpx;
}

.course-extra {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-top: 4rpx;
}

.extra-item {
  font-size: 24rpx;
  color: #4D4139;
  line-height: 1.5;
}

.cell-arrow {
  width: 16rpx;
  height: 16rpx;
  border-right: 3rpx solid #B5ADA5;
  border-bottom: 3rpx solid #B5ADA5;
  transform: rotate(45deg);
  margin-left: 16rpx;
  transition: transform 0.25s;
}

.cell-arrow-up {
  transform: rotate(-135deg);
}

.class-list {
  background: #FAF8F5;
  border-top: 1rpx solid #F0ECE8;
  padding: 16rpx 0 20rpx;
}

.class-loading {
  text-align: center;
  padding: 32rpx;
  color: #8C7E74;
  font-size: 26rpx;
}

.class-item {
  margin: 0 20rpx 12rpx;
  padding: 20rpx 24rpx;
  background: #FFF;
  border-radius: 12rpx;
  border: 2rpx solid transparent;
  transition: all 0.2s;
}

.class-item:active:not(.class-item-full) {
  border-color: #0E7490;
  background: #ECFEFF;
}

.class-item-full {
  opacity: 0.5;
  pointer-events: none;
}

.class-row1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.class-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #2D2A26;
  flex: 1;
}

.class-spots {
  font-size: 24rpx;
  font-weight: 600;
  color: #0E7490;
  background: #ECFEFF;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.class-spots-full {
  color: #999;
  background: #F0F0F0;
}

.class-row2 {
  display: flex;
  gap: 24rpx;
  margin-bottom: 8rpx;
  flex-wrap: wrap;
}

.class-meta {
  font-size: 24rpx;
  color: #4D4139;
}

.class-row3 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4rpx;
}

.class-start {
  font-size: 22rpx;
  color: #8C7E74;
}

.class-remaining {
  font-size: 22rpx;
  color: #FA5151;
  font-weight: 500;
}

.class-full-tip {
  font-size: 22rpx;
  color: #999;
}

/* ====== 时间冲突标记样式 ====== */
.class-item-conflict {
  opacity: 0.65;
  background: #FFF5F5;
  border: 2rpx solid #FECACA;
}

.class-item-conflict:active {
  border-color: #EF4444;
  background: #FEF2F2;
}

.class-spots-conflict {
  color: #DC2626;
  background: #FEE2E2;
  border: 1rpx solid #FECACA;
}

.class-conflict-desc {
  font-size: 22rpx;
  color: #DC2626;
  line-height: 1.5;
  flex: 1;
  margin-right: 8rpx;
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
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid #F0ECE8;
}

.sheet-info:last-of-type {
  border-bottom: none;
}

.sheet-warning {
  margin-bottom: 16rpx;
  padding: 16rpx 24rpx;
  background: #FEF2F2;
  border: 2rpx solid #FECACA;
  border-radius: 12rpx;
}

.sheet-warning-text {
  font-size: 24rpx;
  color: #DC2626;
  line-height: 1.6;
}

.sheet-label {
  font-size: 28rpx;
  color: #8C7E74;
  flex-shrink: 0;
}

.sheet-value {
  font-size: 28rpx;
  color: #2D2A26;
  font-weight: 500;
  text-align: right;
  margin-left: 16rpx;
}
</style>
