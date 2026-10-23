import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv, searchForWorkspaceRoot } from 'vite'
import { browserDependencyOptimization, developmentServer } from '../dev-server'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const target = env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:18080'
  return {
    plugins: [vue()],
    optimizeDeps: browserDependencyOptimization,
    css: { postcss: '../postcss.config.mjs' },
    server: {
      ...developmentServer(env, 14210, target),
      fs: {
        allow: [
          searchForWorkspaceRoot(fileURLToPath(new URL('.', import.meta.url))),
          '/workspace/rutcampustrack/frontends/node_modules',
        ],
      },
    },
    preview: {
      port: 14210,
      strictPort: true,
      proxy: { '/api': { target, changeOrigin: true, ws: true } },
    },
  }
})
