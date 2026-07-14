<template>
  <view class="switcher-wrap" @click="toggle">
    <view class="switcher-current">
      <view class="switcher-avatar">
        <text class="switcher-avatar-text">{{ currentName.charAt(0) }}</text>
      </view>
      <text class="switcher-name">{{ currentName }}</text>
      <view class="switcher-arrow" :class="{ 'switcher-arrow-up': open }">
        <view class="switcher-arrow-icon" />
      </view>
    </view>

    <view v-if="open && students.length > 1" class="switcher-dropdown">
      <view
        v-for="s in students"
        :key="s.id || s.studentId"
        class="switcher-item"
        :class="{ 'switcher-item-active': (s.id || s.studentId) === currentId }"
        @click.stop="select(s)"
      >
        <view class="switcher-item-avatar">
          <text class="switcher-avatar-text-sm">{{ (s.name || '学').charAt(0) }}</text>
        </view>
        <view class="switcher-item-info">
          <text class="switcher-item-name">{{ s.name || '学员' }}</text>
          <text class="switcher-item-id">ID: {{ s.id || s.studentId }}</text>
        </view>
        <view v-if="(s.id || s.studentId) === currentId" class="switcher-check">
          <text style="color:#0E7490;font-size:32rpx">&#10003;</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { getMyStudents, getCurrentStudentId, setCurrentStudentId } from '@/utils/request'

const emit = defineEmits(['change'])

const open = ref(false)
const students = ref(getMyStudents())
const currentId = ref(getCurrentStudentId())

const currentStudent = computed(() => {
  return students.value.find(s => (s.id || s.studentId) === currentId.value)
})

const currentName = computed(() => {
  return currentStudent.value?.name || '学员'
})

function toggle() {
  if (students.value.length <= 1) return
  open.value = !open.value
}

function select(s) {
  const id = s.id || s.studentId
  currentId.value = id
  setCurrentStudentId(id)
  open.value = false
  emit('change', id)
}
</script>

<style scoped>
.switcher-wrap {
  padding: 0 32rpx;
  position: relative;
  z-index: 100;
}

.switcher-current {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
}

.switcher-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #0E7490, #06B6D4);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16rpx;
  flex-shrink: 0;
  box-shadow: 0 2rpx 12rpx rgba(14, 116, 144, 0.25);
}

.switcher-avatar-text {
  font-size: 28rpx;
  font-weight: 700;
  color: #FFF;
}

.switcher-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2D2A26;
  flex: 1;
}

.switcher-arrow {
  width: 24rpx;
  height: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.25s;
  flex-shrink: 0;
}

.switcher-arrow-up {
  transform: rotate(180deg);
}

.switcher-arrow-icon {
  width: 14rpx;
  height: 14rpx;
  border-right: 3rpx solid #8C7E74;
  border-bottom: 3rpx solid #8C7E74;
  transform: rotate(45deg);
  margin-top: -6rpx;
}

.switcher-dropdown {
  background: #FFF;
  border-radius: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 8rpx 32rpx rgba(45, 42, 38, 0.10);
  overflow: hidden;
}

.switcher-item {
  display: flex;
  align-items: center;
  padding: 24rpx 28rpx;
  position: relative;
}

.switcher-item::after {
  content: '';
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: 0;
  height: 1rpx;
  background: #F0ECE8;
}

.switcher-item:last-child::after {
  display: none;
}

.switcher-item:active {
  background: #F9F6F3;
}

.switcher-item-active {
  background: #ECFEFF;
}

.switcher-item-active:active {
  background: #D8F8FB;
}

.switcher-item-avatar {
  width: 48rpx;
  height: 48rpx;
  border-radius: 24rpx;
  background: #ECFEFF;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.switcher-avatar-text-sm {
  font-size: 24rpx;
  font-weight: 600;
  color: #0E7490;
}

.switcher-item-info {
  flex: 1;
}

.switcher-item-name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: #4D4139;
}

.switcher-item-id {
  display: block;
  font-size: 22rpx;
  color: #8C7E74;
  margin-top: 4rpx;
}

.switcher-check {
  flex-shrink: 0;
  margin-left: 16rpx;
}
</style>
