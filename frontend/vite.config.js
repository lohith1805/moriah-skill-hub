import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // The app calls the backend with a relative base of /api/v1 (see
      // src/services/apiClient.js). In dev, forward those calls to the Spring
      // Boot backend so there is no CORS dance and no hard-coded host in the
      // source. Set VITE_API_BASE_URL in .env to talk to a deployed backend
      // directly and bypass this proxy.
      '/api': {
        target: process.env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
