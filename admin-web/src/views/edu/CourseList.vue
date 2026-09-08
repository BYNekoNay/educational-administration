<template>
  <div>
    <div class="page-header">
      <h3>课程管理</h3>
      <el-button type="primary" @click="openDialog(null)">新增课程</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索课程名称" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="name" label="课程名称" sortable="custom" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="totalLessons" label="总课时" width="80" />
      <el-table-column prop="lessonDuration" label="时长(分)" width="80" />
      <el-table-column prop="price" label="价格" width="100" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="sizes, total, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      @change="loadData"
    />

    <el-dialog :title="isEdit ? '编辑课程' : '新增课程'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="课程名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="form.category"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="输入关键字搜索已有分类，不匹配则直接新增"
            style="width:100%"
            :no-data-text="''"
          >
            <el-option
              v-for="c in categoryOptions"
              :key="c"
              :label="c"
              :value="c"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="总课时"><el-input-number v-model="form.totalLessons" :min="1" /></el-form-item>
        <!-- 后端 updateCourse 白名单不含 lessonDuration，编辑态禁用避免假成功 -->
        <el-form-item label="时长(分钟)"><el-input-number v-model="form.lessonDuration" :min="1" :disabled="isEdit" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option :value="1" label="启用" /><el-option :value="0" label="停用" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi } from '@/api/edu'
import { showError } from '@/utils/error'

const loading = ref(false)
const saving = ref(false)
const keyword = ref(''), sortField = ref(''), sortOrder = ref('')
const tableData = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
// 已有的课程分类（去重），用于"分类"下拉的搜索+新增
const categoryOptions = ref<string[]>([])
const form = reactive<any>({ name: '', category: '', totalLessons: 1, lessonDuration: 45, price: 0, status: 1 })

async function loadData() {
  loading.value = true
  try {
    const res = await courseApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined, sortField: sortField.value || undefined, sortOrder: sortOrder.value || undefined })
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) { showError(e, '加载课程列表失败') }
  finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; loadData() }
function resetSearch() { keyword.value = ''; sortField.value = ''; sortOrder.value = ''; pageNum.value = 1; loadData() }
function handleSortChange({ prop, order }: any) {
  sortField.value = order ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  pageNum.value = 1; loadData()
}

function openDialog(row: any) {
  isEdit.value = !!row
  if (row) Object.assign(form, row)
  else Object.assign(form, { name: '', category: '', totalLessons: 1, lessonDuration: 45, price: 0, status: 1 })
  loadCategoryOptions()
  dialogVisible.value = true
}

/** 拉取课程（后端 pageSize 上限 200，超出被静默钳制），提取去重的分类列表 */
async function loadCategoryOptions() {
  try {
    const res = await courseApi.list({ pageNum: 1, pageSize: 200 })
    const set = new Set<string>()
    ;(res.data?.records || []).forEach((c: any) => {
      if (c.category) set.add(c.category)
    })
    categoryOptions.value = Array.from(set).sort()
  } catch (e) { showError(e, '加载课程分类失败') }
}

async function handleSave() {
  saving.value = true
  try {
    if (isEdit.value) {
      await courseApi.update(form.id, form)
      ElMessage.success('课程已更新')
    } else {
      await courseApi.create(form)
      ElMessage.success('课程已创建')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    showError(e, '保存失败')
  } finally { saving.value = false }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该课程？', '提示', { type: 'warning' })
    await courseApi.delete(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    showError(e, '删除失败')
  }
}

onMounted(loadData)
</script>
