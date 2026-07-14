import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import { noticeApi } from '@/api/auth'
import NoticeList from '@/views/admin/NoticeList.vue'

function m() { return mountPage(NoticeList, { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] } }) }

describe('NoticeList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it('renders title', () => { expect(m().html()).toContain('公告管理') })
  it('renders publish button', () => { expect(m().text()).toContain('发布公告') })
  it('calls list API', () => { m(); expect(vi.mocked(noticeApi.list)).toHaveBeenCalled() })
  it('publish opens dialog', async () => {
    const w = m()
    await w.findAll('button').find(b => b.text() === '发布公告')!.trigger('click')
    await nextTick()
    expect(w.find('.el-dialog').exists()).toBe(true)
  })
})
