import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 使用 import.meta.dirname 而非 __dirname，兼容 Vite 未来的 configLoader: 'native'
      '@': resolve(import.meta.dirname, 'src')
    }
  },
  server: {
    host: true, // 监听 0.0.0.0，允许通过局域网 IP 访问
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
