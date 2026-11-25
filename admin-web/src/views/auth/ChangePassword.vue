<template>
  <div class="cp-page">
    <el-card class="cp-card">
      <h3 class="cp-title">修改密码</h3>
      <p class="cp-tip">
        为保障账号安全，请先设置新密码后再继续使用系统。
        修改成功后其他设备将退出登录。
      </p>

      <el-form label-width="96px">
        <el-form-item label="原密码">
          <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>

      <el-button type="primary" :loading="saving" style="width: 100%" @click="submit">
        确认修改
      </el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const router = useRouter()
const saving = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })

async function submit() {
  if (!form.oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  saving.value = true
  try {
    await request.put('/auth/password', {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    // 改密后旧 token 已因 version 递增失效，这里直接回登录页重新登录最稳妥
    ElMessage.success('密码已更新，请重新登录')
    localStorage.removeItem('token')
    router.replace('/login')
  } catch (e: any) {
    ElMessage.error(e?.message || '修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.cp-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.cp-card {
  width: 440px;
  padding: 8px 12px;
}

.cp-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}

.cp-tip {
  margin: 0 0 20px;
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
}
</style>
