import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// target.frontend.port = 5173, target.frontend.proxyPath = /api, target.backend.port = 5000
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:5000', changeOrigin: true },
    },
  },
  test: {
    environment: 'node',
  },
});
