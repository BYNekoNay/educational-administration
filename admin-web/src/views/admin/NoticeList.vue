<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>公告管理</h3>
      <el-button type="primary" @click="showDialog(null)">发布公告</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索公告标题" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="title" label="标题" min-width="180" sortable />
      <el-table-column prop="noticeType" label="类型" width="100" sortable>
        <template #default="{ row }">
          <el-tag :type="tagStyle(row.noticeType)" size="small">
            {{ noticeTypeLabel(row.noticeType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="receiverType" label="接收范围" width="100" sortable>
        <template #default="{ row }">
          {{ receiverTypeLabel(row.receiverType) }}
        </template>
      </el-table-column>
      <el-table-column prop="publishTime" label="发布时间" width="170" sortable="custom" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum" v-model:page-size="pageSize"
      :total="total" layout="total, prev, pager, next" @change="loadData" />

    <el-dialog :title="editing?.id ? '编辑公告' : '发布公告'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="通知类型">
          <el-select v-model="form.noticeType">
            <el-option :value="1" label="公告" />
            <el-option :value="2" label="调课通知" />
            <el-option :value="3" label="上课提醒" />
            <el-option :value="4" label="课时不足提醒" />
            <el-option :value="5" label="报名留位到期" />
            <el-option :value="6" label="考级通知" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收范围">
          <el-select v-model="form.receiverType">
            <el-option value="ALL" label="全体" />
            <el-option value="TEACHER" label="教师" />
            <el-option value="PARENT" label="家长" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noticeApi } from '@/api/auth'
import { showError } from '@/utils/error'

const loading = ref(false), saving = ref(false)
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const dialogVisible = ref(false)
const editing = ref<any>(null)
const form = reactive<any>({ title: '', content: '', noticeType: 1, receiverType: 'ALL' })

// ---- 枚举映射 ----

const noticeTypeLabels: Record<number, string> = { 1: '公告', 2: '调课通知', 3: '上课提醒', 4: '课时不足提醒', 5: '报名留位到期', 6: '考级通知' }

function noticeTypeLabel(v: any): string {
  return noticeTypeLabels[Number(v)] || '公告'
}

function tagStyle(v: any): string {
  const n = Number(v)
  return n === 2 ? 'warning' : n === 3 ? 'primary' : n === 6 ? 'danger' : 'info'
}

function receiverTypeLabel(v: any): string {
  const map: Record<string, string> = { 'ALL': '全体', 'TEACHER': '教师', 'PARENT': '家长' }
  // 兼容历史存为数字的情况
  const numMap: Record<string, string> = { '1': '全体', '2': '教师', '3': '家长' }
  return map[String(v)] || numMap[String(v)] || String(v) || '全体'
}

// ---- 数据加载 ----

async function loadData() {
  loading.value = true
    const res = await noticeApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
    tableData.value = res.data.records; total.value = res.data.total; loading.value = false
}

function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }
function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

function showDialog(row: any) {
  editing.value = row
  if (row) {
    form.title = row.title
    form.content = row.content
    form.noticeType = Number(row.noticeType) || 1
    form.receiverType = row.receiverType || 'ALL'
  } else {
    form.title = ''
    form.content = ''
    form.noticeType = 1
    form.receiverType = 'ALL'
  }
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    const payload = { ...form, publishTime: new Date().toISOString().slice(0, 19) }
    if (editing.value?.id) {
      await noticeApi.update(editing.value.id, payload)
    } else {
      await noticeApi.create(payload)
    }
    ElMessage.success(editing.value?.id ? '已更新' : '已发布')
    dialogVisible.value = false; loadData()
  } catch (e) { showError(e, '操作失败') } finally { saving.value = false }
}

async function handleDelete(id: number) {
  ElMessageBox.confirm('确认删除？', '提示', { confirmButtonText: '确认', type: 'warning' }).then(async () => {
    await noticeApi.delete(id); ElMessage.success('已删除'); loadData()
  }).catch(() => {})
}

onMounted(loadData)
</script>
