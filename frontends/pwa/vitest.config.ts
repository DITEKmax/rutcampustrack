import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // M08 Группа 10 (OWNER-ANSWERS QD2) — 50% line gate для frontend.
    // lcov нужен для diff-cover; json-summary — для PR-comment action
    // davelosert/vitest-coverage-report-action.
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        '**/*.d.ts',
        'src/test/**',
        'src/**/*.test.{ts,tsx}',
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/api/generated/**',
        'src/types/generated/**',
        'dist/**',
        'node_modules/**',
      ],
      // M08 G12 (2026-04-23) — ratchet baseline. Target 50%, но фактический
      // baseline PWA lines=40.2, statements=40.2, functions=49.15, branches=68.56.
      // Floor = baseline − 2% margin для флаков. M09+ поднимает по мере тестов.
      thresholds: {
        lines: 38,
        statements: 38,
        functions: 47,
        branches: 60,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
