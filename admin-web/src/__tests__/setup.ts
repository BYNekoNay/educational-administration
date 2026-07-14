import { vi } from 'vitest'

// ---- Mock echarts (avoids jsdom canvas errors) ----
vi.mock('echarts', () => ({
  default: {
    init: () => ({
      setOption: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
    }),
  },
}))

// ---- Mock ElementPlus ElMessage / ElMessageBox ----
vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
      alert: vi.fn().mockResolvedValue('alert'),
      prompt: vi.fn().mockResolvedValue({ value: '' }),
    },
  }
})

// Mock vue-router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    currentRoute: { value: { path: '/admin/dashboard', name: 'Dashboard' } },
  }),
  useRoute: () => ({
    path: '/admin/dashboard',
    name: 'Dashboard',
    params: {},
    query: {},
  }),
  createRouter: vi.fn(),
  createWebHashHistory: vi.fn(),
}))
