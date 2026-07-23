<template>
  <div>
    <!-- 页面标题 -->
    <div class="page-header">
      <h3>薪资管理</h3>
      <span class="page-desc">维护教师薪资规则、按月核算并跟踪发放状态</span>
    </div>

    <el-tabs v-model="activeTab" class="salary-tabs">
      <!-- ========== 薪资规则 Tab ========== -->
      <el-tab-pane label="薪资规则" name="rules">
        <el-card shadow="never" class="filter-card">
          <el-form inline :model="{ keyword }" @submit.prevent>
            <el-form-item label="教师姓名">
              <el-input v-model="keyword" placeholder="搜索教师" clearable style="width: 220px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
              <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
            </el-form-item>
            <el-form-item style="margin-left: auto">
              <el-button type="primary" :icon="Plus" @click="showRuleDialog(null)">新增规则</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-table :data="rules" v-loading="rulesLoading" border stripe
                  style="margin-top: 12px"
                  @sort-change="(v:any) => handleSortChange(v, 'rules')">
          <el-table-column prop="id" label="ID" width="60" sortable="custom" />
          <el-table-column prop="teacherName" label="教师" min-width="100" />
          <el-table-column prop="courseName" label="课程" min-width="120" />
          <el-table-column prop="lessonUnitPrice" label="课时单价" width="120" align="right">
            <template #default="{ row }">¥ {{ row.lessonUnitPrice || 0 }}</template>
          </el-table-column>
          <el-table-column prop="substituteRate" label="代课系数" width="100" align="right" />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
                <el-button size="small" link type="primary" @click="showRuleDialog(row)">编辑</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 12px; justify-content: flex-end" size="small"
          v-model:current-page="rulesPage" v-model:page-size="rulesPageSize"
          :total="rulesTotal" layout="sizes, total, prev, pager, next, jumper" :page-sizes="[10, 20, 50, 100]" @change="loadRules" />

        <el-dialog :title="editingRule?.id ? '编辑规则' : '新增规则'" v-model="ruleVisible" width="420px">
          <el-form :model="ruleForm" label-width="90px">
            <el-form-item label="教师" required>
              <!-- 后端 updateSalaryRule 强制忽略 teacherId/courseId（防止历史薪资重算错用费率），编辑态禁用 -->
              <el-select v-model="ruleForm.teacherId" placeholder="请选择教师" filterable style="width:100%" :disabled="!!editingRule?.id">
                <el-option v-for="t in teacherList" :key="t.id" :label="t.realName || t.username" :value="t.id">
                  <span>{{ t.realName || t.username }}</span>
                  <el-tag v-for="sp in (t.specialties || [])" :key="sp.id"
                          size="small" type="info" style="margin-left:4px;font-size:10px">
                    {{ sp.name }}
                  </el-tag>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="课程" required>
              <el-select v-model="ruleForm.courseId" placeholder="请选择课程" filterable style="width:100%" :disabled="!!editingRule?.id">
                <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="课时单价">
              <el-input-number v-model="ruleForm.lessonUnitPrice" :min="0.01" :precision="2" style="width:100%" />
            </el-form-item>
            <el-form-item label="代课系数">
              <el-input-number v-model="ruleForm.substituteRate" :min="0.01" :precision="2" :step="0.1" style="width:100%" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="ruleVisible = false">取消</el-button>
            <el-button type="primary" @click="saveRule" :loading="ruleSaving">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- ========== 薪资列表 Tab ========== -->
      <el-tab-pane label="薪资列表" name="salaries">
        <!-- 1. 核算区 -->
        <el-card shadow="never" class="calc-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">月度薪资核算</span>
              <span class="card-tip">按月统计每位教师的主讲 / 代课课时，并叠加奖金生成应发工资</span>
            </div>
          </template>
          <el-form inline @submit.prevent>
            <el-form-item label="教师" required>
              <el-select v-model="calcTeacherId" placeholder="选择教师" filterable style="width: 180px">
                <el-option v-for="t in teacherList" :key="t.id" :label="t.realName || t.username" :value="t.id">
                  <span>{{ t.realName || t.username }}</span>
                  <el-tag v-for="sp in (t.specialties || [])" :key="sp.id"
                          size="small" type="info" style="margin-left:4px;font-size:10px">
                    {{ sp.name }}
                  </el-tag>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="月份" required>
              <el-date-picker v-model="calcMonthDate" type="month" placeholder="选择月份" value-format="YYYY-MM"
                              style="width: 140px" />
            </el-form-item>
            <el-form-item label="奖金">
              <el-input-number v-model="calcBonus" :min="0" :precision="2" :step="50" style="width: 160px" placeholder="0.00">
                <template #prefix>¥</template>
              </el-input-number>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Coin" :loading="calculating" @click="handleCalculate">核算薪资</el-button>
              <el-button type="success" :icon="MagicStick" :loading="batchCalculating" @click="handleBatchCalculate">一键结算本月</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 2. 概览卡片（基于全量数据，不受列表筛选影响） -->
        <div class="stat-row">
          <div class="stat-card" v-for="s in statCards" :key="s.label">
            <div class="stat-label">{{ s.label }}</div>
            <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
            <div class="stat-extra" v-if="s.extra">{{ s.extra }}</div>
          </div>
        </div>

        <!-- 2.5 按月统计 -->
        <el-card v-if="monthlyStats.length" shadow="never" class="monthly-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">按月统计</span>
              <span class="card-tip">点击月份可快速筛选下方列表</span>
            </div>
          </template>
          <div class="monthly-grid">
            <div v-for="m in monthlyStats" :key="m.month"
                 class="monthly-item"
                 :class="{ active: filterMonth === m.month }"
                 @click="filterMonth = filterMonth === m.month ? null : m.month">
              <div class="m-head">
                <span class="m-month">{{ m.month }}</span>
                <span class="m-total">¥ {{ m.totalAmount.toFixed(2) }}</span>
              </div>
              <div class="m-body">
                <div class="m-row">
                  <span class="m-dot dot-primary"></span><span>待确认 {{ m.cntByStatus[1] }}</span>
                </div>
                <div class="m-row">
                  <span class="m-dot dot-success"></span><span>已确认 {{ m.cntByStatus[2] }}</span>
                </div>
                <div class="m-row">
                  <span class="m-dot dot-warning"></span><span>已发放 {{ m.cntByStatus[3] }}</span>
                </div>
                <div v-if="m.cntByStatus[4]" class="m-row">
                  <span class="m-dot dot-info"></span><span>已撤销 {{ m.cntByStatus[4] }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 3. 筛选 + 列表 -->
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">薪资记录</span>
              <div class="list-filters">
                <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width: 130px" size="default">
                  <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-select v-model="filterMonth" placeholder="月份筛选" clearable style="width: 140px" size="default">
                  <el-option v-for="m in availableMonths" :key="m" :label="m" :value="m" />
                </el-select>
                <el-button :icon="Refresh" @click="resetListFilter" size="default">重置</el-button>
                <ExportButton url="/export/salaries" :params="{ month: filterMonth || '' }"
                              label="导出薪资" type="primary" size="default" />
              </div>
            </div>
          </template>

          <el-table :data="salaries" v-loading="salariesLoading" border stripe
                    empty-text="暂无符合条件的薪资记录"
                    @sort-change="(v:any) => handleSortChange(v, 'salaries')">
            <el-table-column prop="id" label="ID" width="60" sortable="custom" />
            <el-table-column prop="teacherName" label="教师" min-width="100">
              <template #default="{ row }">
                <el-avatar :size="22" style="vertical-align: middle; margin-right: 6px">{{ row.teacherName?.charAt(0) }}</el-avatar>
                <span>{{ row.teacherName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="salaryMonth" label="月份" width="100" sortable="custom">
              <template #default="{ row }">
                <el-tag effect="plain" round>{{ row.salaryMonth }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lessonCount" label="主讲课时" width="90" align="right" />
            <el-table-column prop="substituteCount" label="代课课时" width="90" align="right" />
            <el-table-column prop="baseAmount" label="基础工资" width="110" align="right">
              <template #default="{ row }">¥ {{ row.baseAmount || 0 }}</template>
            </el-table-column>
            <el-table-column prop="bonusAmount" label="奖金" width="100" align="right">
              <template #default="{ row }">¥ {{ row.bonusAmount || 0 }}</template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="应发工资" width="120" sortable="custom" align="right">
              <template #default="{ row }">
                <span class="total-amount">¥ {{ row.totalAmount || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" effect="light" round>{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="calcSnapshotTime" label="核算时间" width="170" sortable="custom">
              <template #default="{ row }">
                <span class="time-text">{{ formatTime(row.calcSnapshotTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center; justify-content: center">
                  <el-button v-if="row.status === 1" size="small" type="success" link @click="handleConfirm(row.id)">确认</el-button>
                  <el-button v-if="row.status === 1 || row.status === 2" size="small" type="warning" link @click="handleVoid(row.id)">作废</el-button>
                  <el-button v-if="row.status === 2" size="small" type="primary" link @click="showAdjust(row.id)">调整</el-button>
                  <el-button v-if="row.status === 2" size="small" type="success" link @click="handlePay(row.id)">发放</el-button>
                  <span v-if="row.status === 3" class="muted-tip">已发放 · 终态</span>
                  <span v-if="row.status === 4" class="muted-tip">已撤销</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination style="margin-top: 12px; justify-content: flex-end" size="small"
            v-model:current-page="salariesPage" v-model:page-size="salariesPageSize"
            :total="salariesTotal" layout="sizes, total, prev, pager, next, jumper" :page-sizes="[10, 20, 50, 100]" @change="loadSalaries" />
        </el-card>

        <el-dialog title="薪资调整" v-model="adjustVisible" width="420px">
          <el-form :model="adjustForm" label-width="90px">
            <el-form-item label="薪资ID"><span>ID: {{ adjustForm.salaryId }}</span></el-form-item>
            <el-form-item label="调整金额">
              <el-input-number v-model="adjustForm.adjustAmount" :precision="2" :step="50" style="width:100%" />
              <span class="form-tip">正数补发 / 负数扣回</span>
            </el-form-item>
            <el-form-item label="原因">
              <el-input v-model="adjustForm.reason" type="textarea" :rows="3" placeholder="请填写调整原因（必填）" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="adjustVisible = false">取消</el-button>
            <el-button type="primary" @click="saveAdjust" :loading="adjustSaving">保存</el-button>
          </template>
        </el-dialog>

        <!-- 一键结算结果 -->
        <el-dialog v-model="batchResultVisible" title="一键结算结果" width="640px">
          <div v-if="batchResult" class="batch-summary">
            <div class="summary-row">
              <span class="summary-label">结算月份</span>
              <el-tag effect="plain">{{ batchResult.salaryMonth }}</el-tag>
            </div>
            <div class="summary-row">
              <span class="summary-label">教师总数</span>
              <span>{{ batchResult.totalTeachers }}</span>
            </div>
            <div class="summary-stats">
              <div class="stat-block success">
                <div class="stat-num">{{ batchResult.successCount }}</div>
                <div class="stat-name">成功</div>
              </div>
              <div class="stat-block danger">
                <div class="stat-num">{{ batchResult.failedCount }}</div>
                <div class="stat-name">失败</div>
              </div>
              <div class="stat-block primary">
                <div class="stat-num">¥ {{ batchResult.totalAmount }}</div>
                <div class="stat-name">合计应发</div>
              </div>
            </div>
            <template v-if="batchResult.errors?.length">
              <div class="error-title">失败明细</div>
              <el-table :data="batchResult.errors" size="small" max-height="240">
                <el-table-column prop="teacherName" label="教师" />
                <el-table-column prop="message" label="失败原因" />
              </el-table>
            </template>
          </div>
          <template #footer>
            <el-button type="primary" @click="batchResultVisible = false; loadSalaries()">关闭</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Coin, MagicStick } from '@element-plus/icons-vue'
import ExportButton from '@/components/ExportButton.vue'
import { salaryApi } from '@/api/finance'
import { teacherApi, courseApi } from '@/api/edu'
import { showError } from '@/utils/error'

const activeTab = ref('rules')

// ---- 规则 Tab ----
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const rules = ref<any[]>([]), rulesLoading = ref(false), rulesPage = ref(1), rulesPageSize = ref(10), rulesTotal = ref(0)
const ruleVisible = ref(false), ruleSaving = ref(false)
const editingRule = ref<any>(null)
const teacherList = ref<any[]>([])
const courseList = ref<any[]>([])
const ruleForm = reactive<any>({ teacherId: null, courseId: null, lessonUnitPrice: 100, substituteRate: 1.0 })

async function loadOptions() {
  try {
    const [tRes, cRes] = await Promise.all([
      teacherApi.list(),
      courseApi.list({ pageNum: 1, pageSize: 100 }),
    ])
    teacherList.value = tRes.data || []
    courseList.value = cRes.data?.records || []
  } catch (e) {
    // 注意：/edu/teachers、/edu/courses 仅放行 SUPER_ADMIN/EDU_ADMIN，FINANCE 角色会 403
    // （第八轮待办：后端为薪资页补充 FINANCE 可见的教师/课程下拉接口）
    showError(e, '加载教师/课程下拉数据失败')
  }
}

async function loadRules() {
  rulesLoading.value = true
  try {
    const res = await salaryApi.rules({
      pageNum: rulesPage.value, pageSize: rulesPageSize.value,
      keyword: keyword.value || undefined,
      sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined,
    })
    rules.value = res.data.records; rulesTotal.value = res.data.total
  } finally { rulesLoading.value = false }
}

function handleSearch() { rulesPage.value = 1; salariesPage.value = 1; loadRules(); loadSalaries() }
function resetSearch() {
  keyword.value = ''; sortField.value = ''; sortOrder.value = ''
  rulesPage.value = 1; salariesPage.value = 1
  filterStatus.value = null; filterMonth.value = null
  activeTab.value === 'rules' ? loadRules() : loadSalaries()
}
function handleSortChange({ prop, order }: any, tab: string) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  if (tab === 'rules') { rulesPage.value = 1; loadRules() } else { salariesPage.value = 1; loadSalaries() }
}
function showRuleDialog(row: any) {
  editingRule.value = row
  if (row) { ruleForm.teacherId = row.teacherId; ruleForm.courseId = row.courseId; ruleForm.lessonUnitPrice = row.lessonUnitPrice; ruleForm.substituteRate = row.substituteRate }
  else { ruleForm.teacherId = null; ruleForm.courseId = null; ruleForm.lessonUnitPrice = 100; ruleForm.substituteRate = 1.0 }
  ruleVisible.value = true
}
async function saveRule() {
  ruleSaving.value = true
  try {
    if (editingRule.value?.id) {
      await salaryApi.updateRule(editingRule.value.id, { ...ruleForm })
    } else {
      await salaryApi.createRule({ ...ruleForm })
    }
    ElMessage.success('保存成功'); ruleVisible.value = false; loadRules()
  } catch (e) { showError(e, '薪资规则保存失败') } finally { ruleSaving.value = false }
}

// ---- 薪资 Tab ----
const salaries = ref<any[]>([]), salariesLoading = ref(false), salariesPage = ref(1), salariesPageSize = ref(10), salariesTotal = ref(0)
const salariesAll = ref<any[]>([])  // 全量数据，供概览卡和按月统计使用，不受分页影响
const calculating = ref(false)
const batchCalculating = ref(false)
const batchResultVisible = ref(false)
const batchResult = ref<any>(null)
const calcTeacherId = ref<number | null>(null)
const calcMonthDate = ref<string>(new Date().toISOString().slice(0, 7))
const calcBonus = ref<number>(0)

const filterStatus = ref<number | null>(null)
const filterMonth = ref<string | null>(null)

const adjustVisible = ref(false), adjustSaving = ref(false)
const adjustForm = reactive({ salaryId: 0, adjustAmount: 0, reason: '' })

const statusOptions = [
  { value: 1, label: '待确认' },
  { value: 2, label: '已确认' },
  { value: 3, label: '已发放' },
  { value: 4, label: '已撤销' },
]

function statusLabel(s: number) { const map: Record<number, string> = { 1: '待确认', 2: '已确认', 3: '已发放', 4: '已撤销' }; return map[s] || s }
function statusTag(s: number) { const map: Record<number, string> = { 1: 'primary', 2: 'success', 3: 'success', 4: 'info' }; return map[s] || 'info' }

const availableMonths = computed(() => {
  const set = new Set<string>()
  salariesAll.value.forEach(r => r.salaryMonth && set.add(r.salaryMonth))
  return Array.from(set).sort().reverse()
})

function resetListFilter() {
  filterStatus.value = null
  filterMonth.value = null
  salariesPage.value = 1
  loadSalaries()
}

// 状态/月份筛选变化时重置分页并重新加载（服务端过滤）
watch([filterStatus, filterMonth], () => {
  salariesPage.value = 1
  loadSalaries()
})

// 概览卡片：按全量数据汇总（不受列表筛选影响）
const statCards = computed(() => {
  const data = salariesAll.value
  const total = data.length
  const sumBy = (st: number) => data.filter(r => r.status === st).reduce((s, r) => s + Number(r.totalAmount || 0), 0)
  const cntBy = (st: number) => data.filter(r => r.status === st).length
  return [
    { label: '全部记录', value: total, color: '#303133', extra: '' },
    { label: '待确认', value: cntBy(1), color: '#409EFF', extra: `¥ ${sumBy(1).toFixed(2)}` },
    { label: '已确认', value: cntBy(2), color: '#67C23A', extra: `¥ ${sumBy(2).toFixed(2)}` },
    { label: '已发放', value: cntBy(3), color: '#E6A23C', extra: `¥ ${sumBy(3).toFixed(2)}` },
    { label: '已撤销', value: cntBy(4), color: '#909399', extra: `¥ ${sumBy(4).toFixed(2)}` },
  ]
})

// 按月统计：每个月一行
const monthlyStats = computed(() => {
  const groups = new Map<string, { totalAmount: number; cntByStatus: Record<number, number> }>()
  for (const r of salariesAll.value) {
    if (!r.salaryMonth) continue
    let g = groups.get(r.salaryMonth)
    if (!g) {
      g = { totalAmount: 0, cntByStatus: { 1: 0, 2: 0, 3: 0, 4: 0 } }
      groups.set(r.salaryMonth, g)
    }
    g.totalAmount += Number(r.totalAmount || 0)
    g.cntByStatus[r.status] = (g.cntByStatus[r.status] || 0) + 1
  }
  return Array.from(groups.entries())
    .map(([month, v]) => ({ month, ...v }))
    .sort((a, b) => b.month.localeCompare(a.month))
})

function formatTime(t: string | undefined) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

async function loadSalaries() {
  salariesLoading.value = true
  try {
    const res = await salaryApi.list({
      pageNum: salariesPage.value, pageSize: salariesPageSize.value,
      keyword: keyword.value || undefined,
      status: filterStatus.value ?? undefined,
      month: filterMonth.value || undefined,
      sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined,
    })
    salaries.value = res.data?.records || []
    salariesTotal.value = Number(res.data?.total || 0)
  } finally { salariesLoading.value = false }
  // 同时拉全量数据给概览卡/按月统计（不做分页限制，但最多 500 条兜底）
  loadAllSalaries()
}

/** 拉全量薪资记录用于统计概览，与列表分页解耦 */
async function loadAllSalaries() {
  try {
    const res = await salaryApi.list({ pageNum: 1, pageSize: 500 })
    salariesAll.value = res.data?.records || []
  } catch { /* 非关键，统计失败不影响列表 */ }
}

async function handleCalculate() {
  if (!calcTeacherId.value) { ElMessage.warning('请先选择教师'); return }
  if (!calcMonthDate.value) { ElMessage.warning('请选择月份'); return }
  calculating.value = true
  try {
    const res = await salaryApi.calculate({ salaryMonth: calcMonthDate.value, teacherId: calcTeacherId.value, bonusAmount: calcBonus.value || 0 })
    ElMessage.success(`核算完成：主讲${res.data.lessonCount}课时 代课${res.data.substituteCount}课时 应发¥${res.data.totalAmount}`)
    loadSalaries()
  } catch (e: any) { showError(e, '核算失败') } finally { calculating.value = false }
}

async function handleBatchCalculate() {
  if (!calcMonthDate.value) { ElMessage.warning('请选择月份'); return }
  try {
    await ElMessageBox.confirm(
      `将对【${calcMonthDate.value}】所有在职教师执行薪资核算，奖金¥${calcBonus.value || 0}。\n已确认的薪资单不会被覆盖，失败项将汇总显示。`,
      '一键结算本月', { confirmButtonText: '开始结算', cancelButtonText: '取消', type: 'warning' })
  } catch { return /* canceled */ }
  batchCalculating.value = true
  try {
    const res = await salaryApi.calculateBatch({ salaryMonth: calcMonthDate.value, bonusAmount: calcBonus.value || 0 })
    batchResult.value = res.data
    batchResultVisible.value = true
    loadSalaries()
  } catch (e: any) { showError(e, '批量结算失败') } finally { batchCalculating.value = false }
}

async function handleConfirm(id: number) {
  try {
    await salaryApi.confirm(id)
    ElMessage.success('薪资已确认'); loadSalaries()
  } catch (e: any) { showError(e, '确认失败') }
}
async function handleVoid(id: number) {
  try {
    await ElMessageBox.confirm('确认作废该薪资单吗？作废后该月可重新核算。', '作废确认',
      { confirmButtonText: '确认作废', cancelButtonText: '取消', type: 'warning' })
  } catch { return /* canceled */ }
  try {
    await salaryApi.void(id)
    ElMessage.success('已作废，可重新核算'); loadSalaries()
  } catch (e: any) { showError(e, '作废失败') }
}

async function handlePay(id: number) {
  try {
    await ElMessageBox.confirm('确认将该薪资单标记为"已发放"吗？操作不可撤回。', '发放确认',
      { confirmButtonText: '确认发放', cancelButtonText: '取消', type: 'success' })
  } catch { return /* canceled */ }
  try {
    await salaryApi.pay(id)
    ElMessage.success('薪资已发放，记入终态'); loadSalaries()
  } catch (e: any) { showError(e, '发放失败') }
}
function showAdjust(salaryId: number) { adjustForm.salaryId = salaryId; adjustForm.adjustAmount = 0; adjustForm.reason = ''; adjustVisible.value = true }
async function saveAdjust() {
  if (!adjustForm.reason?.trim()) { ElMessage.warning('请填写调整原因'); return }
  if (!adjustForm.adjustAmount) { ElMessage.warning('调整金额不能为 0'); return }
  adjustSaving.value = true
  try {
    await salaryApi.adjustments({ ...adjustForm })
    ElMessage.success('调整已保存'); adjustVisible.value = false
    loadSalaries()
  } catch (e: any) { showError(e, '调整失败') } finally { adjustSaving.value = false }
}

onMounted(() => { loadOptions(); loadRules(); loadSalaries() })
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.page-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.page-desc {
  color: #909399;
  font-size: 13px;
}
.salary-tabs {
  background: #fff;
  padding: 0 16px;
  border-radius: 4px;
}

.filter-card,
.calc-card,
.list-card {
  margin-top: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.filter-card :deep(.el-card__body),
.calc-card :deep(.el-card__body),
.list-card :deep(.el-card__body) {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.card-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.list-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 12px 0;
}

.monthly-card {
  margin-top: 12px;
  border: none;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.monthly-card :deep(.el-card__body) { padding: 16px; }

.monthly-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.monthly-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  background: #fafbfc;
  cursor: pointer;
  transition: all 0.2s;
}
.monthly-item:hover {
  border-color: #409EFF;
  background: #ecf5ff;
}
.monthly-item.active {
  border-color: #409EFF;
  background: #ecf5ff;
  box-shadow: 0 0 0 2px rgba(64,158,255,0.15);
}
.m-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e4e7ed;
  margin-bottom: 8px;
}
.m-month {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-total {
  font-size: 14px;
  font-weight: 600;
  color: #f56c6c;
}
.m-body { display: flex; flex-direction: column; gap: 4px; }
.m-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #606266;
}
.m-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.dot-primary { background: #409EFF; }
.dot-success { background: #67C23A; }
.dot-warning { background: #E6A23C; }
.dot-info    { background: #909399; }
.stat-card {
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 18px;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}
.stat-extra {
  margin-top: 4px;
  font-size: 12px;
  color: #606266;
}

.total-amount {
  font-weight: 600;
  color: #f56c6c;
}
.time-text {
  color: #606266;
  font-size: 12px;
}
.muted-tip {
  color: #c0c4cc;
  font-size: 12px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.batch-summary .summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  font-size: 14px;
}
.batch-summary .summary-label {
  color: #909399;
  width: 80px;
}
.batch-summary .summary-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 16px 0;
}
.batch-summary .stat-block {
  border-radius: 6px;
  padding: 14px;
  text-align: center;
  color: #fff;
}
.batch-summary .stat-block.success { background: #67C23A; }
.batch-summary .stat-block.danger { background: #F56C6C; }
.batch-summary .stat-block.primary { background: #409EFF; }
.batch-summary .stat-num {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}
.batch-summary .stat-name {
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.9;
}
.batch-summary .error-title {
  margin: 12px 0 8px;
  font-weight: 600;
  color: #F56C6C;
}

@media (max-width: 1100px) {
  .stat-row { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 700px) {
  .stat-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
