import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The backend address can be overridden with the API_TARGET environment variable (for example when port
// 8081 is already used on the machine). Default: the FlowBoard backend on 8081.
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8081'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // SmartCourt uses 5173 and TrackGo 5175, so the three frontends can run side by side.
    port: 5174,
    proxy: {
      // The browser requests /api/xxx from the Vite server; Vite forwards it to the backend.
      // Because the browser only ever talks to one origin, no CORS configuration is needed in development.
      '/api': apiTarget,
    },
  },
})
