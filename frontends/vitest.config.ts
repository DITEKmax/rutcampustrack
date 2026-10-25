import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

// The tests run in Node with custom renderers, so compile SFCs for client rendering
// without changing Node's filesystem/import.meta semantics for the test modules.
const vuePlugin = vue()
const vueTransform = vuePlugin.transform
if (vueTransform && typeof vueTransform !== 'function') {
  const transform = vueTransform.handler
  vueTransform.handler = function (code, id, options) {
    return transform.call(this, code, id, { ...options, ssr: false })
  }
}

export default defineConfig({
  plugins: [vuePlugin],
  define: {
    __RCT_SERVICE_WORKER_ENABLED__: false,
    __RCT_APP_VERSION__: JSON.stringify('0.0.0'),
    __RCT_MIN_SUPPORTED_VERSION__: JSON.stringify('0.0.0'),
    __RCT_FORCE_UPDATE__: true,
  },
  resolve: {
    alias: {
      '@rct/mobile-core': fileURLToPath(new URL('./mobile-core/src/index.ts', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: [
      'mobile-core/src/**/*.test.ts',
      'pwa-vue/src/**/*.test.ts',
      'tma-vue/src/**/*.test.ts',
      'web-vue/src/**/*.test.ts',
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'lcov', 'html'],
      include: [
        'mobile-core/src/**/*.{ts,vue}',
        'pwa-vue/src/**/*.{ts,vue}',
        'tma-vue/src/**/*.{ts,vue}',
        'web-vue/src/**/*.{ts,vue}',
        'shared/**/*.{ts,vue}',
      ],
      exclude: ['**/*.test.ts', '**/api/generated/**', '**/*.d.ts'],
    },
  },
})
