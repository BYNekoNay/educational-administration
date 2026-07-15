<template>
  <div class="login-page">
    <!-- Left: Brand Panel -->
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-icon">
          <svg viewBox="0 0 48 48" width="48" height="48">
            <rect x="4" y="20" width="6" height="24" rx="3" fill="white" opacity="0.2"/>
            <rect x="14" y="12" width="6" height="32" rx="3" fill="white" opacity="0.4"/>
            <rect x="24" y="4" width="6" height="40" rx="3" fill="white" opacity="0.6"/>
            <rect x="34" y="16" width="6" height="28" rx="3" fill="white" opacity="0.8"/>
          </svg>
        </div>
        <h1 class="brand-name">艺培通</h1>
        <p class="brand-desc">艺术培训机构全流程教务管理平台</p>
        <div class="brand-features">
          <span>智能排课</span><span>·</span><span>课时管理</span><span>·</span><span>学员成长</span>
        </div>
      </div>
    </div>

    <!-- Right: Login Form -->
    <div class="login-form-panel">
      <div class="form-wrapper">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-subtitle">登录您的管理账号</p>

        <el-form :model="form" :rules="rules" ref="formRef" label-width="0" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
          </el-form-item>
        </el-form>

        <div class="demo-accounts">
          <p class="demo-title">管理端演示账号</p>
          <div class="demo-tags">
            <el-tag size="small" effect="plain">admin / 123456</el-tag>
            <el-tag size="small" effect="plain">edu / 123456</el-tag>
            <el-tag size="small" effect="plain">finance / 123456</el-tag>
          </div>
          <p class="demo-hint">教师和家长请使用移动端登录</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const UserIcon = shallowRef(User)
const LockIcon = shallowRef(Lock)

const form = reactive({ username: '', password: '' })
const formRef = ref()
const loading = ref(false)

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const ADMIN_ROLES = new Set(['SUPER_ADMIN', 'EDU_ADMIN', 'FINANCE'])

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await authApi.login(form)
    const { token, userId, username, realName, roleCode, permissions = [] } = res.data

    if (!ADMIN_ROLES.has(roleCode)) {
      ElMessage.warning('该账号为教师或家长角色，请使用移动端（艺培通）登录')
      return
    }

    authStore.setLogin(token, { userId, username, realName, roleCode }, permissions)
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch (e: any) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  background: var(--surface-page);
}

/* ─── Brand Panel ─── */
.login-brand {
  flex: 1;
  background: linear-gradient(160deg, #0c4a6e 0%, #0e7490 40%, #06b6d4 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}
.login-brand::before {
  content: '';
  position: absolute;
  inset: 0;
  background: url("data:image/svg+xml,%3Csvg width='60' height='60' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M30 5 Q35 15 45 20 Q35 25 30 35 Q25 25 15 20 Q25 15 30 5Z' fill='white' opacity='0.03'/%3E%3C/svg%3E");
  background-size: 60px 60px;
}
.brand-content {
  text-align: center;
  color: white;
  position: relative;
  z-index: 1;
  padding: var(--space-8);
}
.brand-icon {
  margin-bottom: var(--space-6);
  opacity: 0.9;
}
.brand-name {
  font-size: 3rem;
  font-weight: 700;
  margin: 0 0 var(--space-3);
  letter-spacing: 0.08em;
}
.brand-desc {
  font-size: var(--text-lg);
  opacity: 0.85;
  margin: 0 0 var(--space-6);
  font-weight: var(--font-light);
}
.brand-features {
  font-size: var(--text-sm);
  opacity: 0.65;
  display: flex;
  gap: var(--space-2);
  justify-content: center;
}

/* ─── Form Panel ─── */
.login-form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 500px;
}
.form-wrapper {
  width: 100%;
  max-width: 380px;
  padding: var(--space-8);
}
.form-title {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--neutral-800);
  margin: 0 0 var(--space-1);
}
.form-subtitle {
  color: var(--neutral-500);
  margin: 0 0 var(--space-8);
  font-size: var(--text-sm);
}

/* ─── Login Button ─── */
.login-btn {
  width: 100%;
  height: 48px !important;
  font-size: var(--text-base) !important;
  letter-spacing: 0.15em !important;
  margin-top: var(--space-2);
}

/* ─── Demo Accounts ─── */
.demo-accounts {
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 1px solid var(--neutral-200);
}
.demo-title {
  font-size: var(--text-xs);
  color: var(--neutral-400);
  margin: 0 0 var(--space-3);
  text-align: center;
}
.demo-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: center;
}
.demo-hint {
  font-size: var(--text-xs);
  color: var(--neutral-400);
  margin: var(--space-3) 0 0;
  text-align: center;
}

/* ─── Responsive ─── */
@media (max-width: 768px) {
  .login-brand { display: none; }
  .login-form-panel { max-width: 100%; }
}
</style>
