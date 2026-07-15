<template>
  <div>
    <h3>机构信息配置</h3>
    <el-card style="max-width: 600px">
      <el-form label-width="100px" v-loading="loading">
        <el-form-item label="机构名称">
          <el-input v-model="form.orgName" placeholder="请输入机构名称" />
        </el-form-item>
        <el-form-item label="校区">
          <el-input v-model="form.campus" placeholder="请输入校区" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { organizationApi } from '@/api/auth'

const loading = ref(false)
const saving = ref(false)
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
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载机构信息失败')
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
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
