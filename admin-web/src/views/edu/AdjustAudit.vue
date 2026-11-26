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
              <el-button size="small" type="success" :loading="auditingId === item.id" @click="openAuditDialog(item, 'approve')">通过</el-button>
              <el-button size="small" type="danger" plain @click="openAuditDialog(item, 'reject')">驳回</el-button>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div style="margin-top:16px;display:flex;justify-content:flex-end">
      <el-pagination v-if="total > 0"
                     v-model:current-page="pagenum"
                     v-model:page-size="pageSize"
                     :total="total"
                     :page-sizes="[10, 20, 50, 100]"
                     layout="total, sizes, prev, pager, next"
                     @current-change="loadData"
                     @size-change="onSizeChange"
                     size="small" />
    </div>

    <!-- 审核 dialog：通过（选填备注）与驳回（必填原因）共用，显式表单避免 MessageBox 输入框不显眼 -->
    <el-dialog v-model="auditDialog.visible" :close-on-click-modal="false" width="460px"
               :title="auditDialog.type === 'approve' ? '通过调课申请' : '驳回调课申请'">
      <p style="margin:0 0 12px;line-height:1.6">
        {{ dialogContext }}，{{ auditDialog.type === 'approve' ? '确认通过？' : '请填写驳回原因（教师端可见）' }}
      </p>
      <el-input v-model="auditDialog.remark" type="textarea" :rows="3" maxlength="200" show-word-limit
                :placeholder="auditDialog.type === 'approve' ? '通过备注（选填，教师端可见）' : '如：该时段教室已被占用'" />
      <template #footer>
        <el-button @click="auditDialog.visible = false">取消</el-button>
        <el-button :type="auditDialog.type === 'approve' ? 'success' : 'danger'"
                   :loading="auditingId !== null"
                   :disabled="auditDialog.type === 'reject' && !auditDialog.remark.trim()"
                   @click="confirmAudit">{{ auditDialog.type === 'approve' ? '确认通过' : '确认驳回' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Right } from '@element-plus/icons-vue'
import { adjustApi } from '@/api/edu'
import { showError } from '@/utils/error'
import type { AdjustRequestVO, PageParams } from '@/types'

const loading = ref(false)
const records = ref<AdjustRequestVO[]>([])
const pagenum = ref(1)
const pageSize = ref(20)
const total = ref(0)
/** 待审核总数（跨筛选态展示，用于顶部待办提示） */
const pendingTotal = ref(0)
const auditingId = ref<number | null>(null)

/** 默认聚焦"待审核"——审核员进来先看待办，而不是混在历史单据里找 */
const filters = reactive<{ status: number | null }>({ status: 1 })

function statusLabel(s: number | undefined) { return ({ 1: '待审核', 2: '已通过', 3: '已驳回' } as Record<number, string>)[s as number] || '未知' }
function statusTagType(s: number | undefined): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = { 1: 'warning', 2: 'success', 3: 'danger' }
  return map[s as number] || 'info'
}

function fmtExpect(t: string | null | undefined) {
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

function onSizeChange() {
  pagenum.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params: PageParams = { pageNum: pagenum.value, pageSize: pageSize.value }
    if (filters.status) params.status = filters.status
    const res = await adjustApi.list(params)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
    if (filters.status === 1) pendingTotal.value = total.value
    else loadPendingTotal()
  } catch (e) { showError(e, '加载调课申请失败') }
  finally { loading.value = false }
}

// ---- 审核 dialog（第六轮：原双 ElMessageBox.prompt 的输入框视觉太弱被复测连续看漏，改显式表单）----
const auditDialog = reactive({
  visible: false,
  type: 'approve' as 'approve' | 'reject',
  item: null as AdjustRequestVO | null,
  remark: '',
})

const dialogContext = computed(() => {
  const it = auditDialog.item
  return it ? `${it.courseName || ''} ${it.className || ''} → ${fmtExpect(it.expectTime)}` : ''
})

function openAuditDialog(item: AdjustRequestVO, type: 'approve' | 'reject') {
  auditDialog.item = item
  auditDialog.type = type
  auditDialog.remark = ''
  auditDialog.visible = true
}

/** 统一提交：通过（status=2，备注选填）/ 驳回（status=3，原因必填由按钮 disabled 兜底） */
async function confirmAudit() {
  if (!auditDialog.item || auditingId.value !== null) return
  const status = auditDialog.type === 'approve' ? 2 : 3
  auditingId.value = auditDialog.item.id ?? null
  try {
    await adjustApi.audit(auditDialog.item.id!, { status, remark: auditDialog.remark.trim() })
    ElMessage.success(status === 2 ? '已通过' : '已驳回')
    auditDialog.visible = false
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
