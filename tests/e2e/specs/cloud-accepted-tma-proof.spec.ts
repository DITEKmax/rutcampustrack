import {
  test,
  expect,
  http,
  signedAuthDate,
  unlinkTeacher,
} from '../fixtures/cloud-accepted-support'
import { installTelegramHost, syntheticInitData } from '../fixtures/telegram'

// CLIENT-TMA-PROVIDER / CLIENT-FE-TMA-02 / BOUNDARY-06; D003.
// Correct signatures isolate malformed/age validation from existing tampered/reuse cases.
const invalidDates = [
  { label: 'missing date', value: () => null },
  { label: 'non-numeric date', value: () => 'invalid' },
  { label: 'overflow date', value: () => '9223372036854775808' },
  { label: 'future date', value: () => String(Math.floor(Date.now() / 1000) + 86400) },
  { label: 'expired date', value: () => String(Math.floor(Date.now() / 1000) - 172800) },
] as const

for (const sample of invalidDates) {
  test(`signed TMA ${sample.label} issues no session or role owner @cloud-accepted`, async ({
    page,
    context,
    dataset,
  }) => {
    await installTelegramHost(page, signedAuthDate(dataset.teacher.telegramId, sample.value()))
    const admission = http(page, '/api/auth/tma', 'POST')
    await page.goto('/mini-app/')
    expect((await admission).status()).toBe(401)
    await expect(page.getByRole('heading', { name: 'Не удалось открыть приложение' })).toBeVisible()
    await expect(page.locator('.teacher-feature-owner')).toHaveCount(0)
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0)
    expect((await context.cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(false)
    expect((await page.request.post('/api/auth/refresh')).status()).toBe(401)
  })
}

test('a still signed reusable TMA proof cannot admit the old Telegram binding @cloud-accepted', async ({
  page,
  context,
  dataset,
}) => {
  const user = await dataset.user('TEACHER')
  const proof = syntheticInitData(user.telegramId)
  await installTelegramHost(page, proof)
  const initial = http(page, '/api/auth/tma', 'POST')
  await page.goto('/mini-app/')
  expect((await initial).status()).toBe(200)
  await expect(page.locator('.teacher-feature-owner')).toBeVisible()
  await unlinkTeacher(dataset, user)
  // Remove only this browser's old refresh cookie: a failed proof must not issue a new one.
  await context.clearCookies()
  const repeated = http(page, '/api/auth/tma', 'POST')
  await page.reload()
  expect((await repeated).status()).toBe(401)
  await expect(page.getByRole('heading', { name: 'Не удалось открыть приложение' })).toBeVisible()
  await expect(page.locator('.teacher-feature-owner')).toHaveCount(0)
  expect((await context.cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(false)
})
