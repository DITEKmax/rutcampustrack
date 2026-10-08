import { expect, test, type Page } from '@playwright/test'

/** New Vue entry with a synthetic host/auth boundary; no Telegram provider claim. */
async function teacherHost(page: Page, denied = false): Promise<void> {
  await page.route('https://telegram.org/js/telegram-web-app.js', route => route.fulfill({ body: '', contentType: 'application/javascript' }))
  await page.addInitScript(() => {
    const state = { backVisible: false, ready: false, callbacks: new Set<() => void>() }
    Object.assign(window, { __tmaEntryState: state, Telegram: { WebApp: {
      initData: 'synthetic-signed-init-data', viewportHeight: 844, viewportStableHeight: 844,
      safeAreaInset: { top: 0, right: 0, bottom: 0, left: 0 },
      ready() { state.ready = true }, expand() {}, onEvent() {}, offEvent() {},
      BackButton: { show() { state.backVisible = true }, hide() { state.backVisible = false }, onClick(callback: () => void) { state.callbacks.add(callback) }, offClick(callback: () => void) { state.callbacks.delete(callback) } },
    } } })
  })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/tma') {
      expect(route.request().postDataJSON()).toEqual({ initData: 'synthetic-signed-init-data' })
      await route.fulfill({ status: denied ? 403 : 200, json: denied ? { code: 'TMA_AUTH_REJECTED' } : { accessToken: 'synthetic-teacher-token' } }); return
    }
    if (path === '/api/auth/session') { await route.fulfill({ json: {
      sessionId: 'synthetic-session', userId: '900', displayName: 'Тестовый преподаватель', sessionVersion: '1', rolesVersion: '1', activeRole: 'TEACHER', readOnly: false,
      roles: [{ grantId: 'teacher-grant', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false }],
      passwordPolicy: { minCodePoints: 10, maxUtf8Bytes: 128, requiresDecimalDigit: true, specialCategories: ['P', 'S'], normalization: 'NONE' },
    } }); return }
    if (path === '/api/v1/teacher/semester') { await route.fulfill({ json: { id: '7', name: 'Тестовый семестр', dateFrom: '2026-09-01', dateTo: '2027-01-31' } }); return }
    if (path === '/api/v1/teacher/day') { await route.fulfill({ json: { semesterId: '7', date: new URL(route.request().url()).searchParams.get('date'), lessons: [], serverNow: '2026-10-08T08:00:00Z' } }); return }
    if (path === '/api/v1/teacher/assignments') { await route.fulfill({ json: [] }); return }
    await route.fulfill({ status: 404, json: { code: 'SYNTHETIC_NOT_FOUND' } })
  })
}

test.describe('Vue Telegram entry @smoke', () => {
  test('denied Telegram authentication keeps role data and dock unavailable', async ({ page }) => {
    await teacherHost(page, true)
    await page.goto('/mini-app/')
    await expect(page.getByRole('heading', { name: 'Не удалось открыть приложение' })).toBeVisible()
    await expect(page.locator('.teacher-feature-owner')).toHaveCount(0)
    await expect(page.locator('.mobile-bottom-nav')).toHaveCount(0)
  })
  test('teacher entry reuses shared profile with host Back and memory-only credentials', async ({ page }) => {
    await teacherHost(page)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/mini-app/')
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible()
    await expect(page.locator('.teacher-feature-owner')).toBeVisible()
    await page.getByRole('button', { name: 'Профиль', exact: true }).click()
    await page.getByRole('button', { name: 'Безопасность', exact: true }).click()
    await expect(page.locator('.mobile-bottom-nav')).toHaveCount(0)
    await expect(page.locator('.profile-back')).toBeHidden()
    await expect.poll(() => page.evaluate(() => (window as unknown as { __tmaEntryState: { backVisible: boolean } }).__tmaEntryState.backVisible)).toBe(true)
    await page.evaluate(() => { for (const callback of (window as unknown as { __tmaEntryState: { callbacks: Set<() => void> } }).__tmaEntryState.callbacks) callback() })
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible()
    expect(await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]))).not.toMatch(/synthetic-teacher-token|synthetic-signed-init-data/)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await page.setViewportSize({ width: 430, height: 932 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  })
})
