import { registryTableBoundary } from './registry-html-boundary';
import { createHmac, randomUUID } from 'node:crypto';
import type { Frame, Page, Request, Response } from '@playwright/test';
import {
  test,
  expect,
  admit,
  selectPwaRole,
  type MobileDataset,
  type SyntheticUser,
} from './mobile-dataset';
import type { CurrentSession } from './pwa-auth';
import { sanitizeTestErrors } from './artifact-privacy';
import { retainNativePrimaryFailure } from './safe-reporter';
import { syntheticInitData } from './telegram';

// Exact accepted wire codes from HeadmanAssistantPermission. Keep the E2E
// helper type local: the Vue module uses Bundler resolution, E2E uses NodeNext.
type HelperCapability =
  | 'MARK_ATTENDANCE'
  | 'MANAGE_EXCUSES'
  | 'MANAGE_HOMEWORK'
  | 'CANCEL_LESSONS'
  | 'VIEW_STATS';

// Register in each spec's suite. A module-level hook would attach only to the
// first spec loading this cached module and leave later files unsanitized.
// MobileDataset still enforces the canonical reporter before credentials exist.
export function registerPrivateSuite(): void {
  test.use({ trace: 'off', video: 'off', screenshot: 'off' });
  test.describe.configure({ mode: 'default' });
  test.afterEach(async ({}, info) => sanitizeTestErrors(info));
}

export function http(page: Page, path: string, method: string): Promise<Response> {
  return page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === path && response.request().method() === method,
  );
}

export async function selectRole(page: Page, label: string) {
  const changed = http(page, '/api/auth/session/active-role', 'PUT');
  await selectPwaRole(page, label);
  return (await changed).json() as Promise<{ accessToken: string; session: CurrentSession }>;
}

export async function reloadSession(page: Page): Promise<CurrentSession> {
  const mainFrame = page.mainFrame();
  let reloadStarted = false;
  let committed = false;
  let ownedRequest: Request | undefined;
  const onNavigation = (frame: Frame): void => {
    if (reloadStarted && frame === mainFrame) committed = true;
  };
  const onRequest = (request: Request): void => {
    if (!committed || ownedRequest) return;
    try {
      if (
        request.frame() === mainFrame &&
        request.method() === 'GET' &&
        new URL(request.url()).pathname === '/api/auth/session'
      )
        ownedRequest = request;
    } catch {
      // Worker and early-navigation requests may not expose a frame.
    }
  };
  let action: 'NAVIGATION' | 'WAIT_RESPONSE' | 'HTTP_STATUS' | 'JSON_DECODE' = 'NAVIGATION';
  let httpStatus: number | null = null;
  try {
    page.on('framenavigated', onNavigation);
    page.on('request', onRequest);
    const response = page.waitForResponse(
      (candidate) =>
        candidate.request() === ownedRequest &&
        new URL(candidate.url()).pathname === '/api/auth/session' &&
        candidate.request().method() === 'GET',
    );
    // Navigation failure can finish this helper before its original waiter rejects.
    void response.catch(() => undefined);
    reloadStarted = true;
    await page.reload();
    action = 'WAIT_RESPONSE';
    const current = await response;
    action = 'HTTP_STATUS';
    const status = current.status();
    if (Number.isInteger(status) && status >= 100 && status <= 599) httpStatus = status;
    expect(current.status()).toBe(200);
    action = 'JSON_DECODE';
    return await current.json();
  } catch (error) {
    try {
      retainNativePrimaryFailure(test.info().annotations, 'SESSION_RELOAD', action, error, {
        httpStatus,
      });
    } catch {
      // Diagnostic failures must not replace the original reload failure.
    }
    throw error;
  } finally {
    page.off('framenavigated', onNavigation);
    page.off('request', onRequest);
    ownedRequest = undefined;
  }
}

export async function singleCapability(dataset: MobileDataset, permission: HelperCapability) {
  const user = await dataset.user('STUDENT', dataset.group.id);
  const grant = await dataset.headmanActor.call(
    'POST',
    '/api/academic/assistants',
    { groupId: dataset.group.id, studentId: user.id, permissions: [permission] },
    [201],
  );
  return { user, grant };
}

export async function openAssistant(page: Page, action: string): Promise<void> {
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await page.getByRole('button', { name: 'Действия помощника', exact: true }).click();
  await page.getByRole('button', { name: action, exact: true }).click();
}

export async function oneOff(dataset: MobileDataset, date: string, lessonNumber: number) {
  return dataset.headmanActor.call(
    'POST',
    '/api/schedule/one-off-lessons',
    {
      groupId: dataset.group.id,
      subjectId: dataset.subject.id,
      assignmentId: dataset.subject.assignments[0].id,
      date,
      lessonNumber,
      classroom: 'Синтетическая аудитория',
    },
    [201],
    { 'Idempotency-Key': randomUUID() },
  );
}

