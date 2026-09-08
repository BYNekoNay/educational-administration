<template>
  <div>
    <div class="page-header">
      <h3>报名审核</h3>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索学员/课程" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="studentName" label="学员" min-width="80" />
      <el-table-column prop="parentName" label="家长" min-width="80" />
      <el-table-column prop="courseName" label="课程" min-width="100" />
      <el-table-column prop="className" label="意向班级" min-width="120" />
      <el-table-column prop="status" label="状态" width="100" sortable="custom">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="warning">待审核</el-tag>
          <el-tag v-else-if="row.status === 2" type="primary">待缴费</el-tag>
          <el-tag v-else-if="row.status === 3" type="success">已完成</el-tag>
          <el-tag v-else-if="row.status === 4" type="danger">已拒绝</el-tag>
          <el-tag v-else-if="row.status === 5" type="info">已失效</el-tag>
          <el-tag v-else-if="row.status === 6" type="info">已退费</el-tag>
          <el-tag v-else type="info">未知</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditorName" label="审核人" min-width="80" />
      <el-table-column prop="auditRemark" label="审核备注" min-width="120" />
      <el-table-column prop="createTime" label="申请时间" width="170" sortable="custom" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
            <template v-if="row.status === 1">
              <el-button size="small" type="success" @click="handleAudit(row, 2)" :loading="auditing">通过</el-button>
              <el-button size="small" type="danger" @click="showReject(row)">拒绝</el-button>
            </template>
            <span v-else style="color: #999">--</span>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]"
      @change="loadData"
    />

    <!-- 拒绝原因弹窗 -->
    <el-dialog title="拒绝原因" v-model="rejectVisible" width="400px">
      <el-input v-model="rejectRemark" placeholder="请输入拒绝原因" type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="handleAudit(rejectRow, 4)" :loading="auditing">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { enrollmentApi } from '@/api/edu'
import { showError } from '@/utils/error'

const loading = ref(false), auditing = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)
const rejectVisible = ref(false), rejectRow = ref<any>(null), rejectRemark = ref('')

async function loadData() {
  loading.value = true
  try {
    // keyword 目前被后端报名分页接口丢弃（第八轮待办：后端补关键字过滤），保留传参以便后端支持后自动生效
    const res = await enrollmentApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
    tableData.value = res.data.records; total.value = res.data.total
  } catch (e) { showError(e, '加载报名列表失败') }
  finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }
function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

function showReject(row: any) { rejectRow.value = row; rejectRemark.value = ''; rejectVisible.value = true }

async function handleAudit(row: any, status: number) {
  auditing.value = true
  try {
    await enrollmentApi.audit(row.id, { status, remark: status === 4 ? rejectRemark.value : '' })
    ElMessage.success(status === 2 ? '审核通过，进入待缴费' : '已拒绝')
    rejectVisible.value = false
    loadData()
  } catch (e) { showError(e, '审核失败') } finally { auditing.value = false }
}

onMounted(loadData)
</script>
