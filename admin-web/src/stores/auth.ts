import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

interface MenuNode {
  id: number
  parentId: number
  menuName: string
  icon: string | null
  path: string | null
  permissionCode: string | null
  sortOrder: number
  visible: number
  children?: MenuNode[]
}

interface UserInfo {
  userId: number
  username: string
  realName: string
  roleCode: string
}

export const useAuthStore = defineStore('auth', () => {
  /** 安全读取 JSON 持久化值：损坏时清除该 key 并返回默认值，避免启动白屏 */
  function safeParse<T>(key: string, fallback: T): T {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    try {
      return JSON.parse(raw) as T
    } catch {
      localStorage.removeItem(key)
      return fallback
    }
  }

  const token = ref<string>(localStorage.getItem('token') || '')
  // 持久化恢复必须做运行时类型校验：localStorage 可能被旧版本/手工改写污染，
  // safeParse 只防 JSON 解析失败，不防"解析出来类型不对"（如 new Set(123) 直接抛错白屏）
  const rawUser = safeParse<UserInfo | null>('userInfo', null)
  const userInfo = ref<UserInfo | null>(rawUser && typeof rawUser === 'object' ? rawUser : null)
  const roleCode = ref<string>(userInfo.value?.roleCode || '')

  // 角色拥有的菜单权限码集合（仅接受字符串数组，过滤非法元素）
  const rawPermissions = safeParse<unknown>('permissions', [])
  const permissions = ref<Set<string>>(new Set(
    Array.isArray(rawPermissions)
      ? rawPermissions.filter((p): p is string => typeof p === 'string')
      : []
  ))

  /** 判断当前用户是否拥有指定菜单权限 */
  function hasPermission(code: string): boolean {
    if (roleCode.value === 'SUPER_ADMIN') return true
    return permissions.value.has(code)
  }

  /** 当前用户可见菜单树（数据驱动侧栏） */
  const menuTree = ref<MenuNode[]>([])

  /** 从后端拉取当前用户可见菜单树 */
  async function fetchMyMenus() {
    try {
      const { authApi } = await import('@/api/auth')
      const res = await authApi.myMenus()
      menuTree.value = res.data || []
    } catch {
      menuTree.value = []
      // 侧栏菜单空白且无任何提示会让用户困惑；此处仅在挂载时调用一次，不会刷屏
      ElMessage.error('菜单加载失败，请刷新重试')
    }
  }

  function setLogin(tokenValue: string, info: UserInfo, userPermissions: string[] = []) {
    token.value = tokenValue
    userInfo.value = info
    roleCode.value = info.roleCode
    permissions.value = new Set(userPermissions)
    localStorage.setItem('token', tokenValue)
    localStorage.setItem('userInfo', JSON.stringify(info))
    localStorage.setItem('permissions', JSON.stringify(userPermissions))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    roleCode.value = ''
    permissions.value = new Set()
    menuTree.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
  }

  return { token, userInfo, roleCode, permissions, menuTree, setLogin, logout, hasPermission, fetchMyMenus }
})
