import { pathToFileURL } from 'node:url'

const { chromium } = await import(pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/playwright/index.mjs').href)

const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' })
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'light', locale: 'ru-RU', timezoneId: 'Europe/Moscow' })
const page = await context.newPage()
await page.goto('http://127.0.0.1:5181/?fixtureScene=open&fixtureRootFont=16', { waitUntil: 'networkidle' })
await page.waitForSelector('.homework-card')
await page.evaluate(() => document.fonts.ready)
console.log(JSON.stringify(await page.evaluate(() => ({
  fonts: [...document.fonts].map((font) => ({ family: font.family, status: font.status, weight: font.weight })),
  screenFamily: getComputedStyle(document.querySelector('.homework-screen')).fontFamily,
  titleFamily: getComputedStyle(document.querySelector('.homework-card__title')).fontFamily,
  onestLoaded: document.fonts.check('13px "Onest Variable"'),
})), null, 2))
await context.close()
await browser.close()
