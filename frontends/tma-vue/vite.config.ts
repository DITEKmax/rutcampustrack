import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

function publicBase(mode: string): string {
  const value = loadEnv(mode, '.', '').VITE_PUBLIC_BASE ?? '/'
  if (!value.startsWith('/') || !value.endsWith('/')) {
    throw new Error('VITE_PUBLIC_BASE must begin and end with /')
  }
  return value
}

export default defineConfig(({ mode }) => ({
  base: publicBase(mode),
  plugins: [vue()],
  css: {
    postcss: '../postcss.config.mjs',
  },
  server: {
    port: 5176,
    strictPort: true,
    proxy: {
      '/api': {
        target: loadEnv(mode, '.', '').VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}))
