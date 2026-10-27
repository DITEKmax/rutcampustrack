import { retainFirstFailure } from './failed-case-diagnostics';
import { expect, test, type Locator, type Page, type Response } from '@playwright/test';
import {
  currentDiagnosticCase,
  diagnosticUiFailure,
  type LogoutControlObservation,
} from './diagnostic-events';
import { safeFailureAnnotation } from './safe-reporter';
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
  await page
    .getByLabel('Пароль', { exact: true })
    .and(page.locator('input[name="password"]'))
    .fill(user.password);
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
    let stage: 'PWA_LOGIN' | 'PWA_SESSION' | 'PWA_READY' = 'PWA_LOGIN';
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
      if (login.status() !== 200) {
        const failure = new Error(`Synthetic actor login: HTTP ${login.status()}`);
        if (login.status() === 429) await retainLoginRateLimit(login);
        throw failure;
      }
      expect(login.status()).toBe(200);
      const { accessToken } = (await login.json()) as { accessToken: string };
      expect(typeof accessToken).toBe('string');
      stage = 'PWA_SESSION';
      const current =
        observedSession ?? (await page.waitForResponse(sessionMatch, responseOptions()));
      if (current.status() !== 200)
        throw new Error(`Synthetic actor session: HTTP ${current.status()}`);
      expect(current.status()).toBe(200);
      stage = 'PWA_READY';
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
    } catch (primary) {
      try {
        retainFirstFailure(test.info(), stage, { loginStatus: observedLogin?.status() ?? null, sessionStatus: observedSession?.status() ?? null });
      } catch { /* Optional observation preserves the primary login/session failure. */ }
      throw primary;
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
  const profileLogout = profile.getByRole('button', { name: 'Выйти', exact: true });
  // Teacher PWA exposes logout in the app shell outside the profile main.
  const useProfileLogout = (await profileLogout.count()) > 0;
  const shellLogout = page.locator('button.pwa-logout');
  const logoutButton = useProfileLogout
    ? profileLogout
    : shellLogout.and(page.getByRole('button', { name: 'Выйти', exact: true }));
  // Allow 10s device cleanup + 10s logout HTTP + 5s response delivery margin.
  const logoutResponseTimeoutMs = 25_000;
  const [response] = await Promise.all([
    page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/logout' &&
        response.request().method() === 'POST',
      { timeout: logoutResponseTimeoutMs },
    ),
    logoutButton.click().catch(async (error: unknown) => {
      if (
        error instanceof Error &&
        currentDiagnosticCase() === 'D002_RECONNECT_ACCOUNT_AUTHORITY'
      ) {
        // The role intersection excludes hidden shell controls; observe the raw candidate.
        diagnosticUiFailure(
          error,
          await observeLogoutControl(useProfileLogout ? logoutButton : shellLogout),
        );
      }
      throw error;
    }),
  ]);
  expect(response.status()).toBe(204);
  await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
}

/** Current DOM facts after a failed real click, not a claim about the click's cause. */
async function observeLogoutControl(locator: Locator): Promise<LogoutControlObservation> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    // One immediate locator query; no actionability wait, scroll or retained handles.
    return await Promise.race([
      locator.evaluateAll((elements): LogoutControlObservation => {
        if (elements.length === 0) return 'LOGOUT_CONTROL_MISSING';
        if (elements.length !== 1) return 'LOGOUT_CONTROL_MULTIPLE';
        const element = elements[0];
        if (!element.isConnected) return 'LOGOUT_CLICK_UNKNOWN';
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        if (
          rect.width <= 0 || rect.height <= 0 ||
          style.visibility === 'hidden' || style.visibility === 'collapse'
        ) return 'LOGOUT_CONTROL_HIDDEN';
        if (element.matches(':disabled') || element.closest('[aria-disabled="true"]'))
          return 'LOGOUT_CONTROL_DISABLED';
        const left = Math.max(0, rect.left), top = Math.max(0, rect.top);
        const right = Math.min(innerWidth, rect.right), bottom = Math.min(innerHeight, rect.bottom);
        if (right <= left || bottom <= top) return 'LOGOUT_CONTROL_OUTSIDE_VIEWPORT';
        const hit = document.elementFromPoint((left + right) / 2, (top + bottom) / 2);
        if (hit && !element.contains(hit)) return 'LOGOUT_CONTROL_POINTER_INTERCEPTED';
        return 'LOGOUT_CLICK_UNKNOWN';
      }),
      new Promise<LogoutControlObservation>((resolve) => {
        timer = setTimeout(() => resolve('LOGOUT_CLICK_UNKNOWN'), 750);
      }),
    ]);
  } catch {
    return 'LOGOUT_CLICK_UNKNOWN';
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}

/** Inspect only an already returned normal login rejection; this cannot retry or replace it. */
async function retainLoginRateLimit(response: Response): Promise<void> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    const body = await Promise.race([
      response.body(),
      new Promise<undefined>((resolve) => {
        timer = setTimeout(() => resolve(undefined), 250);
      }),
    ]);
    if (!Buffer.isBuffer(body) || body.length > 4096) return;
    const problem: unknown = JSON.parse(body.toString('utf8'));
    if (!problem || typeof problem !== 'object' || Array.isArray(problem)) return;
    if (Object.getOwnPropertyDescriptor(problem, 'status')?.value !== 429) return;
    const type: unknown = Object.getOwnPropertyDescriptor(problem, 'type')?.value;
    const marker =
      type === 'https://ruttrack.site/problems/rate-limit-exceeded'
        ? 'ACTOR_LOGIN_GATEWAY_LOCAL:429'
        : type === 'https://api.rutcampustrack.ru/problems/rate-limit-exceeded'
          ? 'ACTOR_LOGIN_AUTH_SERVICE:429'
          : undefined;
    if (marker === undefined) return;
    const annotations = test.info().annotations;
    if (
      annotations.length >= 32 ||
      annotations.some(
        (annotation) =>
          annotation.type === safeFailureAnnotation &&
          (annotation.description === 'ACTOR_LOGIN_GATEWAY_LOCAL:429' ||
            annotation.description === 'ACTOR_LOGIN_AUTH_SERVICE:429'),
      )
    )
      return;
    annotations.push({ type: safeFailureAnnotation, description: marker });
  } catch {
    // Malformed, unavailable, or late response observations preserve the original failure.
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}
