import axios, { type AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import type { ApiResponse } from '@/types'

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

instance.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

instance.interceptors.response.use(
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

/**
 * 类型化客户端：响应拦截器已把后端统一响应体 { code, message, data } 解包为返回值，
 * 这里用接口约束返回类型（T 为业务数据类型），API 层方法即可获得完整类型提示。
 */
interface ApiClient {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
}

const request = instance as unknown as ApiClient

export default request