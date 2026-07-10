const BASE_URL = 'http://localhost:8080'

/**
 * 统一封装 uni.request。
 * 自动注入 Authorization token，统一 BASE_URL。
 */
export function api(options: UniApp.RequestOptions) {
  const token = uni.getStorageSync('token') || ''
  return uni.request({
    ...options,
    url: BASE_URL + options.url,
    header: {
      Authorization: token ? `Bearer ${token}` : '',
      ...options.header,
    },
  }).then(res => {
    const data = res.data as any
    if (data.code !== 0) {
      uni.showToast({ title: data.message || '请求失败', icon: 'none' })
      return Promise.reject(new Error(data.message))
    }
    return data
  }).catch(err => {
    if (err.statusCode === 401) {
      uni.removeStorageSync('token')
      uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
      uni.navigateTo({ url: '/pages/login/login' })
    }
    return Promise.reject(err)
  })
}

/**
 * 获取当前家长的学员列表（从本地存储）。
 */
export function getMyStudents() {
  try {
    return uni.getStorageSync('students') || []
  } catch {
    return []
  }
}

/**
 * 获取默认学员 ID（第一个学员，若无则返回 null）。
 */
export function getDefaultStudentId() {
  const students = getMyStudents()
  return students.length > 0 ? students[0].id || students[0].studentId : null
}
