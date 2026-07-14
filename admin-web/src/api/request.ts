import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const data = response.data
    // 401 未登录 / Token 过期：清除登录态并跳转
    if (data.code === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
      return Promise.reject(new Error(data.message))
    }
    // 403 权限不足：提示后不跳转（用户可能在看板等公开页面）
    if (data.code === 403) {
      ElMessage.error(data.message || '权限不足')
      return Promise.reject(new Error(data.message))
    }
    if (data.code !== 0) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error) => {
    // HTTP 级 401（备用，如 Spring Security 等框架层返回）
    if (error.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
      return Promise.reject(error)
    }
    const msg = getErrorMessage(error)
    if (msg) {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
