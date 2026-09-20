import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const playwrightEntry = pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/playwright/index.mjs').href
const { chromium } = await import(playwrightEntry)
const axeEntry = pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/@axe-core/playwright/dist/index.mjs').href
const { AxeBuilder } = await import(axeEntry)

const ownRoot = path.dirname(fileURLToPath(import.meta.url))
const screenshotsDir = path.join(ownRoot, 'screenshots-final')
const baseURL = process.env.HOMEWORK_FIXTURE_URL ?? 'http://127.0.0.1:5181'
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const viewport = { width: 390, height: 844 }
const rootFonts = [16, 20, 24]
const expectedTitle = 'Собери консольное приложение и добавь тесты.'
const expectedDescription = 'Добавь короткое README и приложи ссылку на репозиторий. Репозиторий должен быть доступен преподавателю.'
const emptyDescriptionTitle = 'Спроектируй таблицу сравнения протоколов.'

await fs.mkdir(screenshotsDir, { recursive: true })

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

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

async function cardPresentation(card) {
  return card.evaluate((node) => {
    const element = node
    const children = [...element.children].map((child) => typeof child.className === 'string' ? child.className : child.tagName)
    const actions = element.querySelector('.homework-card__actions')
    const expand = element.querySelector('.homework-expand')
    const title = element.querySelector('.homework-card__title')
    const description = element.querySelector('.homework-card__description')
    const rect = (value) => value?.getBoundingClientRect().toJSON() ?? null
    return {
      subject: element.querySelector('h3')?.textContent?.trim() ?? '',
      title: title?.textContent?.trim() ?? null,
      description: description?.textContent?.trim() ?? null,
      descriptionFontPx: description ? Number.parseFloat(getComputedStyle(description).fontSize) : null,
      actions: rect(actions),
      expand: rect(expand),
      expandCount: element.querySelectorAll('.homework-expand').length,
      materialCount: element.querySelectorAll('.homework-material').length,
      error: element.querySelector('.homework-material-error')?.textContent?.trim() ?? null,
      children,
    }
  })
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
      fontCheck: document.fonts.check('13px "Onest Variable"'),
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
  const firstCard = page.locator('.homework-card').first()
  const emptyDescriptionCard = page.locator('.homework-card').nth(1)
  const before = await cardPresentation(firstCard)
  const emptyBefore = await cardPresentation(emptyDescriptionCard)
  assert(before.subject === 'Основы программирования', `Unexpected first subject: ${before.subject}`)
  assert(before.title === expectedTitle, 'Mandatory title is not the always-visible brief')
  assert(before.description === null, 'Optional description is rendered before expansion')
  assert(before.expandCount === 1, 'Non-empty description is missing its disclosure control')
  assert(await firstCard.getByRole('button', { name: 'Раскрыть описание' }).getAttribute('aria-controls') === null, 'Collapsed disclosure references an absent detail region')
  assert(emptyBefore.title === emptyDescriptionTitle, 'Empty-description fixture title is missing')
  assert(emptyBefore.description === null && emptyBefore.expandCount === 0, 'Empty description created a disclosure region or control')
  const current = await capture(page, '01-open-current', { expectedFrame: '4601:142', state: 'open', accessibilityViolations: await accessibility(page), presentation: { before, emptyBefore } })
  await page.getByRole('button', { name: 'Раскрыть описание' }).first().click()
  await firstCard.locator('.homework-card__description').waitFor()
  const expandedPresentation = await cardPresentation(firstCard)
  assert(expandedPresentation.description === expectedDescription, 'Expanded detail lost or changed its source text')
  assert(expandedPresentation.descriptionFontPx === 12, `Expected 12px detail, got ${expandedPresentation.descriptionFontPx}`)
  const titleIndex = expandedPresentation.children.indexOf('homework-card__title')
  const descriptionIndex = expandedPresentation.children.indexOf('homework-card__description')
  const actionsIndex = expandedPresentation.children.indexOf('homework-card__actions')
  assert(titleIndex >= 0 && titleIndex < descriptionIndex && descriptionIndex < actionsIndex, 'Brief/detail/actions order does not match the accepted anatomy')
  assert(await firstCard.getByRole('button', { name: 'Свернуть описание' }).getAttribute('aria-controls') === 'homework-description-source-programming', 'Expanded disclosure does not reference its detail region')
  const expanded = await capture(page, '02-expanded', { expectedFrame: '4601:848562', state: 'expanded', accessibilityViolations: await accessibility(page), presentation: { expanded: expandedPresentation } })
  await page.getByRole('button', { name: 'Свернуть описание' }).first().click()
  assert((await cardPresentation(firstCard)).description === null, 'Collapse left an optional detail region in the DOM')
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
  const noMaterialCard = page.locator('.homework-card[data-material="none"]').first()
  const firstMaterial = page.getByRole('button', { name: 'Материалы' }).first()
  const materialButtons = await page.getByRole('button', { name: 'Материалы' }).count()
  const noMaterialPresentation = await cardPresentation(noMaterialCard)
  assert(noMaterialPresentation.materialCount === 0, 'materials=null rendered a materials action')
  assert(noMaterialPresentation.actions && noMaterialPresentation.expandCount === 1 && noMaterialPresentation.expand, 'Non-empty no-material card lost its disclosure control')
  assert(Math.abs(noMaterialPresentation.actions.right - noMaterialPresentation.expand.right) < 0.5, 'No-material disclosure is not aligned to the action-row right edge')
  assert(!noMaterialPresentation.error, 'materials=null rendered an unsupported-link error')
  await noMaterialCard.scrollIntoViewIfNeeded()
  const noMaterials = await capture(page, '05-no-materials', {
    expectedFrame: '4922:343',
    state: 'materials-none',
    materialButtons,
    noMaterialPresentation,
    noMaterialText: await noMaterialCard.textContent(),
    availableMaterialVisible: await firstMaterial.isVisible(),
    accessibilityViolations: await accessibility(page),
  })
  await context.close()
  return noMaterials
}

