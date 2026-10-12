import { createHmac, randomUUID } from 'node:crypto';
import type { Page, Response } from '@playwright/test';
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
  const response = http(page, '/api/auth/session', 'GET');
  await page.reload();
  const current = await response;
  expect(current.status()).toBe(200);
  return current.json();
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
  const before = await dataset.headmanActor.call('GET', reportPath);
  const entry = before.entries.find((row: { userId: number }) => row.userId === dataset.student.id);
  const response = await dataset.request.put(
    `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.student.id}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        status: 'PRESENT',
        expectedStatus: String(entry.status).toUpperCase(),
        expectedRevision: entry.markRevision ?? 'EMPTY',
      },
    },
  );
  expect(response.status()).toBe(403);
  const after = await dataset.headmanActor.call('GET', reportPath);
  const actual = after.entries.find((row: { userId: number }) => row.userId === dataset.student.id);
  expect(actual.status).toBe(entry.status);
  expect(actual.markRevision).toBe(entry.markRevision);
}

/** Closed registry table boundary; exported identities stay in memory, results are booleans. */
export function registryHtmlBoundary(
  content: string,
  full: boolean,
  expectedRow: readonly string[],
  nonmatching: { login: string; displayName: string },
) {
  // RegistryTableRenderer.html emits one text-only table with escaped cell values.
  // Read that table itself: matching names elsewhere in the document proves no row/filter rule.
  const decode = (value: string) =>
    value.replace(/&(amp|lt|gt|quot|apos);/g, (entity) => {
      const entities: Record<string, string> = {
        '&amp;': '&',
        '&lt;': '<',
        '&gt;': '>',
        '&quot;': '"',
        '&apos;': "'",
      };
      return entities[entity];
    });
  const cells = (row: string, tag: 'th' | 'td'): string[] | null => {
    const pattern = new RegExp(`<${tag}>([^<]*)</${tag}>`, 'g');
    const values = [...row.matchAll(pattern)].map((match) => decode(match[1]));
    return row.replace(pattern, '').trim() === '' ? values : null;
  };
  const tables = [...content.matchAll(/<table>([\s\S]*?)<\/table>/g)];
  const tableOpenings = content.match(/<table\b/gi)?.length ?? 0;
  const sections = tables[0]?.[1].match(
    /^<thead><tr>([\s\S]*?)<\/tr><\/thead><tbody>([\s\S]*?)<\/tbody>$/,
  );
  const headers = sections ? cells(sections[1], 'th') : null;
  const body = sections?.[2] ?? '';
  const rowPattern = /<tr>([\s\S]*?)<\/tr>/g;
  const rows = [...body.matchAll(rowPattern)].map((match) => cells(match[1], 'td'));
  const columns = [
    'ФИО',
    'Логин',
    'Роли и статусы',
    'Группа',
    'Табельный номер',
    'Привязка Telegram',
    ...(full ? ['Telegram ID'] : []),
  ];
  return {
    tableShape:
      tableOpenings === 1 &&
      tables.length === 1 &&
      !!sections &&
      body.replace(rowPattern, '').trim() === '' &&
      rows.every((row) => row !== null),
    columnsExact:
      headers?.length === columns.length &&
      headers.every((value, index) => value === columns[index]),
    oneRow: rows.length === 1,
    valuesExact:
      expectedRow.length === columns.length &&
      rows[0]?.length === columns.length &&
      rows[0].every((value, index) => value === expectedRow[index]),
    knownNonmatchAbsent:
      !decode(content).includes(nonmatching.login) &&
      !decode(content).includes(nonmatching.displayName),
  };
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
