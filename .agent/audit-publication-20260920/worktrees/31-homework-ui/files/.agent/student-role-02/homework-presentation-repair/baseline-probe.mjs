import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const playwrightEntry = pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/playwright/index.mjs').href
const { chromium } = await import(playwrightEntry)
const axeEntry = pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/@axe-core/playwright/dist/index.mjs').href
const { AxeBuilder } = await import(axeEntry)

const ownRoot = path.dirname(fileURLToPath(import.meta.url))
const screenshotsDir = path.join(ownRoot, 'screenshots')
const baseURL = process.env.HOMEWORK_FIXTURE_URL ?? 'http://127.0.0.1:5181'
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const viewport = { width: 390, height: 844 }
const rootFonts = [16, 20, 24]

await fs.mkdir(screenshotsDir, { recursive: true })

function pngDimensions(buffer) {
  if (buffer.readUInt32BE(0) !== 0x89504e47 || buffer.readUInt32BE(4) !== 0x0d0a1a0a) {
    throw new Error('not a PNG')
  }
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) }
}

async function waitForFeed(page) {
  await page.waitForSelector('#homework-title')
  await page.locator('.homework-card').first().waitFor()
  await page.waitForTimeout(320)
}

async function scenePage(browser, scene, colorScheme = 'dark', rootFont = 16, requestedViewport = viewport) {
  const context = await browser.newContext({
    viewport: requestedViewport,
    deviceScaleFactor: 1,
    colorScheme,
    locale: 'ru-RU',
    timezoneId: 'Europe/Moscow',
  })
  const page = await context.newPage()
  await page.goto(`${baseURL}/?fixtureScene=${scene}&fixtureRootFont=${rootFont}`, { waitUntil: 'networkidle' })
  await waitForFeed(page)
  await page.evaluate(() => document.fonts.ready.then(() => true))
  return { context, page }
}

async function capture(page, name, details = {}) {
  const file = path.join(screenshotsDir, `${name}.png`)
  const image = await page.screenshot({ path: file, animations: 'disabled' })
  const computed = await page.evaluate(() => {
    const root = document.documentElement
    const body = document.body
    const screen = document.querySelector('.homework-screen')
    const content = document.querySelector('.homework-content')
    const cards = [...document.querySelectorAll('.homework-card')]
    const groupHeading = document.querySelector('.homework-group h2')
    const navItems = [...document.querySelectorAll('.mobile-bottom-nav__item')]
    const horizontalOverflow = Math.max(root.scrollWidth, body.scrollWidth) > window.innerWidth
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight, dpr: window.devicePixelRatio },
      rootFontPx: Number.parseFloat(getComputedStyle(root).fontSize),
      media: {
        light: matchMedia('(prefers-color-scheme: light)').matches,
        dark: matchMedia('(prefers-color-scheme: dark)').matches,
      },
      fontFamily: getComputedStyle(screen).fontFamily,
      fontFaces: [...document.fonts].map((font) => ({ family: font.family, status: font.status })),
      groupHeadingFontPx: groupHeading ? Number.parseFloat(getComputedStyle(groupHeading).fontSize) : null,
      navItems: navItems.map((item) => ({
        active: item.classList.contains('mobile-bottom-nav__item--active'),
        disabled: item instanceof HTMLButtonElement ? item.disabled : false,
        label: item.textContent?.replace(/\s+/g, ' ').trim() ?? '',
        icon: item.querySelector('img')?.getAttribute('src')?.split('/').at(-1) ?? null,
      })),
      background: getComputedStyle(screen).backgroundColor,
      content: content?.getBoundingClientRect().toJSON() ?? null,
      cardCount: cards.length,
      cardBounds: cards.map((card) => card.getBoundingClientRect().toJSON()),
      horizontalOverflow,
      bodyScroll: { width: body.scrollWidth, height: body.scrollHeight },
      scrollY: window.scrollY,
      scrollingElementScrollTop: document.scrollingElement?.scrollTop ?? null,
      activeText: document.querySelector('.homework-content')?.textContent?.replace(/\s+/g, ' ').trim() ?? '',
    }
  })
  const dimensions = pngDimensions(image)
  return { name, file: path.relative(ownRoot, file), png: dimensions, computed, ...details }
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

