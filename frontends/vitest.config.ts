import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@rct/mobile-core': fileURLToPath(new URL('./mobile-core/src/index.ts', import.meta.url)) },
  },
  test: {
    environment: 'node',
    include: ['mobile-core/src/**/*.test.ts', 'pwa-vue/src/**/*.test.ts', 'tma-vue/src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'lcov', 'html'],
      include: ['mobile-core/src/**/*.{ts,vue}', 'pwa-vue/src/**/*.{ts,vue}', 'tma-vue/src/**/*.{ts,vue}'],
      exclude: ['**/*.test.ts', '**/api/generated/**', '**/*.d.ts'],
    },
  },
})
