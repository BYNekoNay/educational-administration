<template>
  <div class="editable-week-grid">
    <div v-if="message" class="ewg-message">{{ message }}</div>
    <table class="ewg-table">
      <thead>
        <tr>
          <th class="ewg-period-head">时段</th>
          <th v-for="(label, i) in weekDays" :key="'d' + i" class="ewg-day-head">{{ label }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in periods" :key="String(p.id || p.name)">
          <td class="ewg-period">
            <div class="ewg-period-name">{{ p.name }}</div>
            <div class="ewg-period-time">{{ fmtTime(p.startTime) }}-{{ fmtTime(p.endTime) }}</div>
          </td>
          <td
            v-for="(day, i) in weekDates" :key="'c' + String(p.id || p.name) + i"
            class="ewg-cell"
            :class="cellClass(day, p)"
            @dragover.prevent="onDragOver(day, p)"
            @dragleave="onDragLeave(day, p)"
            @drop.prevent="onDrop(day, p)"
          >
            <div
              v-for="l in lessonsInCell(day, p)" :key="l.id"
              class="ewg-lesson"
              :class="{ 'lesson-draggable': canEdit && canDrag(l).allowed, 'lesson-disabled': !canDrag(l).allowed }"
              :draggable="canEdit && canDrag(l).allowed"
              :title="canDrag(l).allowed ? '' : (canDrag(l).reason || '')"
              @dragstart="onDragStart($event, l)"
              @dragend="onDragEnd"
            >
              <div class="ewg-lesson-title">
                <span>{{ l.courseName || l.className }}</span>
                <el-tag v-if="!canDrag(l).allowed && canEdit" size="small" type="info">不可调</el-tag>
              </div>
              <div class="ewg-lesson-time">{{ fmtTime(l.startTime) }}-{{ fmtTime(l.endTime) }}</div>
              <div class="ewg-lesson-info">{{ l.teacherName }} · {{ l.classroomName }}</div>
            </div>
            <div v-if="!lessonsInCell(day, p).length" class="ewg-cell-empty">
              <span v-if="isPast(day)">已过去</span>
              <span v-else>＋</span>
            </div>
            <div v-if="hoverKey === cellKey(day, p) && hoverConflicts.length" class="ewg-conflict">
              {{ hoverConflicts[0] }}
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="canEdit" class="ewg-tip">提示：仅「待上课且未开始」的课次可拖拽；冲突格会标红，保存时后端将做最终复核。</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { periodApi } from '@/api/edu'
import {
  canDragLesson,
  fmtTime,
  isPastDay,
  localConflicts,
  targetForPeriod,
  timeToMinutes,
} from './useScheduleDrag'

const props = defineProps<{
  lessons: any[]
  /** 表头标签（如 7/6 周一） */
  weekDays: string[]
  /** 周一~周日 7 个 yyyy-MM-dd */
  weekDates: string[]
  /** 是否处于可编辑模式 */
  canEdit: boolean
}>()

const emit = defineEmits<{
  (e: 'scheduleMove', payload: { lesson: any; target: { lessonDate: string; startTime: string; endTime: string } }): void
}>()

const periods = ref<any[]>([])
const dragLesson = ref<any>(null)
const hoverKey = ref('')
const hoverConflicts = ref<string[]>([])
const message = ref('')

onMounted(async () => {
  try {
    const res = await periodApi.list()
    periods.value = (res.data || []) as any[]
  } catch {
    periods.value = []
  }
})

function cellKey(day: string, p: any): string {
  return day + '|' + String(p.id ?? p.name)
}

function isPast(day: string): boolean {
  return isPastDay(day)
}

function lessonsInCell(day: string, p: any): any[] {
  const start = timeToMinutes(p.startTime)
  const end = timeToMinutes(p.endTime)
  if (start === null || end === null) return []
  return (props.lessons || []).filter((l) => {
    const ls = timeToMinutes(l.startTime)
    return l.lessonDate === day && ls !== null && ls >= start && ls < end
  })
}

function canDrag(l: any) {
  return canDragLesson(l)
}

function onDragStart(e: DragEvent, l: any) {
  dragLesson.value = l
  hoverKey.value = ''
  hoverConflicts.value = []
  message.value = ''
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(l.id))
  }
}

