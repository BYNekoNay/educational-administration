<template>
  <div>
    <div class="page-header"><h3>课时流水</h3></div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索学员" clearable style="width:260px"
        @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column label="来源类型" width="90">
        <template #default="{ row }">
          <el-tag :type="sourceTypeTag(row.sourceType)" size="small">{{ sourceTypeLabel(row.sourceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="changeAmount" label="变动课时" width="100" sortable="custom">
        <template #default="{ row }">
          <span :class="row.changeAmount >= 0 ? 'amount-plus' : 'amount-minus'">
            {{ row.changeAmount >= 0 ? '+' : '' }}{{ row.changeAmount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="beforeBalance" label="变动前" width="80" />
      <el-table-column prop="afterBalance" label="变动后" width="80" />
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column prop="createTime" label="时间" width="170" sortable="custom" />
    </el-table>
    <el-pagination v-if="loaded && total > 0" style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { lessonFlowApi } from '@/api/finance'
import { showError } from '@/utils/error'
import type { LessonFlow } from '@/types'

const loading = ref(false)
const loaded = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const tableData = ref<LessonFlow[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)

// 后端已支持 keyword（学员姓名/课程名），走服务端过滤；原先的 filteredData 只过滤当前页，跨页搜不到
function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }

function sourceTypeLabel(v: number) {
  const map: Record<number, string> = { 1: '充值', 2: '消费', 3: '回冲', 4: '退费', 5: '人工调整' }
  return map[v] || '其他'
}
function sourceTypeTag(v: number) {
  const map: Record<number, string> = { 1: 'primary', 2: 'warning', 3: 'info', 4: 'danger', 5: 'success' }
  return map[v] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await lessonFlowApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined,
      // 后端已支持 keyword（按学员姓名/课程名匹配）；清空时传空串而不是残留上一次的关键字
      keyword: keyword.value || '' })
    tableData.value = res.data.records; total.value = res.data.total
  } catch (e) {
    showError(e, '加载课时流水失败')
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

onMounted(loadData)
</script>

<style scoped>
.amount-plus { color: var(--color-success); }
.amount-minus { color: var(--color-error); }
</style>