async function currentState(browser) {
  const { context, page } = await scenePage(browser, 'open')
  const current = await capture(page, '01-open-current', { expectedFrame: '4601:142', state: 'open', accessibilityViolations: await accessibility(page) })
  await page.getByRole('button', { name: 'Раскрыть описание' }).first().click()
  await page.getByText('Решить задачи 1–8 и приложить ссылку на репозиторий.').last().waitFor()
  const expanded = await capture(page, '02-expanded', { expectedFrame: '4601:848562', state: 'expanded', accessibilityViolations: await accessibility(page) })
  await context.close()
  return { current, expanded }
}

async function completedState(browser) {
  const { context, page } = await scenePage(browser, 'open')
  const math = page.getByRole('button', { name: 'Отметить задание «Основы программирования» выполненным' })
  await math.click()
  await page.getByRole('button', { name: 'Снять отметку «Выполнено» с задания «Основы программирования»' }).waitFor()
  const networks = page.getByRole('button', { name: 'Отметить задание «Компьютерные сети» выполненным' })
  await networks.click()
  await page.getByRole('button', { name: 'Снять отметку «Выполнено» с задания «Компьютерные сети»' }).waitFor()
  await page.evaluate(() => window.scrollTo(0, 0))
  const completed = await capture(page, '03-completed-multiple', { expectedFrame: '4601:848636', state: 'completed-multiple', accessibilityViolations: await accessibility(page) })
  await context.close()
  return completed
}

async function historicalState(browser) {
  const { context, page } = await scenePage(browser, 'runtime')
  await page.getByRole('button', { name: 'Посмотреть предыдущие' }).click()
  await page.getByRole('button', { name: 'Вернуться к сегодняшним заданиям' }).waitFor()
  await page.getByRole('heading', { name: '28 августа' }).waitFor()
  await page.evaluate(() => window.scrollTo(0, 0))
  const historical = await capture(page, '04-historical', { expectedFrame: '4788:146', state: 'historical', accessibilityViolations: await accessibility(page) })
  await context.close()
  return historical
}

async function noMaterialsState(browser) {
  const { context, page } = await scenePage(browser, 'no-materials')
  const physicsCard = page.getByRole('heading', { name: 'Основы программирования' }).locator('..')
  const firstMaterial = page.getByRole('button', { name: 'Материалы' }).first()
  const materialButtons = await page.getByRole('button', { name: 'Материалы' }).count()
  const firstCardMaterial = await physicsCard.getByRole('button', { name: 'Материалы' }).count()
  const noMaterialCard = page.locator('.homework-card[data-material="none"]').first()
  await noMaterialCard.scrollIntoViewIfNeeded()
  const noMaterials = await capture(page, '05-no-materials', {
    expectedFrame: '4922:343',
    state: 'materials-none',
    materialButtons,
    firstCardMaterial,
    noMaterialText: await noMaterialCard.textContent(),
    availableMaterialVisible: await firstMaterial.isVisible(),
    accessibilityViolations: await accessibility(page),
  })
  await context.close()
  return noMaterials
}

