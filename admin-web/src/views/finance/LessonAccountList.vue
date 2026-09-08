<template>
  <div>
    <div class="page-header"><h3>课时账户</h3></div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索学员或课程" clearable style="width:260px" />
    </div>
    <el-table :data="filteredData" v-loading="loading" border stripe @sort-change="handleSortChange">
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
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { lessonAccountApi } from '@/api/finance'

const loading = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const filteredData = computed(() => {
  if (!keyword.value) return tableData.value
  const kw = keyword.value.toLowerCase()
  return tableData.value.filter((r: any) =>
    (r.studentName && String(r.studentName).toLowerCase().includes(kw)) ||
    (r.courseName && String(r.courseName).toLowerCase().includes(kw))
  )
})
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)

async function loadData() {
  loading.value = true
  const res = await lessonAccountApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
  tableData.value = res.data.records; total.value = res.data.total; loading.value = false
}

function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

onMounted(loadData)
</script>

<style scoped>
.balance-warn { color: var(--color-error); font-weight: bold; }
</style>