async function emptyNoMaterialsState(browser) {
  const { context, page } = await scenePage(browser, 'no-materials-empty')
  const card = page.locator('.homework-card[data-material="none"]').first()
  const presentation = await cardPresentation(card)
  assert(presentation.title === emptyDescriptionTitle, 'Empty no-material fixture title is missing')
  assert(presentation.description === null && presentation.expandCount === 0, 'Empty no-material detail created a disclosure region or control')
  assert(presentation.actions === null, 'Empty no-material card created an empty action container')
  const screenshot = await capture(page, '08-no-materials-empty', {
    expectedFrame: '4922:343',
    state: 'materials-none-empty-detail',
    presentation,
    accessibilityViolations: await accessibility(page),
  })
  await context.close()
  return screenshot
}

async function unsafeMaterialsState(browser) {
  const { context, page } = await scenePage(browser, 'runtime')
  const card = page.locator('.homework-card[data-material="unsafe"]').first()
  const presentation = await cardPresentation(card)
  assert(presentation.materialCount === 0, 'Unsafe material rendered an actionable materials button')
  assert(presentation.error === 'Материалы недоступны: ссылка не поддерживается.', 'Unsafe material did not expose the recoverable explanation')
  assert(presentation.expandCount === 1, 'Unsafe-material card lost its independent disclosure control')
  const screenshot = await capture(page, '09-unsafe-material', {
    state: 'unsafe-material',
    presentation,
    accessibilityViolations: await accessibility(page),
  })
  await context.close()
  return screenshot
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
      const result = await capture(page, `matrix-${colorScheme}-${rootFont}`, {
        state: 'open',
        requestedColorScheme: colorScheme,
        requestedRootFont: rootFont,
      })
      assert(result.png.width === viewport.width && result.png.height === viewport.height, `Unexpected PNG dimensions for ${colorScheme}/${rootFont}`)
      assert(!result.computed.horizontalOverflow, `Horizontal overflow at ${colorScheme}/${rootFont}`)
      assert(result.computed.fontCheck, `Onest is not available at ${colorScheme}/${rootFont}`)
      results.push(result)
      await context.close()
    }
  }
  for (const width of [320, 390, 430]) {
    const { context, page } = await scenePage(browser, 'open', 'dark', 16, { width, height: viewport.height })
    const result = await capture(page, `responsive-${width}`, {
      state: 'open',
      requestedColorScheme: 'dark',
      requestedRootFont: 16,
      requestedViewport: { width, height: viewport.height },
    })
    assert(result.png.width === width && result.png.height === viewport.height, `Unexpected PNG dimensions at width ${width}`)
    assert(!result.computed.horizontalOverflow, `Horizontal overflow at width ${width}`)
    results.push(result)
    await context.close()
  }
  return results
}

const browser = await chromium.launch({ headless: true, executablePath: edgePath })
try {
  const evidence = {
    generatedAt: new Date().toISOString(),
    revision: 'd3c31acb8cce53791a4981e5858a37d44fdc9a0e',
    browser: { engine: 'Microsoft Edge', executablePath: edgePath, headless: true },
    context: { viewport, deviceScaleFactor: 1, timezoneId: 'Europe/Moscow', locale: 'ru-RU' },
    fixture: { source: 'copied actual HomeworkScreen/useHomework fixture', expectedTitle, expectedDescription, emptyDescriptionTitle, note: 'Distinct brief/detail values are source-faithful representatives used to make field mapping observable.' },
    scenes: {
      ...(await currentState(browser)),
      completed: await completedState(browser),
      historical: await historicalState(browser),
      noMaterials: await noMaterialsState(browser),
      interaction: await keyboardAndFailure(browser),
      offline: await offlineState(browser),
      emptyNoMaterials: await emptyNoMaterialsState(browser),
      unsafeMaterials: await unsafeMaterialsState(browser),
    },
    matrix: await themeAndFontMatrix(browser),
  }
  await fs.writeFile(path.join(ownRoot, 'final-visual-evidence.json'), `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
  console.log(JSON.stringify(evidence, null, 2))
} finally {
  await browser.close()
}
