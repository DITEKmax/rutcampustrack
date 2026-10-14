import { expect, type Page, type Response } from '@playwright/test';
import type { TestUser } from './users';

export interface CurrentSession {
  userId: string;
  sessionId: string;
  sessionVersion: string;
  activeRole: 'STUDENT' | 'HEADMAN' | 'TEACHER' | 'ADMIN' | null;
  roles: { role: string; selectable: boolean }[];
}

interface CurrentSessionResponse extends Omit<CurrentSession, 'activeRole'> {
  activeRole?: CurrentSession['activeRole'];
}

/** UI login against the real Vue client and auth service; no API stubs. */
export async function loginPwa(
  page: Page,
  user: TestUser,
  options?: { retryUntilMs: number },
): Promise<{ accessToken: string; session: CurrentSession }> {
  await page.goto('/app/');
  await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
  await page.getByLabel('Логин', { exact: true }).fill(user.login);
  await page.getByLabel('Пароль', { exact: true }).fill(user.password);
  const loginUrl = page.url();
  const loginMatch = (response: Response) =>
    new URL(response.url()).pathname === '/api/auth/login' &&
    response.request().method() === 'POST';
  const sessionMatch = (response: Response) =>
    new URL(response.url()).pathname === '/api/auth/session' &&
    response.request().method() === 'GET';
  const retryUntil = options?.retryUntilMs;
  if (
    options &&
    (!Number.isSafeInteger(retryUntil) ||
      retryUntil! <= Date.now() ||
      retryUntil! > Date.now() + 120000)
  )
    throw new Error('Synthetic actor login retry budget invalid');
  const responseOptions = () =>
    options ? { timeout: Math.max(1, Math.min(10000, retryUntil! - Date.now())) } : {};

  async function submit(
    canRetry: boolean,
  ): Promise<{ accessToken: string; session: CurrentSession }> {
    let observedLogin: Response | undefined;
    let observedSession: Response | undefined;
    const observe = (response: Response) => {
      if (loginMatch(response)) observedLogin ??= response;
      if (sessionMatch(response)) observedSession ??= response;
    };
    // Observation has no outstanding promise or timer until a response is
    // awaited. A rejected login therefore leaves no eager session waiter.
    page.on('response', observe);
    try {
      await page.getByRole('button', { name: 'Войти', exact: true }).click(responseOptions());
      const login = observedLogin ?? (await page.waitForResponse(loginMatch, responseOptions()));
      if (canRetry && login.status() === 429) {
        const header = login.headers()['retry-after'] ?? '';
        const seconds = /^[0-9]+$/.test(header) ? Number(header) : 0;
        if (
          Number.isSafeInteger(seconds) &&
          seconds > 0 &&
          seconds <= 60 &&
          seconds * 1000 + 1000 < retryUntil! - Date.now() &&
          page.url() === loginUrl
        ) {
          page.off('response', observe);
          // This is the actual advertised delay, using real browser timers;
          // the educational Date fixture and all server limits stay unchanged.
          await page.waitForTimeout(seconds * 1000);
          if (Date.now() >= retryUntil! || page.url() !== loginUrl)
            throw new Error(`Synthetic actor login: HTTP ${login.status()}`);
          return await submit(false);
        }
      }
      if (login.status() !== 200) throw new Error(`Synthetic actor login: HTTP ${login.status()}`);
      expect(login.status()).toBe(200);
      const { accessToken } = (await login.json()) as { accessToken: string };
      expect(typeof accessToken).toBe('string');
      const current =
        observedSession ?? (await page.waitForResponse(sessionMatch, responseOptions()));
      if (current.status() !== 200)
        throw new Error(`Synthetic actor session: HTTP ${current.status()}`);
      expect(current.status()).toBe(200);
      const body = (await current.json()) as CurrentSessionResponse;
      const session: CurrentSession = { ...body, activeRole: body.activeRole ?? null };
      if (session.activeRole === null) {
        await expect(page.getByRole('heading', { name: 'Сменить роль' })).toBeVisible();
      } else {
        expect(['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN']).toContain(session.activeRole);
        await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
      }
      if (options && Date.now() >= retryUntil!)
        throw new Error('Synthetic actor login retry budget exceeded');
      return { accessToken, session };
    } finally {
      page.off('response', observe);
    }
  }
  return submit(options !== undefined);
}

export async function logoutPwa(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Профиль', exact: true }).click();
  const profile = page.getByRole('main', { name: 'Профиль', exact: true });
  await expect(profile.getByRole('heading', { name: 'Профиль', exact: true })).toBeVisible();
  const [response] = await Promise.all([
    page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/logout' &&
        response.request().method() === 'POST',
    ),
    profile.getByRole('button', { name: 'Выйти', exact: true }).click(),
  ]);
  expect(response.status()).toBe(204);
  await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
}
