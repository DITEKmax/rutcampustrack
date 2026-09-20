import { pathToFileURL } from 'node:url'
import fs from 'node:fs/promises'

const { chromium } = await import(pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/playwright/index.mjs').href)
const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
})
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 1,
  colorScheme: 'dark',
  locale: 'ru-RU',
  timezoneId: 'Europe/Moscow',
})
const page = await context.newPage()
try {
  await page.goto('http://127.0.0.1:5181/?fixtureScene=runtime', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: 'Отметить задание «Математика» выполненным' }).waitFor()
  const completion = page.getByRole('button', { name: 'Отметить задание «Математика» выполненным' })
  await completion.focus()
  await page.keyboard.press('Space')
  await page.getByRole('button', { name: 'Снять отметку «Выполнено» с задания «Математика»' }).waitFor()
  const samples = []
  for (const milliseconds of [0, 1, 20, 100, 500]) {
    if (milliseconds > 0) await page.waitForTimeout(milliseconds)
    samples.push(await page.evaluate((delay) => ({
      delay,
      activeTag: document.activeElement?.tagName ?? null,
      activeLabel: document.activeElement?.getAttribute('aria-label') ?? null,
      focusedCompletion: document.activeElement?.matches('.homework-completion') ?? false,
      mathButtons: [...document.querySelectorAll('.homework-completion')].filter((button) => button.getAttribute('aria-label')?.includes('Математика')).map((button) => ({
        connected: button.isConnected,
        disabled: button instanceof HTMLButtonElement ? button.disabled : false,
        label: button.getAttribute('aria-label'),
      })),
    }), milliseconds))
  }
  await fs.writeFile('C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-ui/.agent/student-role-02/mobile-theme/focus-evidence.json', JSON.stringify(samples, null, 2))
  console.log(JSON.stringify(samples, null, 2))
} finally {
  await context.close()
  await browser.close()
}
