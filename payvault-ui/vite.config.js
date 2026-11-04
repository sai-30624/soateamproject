import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development every /api call is forwarded to the API Gateway on 8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
