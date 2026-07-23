import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { installGlobalErrorGuard } from './utils/error-guard'
import { showError } from './utils/error'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
// 组件渲染/生命周期异常兜底：error-guard 只管 unhandledrejection，
// Vue 渲染错误默认只进控制台，用户侧毫无反馈
app.config.errorHandler = (err, _instance, info) => {
  console.error('[Vue errorHandler]', info, err)
  showError(err, '页面渲染异常，请刷新重试')
}
installGlobalErrorGuard()
app.mount('#app')
