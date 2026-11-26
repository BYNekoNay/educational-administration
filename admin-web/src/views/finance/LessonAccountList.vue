<template>
  <div>
    <div class="page-header"><h3>课时账户</h3></div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索学员或课程" clearable style="width:260px"
        @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="courseName" label="课程" min-width="100" />
      <el-table-column prop="totalLessons" label="总课时" width="80" sortable="custom" />
      <el-table-column prop="remainingLessons" label="剩余课时" width="100" sortable="custom">
        <template #default="{ row }">
          <span :class="{ 'balance-warn': (row.remainingLessons || 0) <= 3 }">
            {{ row.remainingLessons || 0 }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="expireDate" label="到期日期" width="120" sortable="custom" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAdjust(row)">调整课时</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="loaded && total > 0" style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="loadData" />

    <LessonAdjustDialog v-model="adjustVisible" :account="current" @adjusted="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { lessonAccountApi } from '@/api/finance'
import { showError } from '@/utils/error'
import LessonAdjustDialog from './components/LessonAdjustDialog.vue'
import type { LessonAccount } from '@/types'

const loading = ref(false)
const loaded = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const tableData = ref<LessonAccount[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)

// 后端已支持 keyword（学员姓名/课程名），走服务端过滤；原先的 filteredData 只过滤当前页
function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }

// ---- 课时人工调整：弹窗已抽成 LessonAdjustDialog（收费页复用）----
const adjustVisible = ref(false)
const current = ref<LessonAccount | null>(null)

function openAdjust(row: LessonAccount) {
  current.value = row
  adjustVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res = await lessonAccountApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined,
      // 后端已支持 keyword（按学员姓名/课程名匹配）；清空时传空串而不是残留上一次的关键字
      keyword: keyword.value || '' })
    tableData.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  }   catch (e) { showError(e, '加载课时账户失败') }
  finally { loading.value = false; loaded.value = true }
}

function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

onMounted(loadData)
</script>

<style scoped>
.balance-warn { color: var(--color-error); font-weight: bold; }
</style>
