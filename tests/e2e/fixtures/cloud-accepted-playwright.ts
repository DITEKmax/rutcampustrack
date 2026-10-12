import { defineConfig } from '@playwright/test'
import existing from '../playwright.config'

/** Explicit opt-in lane; one real API dataset writer and private artifacts only. */
export default defineConfig({
  ...existing,
  testDir: '../specs',
  testMatch: /cloud-accepted-.*\.spec\.ts/,
  testIgnore: [],
  outputDir: '../test-results/cloud-accepted',
  reporter: [['./safe-reporter.ts']],
  use: {
    ...existing.use,
    viewport: { width: 390, height: 844 },
    trace: 'off',
    video: 'off',
    screenshot: 'off',
  },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60000,
  projects: existing.projects
    ?.filter((project) => project.name === 'chromium' || project.name === 'webkit')
    .map((project) => ({
      ...project,
      use: { ...project.use, viewport: { width: 390, height: 844 } },
    })),
})
