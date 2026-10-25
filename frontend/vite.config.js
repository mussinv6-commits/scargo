import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],

  // 26.10.24 개발 서버가 /api 요청을 백엔드(8080)로 대신 전달한다 (같은 주소로 요청할 때 쓰며, 기본 동작은 그대로)
  server: {
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
  },

  resolve: {      // <- 추가
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
