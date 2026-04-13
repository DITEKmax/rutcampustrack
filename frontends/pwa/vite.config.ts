import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  // BUG-008: PWA serves under /app/ on prod (see nginx/conf.d/default.conf "/app/" location).
  // Without this base, Vite emits asset URLs like "/assets/...", which would be resolved
  // against the host (https://ruttrack.site/assets/...) and proxied to the web-panel container,
  // returning 404 → blank screen.
  base: '/app/',
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'autoUpdate',
      injectRegister: false,
      manifest: {
        name: 'RutTrack',
        short_name: 'RutTrack',
        display: 'standalone',
        // start_url and scope must include /app/ so the installed PWA opens the right URL.
        start_url: '/app/',
        scope: '/app/',
        background_color: '#0A0E17',
        theme_color: '#0A0E17',
        orientation: 'portrait',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' }
        ]
      },
      devOptions: {
        enabled: true,
        type: 'module'
      }
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
