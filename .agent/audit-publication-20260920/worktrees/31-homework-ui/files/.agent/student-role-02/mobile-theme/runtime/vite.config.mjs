import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '../../../../frontends/node_modules/vite/dist/node/index.js'
import vue from '../../../../frontends/node_modules/@vitejs/plugin-vue/dist/index.mjs'

const fixtureRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../homework-ui-visual/runtime')
const frontendRoot = path.resolve(fixtureRoot, '../../../../frontends')
const frontendModules = path.join(frontendRoot, 'node_modules')

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
  css: { postcss: path.join(frontendRoot, 'postcss.config.mjs') },
  optimizeDeps: { exclude: ['@tanstack/vue-query', '@tanstack/query-core'] },
  server: {
    host: '127.0.0.1',
    port: 5181,
    strictPort: true,
    fs: { strict: false, allow: [fixtureRoot, frontendRoot] },
  },
})
