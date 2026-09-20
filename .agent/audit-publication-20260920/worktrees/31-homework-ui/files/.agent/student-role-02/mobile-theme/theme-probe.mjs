import fs from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const { chromium } = await import(pathToFileURL('C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/e2e/node_modules/playwright/index.mjs').href)

const root = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-ui'
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
  const foregroundLuminance = luminance(foreground)
  const backgroundLuminance = luminance(background)
  return Number(((Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
    / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05)).toFixed(2))
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
      await page.goto(`http://127.0.0.1:5181/?fixtureScene=runtime&fixtureRootFont=${testCase.rootFont}`, { waitUntil: 'networkidle' })
      await page.evaluate(async (mode) => {
        await document.fonts.ready
        document.documentElement.setAttribute('data-theme', mode)
      }, testCase.mode)
      await page.locator('.homework-completion').first().waitFor()
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
          const foregroundLuminance = luminance(foreground)
          const backgroundLuminance = luminance(background)
          return Number(((Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
            / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05)).toFixed(2))
        }
        const rootStyle = getComputedStyle(document.documentElement)
        const read = (name) => rootStyle.getPropertyValue(name).trim()
        const parseHex = (value) => {
          const match = value.match(/^#([\da-f]{6})$/i)
          if (!match) return null
          return [1, 3, 5].map((index) => Number.parseInt(match[1].slice(index - 1, index + 1), 16))
        }
        const completion = document.querySelector('.homework-completion')
        const completionHandle = document.querySelector('.homework-completion__handle')
        const completedHandle = document.querySelector('.homework-completion__handle--completed')
        const completionCheck = document.querySelector('.homework-completion__check')
        const completionDirection = document.querySelector('.homework-completion__direction')
        const activeNav = document.querySelector('.mobile-bottom-nav__item--active')
        const activeNavIcon = activeNav?.querySelector('.mobile-bottom-nav__icon')
        const firstHeading = document.querySelector('.homework-group h2')
        const firstCard = document.querySelector('.homework-card')
        const completionRect = completion?.getBoundingClientRect()
        const headingRect = firstHeading?.getBoundingClientRect()
        const cardRect = firstCard?.getBoundingClientRect()
        const viewport = document.documentElement
        return {
          rootTheme: document.documentElement.getAttribute('data-theme'),
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
            successText: read('--rct-color-success-text'),
            successSurface: read('--rct-color-success-surface'),
            dockOpacity: read('--rct-mobile-dock-opacity'),
            dockBlur: read('--rct-mobile-dock-blur'),
            dateHeadingSize: read('--rct-homework-date-heading-size'),
          },
          contrast: {
            primaryOnBase: contrast(parseHex(read('--rct-color-text-primary')), parseHex(read('--rct-color-surface-base'))),
            secondaryOnRaised: contrast(parseHex(read('--rct-color-text-secondary')), parseHex(read('--rct-color-surface-raised'))),
            accentOnAccent: contrast(parseHex(read('--rct-color-accent-on-now')), parseHex(read('--rct-color-accent-now'))),
            successTextOnSurface: contrast(parseHex(read('--rct-color-success-text')), parseHex(read('--rct-color-success-surface'))),
          },
          icons: {
            completionBackground: completion ? getComputedStyle(completion).backgroundColor : null,
            completionHandleCount: document.querySelectorAll('.homework-completion__handle').length,
            completionHandleColor: completionHandle ? getComputedStyle(completionHandle).backgroundColor : null,
            completionHandleStyleMask: completionHandle ? getComputedStyle(completionHandle).maskImage : null,
            completionHandleWebkitMask: completionHandle ? getComputedStyle(completionHandle).webkitMaskImage : null,
            completedHandleColor: completedHandle ? getComputedStyle(completedHandle).backgroundColor : null,
            completedHandleMask: completedHandle ? getComputedStyle(completedHandle).maskImage : null,
            completionCheckColor: completionCheck ? getComputedStyle(completionCheck).backgroundColor : null,
            completionCheckMask: completionCheck ? getComputedStyle(completionCheck).maskImage : null,
            completionDirectionColor: completionDirection ? getComputedStyle(completionDirection).backgroundColor : null,
            completionDirectionMask: completionDirection ? getComputedStyle(completionDirection).maskImage : null,
            activeNavIconColor: activeNavIcon ? getComputedStyle(activeNavIcon).backgroundColor : null,
            activeNavIconMask: activeNavIcon ? getComputedStyle(activeNavIcon).maskImage : null,
            dockBackdropFilter: activeNav ? getComputedStyle(activeNav.closest('.mobile-bottom-nav')).backdropFilter : null,
          },
          geometry: {
            viewportWidth: viewport.clientWidth,
            scrollWidth: viewport.scrollWidth,
            noHorizontalOverflow: viewport.scrollWidth <= viewport.clientWidth,
            completionWidth: completionRect ? Number(completionRect.width.toFixed(2)) : null,
            headingTop: headingRect ? Number(headingRect.top.toFixed(2)) : null,
            headingHeight: headingRect ? Number(headingRect.height.toFixed(2)) : null,
            firstCardTop: cardRect ? Number(cardRect.top.toFixed(2)) : null,
            firstCardWidth: cardRect ? Number(cardRect.width.toFixed(2)) : null,
          },
        }
      })

      let mediaTransition = null
      if (testCase.mode === 'system' || testCase.mode === 'light') {
        const before = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--rct-color-surface-base').trim())
        await page.emulateMedia({ colorScheme: testCase.mode === 'system' && testCase.colorScheme === 'dark' ? 'light' : 'dark' })
        await page.waitForTimeout(30)
        const after = await page.evaluate(() => ({
          surfaceBase: getComputedStyle(document.documentElement).getPropertyValue('--rct-color-surface-base').trim(),
          colorScheme: getComputedStyle(document.documentElement).colorScheme,
        }))
        mediaTransition = { before, after, expectedStable: testCase.mode === 'light' }
      }

      const screenshot = `${screenshots}/${testCase.name}.png`
      await page.screenshot({ path: screenshot, fullPage: true })
      results.push({ ...testCase, screenshot, palette, mediaTransition })
    } finally {
      await context.close()
    }
  }
} finally {
  await browser.close()
}

await fs.writeFile(`${root}/.agent/student-role-02/mobile-theme/runtime-evidence.json`, JSON.stringify(results, null, 2))
console.log(JSON.stringify(results, null, 2))
