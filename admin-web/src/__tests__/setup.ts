import { vi } from 'vitest'
import { config } from '@vue/test-utils'
import { ElLink, ElDivider, ElTag, ElLoading } from 'element-plus'

// ---- Mock echarts (avoids jsdom canvas errors) ----
const echartsMock = {
  init: () => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
  }),
}
vi.mock('echarts', () => ({
  default: echartsMock,
  ...echartsMock,
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

// ---- 注册测试环境缺失的 Element Plus 组件与指令 ----
// 生产入口 main.ts 走全量注册（app.use(ElementPlus)），但测试不会加载 main.ts，
// 导致 el-link / el-divider / el-tag 与 v-loading 解析失败：Vue 只打印 warn，
// 对应节点**直接不渲染**，于是针对这些节点的断言被静默跳过——表面"测试通过"，
// 实际什么都没覆盖，覆盖数字虚高。这里补齐注册，让断言真正执行。
config.global.components = {
  ...(config.global.components || {}),
  ElLink,
  ElDivider,
  ElTag,
}
config.global.directives = {
  ...(config.global.directives || {}),
  loading: ElLoading.directive,
}

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
