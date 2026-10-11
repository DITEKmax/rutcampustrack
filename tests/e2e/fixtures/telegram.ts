import { createHmac, randomUUID } from 'node:crypto'
import type { Page } from '@playwright/test'

// Matches only tests/e2e/.env.ci and infra/seed-telegram.sql.
const CI_TMA_BOT_TOKEN = 'ci_test_tma_bot_token_placeholder'

export function teacherInitData(tampered = false): string {
  const params = new URLSearchParams({
    auth_date: String(Math.floor(Date.now() / 1000)),
    query_id: randomUUID(),
    user: JSON.stringify({ id: 900000001, first_name: 'E2E teacher' }),
  })
  const check = [...params.entries()].sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`).join('\n')
  const key = createHmac('sha256', 'WebAppData').update(CI_TMA_BOT_TOKEN).digest()
  const hash = createHmac('sha256', key).update(check).digest('hex')
  params.set('hash', tampered ? '0'.repeat(64) : hash)
  return params.toString()
}

/** Synthetic Telegram UI host; auth/session/domain HTTP still reach real services. */
export async function installTelegramHost(page: Page, initData: string): Promise<void> {
  await page.route('https://telegram.org/js/telegram-web-app.js', route =>
    route.fulfill({ body: '', contentType: 'application/javascript' }))
  await page.addInitScript(initData => {
    const state = { backVisible: false, ready: false, callbacks: new Set<() => void>() }
    Object.assign(window, { __tmaEntryState: state, Telegram: { WebApp: {
      initData, viewportHeight: 844, viewportStableHeight: 844,
      safeAreaInset: { top: 0, right: 0, bottom: 0, left: 0 },
      ready() { state.ready = true }, expand() {}, onEvent() {}, offEvent() {},
      BackButton: {
        show() { state.backVisible = true }, hide() { state.backVisible = false },
        onClick(callback: () => void) { state.callbacks.add(callback) },
        offClick(callback: () => void) { state.callbacks.delete(callback) },
      },
    } } })
  }, initData)
}