function onDragEnd() {
  resetDragState()
}

function resetDragState() {
  dragLesson.value = null
  hoverKey.value = ''
  hoverConflicts.value = []
}

function onDragOver(day: string, p: any) {
  if (!props.canEdit || !dragLesson.value) return
  const key = cellKey(day, p)
  if (isPast(day)) {
    hoverKey.value = key
    hoverConflicts.value = ['过去日期不可放置']
    return
  }
  const target = targetForPeriod(day, p)
  const conflicts = localConflicts(dragLesson.value, target, props.lessons)
  hoverKey.value = key
  hoverConflicts.value = conflicts
}

function onDragLeave(day: string, p: any) {
  if (hoverKey.value === cellKey(day, p)) {
    hoverKey.value = ''
    hoverConflicts.value = []
  }
}

function onDrop(day: string, p: any) {
  const lesson = dragLesson.value
  if (!props.canEdit || !lesson) return
  const dragState = canDragLesson(lesson)
  if (!dragState.allowed) {
    message.value = dragState.reason || '该课次不可调'
    resetDragState()
    return
  }
  if (isPast(day)) {
    message.value = '目标为过去日期，禁止放置'
    resetDragState()
    return
  }
  const target = targetForPeriod(day, p)
  const conflicts = localConflicts(lesson, target, props.lessons)
  if (conflicts.length) {
    message.value = '存在冲突，禁止放置：' + conflicts.join('；')
    resetDragState()
    return
  }
  emit('scheduleMove', { lesson, target })
  resetDragState()
}

function cellClass(day: string, p: any): string {
  const classes: string[] = []
  if (isPast(day)) classes.push('cell-past')
  if (hoverKey.value === cellKey(day, p)) {
    classes.push(hoverConflicts.value.length ? 'cell-conflict' : 'cell-ok')
  }
  return classes.join(' ')
}
</script>

<style scoped>
.editable-week-grid { overflow-x: auto; background: #fff; border: 1px solid #ebeef5; border-radius: 6px; }
.ewg-message { padding: 8px 12px; color: #f56c6c; background: #fef0f0; font-size: 13px; }
.ewg-table { border-collapse: collapse; width: 100%; min-width: 860px; table-layout: fixed; }
.ewg-table th, .ewg-table td { border: 1px solid #ebeef5; vertical-align: top; }
.ewg-period-head { width: 96px; background: #f5f7fa; padding: 8px; text-align: center; font-size: 13px; }
.ewg-day-head { background: #f5f7fa; padding: 8px; text-align: center; font-size: 12px; color: #303133; }
.ewg-period { padding: 8px; background: #fafafa; text-align: center; }
.ewg-period-name { font-size: 13px; font-weight: 600; color: #303133; }
.ewg-period-time { font-size: 11px; color: #909399; margin-top: 2px; }
.ewg-cell { min-height: 76px; padding: 6px; }
.ewg-cell.cell-past { background: #fafafa; }
.ewg-cell.cell-ok { background: #f0f9eb; box-shadow: inset 0 0 0 1px #67c23a; }
.ewg-cell.cell-conflict { background: #fef0f0; box-shadow: inset 0 0 0 1px #f56c6c; }
.ewg-cell-empty { color: #c0c4cc; text-align: center; font-size: 14px; padding: 18px 0; }
.ewg-lesson { background: #ecf5ff; border-left: 3px solid #409eff; border-radius: 4px; padding: 4px 6px; margin-bottom: 4px; cursor: grab; }
.ewg-lesson.lesson-disabled { opacity: 0.55; cursor: not-allowed; border-left-color: #909399; }
.ewg-lesson-title { font-size: 12px; font-weight: 600; color: #303133; display: flex; gap: 4px; align-items: center; justify-content: space-between; }
.ewg-lesson-time { font-size: 11px; color: #606266; }
.ewg-lesson-info { font-size: 11px; color: #909399; }
.ewg-conflict { margin-top: 4px; color: #f56c6c; font-size: 11px; }
.ewg-tip { color: #909399; font-size: 12px; margin: 8px 2px 0; }
</style>
