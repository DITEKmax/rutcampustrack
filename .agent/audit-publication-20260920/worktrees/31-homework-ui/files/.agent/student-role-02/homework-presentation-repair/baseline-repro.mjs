import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const playwrightEntry = pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/playwright/index.mjs').href
const { chromium } = await import(playwrightEntry)
const axeEntry = pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/@axe-core/playwright/dist/index.mjs').href
const { AxeBuilder } = await import(axeEntry)

const ownRoot = path.dirname(fileURLToPath(import.meta.url))
const screenshotsDir = path.join(ownRoot, 'screenshots-baseline')
const baseURL = process.env.HOMEWORK_FIXTURE_URL ?? 'http://127.0.0.1:5181'
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const viewport = { width: 390, height: 844 }
const expectedTitle = 'Собери консольное приложение и добавь тесты.'
const expectedDescription = 'Добавь короткое README и приложи ссылку на репозиторий. Репозиторий должен быть доступен преподавателю.'

await fs.mkdir(screenshotsDir, { recursive: true })

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function openPage(browser, scene) {
  const context = await browser.newContext({
    viewport,
    deviceScaleFactor: 1,
    colorScheme: 'dark',
    locale: 'ru-RU',
    timezoneId: 'Europe/Moscow',
  })
  const page = await context.newPage()
  await page.goto(`${baseURL}/?fixtureScene=${scene}&fixtureRootFont=16`, { waitUntil: 'networkidle' })
  await page.waitForSelector('#homework-title')
  await page.locator('.homework-card').first().waitFor()
  await page.evaluate(() => document.fonts.ready.then(() => true))
  return { context, page }
}

async function cardSnapshot(card) {
  return card.evaluate((node) => {
    const element = node
    const actions = element.querySelector('.homework-card__actions')
    const expand = element.querySelector('.homework-expand')
    const rect = (value) => value?.getBoundingClientRect().toJSON() ?? null
    const children = [...element.children].map((child) => typeof child.className === 'string' ? child.className : child.tagName)
    const description = element.querySelector('.homework-card__description')
    const disclosure = element.querySelector('.homework-card__disclosure')
    return {
      heading: element.querySelector('h3')?.textContent?.trim() ?? '',
      description: description?.textContent?.trim() ?? '',
      descriptionPresent: Boolean(description),
      disclosure: disclosure?.textContent?.trim() ?? null,
      material: element.getAttribute('data-material'),
      children,
      actions: rect(actions),
      expand: rect(expand),
      expandCount: element.querySelectorAll('.homework-expand').length,
      descriptionFontPx: description ? Number.parseFloat(getComputedStyle(description).fontSize) : null,
      disclosureFontPx: disclosure ? Number.parseFloat(getComputedStyle(disclosure).fontSize) : null,
    }
  })
}

async function accessibility(page) {
  const result = await new AxeBuilder({ page }).analyze()
  return result.violations.map((violation) => ({
    id: violation.id,
    impact: violation.impact,
    help: violation.help,
    nodes: violation.nodes.map((node) => ({ target: node.target, html: node.html })),
  }))
}

async function capture(page, name) {
  const file = path.join(screenshotsDir, `${name}.png`)
  await page.screenshot({ path: file, animations: 'disabled' })
  return path.relative(ownRoot, file)
}

const browser = await chromium.launch({ headless: true, executablePath: edgePath })
try {
  const { context: openContext, page: open } = await openPage(browser, 'open')
  const openCard = open.locator('.homework-card').first()
  const before = await cardSnapshot(openCard)
  const emptyCard = await cardSnapshot(open.locator('.homework-card').nth(1))
  const openScreenshot = await capture(open, 'baseline-open')
  await open.getByRole('button', { name: 'Раскрыть описание' }).first().click()
  await openCard.locator('.homework-card__disclosure').waitFor()
  const expanded = await cardSnapshot(openCard)
  const expandedScreenshot = await capture(open, 'baseline-expanded')
  const openA11y = await accessibility(open)
  await openContext.close()

  assert(before.heading === 'Основы программирования', `Unexpected subject heading: ${before.heading}`)
  assert(before.description === expectedDescription, 'Baseline fixture did not expose the distinct detail text')
  assert(before.disclosure === null, 'Baseline unexpectedly exposes title before expansion')
  assert(expanded.disclosure === expectedTitle, 'Baseline expansion does not expose the mandatory title')
  assert(expanded.children.indexOf('homework-card__actions') < expanded.children.indexOf('homework-card__disclosure'), 'Baseline title is no longer after actions')
  assert(emptyCard.descriptionPresent && emptyCard.description === '', 'Baseline empty-description fixture is missing')

  const { context: noMaterialContext, page: noMaterialPage } = await openPage(browser, 'no-materials')
  const noMaterialCard = noMaterialPage.locator('.homework-card[data-material="none"]').first()
  const noMaterial = await cardSnapshot(noMaterialCard)
  const noMaterialScreenshot = await capture(noMaterialPage, 'baseline-no-materials')
  const noMaterialA11y = await accessibility(noMaterialPage)
  await noMaterialContext.close()

  assert(noMaterial.material === 'none', 'No-material baseline card was not selected')
  assert(noMaterial.expand && noMaterial.actions, 'Baseline no-material disclosure bounds are missing')
  assert(noMaterial.expand.left < noMaterial.actions.right - noMaterial.expand.width - 8, 'Baseline no-material disclosure is already right aligned')

  const evidence = {
    generatedAt: new Date().toISOString(),
    revision: 'd3c31acb8cce53791a4981e5858a37d44fdc9a0e',
    environment: { browser: 'Microsoft Edge headless', viewport, locale: 'ru-RU', timezoneId: 'Europe/Moscow' },
    fixture: { source: 'copied homework-ui-visual actual component fixture', expectedTitle, expectedDescription, emptyDescriptionItem: 'source-networks' },
    findings: {
      materialsNoneAlignment: { status: 'REPRODUCED', card: noMaterial },
      titleDescriptionOrder: { status: 'REPRODUCED', before, expanded, emptyCard },
    },
    screenshots: { open: openScreenshot, expanded: expandedScreenshot, noMaterials: noMaterialScreenshot },
    accessibilityViolations: { open: openA11y, noMaterials: noMaterialA11y },
  }
  await fs.writeFile(path.join(ownRoot, 'baseline-evidence.json'), `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
  console.log(JSON.stringify(evidence, null, 2))
} finally {
  await browser.close()
}
