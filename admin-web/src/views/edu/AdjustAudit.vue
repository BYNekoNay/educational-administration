<template>
  <div>
    <h3>调课审核</h3>

    <!-- 过滤栏 -->
    <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:12px 0">
      <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:130px" @change="loadData">
        <el-option label="待审核" :value="1" />
        <el-option label="已通过" :value="2" />
        <el-option label="已驳回" :value="3" />
      </el-select>
    </div>

    <!-- 列表 -->
    <div v-loading="loading" style="min-height:200px">
      <el-empty v-if="!loading && records.length===0" description="暂无调课申请" />

      <div v-for="item in records" :key="item.id" class="audit-card" :class="{ 'audit-done': item.status !== 1 }">
        <div class="audit-header">
          <div class="audit-title">
            <span class="audit-course">{{ item.courseName || '未知课程' }}</span>
            <span class="audit-sep">·</span>
            <span>{{ item.className || '-' }}</span>
          </div>
          <el-tag :type="statusTagType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
        </div>

        <div class="audit-body">
          <div class="audit-row">
            <span class="audit-label">教师</span>
            <span class="audit-value">{{ item.teacherName || '-' }}</span>
          </div>
          <div class="audit-row">
            <span class="audit-label">原课次</span>
            <span class="audit-value">{{ item.lessonDate }} {{ item.startTime?.slice(0,5) }}-{{ item.endTime?.slice(0,5) }}</span>
            <span v-if="item.periodName" class="audit-period">{{ item.periodName }}</span>
          </div>
          <div class="audit-row">
            <span class="audit-label">调至</span>
            <span class="audit-value highlight">{{ fmtExpect(item.expectTime) }}</span>
          </div>
          <div class="audit-row">
            <span class="audit-label">原因</span>
            <span class="audit-value">{{ item.reason }}</span>
          </div>
          <div v-if="item.auditRemark" class="audit-row">
            <span class="audit-label">备注</span>
            <span class="audit-value remark">{{ item.auditRemark }}</span>
          </div>
        </div>

        <div class="audit-footer" v-if="item.status === 1">
          <el-button size="small" type="success" @click="openAudit(item, 2)">通过</el-button>
          <el-button size="small" type="danger" @click="openAudit(item, 3)">驳回</el-button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div style="margin-top:16px;display:flex;justify-content:flex-end">
      <el-pagination v-if="total>pageSize" v-model:current-page="pagenum" :page-size="pageSize"
                     :total="total" layout="prev,next" @current-change="loadData" small />
    </div>

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditVisible" :title="auditAction === 2 ? '通过申请' : '驳回申请'" width="420px">
      <el-form label-position="top">
        <el-form-item :label="auditAction === 2 ? '审核通过备注（选填）' : '驳回原因'">
          <el-input v-model="auditRemark" type="textarea" :rows="3"
                    :placeholder="auditAction === 2 ? '同意调课，已安排新课次' : '请填写驳回原因'"
                    maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button :type="auditAction === 2 ? 'success' : 'danger'" :loading="auditingId !== null"
                   @click="doAudit">{{ auditAction === 2 ? '确认通过' : '确认驳回' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustApi } from '@/api/edu'
import { showError } from '@/utils/error'

const loading = ref(false)
const records = ref<any[]>([])
const pagenum = ref(1)
const pageSize = 20
const total = ref(0)
const auditingId = ref<number | null>(null)
const auditVisible = ref(false)
const auditRemark = ref('')
const auditAction = ref(2)
const auditItem = ref<any>(null)

const filters = reactive({ status: null as number | null })

function statusLabel(s: number) { return { 1: '待审核', 2: '已通过', 3: '已驳回' }[s] || '未知' }
function statusTagType(s: number) { return { 1: 'warning', 2: 'success', 3: 'danger' }[s] || 'info' as any }

function fmtExpect(t: string | null) {
  if (!t) return '-'
  const s = String(t).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: pagenum.value, pageSize }
    if (filters.status) params.status = filters.status
    const res = await adjustApi.list(params)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) { showError(e, '加载调课申请失败') }
  finally { loading.value = false }
}

function openAudit(item: any, status: number) {
  auditItem.value = item
  auditAction.value = status
  auditRemark.value = ''
  auditVisible.value = true
}

async function doAudit() {
  if (!auditItem.value) return
  auditingId.value = auditItem.value.id
  try {
    await adjustApi.audit(auditItem.value.id, { status: auditAction.value, remark: auditRemark.value })
    ElMessage.success(auditAction.value === 2 ? '已通过' : '已驳回')
    auditVisible.value = false
    loadData()
  } catch (e) { showError(e, '审核失败') }
  finally { auditingId.value = null }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.audit-card {
  background: #fff; border-radius: 8px; padding: 16px 20px; margin-bottom: 12px;
  border: 1px solid #ebeef5; transition: box-shadow .2s;
}
.audit-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.06) }
.audit-done { opacity: .7 }
.audit-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px }
.audit-title { font-size: 15px; font-weight: 600; color: #303133 }
.audit-course { color: #0E7490 }
.audit-sep { margin: 0 6px; color: #c0c4cc }
.audit-body { margin-bottom: 8px }
.audit-row { display: flex; align-items: center; margin-bottom: 6px; font-size: 13px }
.audit-label { width: 60px; color: #909399; flex-shrink: 0 }
.audit-value { color: #303133 }
.audit-value.highlight { color: #0E7490; font-weight: 600 }
.audit-value.remark { color: #E6A23C }
.audit-period { margin-left: 8px; font-size: 12px; padding: 1px 8px; border-radius: 10px; background: #ecf5ff; color: #409EFF }
.audit-footer { display: flex; gap: 8px; padding-top: 8px; border-top: 1px solid #f0f0f0 }
</style>