async function keyboardAndFailure(browser) {
  const { context, page } = await scenePage(browser, 'runtime')
  const material = page.getByRole('button', { name: 'Материалы' }).first()
  await material.click()
  await page.getByText('Открыт материал: https://example.test/history').waitFor()
  const materialEvidence = await page.getByText('Открыт материал: https://example.test/history').textContent()

  const completion = page.getByRole('button', { name: 'Отметить задание «Математика» выполненным' })
  await completion.focus()
  await page.keyboard.press('Space')
  await page.getByRole('button', { name: 'Снять отметку «Выполнено» с задания «Математика»' }).waitFor()
  const keyboard = await page.evaluate(() => ({
    active: document.activeElement?.getAttribute('aria-label') ?? null,
    activeTag: document.activeElement?.tagName ?? null,
    focusedCompletion: document.activeElement?.matches('.homework-completion') ?? false,
    state: document.querySelector('[data-completion="completed"] [data-state="completed"]')?.getAttribute('data-state') ?? null,
  }))

  const reverse = page.getByRole('button', { name: 'Снять отметку «Выполнено» с задания «Математика»' })
  await reverse.click()
  await page.getByRole('button', { name: 'Отметить задание «Математика» выполненным' }).waitFor()
  const reversal = await page.evaluate(() => ({
    open: document.querySelector('[data-completion="open"] [aria-label*="Математика"]')?.getAttribute('aria-pressed') ?? null,
  }))

  await page.getByRole('button', { name: 'Ломать сохранение' }).click()
  const secondCompletion = page.getByRole('button', { name: 'Отметить задание «Компьютерные сети» выполненным' })
  await secondCompletion.click()
  await page.getByRole('alert').filter({ hasText: 'Сохранение временно недоступно' }).waitFor()
  const failure = await capture(page, '06-failure-recovery', {
    state: 'failed-completion',
    keyboard,
    reversal,
    materialEvidence,
    accessibilityViolations: await accessibility(page),
  })
  await context.close()
  return { keyboard, reversal, materialEvidence, failure }
}

async function offlineState(browser) {
  const { context, page } = await scenePage(browser, 'runtime')
  await page.getByRole('button', { name: 'Отключить сеть' }).click()
  await page.getByText('Офлайн · задания доступны только для просмотра').waitFor()
  const offline = await page.evaluate(() => ({
    completionButtons: [...document.querySelectorAll('.homework-completion')].map((button) => ({
      disabled: (button instanceof HTMLButtonElement) ? button.disabled : false,
      label: button.getAttribute('aria-label'),
    })),
    materialButtons: document.querySelectorAll('.homework-material').length,
    disclosureButtons: document.querySelectorAll('.homework-expand').length,
  }))
  const screenshot = await capture(page, '07-offline', {
    state: 'offline-read-only',
    offline,
    accessibilityViolations: await accessibility(page),
  })
  await context.close()
  return { offline, screenshot }
}

async function themeAndFontMatrix(browser) {
  const results = []
  for (const colorScheme of ['light', 'no-preference', 'dark']) {
    for (const rootFont of rootFonts) {
      const { context, page } = await scenePage(browser, 'open', colorScheme, rootFont)
      results.push(await capture(page, `matrix-${colorScheme}-${rootFont}`, {
        state: 'open',
        requestedColorScheme: colorScheme,
        requestedRootFont: rootFont,
      }))
      await context.close()
    }
  }
  for (const width of [320, 390, 430]) {
    const { context, page } = await scenePage(browser, 'open', 'dark', 16, { width, height: viewport.height })
    results.push(await capture(page, `responsive-${width}`, {
      state: 'open',
      requestedColorScheme: 'dark',
      requestedRootFont: 16,
      requestedViewport: { width, height: viewport.height },
    }))
    await context.close()
  }
  return results
}

const browser = await chromium.launch({ headless: true, executablePath: edgePath })
try {
  const evidence = {
    generatedAt: new Date().toISOString(),
    browser: { engine: 'Microsoft Edge', executablePath: edgePath, headless: true },
    context: { viewport, deviceScaleFactor: 1, timezoneId: 'Europe/Moscow', locale: 'ru-RU' },
    scenes: {
      ...(await currentState(browser)),
      completed: await completedState(browser),
      historical: await historicalState(browser),
      noMaterials: await noMaterialsState(browser),
      interaction: await keyboardAndFailure(browser),
      offline: await offlineState(browser),
    },
    matrix: await themeAndFontMatrix(browser),
  }
  await fs.writeFile(path.join(ownRoot, 'visual-evidence.json'), `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
  console.log(JSON.stringify(evidence, null, 2))
} finally {
  await browser.close()
}
