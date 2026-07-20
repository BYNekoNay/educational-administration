import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('axios', () => ({ default: { get } }))

import { normalizeProtectedFileUrl, openProtectedFile } from '@/utils/protectedFile'

describe('protected file helper', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('normalizes legacy public file URLs', () => {
    expect(normalizeProtectedFileUrl('/files/demo.pdf')).toBe('/api/files/demo.pdf')
    expect(normalizeProtectedFileUrl('/api/files/demo.pdf')).toBe('/api/files/demo.pdf')
  })

  it('opens files through an authenticated blob request', async () => {
    const popup = { location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(popup as any)
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:protected'),
      revokeObjectURL: vi.fn()
    })
    localStorage.setItem('token', 'jwt-token')
    get.mockResolvedValue({ data: new Blob(['file']) })

    await openProtectedFile('/files/demo.pdf')

    expect(get).toHaveBeenCalledWith('/api/files/demo.pdf', expect.objectContaining({
      responseType: 'blob',
      headers: { Authorization: 'Bearer jwt-token' }
    }))
    expect(popup.location.href).toBe('blob:protected')
  })
})
