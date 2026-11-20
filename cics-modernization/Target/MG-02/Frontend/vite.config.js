import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Shell SPA (MG-02) hosting the MG-01 screens. UI 5173; SignOnService 5001; TransactionTypeService 5000.
const mg01 = fileURLToPath(new URL('../../MG-01/Frontend/src', import.meta.url));
const signOn = process.env.SIGNON_PROXY_TARGET ?? 'http://localhost:5001';
const mg01Api = process.env.API_PROXY_TARGET ?? 'http://localhost:5000';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@mg01': mg01 },
    dedupe: ['react', 'react-dom', 'react-router-dom'],
  },
  server: {
    port: 5173,
    strictPort: true,
    fs: { allow: ['..', mg01] },
    proxy: {
      '/api/v1/auth': { target: signOn, changeOrigin: true, xfwd: true },
      '/api/v1/menus': { target: signOn, changeOrigin: true, xfwd: true },
      '/api': { target: mg01Api, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.test.{js,jsx}'],
    env: { VITE_AUTH_BYPASS: 'false' },
    server: { deps: { inline: [/MG-01/] } },
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/main.jsx', 'src/test/**', 'src/**/*.test.{js,jsx}'],
      thresholds: { lines: 70, functions: 70, branches: 60, statements: 70 },
    },
  },
});
