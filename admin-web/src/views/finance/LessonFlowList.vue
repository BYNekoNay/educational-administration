<template>
  <div>
    <h3 style="margin-bottom: 16px">课时流水</h3>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column label="来源类型" width="90">
        <template #default="{ row }">
          <el-tag :type="sourceTypeTag(row.sourceType)" size="small">{{ sourceTypeLabel(row.sourceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="变动课时" width="100">
        <template #default="{ row }">
          <span :class="row.changeAmount >= 0 ? 'amount-plus' : 'amount-minus'">
            {{ row.changeAmount >= 0 ? '+' : '' }}{{ row.changeAmount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="beforeBalance" label="变动前" width="80" />
      <el-table-column prop="afterBalance" label="变动后" width="80" />
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column prop="createTime" label="时间" width="170" />
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="total, prev, pager, next" @change="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { lessonFlowApi } from '@/api/finance'

const loading = ref(false)
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)

function sourceTypeLabel(v: number) {
  const map: Record<number, string> = { 1: '充值', 2: '消费', 3: '回冲', 4: '退费' }
  return map[v] || '其他'
}
function sourceTypeTag(v: number) {
  const map: Record<number, string> = { 1: 'primary', 2: 'warning', 3: 'info', 4: 'danger' }
  return map[v] || 'info'
}

async function loadData() {
  loading.value = true
  const res = await lessonFlowApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
  tableData.value = res.data.records; total.value = res.data.total; loading.value = false
}

onMounted(loadData)
</script>

<style scoped>
.amount-plus { color: #67C23A; }
.amount-minus { color: #F56C6C; }
</style>
