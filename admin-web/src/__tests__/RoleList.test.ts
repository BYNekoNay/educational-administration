import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import { roleApi } from '@/api/auth'
import RoleList from '@/views/admin/RoleList.vue'

describe('RoleList.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('mounts without error and calls roleApi.list', async () => {
    expect(() => {
      mount(RoleList, {
        global: {
          stubs: {
            'el-table': true, 'el-table-column': true, 'el-tag': true, 'el-dialog': true,
            'el-button': true, 'el-form': true, 'el-form-item': true, 'el-input': true,
            'el-checkbox': true, 'el-popconfirm': true, 'el-icon': true, 'el-tree': true,
          },
        },
      })
    }).not.toThrow()
    await nextTick()
    await new Promise(r => setTimeout(r, 100))
    expect(vi.mocked(roleApi.list)).toHaveBeenCalled()
  })
})