/** Negative mark checks compare the durable status/revision before and after. */
export async function deniedMark(dataset: MobileDataset, token: string): Promise<void> {
  const reportPath = `/api/attendance/reports/lesson/${dataset.lesson.physicalLessonId}`;
  let action:
    | 'BEFORE_REPORT'
    | 'BEFORE_ENTRY'
    | 'MARK_REQUEST'
    | 'MARK_STATUS'
    | 'AFTER_REPORT'
    | 'STATUS_EQUAL'
    | 'REVISION_EQUAL' = 'BEFORE_REPORT';
  let httpStatus: number | null = null;
  let markHttpStatus: number | null = null;
  let statusEqual: boolean | null = null;
  let revisionEqual: boolean | null = null;
  let excusedTransition: boolean | null = null;
  const observeHttpStatus = (status: number | null): void => {
    httpStatus =
      typeof status === 'number' && Number.isInteger(status) && status >= 100 && status <= 599
        ? status
        : null;
  };
  try {
    const before = await dataset.headmanActor.call(
      'GET',
      reportPath,
      undefined,
      undefined,
      undefined,
      observeHttpStatus,
    );
    action = 'BEFORE_ENTRY';
    const entry = before.entries.find(
      (row: { userId: number }) => row.userId === dataset.student.id,
    );
    const expectedStatus =
      entry?.status === null
        ? 'EMPTY'
        : typeof entry?.status === 'string'
          ? entry.status.toUpperCase()
          : '';
    const expectedRevision = entry?.markRevision ?? 'EMPTY';
    expect(
      (entry?.status === null && entry.markRevision === null) ||
        (typeof entry?.status === 'string' &&
          ['PRESENT', 'EXCUSED', 'ABSENT'].includes(expectedStatus) &&
          typeof expectedRevision === 'string' &&
          expectedRevision !== 'EMPTY' &&
          /^[A-Za-z0-9_-]{1,128}$/.test(expectedRevision)),
    ).toBe(true);
    excusedTransition = expectedStatus === 'EXCUSED';
    action = 'MARK_REQUEST';
    httpStatus = null;
    const response = await dataset.request.put(
      `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.student.id}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        data: {
          status: 'PRESENT',
          expectedStatus,
          expectedRevision,
        },
      },
    );
    const observed = response.status();
    observeHttpStatus(observed);
    markHttpStatus = httpStatus;
    action = 'MARK_STATUS';
    expect(observed).toBe(403);
    action = 'AFTER_REPORT';
    httpStatus = null;
    const after = await dataset.headmanActor.call(
      'GET',
      reportPath,
      undefined,
      undefined,
      undefined,
      observeHttpStatus,
    );
    const actual = after.entries.find(
      (row: { userId: number }) => row.userId === dataset.student.id,
    );
    action = 'STATUS_EQUAL';
    statusEqual = actual ? actual.status === entry.status : null;
    revisionEqual = actual ? actual.markRevision === entry.markRevision : null;
    expect(actual.status).toBe(entry.status);
    action = 'REVISION_EQUAL';
    expect(actual.markRevision).toBe(entry.markRevision);
  } catch (error) {
    try {
      retainNativePrimaryFailure(test.info().annotations, 'HELPER_MARK_DENIAL', action, error, {
        httpStatus,
        markHttpStatus,
        statusEqual,
        revisionEqual,
        excusedTransition,
      });
    } catch {
      // Preserve the first original helper failure even when diagnostics are unavailable.
    }
    throw error;
  }
}

/** Closed registry table boundary; exported identities stay in memory, results are booleans. */
export async function registryHtmlBoundary(
  page: Page, content: string, full: boolean, expectedRow: readonly string[],
  nonmatching: { login: string; displayName: string },
  sensitiveValues: readonly string[],
) {
  const boundary = await registryTableBoundary(page, content, [
    'ФИО', 'Логин', 'Роли и статусы', 'Группа', 'Табельный номер', 'Привязка Telegram',
    ...(full ? ['Telegram ID'] : []),
  ], [expectedRow], [nonmatching.login, nonmatching.displayName, ...sensitiveValues]);
  return { tableShape: boundary.tableShape, columnsExact: boundary.columnsExact,
    oneRow: boundary.oneRow, valuesExact: boundary.rowsExact,
    knownNonmatchAbsent: boundary.forbiddenAbsent };
}

/** Only the public CI placeholder from fixtures/telegram.ts; never an environment secret. */
export function signedAuthDate(telegramId: number, date: string | null): string {
  const params = new URLSearchParams(syntheticInitData(telegramId));
  params.delete('hash');
  if (date === null) params.delete('auth_date');
  else params.set('auth_date', date);
  const check = [...params.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');
  const key = createHmac('sha256', 'WebAppData')
    .update('ci_test_tma_bot_token_placeholder')
    .digest();
  params.set('hash', createHmac('sha256', key).update(check).digest('hex'));
  return params.toString();
}

export async function unlinkTeacher(dataset: MobileDataset, user: SyntheticUser): Promise<void> {
  const current = await dataset.admin.call('GET', `/api/academic/users/${user.id}`);
  await dataset.admin.call('PUT', `/api/academic/users/${user.id}`, {
    lastName: current.lastName,
    firstName: current.firstName,
    middleName: current.middleName,
    role: 'TEACHER',
    groupId: null,
    employeeNumber: current.employeeNumber,
    telegramId: null,
  });
  const actual = await dataset.admin.call('GET', `/api/academic/users/${user.id}`);
  expect(actual.telegramId === null).toBe(true);
}

export { test, expect, admit };
