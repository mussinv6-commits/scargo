// S카고 AMR 관제 앱 빌드 설정 — 결과물은 dist/ (Capacitor webDir)
import { defineConfig } from 'vite'
export default defineConfig({
  base: './',
  build: { outDir: 'dist', emptyOutDir: true, chunkSizeWarningLimit: 1500 },
  server: { port: 5175 },
})
