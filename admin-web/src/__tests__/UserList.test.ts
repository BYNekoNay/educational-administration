import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import { userApi } from '@/api/auth'
import UserList from '@/views/admin/UserList.vue'

function m() { return mountPage(UserList, { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] }, 'el-input': { template: '<input />' } }) }

describe('UserList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('用户管理') })
  it('renders add button', () => { expect(m().text()).toContain('新增用户') })
  it('renders search/reset', () => { const t = m().text(); expect(t).toContain('搜索'); expect(t).toContain('重置') })
  it('calls list API', () => { m(); expect(vi.mocked(userApi.list)).toHaveBeenCalled() })
  it('add opens dialog', async () => {
    const w = m()
    await w.findAll('button').find(b => b.text() === '新增用户')!.trigger('click')
    await nextTick()
    expect(w.find('.el-dialog').exists()).toBe(true)
  })
})
