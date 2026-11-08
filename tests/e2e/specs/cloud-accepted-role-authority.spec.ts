import type { Request, Route, TestInfo } from '@playwright/test';
import { test, expect, registerPrivateSuite } from '../fixtures/cloud-accepted-support';
import { loginPwa } from '../fixtures/pwa-auth';


registerPrivateSuite();

// Charge page/scenario preparation to the same positive-login retry budget.
const loginStartedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async ({}, info) => {
  loginStartedAt.set(info, performance.now());
});

// CLIENT-FE-AUTH-04: a failed logout cannot reopen a locally closed owner,
// even when that server session and a genuine old data response remain live.
test('unconfirmed UI logout clears its owner and rejects a late real feed through reload @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  const retryCutoffMs =
    (loginStartedAt.get(info) ?? 0) + Math.max(0, Math.min(info.timeout, 120000) - 30000);
  const user = await dataset.user('STUDENT', dataset.group.id);
  const retryRemainingMs = Math.floor(retryCutoffMs - performance.now());
  expect(retryRemainingMs, 'Positive login retry budget remains').toBeGreaterThan(0);
  const { accessToken, session } = await loginPwa(page, user, {
    retryUntilMs: Date.now() + Math.min(65000, retryRemainingMs),
  });
  expect(session.activeRole).toBe('STUDENT');
  const ownerId = String(user.id);
  await expect
    .poll(() =>
      page.evaluate(
        (id) => localStorage.getItem('rct-student-mobile.current-owner') === id,
        ownerId,
      ),
    )
    .toBe(true);

  // Read one already-created owner record; never create or repair a store.
  const ownerState = () =>
    page.evaluate(async (id) => {
      const pointerCleared = localStorage.getItem('rct-student-mobile.current-owner') === null;
      const ownSnapshotPresent = await new Promise<boolean>((resolve, reject) => {
        const opened = indexedDB.open('rct-student-mobile', 1);
        opened.onupgradeneeded = () => opened.transaction?.abort();
        opened.onblocked = () => reject(new Error('Owner snapshot read blocked'));
        opened.onerror = () => reject(new Error('Owner snapshot read failed'));
        opened.onsuccess = () => {
          const database = opened.result;
          if (!database.objectStoreNames.contains('semester-snapshots')) {
            database.close();
            reject(new Error('Owner snapshot store missing'));
            return;
          }
          const transaction = database.transaction('semester-snapshots', 'readonly');
          const record = transaction.objectStore('semester-snapshots').get(id);
          transaction.oncomplete = () => {
            database.close();
            resolve(record.result !== undefined);
          };
          transaction.onerror = transaction.onabort = () => {
            database.close();
            reject(new Error('Owner snapshot transaction failed'));
          };
        };
      });
      return { pointerCleared, ownSnapshotPresent };
    }, ownerId);
  expect((await ownerState()).ownSnapshotPresent).toBe(true);

  await page.getByRole('button', { name: 'Задания', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'День', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  );
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let held = false;
  let ownResponseReady = false;
  let routeFailed = false;
  let logoutCleared = false;
  let releasedAfterLogout = false;
  let abortedLogouts = 0;
  let finishHeld: () => void = () => {};
  const heldDone = new Promise<void>((resolve) => {
    finishHeld = resolve;
  });
  let releaseTimer: ReturnType<typeof setTimeout> | undefined;
  const homeworkPath = '**/api/v1/student/homework?*';
  const logoutPath = '**/api/auth/logout';
  const holdOwnFeed = async (route: Route) => {
    const url = new URL(route.request().url());
    if (
      route.request().method() !== 'GET' ||
      url.searchParams.get('from') !== dataset.tomorrow ||
      url.searchParams.get('to') !== dataset.tomorrow ||
      held
    ) {
      await route.continue();
      return;
    }
    held = true;
    try {
      const response = await route.fetch({ timeout: 10000 });
      try {
        const feed = await response.json();
        ownResponseReady =
          response.status() === 200 &&
          feed.items.some(
            (item: { id: string }) => String(item.id) === String(dataset.homework.id),
          );
        releaseTimer = setTimeout(release, 15000);
        await gate;
        releasedAfterLogout = logoutCleared;
        await route.fulfill({ response });
      } finally {
        await response.dispose();
      }
    } catch {
      routeFailed = true;
      await route.abort('failed').catch(() => {});
    } finally {
      finishHeld();
    }
  };
  const abortLogout = async (route: Route) => {
    if (route.request().method() !== 'POST') {
      await route.continue();
      return;
    }
    abortedLogouts += 1;
    // Do not forward this POST: server revocation must not supply the result.
    await route.abort('failed');
  };
  let automaticAdmissions = 0;
  const countAdmission = (request: Request) => {
    const path = new URL(request.url()).pathname;
    if (
      (path === '/api/auth/refresh' && request.method() === 'POST') ||
      (path === '/api/auth/session' && request.method() === 'GET')
    )
      automaticAdmissions += 1;
  };
  const expectClosedOwner = async () => {
    await expect(
      page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    await expect(page.locator('.today-list, .homework-card')).toHaveCount(0);
    await expect(page.getByText(dataset.marker, { exact: true })).toHaveCount(0);
    await expect
      .poll(async () => {
        const stored = await ownerState();
        return stored.pointerCleared && !stored.ownSnapshotPresent;
      })
      .toBe(true);
    expect(
      await page.evaluate(
        () => localStorage.getItem('rct-pwa-explicit-logout-v1') === 'unconfirmed',
      ),
    ).toBe(true);
  };
  await page.route(homeworkPath, holdOwnFeed);
  try {
    await page.getByRole('button', { name: 'Следующий период', exact: true }).click();
    await expect.poll(() => ownResponseReady, { timeout: 10000 }).toBe(true);
    await page.route(logoutPath, abortLogout);
    await page.getByRole('button', { name: 'Профиль', exact: true }).click();
    const profile = page.getByRole('main', { name: 'Профиль', exact: true });
    await expect(profile.getByRole('heading', { name: 'Профиль', exact: true })).toBeVisible();
    // logoutPwa deliberately expects success, so this failure uses the actual UI directly.
    await profile.getByRole('button', { name: 'Выйти', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText(
      'Локальный доступ закрыт; автоматический вход отключён.',
    );
    await expectClosedOwner();
    expect(abortedLogouts).toBe(1);
    const stillLive = await dataset.request.get('/api/auth/session', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(stillLive.status()).toBe(200);
    const live = await stillLive.json();
    expect(
      String(live.userId) === ownerId &&
        live.sessionId === session.sessionId &&
        live.activeRole === 'STUDENT',
    ).toBe(true);
    logoutCleared = true;
    const delivered = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/v1/student/homework' &&
        url.searchParams.get('from') === dataset.tomorrow &&
        url.searchParams.get('to') === dataset.tomorrow &&
        response.request().method() === 'GET'
      );
    });
    release();
    const late = await delivered;
    expect(late.status()).toBe(200);
    expect(await late.finished()).toBeNull();
    await heldDone;
    expect(routeFailed).toBe(false);
    expect(releasedAfterLogout).toBe(true);
    await page.evaluate(
      () =>
        new Promise<void>((resolve) =>
          requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
        ),
    );
    await expectClosedOwner();
    await page.unroute(logoutPath, abortLogout);
    page.on('request', countAdmission);
    await page.reload();
    await expectClosedOwner();
    expect(automaticAdmissions).toBe(0);
  } finally {
    release();
    if (releaseTimer) clearTimeout(releaseTimer);
    page.off('request', countAdmission);
    try {
      await page.unroute(logoutPath, abortLogout);
    } catch {
      expect.soft(false, 'AUTH logout cleanup LOGOUT_ROUTE_REMOVE_FAILED').toBe(true);
    }
    try {
      await page.unroute(homeworkPath, holdOwnFeed);
    } catch {
      expect.soft(false, 'AUTH logout cleanup HOMEWORK_ROUTE_REMOVE_FAILED').toBe(true);
    }
    try {
      if (held) await heldDone;
    } catch {
      expect.soft(false, 'AUTH logout cleanup HELD_HANDLER_FAILED').toBe(true);
    }
  }
});
