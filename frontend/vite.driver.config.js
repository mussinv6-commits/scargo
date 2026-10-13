// 26.10.08 추가: 화물차 기사 앱(Capacitor) 전용 빌드 설정
// - 진입 화면: driver-app/index.html  →  결과물: dist-driver/ (Capacitor webDir)
// - 웹 빌드(vite.config.js)와 별개라서 기존 웹에는 영향 없음
// 실행: npm run dev:driver (브라우저 미리보기) / npm run build:driver (앱용 빌드)
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const here = (p) => fileURLToPath(new URL(p, import.meta.url))

export default defineConfig({
  root: here('./driver-app'),
  base: './', // 앱 안에서는 파일 경로로 열리므로 상대 경로 필요
  publicDir: here('./public'),
  plugins: [vue()],
  resolve: {
    alias: { '@': here('./src') },
  },
  build: {
    outDir: here('./dist-driver'),
    emptyOutDir: true,
  },
  server: {
    port: 5174,
    host: true, // 같은 와이파이의 휴대폰 브라우저로도 미리보기 가능
    fs: { allow: [here('.')] },
  },
})
