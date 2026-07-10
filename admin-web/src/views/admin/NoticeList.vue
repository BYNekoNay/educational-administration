<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>公告管理</h3>
      <el-button type="primary" @click="showDialog(null)">发布公告</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="noticeType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.noticeType === 1 ? 'warning' : row.noticeType === 2 ? 'primary' : 'info'" size="small">
            {{ row.noticeType === 1 ? '调课通知' : row.noticeType === 2 ? '上课提醒' : row.noticeType === 3 ? '考级通知' : '公告' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="receiverType" label="接收范围" width="100">
        <template #default="{ row }">
          {{ row.receiverType === 1 ? '全体' : row.receiverType === 2 ? '教师' : row.receiverType === 3 ? '家长' : '指定用户' }}
        </template>
      </el-table-column>
      <el-table-column prop="publishTime" label="发布时间" width="170" />
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
            <el-option :value="1" label="调课通知" /><el-option :value="2" label="上课提醒" />
            <el-option :value="3" label="考级通知" /><el-option :value="4" label="公告" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收范围">
          <el-select v-model="form.receiverType">
            <el-option :value="1" label="全体" /><el-option :value="2" label="教师" />
            <el-option :value="3" label="家长" /><el-option :value="4" label="指定用户" />
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

const loading = ref(false), saving = ref(false)
const tableData = ref<any[]>([])
const pageNum = ref(1), pageSize = ref(10), total = ref(0)
const dialogVisible = ref(false)
const editing = ref<any>(null)
const form = reactive<any>({ title: '', content: '', noticeType: 4, receiverType: 1 })

async function loadData() {
  loading.value = true
  const res = await noticeApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
  tableData.value = res.data.records; total.value = res.data.total; loading.value = false
}
function showDialog(row: any) {
  editing.value = row
  if (row) { form.title = row.title; form.content = row.content; form.noticeType = row.noticeType; form.receiverType = row.receiverType }
  else { form.title = ''; form.content = ''; form.noticeType = 4; form.receiverType = 1 }
  dialogVisible.value = true
}
async function handleSave() {
  saving.value = true
  try {
    const payload = { ...form, publishTime: new Date().toISOString().slice(0, 19).replace('T', ' ') }
    if (editing.value?.id) {
      await noticeApi.update(editing.value.id, payload)
    } else {
      await noticeApi.create(payload)
    }
    ElMessage.success(editing.value?.id ? '已更新' : '已发布')
    dialogVisible.value = false; loadData()
  } catch (_) { } finally { saving.value = false }
}
async function handleDelete(id: number) {
  ElMessageBox.confirm('确认删除？', '提示', { confirmButtonText: '确认', type: 'warning' }).then(async () => {
    await noticeApi.delete(id); ElMessage.success('已删除'); loadData()
  }).catch(() => {})
}

onMounted(loadData)
</script>
