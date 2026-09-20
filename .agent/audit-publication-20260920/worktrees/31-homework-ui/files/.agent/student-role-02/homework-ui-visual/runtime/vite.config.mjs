import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const fixtureRoot = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(fixtureRoot, '../../../../frontends')
const frontendModules = path.join(frontendRoot, 'node_modules')
const { defineConfig } = await import(pathToFileURL(path.join(frontendModules, 'vite/dist/node/index.js')).href)
const { default: vue } = await import(pathToFileURL(path.join(frontendModules, '@vitejs/plugin-vue/dist/index.mjs')).href)

export default defineConfig({
  root: fixtureRoot,
  plugins: [vue()],
  resolve: {
    alias: {
      vue: path.join(frontendModules, 'vue'),
      '@tanstack/vue-query': path.join(frontendModules, '@tanstack/vue-query'),
      '@fontsource-variable/onest': path.join(frontendModules, '@fontsource-variable/onest'),
    },
  },
  css: {
    postcss: path.join(frontendRoot, 'postcss.config.mjs'),
  },
  optimizeDeps: {
    exclude: ['@tanstack/vue-query', '@tanstack/query-core'],
  },
  server: {
    host: '127.0.0.1',
    port: 5181,
    strictPort: true,
    fs: {
      strict: false,
      allow: [fixtureRoot, frontendRoot],
    },
  },
})
