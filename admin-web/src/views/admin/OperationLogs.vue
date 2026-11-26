<template>
  <div>
    <div class="page-header"><h3>操作日志</h3></div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-select v-model="operatorId" placeholder="全部操作人" filterable clearable style="width:180px"
        @change="handleSearch">
        <el-option v-for="u in operatorUsers" :key="u.id" :label="u.realName || u.username" :value="u.id" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索模块或操作" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <el-table
      :data="logs"
      border
      stripe
      v-loading="loading"
      size="small"
      @sort-change="handleSortChange"
    >
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column prop="module" label="模块" width="120" sortable="custom" />
      <el-table-column prop="operation" label="操作" min-width="200" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="createTime" label="操作时间" width="180" sortable="custom">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="loadLogs"
        @size-change="loadLogs"
        background
        size="small"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { showError } from '@/utils/error'
import { operationLogApi, userApi } from '@/api/auth'
import type { OperationLog, UserInfo } from '@/types'

const logs = ref<OperationLog[]>([])
const loading = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const operatorId = ref<number | undefined>(undefined)
const operatorUsers = ref<UserInfo[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadOperators() {
  try {
    const res = await userApi.list({ pageNum: 1, pageSize: 200 })
    operatorUsers.value = res.data?.records || []
  } catch {
    // 下拉数据源失败不阻塞日志列表本身
    operatorUsers.value = []
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const res = await operationLogApi.list({ pageNum: currentPage.value, pageSize: pageSize.value,
      keyword: keyword.value || undefined, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined,
      operatorId: operatorId.value || undefined })
    const data = res.data
    logs.value = data?.records || []
    total.value = data?.total || 0
  } catch (e) {
    showError(e, '加载操作日志失败')
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() { currentPage.value = 1; loadLogs() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; operatorId.value = undefined; currentPage.value = 1; loadLogs() }
function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  currentPage.value = 1; loadLogs()
}

function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  try {
    const d = new Date(dateStr)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return dateStr
  }
}

onMounted(() => {
  loadLogs()
  loadOperators()
})
</script>
