import { randomUUID } from 'node:crypto';
import type { Request, Route, TestInfo } from '@playwright/test';
import {
  test,
  expect,
  selectRole,
  reloadSession,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support';
import { loginPwa } from '../fixtures/pwa-auth';
import {
  classifySafeFailure,
  discardReportedFailureHint,
  safeFailureAnnotation,
  safeFailureDiagnostics,
} from '../fixtures/safe-reporter';

registerPrivateSuite();

// Charge page/scenario preparation to the same positive-login retry budget.
const loginStartedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async ({}, info) => {
  loginStartedAt.set(info, performance.now());
});

// CLIENT-AUTH-ROLES / CLIENT-FE-AUTH-02 / BOUNDARY-01:
// JS-SYSTEM-02, JS-SYSTEM-14; canonical job-stories:325,874.
test('four durable grants default to STUDENT; explicit role survives reload and stale version cannot switch @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  let phase:
    | 'SETUP'
    | 'LOGIN'
    | 'HEADMAN_STALE'
    | 'GROUP_READBACK'
    | 'GROUP_VISIBLE_BACK'
    | 'ADMIN_SELECT_HELPER'
    | 'ADMIN_SELECTED_ROLE'
    | 'ADMIN_RELOAD_HELPER'
    | 'ADMIN_RELOADED_ROLE'
    | 'ADMIN_UI'
    | 'STUDENT_RETURN' = 'SETUP';
  try {
    const retryCutoffMs =
      (loginStartedAt.get(info) ?? 0) + Math.max(0, Math.min(info.timeout, 120000) - 30000);
    const user = await dataset.user('STUDENT', dataset.otherGroup.id);
    await dataset.admin.call('PUT', `/api/academic/groups/${dataset.otherGroup.id}/headman`, {
      studentId: user.id,
      expectedHeadmanId: null,
    });
    await dataset.admin.call('PUT', `/api/academic/users/${user.id}/roles/TEACHER`, {
      status: 'ACTIVE',
      employeeNumber: `E2E-${randomUUID().slice(0, 20)}`,
    });
    const profile = await dataset.admin.call('GET', `/api/academic/users/${user.id}/admin-access`);
    await dataset.admin.call('PUT', `/api/academic/users/${user.id}/admin-access`, {
      mode: 'FULL',
      permissions: [],
      expectedVersion: profile.version,
    });
    phase = 'LOGIN';
    const retryRemainingMs = Math.floor(retryCutoffMs - performance.now());
    expect(retryRemainingMs, 'Positive login retry budget remains').toBeGreaterThan(0);
    const { accessToken, session } = await loginPwa(page, user, {
      retryUntilMs: Date.now() + Math.min(65000, retryRemainingMs),
    });
    expect(session.activeRole).toBe('STUDENT');
    expect(
      session.roles
        .filter((grant) => grant.selectable)
        .map((grant) => grant.role)
        .sort(),
    ).toEqual(['ADMIN', 'HEADMAN', 'STUDENT', 'TEACHER']);
    phase = 'HEADMAN_STALE';
    const helper = await dataset.user('STUDENT', dataset.otherGroup.id);
    const command = {
      groupId: dataset.otherGroup.id,
      studentId: helper.id,
      permissions: ['VIEW_STATS'],
    };
    const denied = await dataset.request.post('/api/academic/assistants', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: command,
    });
    expect(denied.status()).toBe(403);
    const headman = await selectRole(page, 'Староста');
    expect(headman.session.activeRole).toBe('HEADMAN');
    const assistants = await dataset.request.get(
      `/api/academic/assistants?groupId=${dataset.otherGroup.id}&size=100`,
      {
        headers: { Authorization: `Bearer ${headman.accessToken}` },
      },
    );
    expect(assistants.status()).toBe(200);
    const list = await assistants.json();
    const grants = list._embedded?.assistantResponseList ?? list.content ?? [];
    expect(grants.some((grant: { studentId: number }) => grant.studentId === helper.id)).toBe(
      false,
    );
    const assigned = await dataset.request.post('/api/academic/assistants', {
      headers: { Authorization: `Bearer ${headman.accessToken}` },
      data: command,
    });
    expect(assigned.status()).toBe(201);
    const assignedGrant = await assigned.json();
    const stale = await dataset.request.put('/api/auth/session/active-role', {
      headers: { Authorization: `Bearer ${headman.accessToken}` },
      data: { role: 'ADMIN', expectedSessionVersion: session.sessionVersion },
    });
    expect(stale.status()).toBe(409);
    expect((await stale.json()).extras.code).toBe('SESSION_VERSION_CONFLICT');
    expect((await reloadSession(page)).activeRole).toBe('HEADMAN');
    phase = 'GROUP_READBACK';
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    const ownAssistants = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/academic/assistants' &&
        url.searchParams.get('groupId') === String(dataset.otherGroup.id) &&
        response.request().method() === 'GET'
      );
    });
    await page.getByRole('button', { name: 'Группа', exact: true }).click();
    const ownResponse = await ownAssistants;
    expect(ownResponse.status()).toBe(200);
    const ownCatalog = await ownResponse.json();
    const ownGrants = ownCatalog._embedded?.assistantResponseList ?? ownCatalog.content ?? [];
    const retained = ownGrants.find((grant: { id: number }) => grant.id === assignedGrant.id);
    expect(retained).toMatchObject({
      id: assignedGrant.id,
      studentId: helper.id,
      groupId: dataset.otherGroup.id,
      active: true,
    });
    expect(retained.permissions).toEqual(['VIEW_STATS']);
    phase = 'GROUP_VISIBLE_BACK';
    const group = page.locator('.headman-group');
    const member = group.locator(`[data-member-id="${helper.id}"]`);
    await expect(member).toContainText(helper.displayName);
    await expect(member).toContainText('Помощник старосты');
    await group.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
    phase = 'ADMIN_SELECT_HELPER';
    const admin = await selectRole(page, 'Администратор');
    phase = 'ADMIN_SELECTED_ROLE';
    expect(admin.session.activeRole).toBe('ADMIN');
    phase = 'ADMIN_RELOAD_HELPER';
    const reloadedAdmin = await reloadSession(page);
    phase = 'ADMIN_RELOADED_ROLE';
    expect(reloadedAdmin.activeRole).toBe('ADMIN');
    phase = 'ADMIN_UI';
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await expect(
      page.getByRole('button', { name: 'Создать пользователя', exact: true }),
    ).toBeVisible();

    // WEB01: return to shared own STUDENT features through the same authenticated session.
    phase = 'STUDENT_RETURN';
    const [studentContextReply, studentReturn] = await Promise.all([
      page.waitForResponse(
        (response) =>
          response.request().method() === 'GET' &&
          new URL(response.url()).pathname === '/api/v1/student/session',
      ),
      selectRole(page, 'Студент'),
    ]);
    expect(studentReturn.session).toMatchObject({
      userId: String(user.id),
      sessionId: session.sessionId,
      activeRole: 'STUDENT',
    });
    expect(studentContextReply.status()).toBe(200);
    const studentContext = await studentContextReply.json();
    expect(
      String(studentContext.user.id) === String(user.id) &&
        String(studentContext.group.id) === String(dataset.otherGroup.id),
      'Student context belongs to the same user and current own group',
    ).toBe(true);
    await expect(page.locator('.today-shell')).toBeVisible();
    const studentNavigation = page.getByRole('navigation', { name: 'Основная навигация' });
    await expect(
      studentNavigation.getByRole('button', { name: 'Посещаемость', exact: true }),
    ).toBeEnabled();
    await expect(
      studentNavigation.getByRole('button', { name: 'Задания', exact: true }),
    ).toBeEnabled();
  } catch (error) {
    // Preserve the original closed cause before the fixed phase wrapper is sanitized.
    const diagnostic = classifySafeFailure(error);
    const previous = safeFailureDiagnostics(info.annotations);
    const coreCount = previous.filter((item) => !item.cause.startsWith('REPORTED_')).length;
    if (
      coreCount < 8 &&
      !previous.some(
        (item) => item.cause === diagnostic.cause && item.httpStatus === diagnostic.httpStatus,
      ) &&
      (previous.length < 8 || discardReportedFailureHint(info.annotations)) &&
      info.annotations.length < 32
    ) {
      info.annotations.push({
        type: safeFailureAnnotation,
        description: `${diagnostic.cause}:${diagnostic.httpStatus}`,
      });
    }
    switch (phase) {
      case 'SETUP':
        throw new Error('Four-grant setup failed', { cause: error });
      case 'LOGIN':
        throw new Error('Four-grant login failed', { cause: error });
      case 'HEADMAN_STALE':
        throw new Error('Four-grant headman and stale-role check failed', { cause: error });
      case 'GROUP_READBACK':
        throw new Error('Four-grant group readback failed', { cause: error });
      case 'GROUP_VISIBLE_BACK':
        throw new Error('Four-grant visible group and back navigation failed', { cause: error });
      case 'ADMIN_SELECT_HELPER':
        throw new Error('Four-grant administrator role selection failed', { cause: error });
      case 'ADMIN_SELECTED_ROLE':
        throw new Error('Four-grant administrator selected-role assertion failed', {
          cause: error,
        });
      case 'ADMIN_RELOAD_HELPER':
        throw new Error('Four-grant administrator reload failed', { cause: error });
      case 'ADMIN_RELOADED_ROLE':
        throw new Error('Four-grant administrator reloaded-role assertion failed', {
          cause: error,
        });
      case 'ADMIN_UI':
        throw new Error('Four-grant administrator UI failed', { cause: error });
      case 'STUDENT_RETURN':
        throw new Error('Four-grant own student return failed', { cause: error });
    }
  }
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
