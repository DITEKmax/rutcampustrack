import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';

export default defineConfig({
  plugins: [angular()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['src/test-setup.ts'],
    include: ['src/**/*.spec.ts'],
    // M08 Группа 10 (OWNER-ANSWERS QD2) — 50% line gate для web-panel.
    // lcov → diff-cover; json-summary → davelosert/vitest-coverage-report-action.
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
      include: ['src/**/*.ts'],
      exclude: [
        '**/*.d.ts',
        'src/**/*.spec.ts',
        'src/test-setup.ts',
        'src/main.ts',
        'src/environments/**',
        'src/app/api/generated/**',
        'src/app/types/generated/**',
        'dist/**',
        'node_modules/**',
      ],
      thresholds: {
        lines: 50,
        statements: 50,
        functions: 50,
        branches: 40,
      },
    },
  },
});
