import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// 개발: `npm run dev` (5173) + Spring (8080) 을 같이 띄운다. /api 요청은 Spring 으로 넘긴다.
// 운영: `npm run build` 결과(dist/)를 Docker 빌드가 Spring 의 static/app/ 으로 복사한다.
//       Thymeleaf 가 /app/assets/app.js, app.css 를 고정 이름으로 불러오므로 파일 이름에 해시를 붙이지 않는다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        entryFileNames: 'assets/app.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/app[extname]',
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
