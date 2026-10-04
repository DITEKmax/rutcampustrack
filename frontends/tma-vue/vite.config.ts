import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { browserDependencyOptimization, developmentServer } from '../dev-server'

function publicBase(mode: string): string {
  const value = loadEnv(mode, '.', '').VITE_PUBLIC_BASE ?? '/'
  if (!value.startsWith('/') || !value.endsWith('/')) {
    throw new Error('VITE_PUBLIC_BASE must begin and end with /')
  }
  return value
}

export default defineConfig(({ mode }) => ({
  base: publicBase(mode),
  resolve: { alias: { '@rct/mobile-core': fileURLToPath(new URL('../mobile-core/src/index.ts', import.meta.url)) } },
  optimizeDeps: browserDependencyOptimization,
  plugins: [vue()],
  css: {
    postcss: '../postcss.config.mjs',
  },
  server: developmentServer(
    loadEnv(mode, '.', ''),
    5176,
    loadEnv(mode, '.', '').VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
  ),
}))
