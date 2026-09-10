import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { cpSync } from 'node:fs'

const __dirname = dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [
    uni(),
    {
      // uni 插件会把 src/static 复制到「默认」输出目录 dist/build/h5/static，
      // 而本项目 outDir 被设为 dist/build/h5-release（历史原因：mp-weixin 目录曾被外部进程锁定）。
      // 两条路径不一致会让产物缺失 static 目录，导致 tabBar 图标 404（显示为占位符）。
      // 此钩子在构建收尾时把静态资源补到真正的输出目录。
      name: 'copy-uni-static-to-outdir',
      closeBundle() {
        cpSync(resolve(__dirname, 'src/static'), resolve(__dirname, 'dist/build/h5-release/static'), {
          recursive: true
        })
      }
    }
  ],
  root: __dirname,
  base: process.env.VITE_BASE || '/',
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: 'dist/build/h5-release',
  },
  server: {
    port: 5175,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
