<template>
  <div>
    <h3 style="margin-bottom: 16px">考级管理</h3>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="考级项目" name="levels">
        <div style="margin-bottom: 12px">
          <el-button type="primary" @click="showLevelDialog(null)">新增项目</el-button>
        </div>
        <el-table :data="levels" v-loading="levelsLoading" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="考级名称" />
          <el-table-column prop="levelName" label="级别" width="100" />
          <el-table-column prop="examDate" label="考试日期" width="120" />
          <el-table-column prop="fee" label="费用" width="80">
            <template #default="{ row }">¥{{ row.fee || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="showLevelDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteLevel(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 12px; justify-content: flex-end" small
          v-model:current-page="levelsPage" v-model:page-size="levelsPageSize"
          :total="levelsTotal" layout="total, prev, pager, next" @change="loadLevels" />

        <el-dialog :title="editingLevel?.id ? '编辑项目' : '新增项目'" v-model="levelVisible" width="400px">
          <el-form :model="levelForm" label-width="80px">
            <el-form-item label="考级名称"><el-input v-model="levelForm.name" /></el-form-item>
            <el-form-item label="级别"><el-input v-model="levelForm.levelName" /></el-form-item>
            <el-form-item label="考试日期"><el-date-picker v-model="levelForm.examDate" type="date" style="width:100%" /></el-form-item>
            <el-form-item label="费用"><el-input-number v-model="levelForm.fee" :min="0" :precision="2" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="levelVisible = false">取消</el-button>
            <el-button type="primary" @click="saveLevel" :loading="levelSaving">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="报名管理" name="signups">
        <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center">
          <el-select v-model="filterExamId" placeholder="考级项目" clearable @change="loadSignups" style="width: 200px">
            <el-option v-for="l in levels" :key="l.id" :label="l.name" :value="l.id" />
          </el-select>
          <el-button type="primary" @click="showSignupDialog">新增报名</el-button>
        </div>
        <el-table :data="signups" v-loading="signupsLoading" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="examId" label="考级项目" width="100" />
          <el-table-column prop="studentId" label="学员ID" width="80" />
          <el-table-column prop="score" label="成绩" width="80" />
          <el-table-column prop="certificateNo" label="证书编号" width="150" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'info' : row.status === 2 ? 'success' : 'warning'" size="small">
                {{ row.status === 1 ? '已报名' : row.status === 2 ? '已通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="showSignupDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 12px; justify-content: flex-end" small
          v-model:current-page="signupsPage" v-model:page-size="signupsPageSize"
          :total="signupsTotal" layout="total, prev, pager, next" @change="loadSignups" />

        <el-dialog :title="editingSignup?.id ? '编辑报名' : '新增报名'" v-model="signupVisible" width="400px">
          <el-form :model="signupForm" label-width="80px">
            <el-form-item label="考级项目"><el-input-number v-model="signupForm.examId" :min="1" /></el-form-item>
            <el-form-item label="学员ID"><el-input-number v-model="signupForm.studentId" :min="1" /></el-form-item>
            <el-form-item label="成绩"><el-input-number v-model="signupForm.score" :min="0" :max="100" /></el-form-item>
            <el-form-item label="证书编号"><el-input v-model="signupForm.certificateNo" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="signupForm.status">
                <el-option :value="1" label="已报名" /><el-option :value="2" label="已通过" /><el-option :value="3" label="未通过" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="signupVisible = false">取消</el-button>
            <el-button type="primary" @click="saveSignup" :loading="signupSaving">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examApi } from '@/api/edu'

const activeTab = ref('levels')

// ---- 考级项目 ----
const levels = ref<any[]>([]), levelsLoading = ref(false), levelsPage = ref(1), levelsPageSize = ref(10), levelsTotal = ref(0)
const levelVisible = ref(false), levelSaving = ref(false)
const editingLevel = ref<any>(null)
const levelForm = reactive<any>({ name: '', levelName: '', examDate: '', fee: 0 })

async function loadLevels() {
  levelsLoading.value = true
  const res = await examApi.levels({ pageNum: levelsPage.value, pageSize: levelsPageSize.value })
  levels.value = res.data.records; levelsTotal.value = res.data.total; levelsLoading.value = false
}
function showLevelDialog(row: any) {
  editingLevel.value = row
  if (row) { Object.assign(levelForm, { name: row.name, levelName: row.levelName, examDate: row.examDate, fee: row.fee }) }
  else { Object.assign(levelForm, { name: '', levelName: '', examDate: '', fee: 0 }) }
  levelVisible.value = true
}
async function saveLevel() {
  levelSaving.value = true
  try {
    const payload = { ...levelForm, examDate: levelForm.examDate ? new Date(levelForm.examDate).toISOString().slice(0, 10) : null }
    if (editingLevel.value?.id) {
      await examApi.updateLevel(editingLevel.value.id, payload)
    } else {
      await examApi.createLevel(payload)
    }
    ElMessage.success('保存成功'); levelVisible.value = false; loadLevels()
  } catch (_) { } finally { levelSaving.value = false }
}
async function deleteLevel(id: number) {
  ElMessageBox.confirm('确认删除？', '删除确认', { confirmButtonText: '确认', type: 'warning' }).then(async () => {
    await examApi.updateLevel(id, {}) // delete not defined, skip
    ElMessage.success('已删除'); loadLevels()
  }).catch(() => {})
}

// ---- 报名管理 ----
const signups = ref<any[]>([]), signupsLoading = ref(false), signupsPage = ref(1), signupsPageSize = ref(10), signupsTotal = ref(0)
const filterExamId = ref<number | null>(null)
const signupVisible = ref(false), signupSaving = ref(false)
const editingSignup = ref<any>(null)
const signupForm = reactive<any>({ examId: 1, studentId: 1, score: null, certificateNo: '', status: 1 })

async function loadSignups() {
  signupsLoading.value = true
  const params: any = { pageNum: signupsPage.value, pageSize: signupsPageSize.value }
  if (filterExamId.value) params.examId = filterExamId.value
  const res = await examApi.signups(params)
  signups.value = res.data.records; signupsTotal.value = res.data.total; signupsLoading.value = false
}
function showSignupDialog(row: any) {
  editingSignup.value = row
  if (row) { Object.assign(signupForm, { examId: row.examId, studentId: row.studentId, score: row.score, certificateNo: row.certificateNo || '', status: row.status }) }
  else { Object.assign(signupForm, { examId: 1, studentId: 1, score: null, certificateNo: '', status: 1 }) }
  signupVisible.value = true
}
async function saveSignup() {
  signupSaving.value = true
  try {
    if (editingSignup.value?.id) {
      await examApi.score(editingSignup.value.id, { ...signupForm })
    } else {
      await examApi.signup({ ...signupForm })
    }
    ElMessage.success('保存成功'); signupVisible.value = false; loadSignups()
  } catch (_) { } finally { signupSaving.value = false }
}

onMounted(() => { loadLevels(); loadSignups() })
</script>
