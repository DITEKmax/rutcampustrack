import fs from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const { chromium } = await import(pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/playwright/index.mjs').href)

const root = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-ui'
const evidencePath = process.argv[2] ?? `${root}/.agent/student-role-02/mobile-theme/today-evidence.json`
const screenshotPrefix = process.argv[3] ?? 'today'
const screenshots = `${root}/.agent/student-role-02/mobile-theme/screenshots`
await fs.mkdir(screenshots, { recursive: true })

function luminance([r, g, b]) {
  const channel = (value) => {
    const normalized = value / 255
    return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

function contrast(foreground, background) {
  if (!foreground || !background) return null
  const foregroundLuminance = luminance(foreground)
  const backgroundLuminance = luminance(background)
  return Number(((Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
    / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05)).toFixed(2))
}

function colorFromCss(value) {
  const match = value.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i)
  if (!match) return null
  return [Number(match[1]), Number(match[2]), Number(match[3])]
}

function colorsFromGradient(value) {
  return [...value.matchAll(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/gi)]
    .map((match) => [Number(match[1]), Number(match[2]), Number(match[3])])
}

function gradientContrast(foreground, gradient) {
  if (!foreground || gradient.length === 0) return null
  let minimum = Number.POSITIVE_INFINITY
  for (let index = 0; index < gradient.length - 1; index += 1) {
    const start = gradient[index]
    const end = gradient[index + 1]
    for (let step = 0; step <= 32; step += 1) {
      const ratio = step / 32
      const sample = start.map((channel, channelIndex) => Math.round(channel + ((end[channelIndex] - channel) * ratio)))
      minimum = Math.min(minimum, contrast(foreground, sample))
    }
  }
  return Number(minimum.toFixed(2))
}

function hexColor(value) {
  const match = value.match(/^#([\da-f]{6})$/i)
  if (!match) return null
  return [1, 3, 5].map((index) => Number.parseInt(match[1].slice(index - 1, index + 1), 16))
}

const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
})

const cases = [
  { name: 'explicit-dark-16', mode: 'dark', colorScheme: 'dark', rootFont: 16 },
  { name: 'explicit-light-16', mode: 'light', colorScheme: 'dark', rootFont: 16 },
  { name: 'system-dark-16', mode: 'system', colorScheme: 'dark', rootFont: 16 },
  { name: 'system-light-16', mode: 'system', colorScheme: 'light', rootFont: 16 },
  { name: 'explicit-dark-20', mode: 'dark', colorScheme: 'dark', rootFont: 20 },
  { name: 'explicit-light-20', mode: 'light', colorScheme: 'dark', rootFont: 20 },
  { name: 'explicit-dark-24', mode: 'dark', colorScheme: 'dark', rootFont: 24 },
  { name: 'explicit-light-24', mode: 'light', colorScheme: 'dark', rootFont: 24 },
]

const results = []
try {
  for (const testCase of cases) {
    const context = await browser.newContext({
      viewport: { width: 390, height: 844 },
      deviceScaleFactor: 1,
      colorScheme: testCase.colorScheme,
      locale: 'ru-RU',
      timezoneId: 'Europe/Moscow',
    })
    const page = await context.newPage()
    try {
      await page.goto(`http://127.0.0.1:5182/?fixtureRootFont=${testCase.rootFont}`, { waitUntil: 'networkidle' })
      await page.evaluate(async (mode) => {
        await document.fonts.ready
        document.documentElement.setAttribute('data-theme', mode)
      }, testCase.mode)
      await page.locator('.today-hero__action').waitFor()
      await page.waitForTimeout(40)

      const palette = await page.evaluate(() => {
        const luminance = ([r, g, b]) => {
          const channel = (value) => {
            const normalized = value / 255
            return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
          }
          return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
        }
        const contrast = (foreground, background) => {
          if (!foreground || !background) return null
          const foregroundLuminance = luminance(foreground)
          const backgroundLuminance = luminance(background)
          return Number(((Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
            / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05)).toFixed(2))
        }
        const gradientContrast = (foreground, gradient) => {
          if (!foreground || gradient.length === 0) return null
          let minimum = Number.POSITIVE_INFINITY
          for (let index = 0; index < gradient.length - 1; index += 1) {
            const start = gradient[index]
            const end = gradient[index + 1]
            for (let step = 0; step <= 32; step += 1) {
              const ratio = step / 32
              const sample = start.map((channel, channelIndex) => Math.round(channel + ((end[channelIndex] - channel) * ratio)))
              minimum = Math.min(minimum, contrast(foreground, sample))
            }
          }
          return Number(minimum.toFixed(2))
        }
        const rootStyle = getComputedStyle(document.documentElement)
        const read = (name) => rootStyle.getPropertyValue(name).trim()
        const role = document.querySelector('.today-role')
        const action = document.querySelector('.today-hero__action')
        const hero = document.querySelector('.today-hero')
        const card = document.querySelector('.today-card')
        const nav = document.querySelector('.mobile-bottom-nav')
        const activeNav = document.querySelector('.mobile-bottom-nav__item--active')
        const parse = (value) => {
          const match = value.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i)
          if (match) return [Number(match[1]), Number(match[2]), Number(match[3])]
          const hex = value.match(/^#([\da-f]{6})$/i)
          return hex ? [1, 3, 5].map((index) => Number.parseInt(hex[1].slice(index - 1, index + 1), 16)) : null
        }
        const parseGradient = (value) => [...value.matchAll(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/gi)]
          .map((match) => [Number(match[1]), Number(match[2]), Number(match[3])])
        const styleOf = (element) => element ? getComputedStyle(element) : null
        const roleStyle = styleOf(role)
        const actionStyle = styleOf(action)
        const heroStyle = styleOf(hero)
        const cardStyle = styleOf(card)
        const navStyle = styleOf(nav)
        const activeNavStyle = styleOf(activeNav)
        const roleForeground = parse(roleStyle?.color ?? '')
        const actionForeground = parse(actionStyle?.color ?? '')
        const roleGradient = parseGradient(roleStyle?.backgroundImage ?? '')
        const actionGradient = parseGradient(actionStyle?.backgroundImage ?? '')
        const heroForeground = parse(heroStyle?.color ?? '')
        const heroBackground = parse(heroStyle?.backgroundColor ?? '')
        const primary = parse(read('--rct-color-text-primary'))
        const secondary = parse(read('--rct-color-text-secondary'))
        const base = parse(read('--rct-color-surface-base'))
        const raised = parse(read('--rct-color-surface-raised'))
        const accent = parse(read('--rct-color-accent-now'))
        const accentOn = parse(read('--rct-color-accent-on-now'))
        return {
          rootTheme: document.documentElement.getAttribute('data-theme'),
          rootFontSize: rootStyle.fontSize,
          colorScheme: rootStyle.colorScheme,
          fontsReady: document.fonts.status === 'loaded',
          onestLoaded: document.fonts.check('16px "Onest Variable"'),
          tokens: {
            surfaceBase: read('--rct-color-surface-base'),
            surfaceRaised: read('--rct-color-surface-raised'),
            surfaceFloat: read('--rct-color-surface-float'),
            textPrimary: read('--rct-color-text-primary'),
            textSecondary: read('--rct-color-text-secondary'),
            accentNow: read('--rct-color-accent-now'),
            accentOnNow: read('--rct-color-accent-on-now'),
            onFillStrong: read('--rct-color-text-on-fill-strong'),
            dockOpacity: read('--rct-mobile-dock-opacity'),
            dockBlur: read('--rct-mobile-dock-blur'),
          },
          contrast: {
            primaryOnBase: contrast(primary, base),
            secondaryOnRaised: contrast(secondary, raised),
            heroOnAccent: contrast(heroForeground, heroBackground),
            roleOnGradient: gradientContrast(roleForeground, roleGradient),
            actionOnGradient: gradientContrast(actionForeground, actionGradient),
            activeNavOnAccent: contrast(parse(activeNavStyle?.color ?? ''), parse(activeNavStyle?.backgroundColor ?? '')),
          },
          controls: {
            role: {
              color: roleStyle?.color ?? null,
              backgroundImage: roleStyle?.backgroundImage ?? null,
              gradientStops: roleGradient,
            },
            action: {
              color: actionStyle?.color ?? null,
              backgroundImage: actionStyle?.backgroundImage ?? null,
              gradientStops: actionGradient,
            },
            hero: {
              color: heroStyle?.color ?? null,
              backgroundColor: heroStyle?.backgroundColor ?? null,
            },
            card: {
              color: cardStyle?.color ?? null,
              backgroundColor: cardStyle?.backgroundColor ?? null,
            },
            dock: {
              backgroundColor: navStyle?.backgroundColor ?? null,
              backdropFilter: navStyle?.backdropFilter ?? null,
            },
          },
          geometry: {
            viewportWidth: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth,
            noHorizontalOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
            roleWidth: role ? Number(role.getBoundingClientRect().width.toFixed(2)) : null,
            heroHeight: hero ? Number(hero.getBoundingClientRect().height.toFixed(2)) : null,
            actionHeight: action ? Number(action.getBoundingClientRect().height.toFixed(2)) : null,
            navWidth: nav ? Number(nav.getBoundingClientRect().width.toFixed(2)) : null,
          },
        }
      })

      let mediaTransition = null
      if (testCase.mode === 'system' || testCase.mode === 'light') {
        const initialScheme = testCase.colorScheme
        const oppositeScheme = initialScheme === 'dark' ? 'light' : 'dark'
        const before = await page.evaluate(() => ({
          surfaceBase: getComputedStyle(document.documentElement).getPropertyValue('--rct-color-surface-base').trim(),
          colorScheme: getComputedStyle(document.documentElement).colorScheme,
        }))
        await page.emulateMedia({ colorScheme: oppositeScheme })
        await page.waitForTimeout(30)
        const after = await page.evaluate(() => ({
          surfaceBase: getComputedStyle(document.documentElement).getPropertyValue('--rct-color-surface-base').trim(),
          colorScheme: getComputedStyle(document.documentElement).colorScheme,
        }))
        await page.emulateMedia({ colorScheme: initialScheme })
        await page.waitForTimeout(30)
        const restored = await page.evaluate(() => ({
          surfaceBase: getComputedStyle(document.documentElement).getPropertyValue('--rct-color-surface-base').trim(),
          colorScheme: getComputedStyle(document.documentElement).colorScheme,
        }))
        mediaTransition = { before, after, restored, expectedStable: testCase.mode === 'light', expectedChanged: testCase.mode === 'system' }
      }

      const screenshot = `${screenshots}/${screenshotPrefix}-${testCase.name}.png`
      await page.screenshot({ path: screenshot, fullPage: true })
      results.push({ ...testCase, screenshot, palette, mediaTransition })
    } finally {
      await context.close()
    }
  }
} finally {
  await browser.close()
}

await fs.writeFile(evidencePath, JSON.stringify(results, null, 2))
console.log(JSON.stringify(results, null, 2))
