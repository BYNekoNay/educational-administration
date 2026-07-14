<template>
  <div>
    <h3 style="margin-bottom: 16px">薪资管理</h3>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索教师" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="薪资规则" name="rules">
        <div style="margin-bottom: 12px">
          <el-button type="primary" @click="showRuleDialog(null)">新增规则</el-button>
        </div>
        <el-table :data="rules" v-loading="rulesLoading" border stripe @sort-change="(v:any) => handleSortChange(v, 'rules')">
          <el-table-column prop="id" label="ID" width="60" sortable="custom" />
          <el-table-column prop="teacherName" label="教师" min-width="80" sortable />
          <el-table-column prop="courseName" label="课程" min-width="100" sortable />
          <el-table-column prop="lessonUnitPrice" label="课时单价" width="100" sortable="custom">
            <template #default="{ row }">¥{{ row.lessonUnitPrice || 0 }}</template>
          </el-table-column>
          <el-table-column prop="substituteRate" label="代课系数" width="80" sortable="custom" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="showRuleDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 12px; justify-content: flex-end" small
          v-model:current-page="rulesPage" v-model:page-size="rulesPageSize"
          :total="rulesTotal" layout="total, prev, pager, next" @change="loadRules" />

        <el-dialog :title="editingRule?.id ? '编辑规则' : '新增规则'" v-model="ruleVisible" width="400px">
          <el-form :model="ruleForm" label-width="90px">
            <el-form-item label="教师">
              <el-select v-model="ruleForm.teacherId" placeholder="请选择教师" filterable style="width:100%">
                <el-option v-for="t in teacherList" :key="t.id" :label="t.realName" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="课程">
              <el-select v-model="ruleForm.courseId" placeholder="请选择课程" filterable style="width:100%">
                <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="课时单价"><el-input-number v-model="ruleForm.lessonUnitPrice" :min="0" :precision="2" /></el-form-item>
            <el-form-item label="代课系数"><el-input-number v-model="ruleForm.substituteRate" :min="0" :precision="2" :step="0.1" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="ruleVisible = false">取消</el-button>
            <el-button type="primary" @click="saveRule" :loading="ruleSaving">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="薪资列表" name="salaries">
        <div style="display: flex; gap: 12px; margin-bottom: 12px; align-items: center">
          <el-select v-model="calcTeacherId" placeholder="选择教师" filterable style="width: 140px">
            <el-option v-for="t in teacherList" :key="t.id" :label="t.realName" :value="t.id" />
          </el-select>
          <el-input v-model="calcMonth" placeholder="月份(YYYY-MM)" style="width: 140px" />
          <el-input-number v-model="calcBonus" :min="0" :precision="2" placeholder="奖金" style="width: 100px" />
          <el-button type="primary" @click="handleCalculate" :loading="calculating">核算薪资</el-button>
        </div>
        <el-table :data="salaries" v-loading="salariesLoading" border stripe @sort-change="(v:any) => handleSortChange(v, 'salaries')">
          <el-table-column prop="id" label="ID" width="60" sortable="custom" />
          <el-table-column prop="teacherName" label="教师" min-width="80" sortable />
          <el-table-column prop="salaryMonth" label="月份" width="100" sortable="custom" />
          <el-table-column prop="lessonCount" label="主讲课时" width="80" sortable="custom" />
          <el-table-column prop="substituteCount" label="代课课时" width="80" sortable="custom" />
          <el-table-column prop="baseAmount" label="基础工资" width="100" sortable="custom">
            <template #default="{ row }">¥{{ row.baseAmount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="bonusAmount" label="奖金" width="80" sortable="custom">
            <template #default="{ row }">¥{{ row.bonusAmount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="totalAmount" label="应发工资" width="100" sortable="custom">
            <template #default="{ row }">
              <b>¥{{ row.totalAmount || 0 }}</b>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="calcSnapshotTime" label="核算时间" width="170" sortable="custom" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 1" size="small" type="success" @click="handleConfirm(row.id)">确认</el-button>
              <el-button v-if="row.status === 1 || row.status === 2" size="small" type="warning" @click="handleVoid(row.id)">作废</el-button>
              <el-button v-if="row.status === 2" size="small" @click="showAdjust(row.id)">调整</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 12px; justify-content: flex-end" small
          v-model:current-page="salariesPage" v-model:page-size="salariesPageSize"
          :total="salariesTotal" layout="total, prev, pager, next" @change="loadSalaries" />

        <el-dialog title="薪资调整" v-model="adjustVisible" width="400px">
          <el-form :model="adjustForm" label-width="80px">
            <el-form-item label="薪资ID"><span>{{ adjustForm.salaryId }}</span></el-form-item>
            <el-form-item label="调整金额"><el-input-number v-model="adjustForm.adjustAmount" :precision="2" />
              <span style="font-size:12px;color:#909399;margin-left:8px">正数补发/负数扣回</span>
            </el-form-item>
            <el-form-item label="原因"><el-input v-model="adjustForm.reason" type="textarea" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="adjustVisible = false">取消</el-button>
            <el-button type="primary" @click="saveAdjust" :loading="adjustSaving">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { salaryApi } from '@/api/finance'
