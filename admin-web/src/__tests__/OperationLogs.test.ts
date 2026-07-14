import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountPage } from './helpers'

vi.mock('@/api/auth', async () => {
  const { createAuthApiMocks } = await import('./mocks/factory')
  return createAuthApiMocks()
})

import { operationLogApi } from '@/api/auth'
import OperationLogs from '@/views/admin/OperationLogs.vue'

describe('OperationLogs.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })
  function m() { return mountPage(OperationLogs) }

  it('renders title', () => { expect(m().html()).toContain('操作日志') })
  it('calls list API', () => { m(); expect(vi.mocked(operationLogApi.list)).toHaveBeenCalled() })
})
