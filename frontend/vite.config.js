import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],

  resolve: {      // <- 추가
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
