import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// 개발: `npm run dev` (5173) + Spring (8080) 을 같이 띄운다. /api 요청은 Spring 으로 넘긴다.
//       /search, /card, /replay 같은 화면 주소는 Vite 가 index.html 로 돌려줘서 React 가 그린다.
//       (단 /s/{id} 공유 주소는 서버가 플레이어를 껍데기에 넣어 주는 화면이라 8080 에서 확인한다)
// 운영: `npm run build` 결과(dist/)를 Docker 빌드가 Spring 의 static/app/ 으로 복사한다.
//       app.html 이 /app/assets/app.js 를 고정 이름으로 불러오므로 파일 이름에 해시를 붙이지 않는다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      // 스타일·그림(카드·지도 조각)은 Spring 에서 받는다
      '/css': 'http://localhost:8080',
      '/images': 'http://localhost:8080',
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
