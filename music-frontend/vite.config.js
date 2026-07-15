import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发环境沿用生产的同源 /api 路径，使 cookie、下载和 EventSource 无需硬编码后端地址。
      '/api': 'http://localhost:8080'
    }
  }
})
