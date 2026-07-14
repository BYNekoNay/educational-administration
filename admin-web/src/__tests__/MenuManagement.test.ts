import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import { menuApi } from '@/api/auth'
import MenuManagement from '@/views/admin/MenuManagement.vue'

function m() { return mountPage(MenuManagement, { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] }, 'component': { template: '<span />' } }) }

describe('MenuManagement.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders description', () => { expect(m().html()).toContain('菜单树管理') })
  it('renders add button', () => { expect(m().text()).toContain('新增顶级菜单') })
  it('calls menuApi.tree', () => { m(); expect(vi.mocked(menuApi.tree)).toHaveBeenCalled() })
  it('add opens dialog', async () => {
    const w = m()
    await w.findAll('button').find(b => b.text() === '新增顶级菜单')!.trigger('click')
    await nextTick()
    expect(w.find('.el-dialog').exists()).toBe(true)
  })
})
