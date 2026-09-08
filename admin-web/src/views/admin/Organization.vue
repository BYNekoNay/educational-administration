<template>
  <div>
    <div class="page-header"><h3>机构信息配置</h3></div>
    <el-card>
      <el-form label-width="100px" v-loading="loading">
        <el-form-item label="机构名称">
          <el-input v-model="form.orgName" placeholder="请输入机构名称" :disabled="!canSave" />
        </el-form-item>
        <el-form-item label="校区">
          <el-input v-model="form.campus" placeholder="请输入校区" :disabled="!canSave" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" :disabled="!canSave" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="请输入地址" :disabled="!canSave" />
        </el-form-item>
        <el-form-item>
          <!-- 后端 PUT /admin/organization 仅放行 SUPER_ADMIN（GET 放行 SUPER_ADMIN/EDU_ADMIN/FINANCE），
               非超管只读展示，避免填完表单点保存吃 403 -->
          <el-button v-if="canSave" type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <span v-else style="color:#909399;font-size:13px">仅超级管理员可修改机构配置</span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { organizationApi } from '@/api/auth'
import { showError } from '@/utils/error'

const loading = ref(false)
const saving = ref(false)

/** 当前用户是否可保存（后端仅放行 SUPER_ADMIN） */
function checkCanSave(): boolean {
  try {
    const raw = localStorage.getItem('userInfo')
    const info = raw ? JSON.parse(raw) : null
    return info?.roleCode === 'SUPER_ADMIN'
  } catch {
    return false
  }
}
const canSave = checkCanSave()

const form = reactive({
  id: undefined as number | undefined,
  orgName: '',
  campus: '',
  contactPhone: '',
  address: '',
})

async function loadData() {
  loading.value = true
  try {
    const res = await organizationApi.get()
    const data = res.data
    if (data) {
      form.id = data.id
      form.orgName = data.orgName || ''
      form.campus = data.campus || ''
      form.contactPhone = data.contactPhone || ''
      form.address = data.address || ''
    }
  } catch (e) {
    showError(e, '加载机构信息失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await organizationApi.update({
      id: form.id,
      orgName: form.orgName,
      campus: form.campus,
      contactPhone: form.contactPhone,
      address: form.address,
    })
    ElMessage.success('保存成功')
  } catch (e) {
    showError(e, '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
