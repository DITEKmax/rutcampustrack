import {
  test,
  expect,
  http,
  signedAuthDate,
  unlinkTeacher,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support';
import { installTelegramHost, syntheticInitData } from '../fixtures/telegram';

registerPrivateSuite();

// CLIENT-TMA-PROVIDER / CLIENT-FE-TMA-02 / BOUNDARY-06; D003.
// Correct signatures isolate malformed/age validation from existing tampered/reuse cases.
const invalidDates = [
  { label: 'missing date', value: () => null, detail: 'Invalid auth_date in initData' },
  { label: 'non-numeric date', value: () => 'invalid', detail: 'Invalid auth_date in initData' },
  {
    label: 'overflow date',
    value: () => '9223372036854775808',
    detail: 'Invalid auth_date in initData',
  },
  {
    label: 'future date',
    value: () => String(Math.floor(Date.now() / 1000) + 86400),
    detail: 'initData expired or not yet valid',
  },
  {
    label: 'expired date',
    value: () => String(Math.floor(Date.now() / 1000) - 172800),
    detail: 'initData expired or not yet valid',
  },
] as const;

for (const sample of invalidDates) {
  test(`signed TMA ${sample.label} issues no session or role owner @cloud-accepted`, async ({
    page,
    context,
    dataset,
  }) => {
    // Same origin, signer and live binding must first admit a fresh valid proof.
    // A separate page avoids installing two competing Telegram init scripts.
    const control = await context.newPage();
    await installTelegramHost(control, syntheticInitData(dataset.teacher.telegramId));
    const accepted = http(control, '/api/auth/tma', 'POST');
    await control.goto('/mini-app/');
    expect((await accepted).status()).toBe(200);
    await expect(control.locator('.teacher-feature-owner')).toBeVisible();
    expect((await context.cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(true);
    await control.close();
    await context.clearCookies();
    await installTelegramHost(page, signedAuthDate(dataset.teacher.telegramId, sample.value()));
    const admission = http(page, '/api/auth/tma', 'POST');
    await page.goto('/mini-app/');
    const rejected = await admission;
    expect(rejected.status()).toBe(401);
    const problem = await rejected.json();
    expect(
      typeof problem.type === 'string' && problem.type.endsWith('/tma-validation-failed'),
    ).toBe(true);
    expect(problem.detail === sample.detail).toBe(true);
    await expect(
      page.getByRole('heading', { name: 'Не удалось открыть приложение' }),
    ).toBeVisible();
    await expect(page.locator('.teacher-feature-owner')).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    expect((await context.cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(false);
    expect((await page.request.post('/api/auth/refresh')).status()).toBe(401);
  });
}

test('a still signed reusable TMA proof cannot admit the old Telegram binding @cloud-accepted', async ({
  page,
  context,
  dataset,
}) => {
  const user = await dataset.user('TEACHER');
  const proof = syntheticInitData(user.telegramId);
  await installTelegramHost(page, proof);
  const initial = http(page, '/api/auth/tma', 'POST');
  await page.goto('/mini-app/');
  expect((await initial).status()).toBe(200);
  await expect(page.locator('.teacher-feature-owner')).toBeVisible();
  await unlinkTeacher(dataset, user);
  // Remove only this browser's old refresh cookie: a failed proof must not issue a new one.
  await context.clearCookies();
  const repeated = http(page, '/api/auth/tma', 'POST');
  await page.reload();
  expect((await repeated).status()).toBe(401);
  await expect(page.getByRole('heading', { name: 'Не удалось открыть приложение' })).toBeVisible();
  await expect(page.locator('.teacher-feature-owner')).toHaveCount(0);
  expect((await context.cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(false);
});
