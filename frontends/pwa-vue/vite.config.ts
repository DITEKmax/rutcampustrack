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
  define: {
    __RCT_SERVICE_WORKER_ENABLED__: false,
  },
  server: {
    port: 5175,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:9080',
        changeOrigin: true,
      },
    },
  },
}))