import { teacherApi, courseApi } from '@/api/edu'

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
  } catch { /* ignore */ }
}

async function loadRules() {
  rulesLoading.value = true
  const res = await salaryApi.rules({ pageNum: rulesPage.value, pageSize: rulesPageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
  rules.value = res.data.records; rulesTotal.value = res.data.total; rulesLoading.value = false
}
function handleSearch() { rulesPage.value = 1; salariesPage.value = 1; activeTab.value === 'rules' ? loadRules() : loadSalaries() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; rulesPage.value = 1; salariesPage.value = 1; activeTab.value === 'rules' ? loadRules() : loadSalaries() }
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
const calculating = ref(false)
const calcTeacherId = ref<number | null>(null), calcMonth = ref('2026-07'), calcBonus = ref(0)

const adjustVisible = ref(false), adjustSaving = ref(false)
const adjustForm = reactive({ salaryId: 0, adjustAmount: 0, reason: '' })

function statusLabel(s: number) { const map: Record<number, string> = { 1: '待确认', 2: '已确认', 4: '已撤销' }; return map[s] || s }
function statusTag(s: number) { const map: Record<number, string> = { 1: 'primary', 2: 'success', 4: 'info' }; return map[s] || 'info' }

async function loadSalaries() {
  salariesLoading.value = true
  const res = await salaryApi.list({ pageNum: salariesPage.value, pageSize: salariesPageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
  salaries.value = res.data.records; salariesTotal.value = res.data.total; salariesLoading.value = false
}

async function handleCalculate() {
  calculating.value = true
  try {
    const res = await salaryApi.calculate({ salaryMonth: calcMonth.value, teacherId: calcTeacherId.value, bonusAmount: calcBonus.value })
    ElMessage.success(`核算完成：主讲${res.data.lessonCount}课时 代课${res.data.substituteCount}课时 应发¥${res.data.totalAmount}`)
    loadSalaries()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '核算失败') } finally { calculating.value = false }
}

async function handleConfirm(id: number) {
  await salaryApi.confirm(id)
  ElMessage.success('薪资已确认'); loadSalaries()
}
async function handleVoid(id: number) {
  ElMessageBox.confirm('确认作废该薪资单？', '作废确认', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
    .then(async () => { await salaryApi.void(id); ElMessage.success('已作废，可重新核算'); loadSalaries() }).catch(() => {})
}
function showAdjust(salaryId: number) { adjustForm.salaryId = salaryId; adjustForm.adjustAmount = 0; adjustForm.reason = ''; adjustVisible.value = true }
async function saveAdjust() {
  adjustSaving.value = true
  try {
    await salaryApi.adjustments({ ...adjustForm })
    ElMessage.success('调整已保存'); adjustVisible.value = false
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '调整失败') } finally { adjustSaving.value = false }
}

onMounted(() => { loadOptions(); loadRules(); loadSalaries() })
</script>
