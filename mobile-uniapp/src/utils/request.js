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

// 防止并发 401 时重复跳转登录页（多个请求同时失败会各触发一次导航）
let isRedirectingToLogin = false

function handleUnauthorized() {
  uni.removeStorageSync('token')
  if (isRedirectingToLogin) return
  isRedirectingToLogin = true
  uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
  // reLaunch 清空导航栈，避免用户返回到已失效的页面
  uni.reLaunch({
    url: '/pages/login/login',
    complete: () => { isRedirectingToLogin = false },
  })
}

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
    // 注意：uni.request 以 Promise 调用时，HTTP 4xx/5xx 仍会 resolve（仅网络层故障才 reject），
    // 因此 401 必须在这里按 statusCode / 业务码判断，不能放到 .catch。
    const data = res.data || {}
    if (res.statusCode === 401 || data.code === 401) {
      // 仅当 401 对应本次请求使用的 token 时才清理会话，
      // 避免旧请求迟到的 401 清掉用户重新登录后的新会话
      if ((uni.getStorageSync('token') || '') === token) {
        handleUnauthorized()
      }
      const err = new Error(data.message || '登录已过期')
      err._handled = true
      return Promise.reject(err)
    }
    if (res.statusCode >= 400) {
      // 网关/代理返回的非 JSON 错误（如 502/504 HTML 页面）：按状态码映射提示
      const msg = (typeof data === 'object' && data.message) || getErrorMessage({ statusCode: res.statusCode }, '请求失败')
      uni.showToast({ title: msg, icon: 'none' })
      const err = new Error(msg)
      err._handled = true
      return Promise.reject(err)
    }
    if (data.code !== 0) {
      const msg = data.message || '请求失败'
      uni.showToast({ title: msg, icon: 'none' })
      const err = new Error(msg)
      err._handled = true // 标记已处理，避免 .catch 再次弹窗
      return Promise.reject(err)
    }
    return data
  }).catch(err => {
    // 已在 .then 处理过的业务/401 错误不再重复提示
    if (err && err._handled) {
      return Promise.reject(err)
    }
    // 网络层错误（超时、DNS 失败等）：显示具体信息
    const msg = getErrorMessage(err, '请求失败')
    uni.showToast({ title: msg, icon: 'none' })
    if (err && typeof err === 'object') err._handled = true
    return Promise.reject(err)
  })
}

export function uploadFile(filePath) {
  const token = uni.getStorageSync('token') || ''
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/api/files/upload',
      filePath,
      name: 'file',
      header: { Authorization: token ? `Bearer ${token}` : '' },
      success(res) {
        if (res.statusCode === 401) {
          handleUnauthorized()
          const err = new Error('登录已过期')
          err._handled = true
          reject(err)
          return
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          const err = new Error(getErrorMessage({ statusCode: res.statusCode }, '上传失败'))
          err._handled = true
          reject(err)
          return
        }
        try {
          const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (data.code !== 0) throw new Error(data.message || '上传失败')
          resolve(data.data.url)
        } catch (error) {
          reject(error)
        }
      },
      fail: reject,
    })
  })
}

export function downloadProtectedFile(url) {
  if (!url) return Promise.resolve('')
  const token = uni.getStorageSync('token') || ''
  const protectedUrl = url.startsWith('/files/') ? `/api${url}` : url
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: BASE_URL + protectedUrl,
      header: { Authorization: token ? `Bearer ${token}` : '' },
      success(result) {
        if (result.statusCode === 200) {
          resolve(result.tempFilePath)
        } else if (result.statusCode === 401) {
          handleUnauthorized()
          const err = new Error('登录已过期')
          err._handled = true
          reject(err)
        } else {
          reject(new Error('附件下载失败'))
        }
      },
      fail: reject,
    })
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
