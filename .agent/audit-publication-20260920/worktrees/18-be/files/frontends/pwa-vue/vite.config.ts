import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

function publicBase(mode: string): string {
  const value = loadEnv(mode, '.', '').VITE_PUBLIC_BASE ?? '/'
  if (!value.startsWith('/') || !value.endsWith('/')) {
    throw new Error('VITE_PUBLIC_BASE must begin and end with /')
  }
  return value
}

function runtime(mode: string) {
  const env = loadEnv(mode, '.', '')
  return {
    apiTarget: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
    serviceWorkerEnabled: mode === 'production' && env.VITE_ENABLE_LOCAL_PWA_SW === 'true',
  }
}

export default defineConfig(({ mode }) => {
  const config = runtime(mode)
  const proxy = {
    '/api': {
      target: config.apiTarget,
      changeOrigin: true,
    },
  }
  return {
    base: publicBase(mode),
    plugins: [vue()],
    css: {
      postcss: '../postcss.config.mjs',
    },
    define: {
      __RCT_SERVICE_WORKER_ENABLED__: config.serviceWorkerEnabled,
    },
    server: {
      port: 5175,
      strictPort: true,
      proxy,
    },
    preview: {
      port: 5175,
      strictPort: true,
      proxy,
    },
  }
})
