import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// The scoped build is invoked from the frontend workspace root. Keeping these
// paths based on cwd also works after Vite bundles this config to a temp file.
const frontendRoot = process.cwd()
const harnessRoot = resolve(frontendRoot, 'mobile-core/harness/attendance-statistics')
const harnessPort = Number(process.env.HARNESS_PORT) === 18211 ? 18211 : 18210

export default defineConfig({
  root: harnessRoot,
  plugins: [vue()],
  css: { postcss: resolve(frontendRoot, 'postcss.config.mjs') },
  resolve: {
    alias: {
      '@rct/mobile-core': resolve(frontendRoot, 'mobile-core/src/index.ts'),
    },
  },
  server: {
    port: harnessPort,
    strictPort: true,
  },
  preview: {
    port: harnessPort,
    strictPort: true,
  },
  build: {
    outDir: resolve(frontendRoot, '../.agent/student-academic-ui/runtime/harness-dist'),
    emptyOutDir: true,
  },
})