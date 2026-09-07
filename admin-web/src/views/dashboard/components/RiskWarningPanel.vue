<template>
  <div class="risk-panel">
    <!-- 风险分布总览 chips -->
    <div class="risk-chips">
      <div class="risk-chip total">
        <div class="chip-num">{{ summary.total }}</div>
        <div class="chip-label">风险学员总数</div>
      </div>
      <div class="risk-chip high">
        <div class="chip-num">{{ summary.high }}</div>
        <div class="chip-label">高风险（优先联系）</div>
      </div>
      <div class="risk-chip medium">
        <div class="chip-num">{{ summary.medium }}</div>
        <div class="chip-label">中风险（本周跟进）</div>
      </div>
      <div class="risk-chip low">
        <div class="chip-num">{{ summary.low }}</div>
        <div class="chip-label">低风险（观察）</div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="risk-filter-bar">
      <el-input v-model="filters.keyword" placeholder="搜索学员姓名" clearable style="width: 160px" @change="search" />
      <el-select v-model="filters.level" placeholder="全部风险档" clearable style="width: 130px" @change="search">
        <el-option label="高风险" value="HIGH" />
        <el-option label="中风险" value="MEDIUM" />
        <el-option label="低风险" value="LOW" />
      </el-select>
      <el-select v-if="canOperate" v-model="filters.classId" placeholder="全部班级" clearable style="width: 150px" @change="search">
        <el-option v-for="c in classOptions" :key="c.id" :label="c.className" :value="c.id" />
      </el-select>
      <el-select v-if="canOperate" v-model="filters.courseId" placeholder="全部课程" clearable style="width: 140px" @change="search">
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filters.followUpStatus" placeholder="全部跟进状态" clearable style="width: 140px" @change="search">
        <el-option label="待跟进" :value="0" />
        <el-option label="已跟进" :value="1" />
        <el-option label="暂不跟进" :value="2" />
      </el-select>
      <el-button type="primary" plain size="small" @click="search">查询</el-button>
    </div>

    <!-- 操作栏 -->
    <div v-if="canOperate" class="risk-actions">
      <el-button type="primary" size="small" :disabled="!selectedRows.length" @click="handleNotify">
        一键站内通知家长（{{ selectedRows.length }}）
      </el-button>
      <el-button type="success" size="small" :disabled="!selectedRows.length" @click="handleMarkFollowed">
        标记为已跟进
      </el-button>
    </div>

    <!-- 名单表格 -->
    <el-table v-loading="loading" :data="list" border stripe size="small"
              @selection-change="onSelectionChange" style="width: 100%">
      <el-table-column v-if="canOperate" type="selection" width="45" />
      <el-table-column prop="studentName" label="学员" width="90" />
      <el-table-column prop="className" label="班级" width="110" />
      <el-table-column prop="courseName" label="课程" width="100" />
      <el-table-column label="风险分" width="70" align="center">
        <template #default="{ row }">
          <span :class="riskClass(row.riskLevel)">{{ row.riskScore }}</span>
        </template>
      </el-table-column>
      <el-table-column label="档位" width="75" align="center">
        <template #default="{ row }">
          <el-tag :type="tagType(row.riskLevel)" size="small">{{ levelLabel(row.riskLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近到课" width="95">
        <template #default="{ row }">{{ row.lastAttendDate || '从未到课' }}</template>
      </el-table-column>
      <el-table-column label="28天缺勤" width="90" align="center">
        <template #default="{ row }">
          {{ row.absentCount28d ?? 0 }}/{{ row.scheduledCount28d ?? 0 }}
          <span v-if="row.absenceRate28d">({{ row.absenceRate28d }}%)</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余/总课时" width="100" align="center">
        <template #default="{ row }">
          {{ row.remainingLessons ?? 0 }}/{{ row.totalLessons ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column label="到期日" width="95">
        <template #default="{ row }">{{ row.expireDate || '—' }}</template>
      </el-table-column>
      <el-table-column prop="suggestedAction" label="建议动作" min-width="150" />
      <el-table-column label="跟进状态" width="110">
        <template #default="{ row }">
          <el-select v-if="canOperate" :model-value="row.followUpStatus ?? 0" size="small"
                     @change="(v: number) => changeFollowUp(row, v)">
            <el-option label="待跟进" :value="0" />
            <el-option label="已跟进" :value="1" />
            <el-option label="暂不跟进" :value="2" />
          </el-select>
          <span v-else>{{ followLabel(row.followUpStatus) }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <span>当前条件下暂无流失风险学员</span>
      </template>
    </el-table>

    <div class="risk-pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="onPageChange"
      />
      <ExportButton url="/export/risk-students" filename="流失预警名单.xlsx" label="导出名单"
                    type="warning" :params="exportParams" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { statisticsApi } from '@/api/auth'
import { classApi, courseApi } from '@/api/edu'
import ExportButton from '@/components/ExportButton.vue'
import { useAuthStore } from '@/stores/auth'
import { showError } from '@/utils/error'

const authStore = useAuthStore()
// 跟进/通知动作仅教务管理员、超级管理员可操作（财务管理员仅查看统计口径）
const canOperate = computed(() => ['SUPER_ADMIN', 'EDU_ADMIN'].includes(authStore.roleCode))

const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const summary = reactive({ total: 0, high: 0, medium: 0, low: 0 })
const filters = reactive({
  keyword: '',
  level: '' as string,
  classId: null as number | null,
  courseId: null as number | null,
  followUpStatus: null as number | null,
})
const classOptions = ref<any[]>([])
const courseOptions = ref<any[]>([])
const selectedRows = ref<any[]>([])

const exportParams = computed(() => ({
  level: filters.level || undefined,
  classId: filters.classId ?? undefined,
  courseId: filters.courseId ?? undefined,
}))

function riskClass(level: string): string {
  if (level === 'HIGH') return 'score-high'
  if (level === 'MEDIUM') return 'score-medium'
  return 'score-low'
}
function levelLabel(level: string): string {
  const map: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[level] || level || ''
}
function tagType(level: string): string {
  const map: Record<string, string> = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
  return map[level] || 'info'
}
function followLabel(status: number | null): string {
  const map: Record<number, string> = { 0: '待跟进', 1: '已跟进', 2: '暂不跟进' }
  return status == null ? '待跟进' : map[status] || '待跟进'
}

async function loadSummary() {
  try {
    const res = await statisticsApi.riskSummary()
    const d = res.data || {}
    Object.assign(summary, { total: d.total || 0, high: d.high || 0, medium: d.medium || 0, low: d.low || 0 })
  } catch (e) {
    showError(e, '风险汇总加载失败')
  }
}

async function loadList() {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filters.keyword?.trim()) params.keyword = filters.keyword.trim()
    if (filters.level) params.level = filters.level
    if (filters.classId) params.classId = filters.classId
    if (filters.courseId) params.courseId = filters.courseId
    if (filters.followUpStatus !== null && filters.followUpStatus !== undefined) {
      params.followUpStatus = filters.followUpStatus
    }
    const res = await statisticsApi.riskWarnings(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    showError(e, '流失预警名单加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [clRes, cuRes] = await Promise.all([
      classApi.list({ pageNum: 1, pageSize: 200 }),
      courseApi.list({ pageNum: 1, pageSize: 200 }),
    ])
    classOptions.value = clRes.data?.records || []
    courseOptions.value = cuRes.data?.records || []
  } catch {
    // 选项加载失败不阻塞主名单
  }
}

function search() {
  pageNum.value = 1
  loadList()
}
function onPageChange(p: number) {
  pageNum.value = p
  loadList()
}
function onSelectionChange(rows: any[]) {
  selectedRows.value = rows
}

async function changeFollowUp(row: any, status: number) {
  try {
    await statisticsApi.riskFollowUp(row.studentId, { status, remark: '' })
    row.followUpStatus = status
    ElMessage.success('跟进状态已更新')
    loadSummary()
  } catch (e) {
    showError(e, '更新失败')
  }
}

async function handleMarkFollowed() {
  const ids = selectedRows.value.map((r) => r.studentId)
  try {
    for (const id of ids) {
      await statisticsApi.riskFollowUp(id, { status: 1, remark: '批量标记已跟进' })
    }
    ElMessage.success(`已标记 ${ids.length} 名学员为已跟进`)
    loadSummary()
    loadList()
  } catch (e) {
    showError(e, '标记失败')
  }
}

async function handleNotify() {
  const ids = selectedRows.value.map((r) => r.studentId)
  try {
    const res = await statisticsApi.riskNotify({ studentIds: ids })
    const n = res.data?.notifiedParentCount ?? 0
    ElMessage.success(`已向 ${n} 位家长发送站内提醒`)
  } catch (e) {
    showError(e, '通知发送失败')
  }
}

onMounted(() => {
  // 班级/课程下拉依赖 /edu/classes、/edu/courses（后端仅放行 SUPER_ADMIN/EDU_ADMIN），
  // 财务等只读角色跳过加载，避免首屏闪现"无权访问该接口"
  if (canOperate.value) {
    loadOptions()
  }
  loadSummary()
  loadList()
})
</script>

<style scoped>
.risk-panel { padding: 4px 0; }
.risk-chips { display: flex; gap: 16px; margin-bottom: 14px; flex-wrap: wrap; }
.risk-chip {
  min-width: 140px; padding: 12px 16px; border-radius: 8px; background: #f5f7fa; text-align: center;
}
.risk-chip.total { background: #ecf5ff; }
.risk-chip.high { background: #fef0f0; }
.risk-chip.medium { background: #fdf6ec; }
.risk-chip.low { background: #f4f4f5; }
.chip-num { font-size: 28px; font-weight: 700; color: #303133; }
.risk-chip.high .chip-num { color: #f56c6c; }
.risk-chip.medium .chip-num { color: #e6a23c; }
.risk-chip.low .chip-num { color: #909399; }
.chip-label { font-size: 12px; color: #606266; margin-top: 4px; }
.risk-filter-bar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.risk-actions { margin-bottom: 10px; }
.score-high { color: #f56c6c; font-weight: 700; }
.score-medium { color: #e6a23c; font-weight: 700; }
.score-low { color: #909399; }
.risk-pager { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; }
</style>
