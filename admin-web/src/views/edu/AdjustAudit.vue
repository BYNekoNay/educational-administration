<template>
  <div>
    <div class="page-header">
      <h3>调课审核</h3>
      <span v-if="pendingTotal > 0" class="pending-hint">
        待审核 <b>{{ pendingTotal }}</b> 条
      </span>
    </div>

    <!-- 状态切换：默认聚焦待办 -->
    <div class="filter-bar">
      <el-radio-group v-model="filters.status" @change="onFilterChange">
        <el-radio-button :value="1">待审核</el-radio-button>
        <el-radio-button :value="2">已通过</el-radio-button>
        <el-radio-button :value="3">已驳回</el-radio-button>
        <el-radio-button :value="null">全部</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 列表 -->
    <div v-loading="loading" style="min-height:200px">
      <el-empty v-if="!loading && records.length===0"
                :description="filters.status === 1 ? '没有待审核的调课申请' : '暂无调课申请'" />

      <div v-for="item in records" :key="item.id"
           class="audit-card" :class="{ 'audit-pending': item.status === 1 }">
        <!-- 主行：课程/班级 + 时间链路 + 操作 -->
        <div class="audit-main">
          <div class="audit-info">
            <div class="audit-title">
              <span class="audit-course">{{ item.courseName || '未知课程' }}</span>
              <span class="audit-sep">·</span>
              <span>{{ item.className || '-' }}</span>
              <span class="audit-teacher">{{ item.teacherName || '-' }}</span>
            </div>
            <div class="audit-time">
              <span class="time-old">{{ item.lessonDate }} {{ item.startTime?.slice(0,5) }}-{{ item.endTime?.slice(0,5) }}</span>
              <span v-if="item.periodName" class="audit-period">{{ item.periodName }}</span>
              <el-icon class="time-arrow"><Right /></el-icon>
              <span class="time-new">{{ fmtExpect(item.expectTime) }}</span>
            </div>
            <div class="audit-reason" v-if="item.reason || item.auditRemark">
              <span v-if="item.reason">原因：{{ item.reason }}</span>
              <span v-if="item.auditRemark" class="remark">备注：{{ item.auditRemark }}</span>
            </div>
          </div>

          <div class="audit-side">
            <el-tag :type="statusTagType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
            <template v-if="item.status === 1">
              <el-button size="small" type="success" :loading="auditingId === item.id" @click="quickApprove(item)">通过</el-button>
              <el-button size="small" type="danger" plain @click="quickReject(item)">驳回</el-button>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div style="margin-top:16px;display:flex;justify-content:flex-end">
      <el-pagination v-if="total>pageSize" v-model:current-page="pagenum" :page-size="pageSize"
                     :total="total" layout="prev,next" @current-change="loadData" size="small" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Right } from '@element-plus/icons-vue'
import { adjustApi } from '@/api/edu'
import { showError } from '@/utils/error'

const loading = ref(false)
const records = ref<any[]>([])
const pagenum = ref(1)
const pageSize = 20
const total = ref(0)
/** 待审核总数（跨筛选态展示，用于顶部待办提示） */
const pendingTotal = ref(0)
const auditingId = ref<number | null>(null)

/** 默认聚焦"待审核"——审核员进来先看待办，而不是混在历史单据里找 */
const filters = reactive<{ status: number | null }>({ status: 1 })

function statusLabel(s: number) { return { 1: '待审核', 2: '已通过', 3: '已驳回' }[s] || '未知' }
function statusTagType(s: number) { return { 1: 'warning', 2: 'success', 3: 'danger' }[s] || 'info' as any }

function fmtExpect(t: string | null) {
  if (!t) return '-'
  const s = String(t).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

async function loadPendingTotal() {
  try {
    const res = await adjustApi.list({ pageNum: 1, pageSize: 1, status: 1 })
    pendingTotal.value = res.data?.total || 0
  } catch { /* 提示性数据，失败不阻塞 */ }
}

function onFilterChange() {
  pagenum.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagenum.value, pageSize }
    if (filters.status) params.status = filters.status
    const res = await adjustApi.list(params)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
    if (filters.status === 1) pendingTotal.value = total.value
    else loadPendingTotal()
  } catch (e) { showError(e, '加载调课申请失败') }
  finally { loading.value = false }
}

/** 一步式通过：确认框直接完成，备注留到需要时再补（减少一次弹窗表单） */
async function quickApprove(item: any) {
  try {
    await ElMessageBox.confirm(
      `${item.courseName || ''} ${item.className || ''} → ${fmtExpect(item.expectTime)}，确认通过？`,
      '通过调课申请',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'success' }
    )
  } catch { return /* 用户取消 */ }
  auditingId.value = item.id
  try {
    await adjustApi.audit(item.id, { status: 2, remark: '' })
    ElMessage.success('已通过')
    loadData()
  } catch (e) { showError(e, '审核失败') }
  finally { auditingId.value = null }
}

/** 驳回必须填原因（prompt 一步完成） */
async function quickReject(item: any) {
  let reason: string
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因（教师端可见）', '驳回调课申请', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '如：该时段教室已被占用',
      inputPattern: /\S+/,
      inputErrorMessage: '驳回原因不能为空',
      type: 'warning',
    })
    reason = value
  } catch { return /* 用户取消 */ }
  auditingId.value = item.id
  try {
    await adjustApi.audit(item.id, { status: 3, remark: reason })
    ElMessage.success('已驳回')
    loadData()
  } catch (e) { showError(e, '审核失败') }
  finally { auditingId.value = null }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.pending-hint { font-size: var(--text-sm); color: var(--neutral-500); }
.pending-hint b { color: var(--accent-amber); font-size: var(--text-lg); }

.filter-bar { margin-bottom: 12px; }

/* 紧凑卡片：左侧信息 + 右侧操作，一屏可见 ~8 条 */
.audit-card {
  background: #fff;
  border-radius: var(--radius-md);
  border: 1px solid var(--neutral-200);
  margin-bottom: 8px;
  transition: box-shadow var(--duration-fast) var(--ease-out-quart), border-color var(--duration-fast);
}
.audit-card:hover { box-shadow: var(--shadow-md); }
.audit-card.audit-pending { border-left: 3px solid var(--accent-amber); }
.audit-card:not(.audit-pending) { opacity: 0.78; }

.audit-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
}
.audit-info { min-width: 0; flex: 1; }

.audit-title {
  font-size: 14px;
  font-weight: var(--font-semibold);
  color: var(--neutral-700);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.audit-course { color: var(--brand-primary); }
.audit-sep { color: var(--neutral-300); margin: 0 2px; }
.audit-teacher {
  font-weight: var(--font-normal);
  font-size: 12px;
  color: var(--neutral-400);
  margin-left: 8px;
}

.audit-time {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  font-size: 13px;
}
.time-old { color: var(--neutral-500); text-decoration: line-through; text-decoration-color: var(--neutral-300); }
.time-arrow { color: var(--brand-primary); font-size: 12px; }
.time-new { color: var(--brand-primary); font-weight: var(--font-semibold); }
.audit-period { font-size: 12px; padding: 1px 8px; border-radius: 10px; background: var(--el-color-primary-light-9); color: var(--brand-primary); }

.audit-reason {
  margin-top: 4px;
  font-size: 12px;
  color: var(--neutral-400);
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.audit-reason .remark { color: var(--accent-amber); }

.audit-side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
