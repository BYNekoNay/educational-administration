<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <h3>教室管理</h3>
      <el-button type="primary" @click="openDialog(null)">新增教室</el-button>
    </div>
    <div style="margin-bottom:12px;display:flex;gap:8px">
      <el-input v-model="keyword" placeholder="搜索教室名称/校区" clearable style="width:240px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe @sort-change="handleSortChange">
      <el-table-column prop="id" label="ID" width="60" sortable="custom" />
      <el-table-column prop="name" label="教室名称" sortable="custom" />
      <el-table-column prop="capacity" label="容量" width="80" />
      <el-table-column prop="campus" label="校区" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{row}"><el-tag :type="row.status===1?'success':'warning'">{{row.status===1?'启用':'停用'}}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{row}">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;justify-content:flex-end" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" layout="sizes, total, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="loadData" />

    <el-dialog :title="isEdit?'编辑教室':'新增教室'" v-model="dialogVisible" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="1" /></el-form-item>
        <el-form-item label="校区"><el-input v-model="form.campus" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option :value="1" label="启用"/><el-option :value="0" label="停用"/></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import { classroomApi } from '@/api/edu'
import { showError } from '@/utils/error'

const loading=ref(false),saving=ref(false)
const keyword=ref(''),sortField=ref(''),sortOrder=ref('')
const tableData=ref<any[]>([]),pageNum=ref(1),pageSize=ref(10),total=ref(0)
const dialogVisible=ref(false),isEdit=ref(false)
const form=reactive<any>({name:'',capacity:10,campus:'',status:1})

async function loadData(){loading.value=true;try{const r=await classroomApi.list({pageNum:pageNum.value,pageSize:pageSize.value,keyword:keyword.value||undefined,sortField:sortField.value||undefined,sortOrder:sortOrder.value||undefined});tableData.value=r.data?.records||[];total.value=r.data?.total||0}catch(e){showError(e,'加载教室失败')}finally{loading.value=false}}
function handleSearch(){pageNum.value=1;loadData()}
function resetSearch(){keyword.value='';sortField.value='';sortOrder.value='';pageNum.value=1;loadData()}
function handleSortChange({prop,order}:any){sortField.value=order?prop:'';sortOrder.value=order==='ascending'?'asc':order==='descending'?'desc':'';pageNum.value=1;loadData()}

function openDialog(row:any){isEdit.value=!!row;if(row)Object.assign(form,row);else Object.assign(form,{name:'',capacity:10,campus:'',status:1});dialogVisible.value=true}
async function handleSave(){saving.value=true;try{if(isEdit.value){await classroomApi.update(form.id,form);ElMessage.success('已更新')}else{await classroomApi.create(form);ElMessage.success('已创建')}dialogVisible.value=false;loadData()}catch(e){showError(e,'保存失败')}finally{saving.value=false}}
async function handleDelete(row:any){try{await ElMessageBox.confirm('确定删除？','提示',{type:'warning'})}catch{return};try{await classroomApi.delete(row.id);ElMessage.success('已删除');loadData()}catch(e){showError(e,'删除失败')}}
onMounted(loadData)
</script>
