import { defineConfig, type PlaywrightTestConfig } from '@playwright/test';
import { join } from 'node:path';
import { requireLocalSpecialLane, type LocalSpecialLane } from './fixtures/local-special';

const selections: Record<LocalSpecialLane, { config: string; titles: string[] }> = {
  bot: { config: './playwright.real-mobile.config', titles: ['actual private bot menu roster and teacher Today use hosted current authority'] },
  'production-service-worker': { config: './playwright.real-mobile.config', titles: ['production worker update activates through the update gate and retains a warm launch'] },
  'password-recovery': { config: './playwright.real-mobile.config', titles: ['password recovery consumes its own delivered OTP and admits the replacement password @cloud-accepted'] },
  'registry-native-pdf': { config: './playwright.registry-export.config', titles: ['FULL native user PDF preserves selected ordered fields and its committed audit @cloud-accepted', 'LIMITED native user PDF preserves selected ordered fields and its committed audit @cloud-accepted', 'renderer outage terminates its PDF job safely and a fresh job downloads after recovery @cloud-accepted'] },
  'semester-metadata': { config: './playwright.semester-lifecycle.config', titles: ['FULL admin manages owned semester metadata and historical group promotion through the ready UI'] },
  'subject-deletion': { config: './playwright.semester-lifecycle.config', titles: ['HEADMAN deletes one subject through real proof and participants while mixed EXCUSE keeps the control subject'] },
  assignment: { config: './playwright.assignment-lifecycle.config', titles: ['assignment lifecycle API phase'] },
  today: { config: './playwright.teaching-clock.config', titles: ['student uses Today quick request after the genuine first lesson closes @cloud-accepted'] },
  'semester-reconciliation': { config: './playwright.semester-lifecycle.config', titles: ['FULL admin reconciles one exact stranded close before archive completion'] },
  'application-recovery': { config: './playwright.application-recovery.config', titles: ['application recovery API phase'] },
  'semester-archive': { config: './playwright.semester-lifecycle.config', titles: ['FULL admin archives and restores the disposable CI semester through real participants'] },
};
const lane = process.env.RCT_LOCAL_SPECIAL_LANE as LocalSpecialLane;
if (!lane || !Object.hasOwn(selections, lane)) throw new Error('Local special selection is required');
const context = requireLocalSpecialLane(lane);
const existing = require(selections[lane].config).default as PlaywrightTestConfig;
const phase = process.env.RCT_LOCAL_SPECIAL_PHASE!;
const escape = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
export default defineConfig({
  ...existing,
  grep: new RegExp(`(?:${selections[lane].titles.map(escape).join('|')})$`),
  grepInvert: undefined,
  outputDir: phase === 'main' ? join(context.laneRun, 'artifacts') : join(context.laneRun, 'artifacts', phase),
  reporter: [['./fixtures/safe-reporter.ts', { localStartupDiagnostic: true }]],
  fullyParallel: false, workers: 1, retries: 0, maxFailures: 0, forbidOnly: true,
  use: { ...existing.use, ignoreHTTPSErrors: false, trace: 'off', video: 'off', screenshot: 'off' },
  projects: existing.projects?.filter(project => project.name === 'chromium').map(project => ({
    ...project, use: { ...project.use, ignoreHTTPSErrors: false, trace: 'off', video: 'off', screenshot: 'off' },
  })),
});
