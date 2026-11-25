<template>
  <div class="teacher-page">
    <div class="page-header">
      <h2>教师管理</h2>
      <p class="page-sub">档案 · 当前任教 · 历史任教 · 换师 · 离职</p>
    </div>

    <!-- 搜索 / 筛选 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索姓名 / 账号 / 手机"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="statusFilter"
        placeholder="全部状态"
        clearable
        style="width: 150px"
        @change="handleSearch"
      >
        <el-option label="在职（含待离职）" :value="1" />
        <el-option label="已停用 / 已离职" :value="0" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="tableData" border stripe v-loading="loading">
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column prop="username" label="账号" width="120" />
      <el-table-column label="手机" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="入职日" width="120">
        <template #default="{ row }">{{ row.hireDate || '未录入' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="180">
        <template #default="{ row }">
          <el-tag :type="row.statusType || 'info'" size="small">{{ row.statusLabel || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="密码" width="92">
        <template #default="{ row }">
          <el-tag v-if="row.mustChangePassword === 1" type="warning" size="small">待改密</el-tag>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="可授课程" min-width="180">
        <template #default="{ row }">
          <template v-if="row.specialties && row.specialties.length">
            <el-tag
              v-for="sp in row.specialties"
              :key="sp.id"
              size="small"
              type="info"
              class="course-tag"
            >{{ sp.name }}</el-tag>
          </template>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" :disabled="row.status !== 1" @click="openTransfer(row)">换师</el-button>
          <el-button link type="danger" :disabled="row.status !== 1" @click="openResign(row)">离职</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- 详情弹窗：档案 / 当前任教 / 历史任教 -->
    <el-dialog v-model="detailVisible" title="教师详情" width="760px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-tabs v-if="detail" v-model="detailTab">
          <el-tab-pane label="档案" name="profile">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="姓名">{{ detail.realName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="账号">{{ detail.username }}</el-descriptions-item>
              <el-descriptions-item label="手机">{{ detail.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="入职日">{{ detail.hireDate || '未录入' }}</el-descriptions-item>
              <el-descriptions-item label="离职日">{{ detail.leaveDate || '在职' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="detail.statusType || 'info'" size="small">{{ detail.statusLabel }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="密码状态">
                <el-tag v-if="detail.mustChangePassword === 1" type="warning" size="small">待改密</el-tag>
                <span v-else>正常</span>
              </el-descriptions-item>
              <el-descriptions-item label="可授课程">
                <template v-if="detail.specialties && detail.specialties.length">
                  <el-tag
                    v-for="sp in detail.specialties"
                    :key="sp.id"
                    size="small"
                    type="info"
                    class="course-tag"
                  >{{ sp.name }}</el-tag>
                </template>
                <span v-else class="muted">未设置</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane :label="`当前任教（${detail.currentClasses?.length || 0}）`" name="current">
            <el-table :data="detail.currentClasses || []" border size="small">
              <el-table-column prop="className" label="班级" min-width="140" />
              <el-table-column prop="courseName" label="课程" min-width="120" />
              <el-table-column prop="studentCount" label="在班人数" width="100" />
              <el-table-column label="操作" width="110">
                <template #default="{ row }">
                  <el-button link type="primary" @click="transferFromDetail(row.classId)">换师</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!detail.currentClasses?.length" description="当前没有任教班级" :image-size="70" />
          </el-tab-pane>

          <el-tab-pane :label="`历史任教（${detail.historyClasses?.length || 0}）`" name="history">
            <el-table :data="detail.historyClasses || []" border size="small">
              <el-table-column prop="className" label="班级" min-width="140" />
              <el-table-column prop="courseName" label="课程" min-width="120" />
              <el-table-column prop="lessonCount" label="已完成课次" width="110" />
              <el-table-column prop="lastLessonDate" label="最后授课日" width="120" />
            </el-table>
            <el-empty v-if="!detail.historyClasses?.length" description="暂无历史任教记录" :image-size="70" />
            <p class="hint">历史任教来自已完成课次（status=2）回溯，换师后仍保留。</p>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <!-- 换师弹窗 -->
    <el-dialog v-model="transferVisible" title="变更任课教师" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="班级">
          <el-select v-model="transferForm.classId" placeholder="请选择班级" style="width: 100%">
            <el-option
              v-for="c in transferCandidates"
              :key="c.classId"
              :label="`${c.className}${c.courseName ? ' · ' + c.courseName : ''}`"
              :value="c.classId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新教师">
          <el-select v-model="transferForm.teacherId" placeholder="请选择接任教师" filterable style="width: 100%">
            <el-option
              v-for="t in otherTeachers"
              :key="t.id"
              :label="`${t.realName || t.username}${t.username ? '（' + t.username + '）' : ''}`"
              :value="t.id!"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="hint">
        只改派「未完成且未来」的课次；已完成课次与历史薪资不会被修改。
      </p>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferSaving" @click="submitTransfer">确定</el-button>
      </template>
    </el-dialog>

    <!-- 离职弹窗 -->
    <el-dialog v-model="resignVisible" title="办理离职" width="460px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="教师">
          <span>{{ resignTarget?.realName || resignTarget?.username }}</span>
        </el-form-item>
        <el-form-item label="离职生效日" required>
          <el-date-picker
            v-model="resignForm.leaveDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择离职日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <p class="hint">
        提交后该教师账号立即停用、已登录会话立即失效；离职日可填未来日期，仅作记录。
      </p>
      <template #footer>
        <el-button @click="resignVisible = false">取消</el-button>
        <el-button type="danger" :loading="resignSaving" @click="submitResign">确认离职</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { teacherApi } from '@/api/edu'
import type { TeacherDetail, TeacherInfo, TeacherListItem, TeachingClassVO } from '@/types'

const loading = ref(false)
const tableData = ref<TeacherListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

// ---- 详情 ----
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('profile')
const detail = ref<TeacherDetail | null>(null)

// ---- 换师 ----
const transferVisible = ref(false)
const transferSaving = ref(false)
const transferCandidates = ref<TeachingClassVO[]>([])
const teachers = ref<TeacherInfo[]>([])
const transferForm = reactive<{ classId?: number; teacherId?: number }>({})

const otherTeachers = computed(() =>
  teachers.value.filter(t => t.id !== detail.value?.id)
)

// ---- 离职 ----
const resignVisible = ref(false)
const resignSaving = ref(false)
const resignTarget = ref<TeacherListItem | null>(null)
const resignForm = reactive<{ leaveDate: string }>({ leaveDate: '' })

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const res = await teacherApi.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: statusFilter.value,
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载教师列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadData()
}

function resetSearch() {
  keyword.value = ''
  statusFilter.value = undefined
  pageNum.value = 1
  loadData()
}

async function openDetail(row: TeacherListItem) {
  detailVisible.value = true
  detailTab.value = 'profile'
  detail.value = null
  detailLoading.value = true
  try {
    const res = await teacherApi.getDetail(row.id)
    detail.value = res.data || null
  } catch (e: any) {
    ElMessage.error(e?.message || '加载教师详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function ensureTeachersLoaded() {
  if (teachers.value.length) return
  try {
    const res = await teacherApi.list()
    teachers.value = res.data || []
  } catch {
    teachers.value = []
  }
}

async function openTransfer(row: TeacherListItem, classId?: number) {
  transferForm.classId = classId
  transferForm.teacherId = undefined
  transferVisible.value = true
  await ensureTeachersLoaded()

  // 若详情已加载则直接复用，否则单独拉一次
  if (detail.value && detail.value.id === row.id) {
    transferCandidates.value = detail.value.currentClasses || []
  } else {
    try {
      const res = await teacherApi.getDetail(row.id)
      transferCandidates.value = res.data?.currentClasses || []
    } catch {
      transferCandidates.value = []
    }
  }

  if (!transferForm.classId && transferCandidates.value.length === 1) {
    transferForm.classId = transferCandidates.value[0].classId
  }
}

async function submitTransfer() {
  if (!transferForm.classId) {
    ElMessage.warning('请选择班级')
    return
  }
  if (!transferForm.teacherId) {
    ElMessage.warning('请选择接任教师')
    return
  }
  transferSaving.value = true
  try {
    const res = await teacherApi.transferTeacher(transferForm.classId, transferForm.teacherId)
    const count = res.data ?? 0
    ElMessage.success(`已变更任课教师，改派未来课次 ${count} 条`)
    transferVisible.value = false
    loadData()
    const current = detail.value
    if (detailVisible.value && current) {
      openDetail(current)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '变更失败')
  } finally {
    transferSaving.value = false
  }
}

/** 详情「当前任教」行内换师：避免在模板里写 TS 非空断言 */
function transferFromDetail(classId: number) {
  const current = detail.value
  if (!current) return
  openTransfer(current, classId)
}

function openResign(row: TeacherListItem) {
  resignTarget.value = row
  resignForm.leaveDate = new Date().toISOString().slice(0, 10)
  resignVisible.value = true
}

async function submitResign() {
  if (!resignTarget.value) return
  if (!resignForm.leaveDate) {
    ElMessage.warning('请选择离职生效日')
    return
  }
  resignSaving.value = true
  try {
    await teacherApi.resign(resignTarget.value.id, resignForm.leaveDate)
    ElMessage.success('已办理离职，该教师账号已停用')
    resignVisible.value = false
    detailVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '办理离职失败')
  } finally {
    resignSaving.value = false
  }
}
</script>

<style scoped>
.teacher-page {
  padding: 4px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.page-sub {
  margin: 0 0 16px;
  color: #909399;
  font-size: 12px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.course-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.muted {
  color: #909399;
}

.hint {
  margin: 8px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>
