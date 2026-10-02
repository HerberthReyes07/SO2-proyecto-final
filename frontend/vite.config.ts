import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

const BACKEND = 'http://localhost:7070'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    // En desarrollo el frontend corre en :5173 y delega API y WebSocket al backend.
    proxy: {
      '/api': BACKEND,
      '/ws': { target: BACKEND, ws: true },
    },
  },
  build: {
    // El build se copia al backend para que `java -jar` sirva todo desde un solo proceso.
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
