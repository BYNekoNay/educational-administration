// API 基础地址
// H5 开发：Vite 代理 /api → localhost:8080，BASE_URL 留空
// H5 发布：需配置反向代理（nginx）将 /api 转发到后端
// 小程序/App：部署时改为后端公网可达地址
// #ifdef MP-WEIXIN
const BASE_URL = 'http://localhost:8080'
// #endif
// #ifdef APP-PLUS
const BASE_URL = 'http://localhost:8080'
// #endif
// #ifdef H5
const BASE_URL = ''
// #endif

import { getErrorMessage } from './error'

/**
 * 统一封装 uni.request。
 * 自动注入 Authorization token，使用 Vite 代理转发到后端。
 */
export function api(options) {
  const token = uni.getStorageSync('token') || ''
  return uni.request({
    ...options,
    url: BASE_URL + options.url,
    header: {
      Authorization: token ? `Bearer ${token}` : '',
      ...options.header,
    },
  }).then(res => {
    const data = res.data
    if (data.code !== 0) {
      const msg = data.message || '请求失败'
      uni.showToast({ title: msg, icon: 'none' })
      return Promise.reject(new Error(msg))
    }
    return data
  }).catch(err => {
    // 401 跳转登录是最高优先级
    if (err && err.statusCode === 401) {
      uni.removeStorageSync('token')
      uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
      uni.navigateTo({ url: '/pages/login/login' })
      return Promise.reject(err)
    }
    // 如果页面没做 catch，这里显示具体错误信息
    if (!err._handled) {
      const msg = getErrorMessage(err, '请求失败')
      uni.showToast({ title: msg, icon: 'none' })
    }
    return Promise.reject(err)
  })
}

export function getMyStudents() {
  try {
    return uni.getStorageSync('students') || []
  } catch {
    return []
  }
}

/**
 * 获取当前选中的学员 ID。
 * 优先读取用户手动切换后持久化的 currentStudentId，
 * 若不存在则回退到学员列表第一个。
 */
export function getCurrentStudentId() {
  const students = getMyStudents()
  if (students.length === 0) return null
  const saved = uni.getStorageSync('currentStudentId')
  if (saved && students.some(s => (s.id || s.studentId) === saved)) {
    return saved
  }
  const first = students[0].id || students[0].studentId
  uni.setStorageSync('currentStudentId', first)
  return first
}

/**
 * 设置当前选中学员并持久化。
 * @param {number|string} id - 学员 ID
 * @returns {number|string} 设置后的 ID
 */
export function setCurrentStudentId(id) {
  uni.setStorageSync('currentStudentId', id)
  return id
}

/**
 * @deprecated 使用 getCurrentStudentId() 代替
 */
export function getDefaultStudentId() {
  return getCurrentStudentId()
}
