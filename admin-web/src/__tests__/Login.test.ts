import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import Login from '@/views/login/Login.vue'

const stubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-button': { template: '<button><slot /></button>' },
}

function m() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(Login, {
    global: { plugins: [pinia], stubs },
  })
}

describe('Login.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().text()).toContain('艺培通') })
  it('renders login button', () => { expect(m().text()).toContain('登 录') })
  it('renders demo account hint', () => { expect(m().text()).toContain('演示账号') })
  it('renders inputs', () => { expect(m().findAll('input').length).toBeGreaterThanOrEqual(2) })
})
