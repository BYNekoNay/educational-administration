<template>
  <div>
    <h3 style="margin-bottom: 16px">课时账户</h3>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="courseName" label="课程" min-width="100" />
      <el-table-column prop="totalLessons" label="总课时" width="80" />
      <el-table-column prop="remainingLessons" label="剩余课时" width="100">
        <template #default="{ row }">
          <span :class="{ 'balance-warn': (row.remainingLessons || 0) <= 3 }">
            {{ row.remainingLessons || 0 }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="expireDate" label="到期日期" width="120" />
      <el-table-column prop="version" label="版本" width="60" />
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="total, prev, pager, next" @change="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { lessonAccountApi } from '@/api/finance'

const loading = ref(false)
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)

async function loadData() {
  loading.value = true
  const res = await lessonAccountApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
  tableData.value = res.data.records; total.value = res.data.total; loading.value = false
}

onMounted(loadData)
</script>

<style scoped>
.balance-warn { color: #F56C6C; font-weight: bold; }
</style>
