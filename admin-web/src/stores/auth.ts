import { defineStore } from 'pinia'
import { ref } from 'vue'

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
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem('userInfo') || 'null')
  )
  const roleCode = ref<string>(userInfo.value?.roleCode || '')

  // 角色拥有的菜单权限码集合
  const permissions = ref<Set<string>>(
    new Set(JSON.parse(localStorage.getItem('permissions') || '[]'))
  )

  /** 判断当前用户是否拥有指定菜单权限 */
  function hasPermission(code: string): boolean {
    if (roleCode.value === 'SUPER_ADMIN') return true
    return permissions.value.has(code)
  }

  /** 从 API 获取全量权限码列表（供 SUPER_ADMIN 或角色管理页使用） */
  async function fetchAllPermissionCodes(): Promise<string[]> {
    try {
      const { permissionApi } = await import('@/api/auth')
      const res = await permissionApi.list()
      const list: any[] = res.data || []
      return list.map((p: any) => p.permissionCode)
    } catch {
      return []
    }
  }

  /** 拉取当前角色的菜单权限（SUPER_ADMIN 从 API 获取全部权限码） */
  async function fetchPermissions() {
    try {
      if (roleCode.value === 'SUPER_ADMIN') {
        const allCodes = await fetchAllPermissionCodes()
        const all = new Set(allCodes)
        permissions.value = all
        localStorage.setItem('permissions', JSON.stringify([...all]))
        return
      }
      const { roleApi } = await import('@/api/auth')
      const rolesRes = await roleApi.list()
      const roles = rolesRes.data || []
      const role = roles.find((r: any) => r.roleCode === roleCode.value)
      if (role) {
        const permRes = await roleApi.permissions(role.id)
        // 返回格式: { roleId, roleCode, permissionCodes: [...] }
        const permData = permRes.data
        const codes = permData?.permissionCodes || []
        const perms = new Set<string>(codes)
        permissions.value = perms
        localStorage.setItem('permissions', JSON.stringify([...perms]))
      }
    } catch {
      // 加载失败时允许访问看板
      permissions.value = new Set<string>(['menu:dashboard'])
    }
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
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
  }

  return { token, userInfo, roleCode, permissions, menuTree, setLogin, logout, hasPermission, fetchPermissions, fetchMyMenus }
})
