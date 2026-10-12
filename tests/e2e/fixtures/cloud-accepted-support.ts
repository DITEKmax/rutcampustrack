import { createHmac, randomUUID } from 'node:crypto'
import type { Page, Response } from '@playwright/test'
import {
  test,
  expect,
  admit,
  selectPwaRole,
  type MobileDataset,
  type SyntheticUser,
} from './mobile-dataset'
import type { CurrentSession } from './pwa-auth'
import { sanitizeTestErrors } from './artifact-privacy'
import { syntheticInitData } from './telegram'

// Exact accepted wire codes from HeadmanAssistantPermission. Keep the E2E
// helper type local: the Vue module uses Bundler resolution, E2E uses NodeNext.
type HelperCapability =
  | 'MARK_ATTENDANCE'
  | 'MANAGE_EXCUSES'
  | 'MANAGE_HOMEWORK'
  | 'CANCEL_LESSONS'
  | 'VIEW_STATS'

// MobileDataset enforces the synthetic loopback stand and canonical safe reporter
// before it generates credentials. Keep the same policy for every new case.
test.use({ trace: 'off', video: 'off', screenshot: 'off' })
test.describe.configure({ mode: 'default' })
test.afterEach(async ({}, info) => sanitizeTestErrors(info))

export function http(page: Page, path: string, method: string): Promise<Response> {
  return page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === path && response.request().method() === method,
  )
}

export async function selectRole(page: Page, label: string) {
  const changed = http(page, '/api/auth/session/active-role', 'PUT')
  await selectPwaRole(page, label)
  return (await changed).json() as Promise<{ accessToken: string; session: CurrentSession }>
}

export async function reloadSession(page: Page): Promise<CurrentSession> {
  const response = http(page, '/api/auth/session', 'GET')
  await page.reload()
  const current = await response
  expect(current.status()).toBe(200)
  return current.json()
}

export async function singleCapability(dataset: MobileDataset, permission: HelperCapability) {
  const user = await dataset.user('STUDENT', dataset.group.id)
  const grant = await dataset.headmanActor.call(
    'POST',
    '/api/academic/assistants',
    { groupId: dataset.group.id, studentId: user.id, permissions: [permission] },
    [201],
  )
  return { user, grant }
}

export async function openAssistant(page: Page, action: string): Promise<void> {
  await page.getByRole('button', { name: 'Ещё', exact: true }).click()
  await page.getByRole('button', { name: 'Действия помощника', exact: true }).click()
  await page.getByRole('button', { name: action, exact: true }).click()
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
  )
}

/** Negative mark checks compare the durable status/revision before and after. */
export async function deniedMark(dataset: MobileDataset, token: string): Promise<void> {
  const reportPath = `/api/attendance/reports/lesson/${dataset.lesson.physicalLessonId}`
  const before = await dataset.headmanActor.call('GET', reportPath)
  const entry = before.entries.find((row: { userId: number }) => row.userId === dataset.student.id)
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
  )
  expect(response.status()).toBe(403)
  const after = await dataset.headmanActor.call('GET', reportPath)
  const actual = after.entries.find((row: { userId: number }) => row.userId === dataset.student.id)
  expect(actual.status).toBe(entry.status)
  expect(actual.markRevision).toBe(entry.markRevision)
}

/** Only the public CI placeholder from fixtures/telegram.ts; never an environment secret. */
export function signedAuthDate(telegramId: number, date: string | null): string {
  const params = new URLSearchParams(syntheticInitData(telegramId))
  params.delete('hash')
  if (date === null) params.delete('auth_date')
  else params.set('auth_date', date)
  const check = [...params.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n')
  const key = createHmac('sha256', 'WebAppData')
    .update('ci_test_tma_bot_token_placeholder')
    .digest()
  params.set('hash', createHmac('sha256', key).update(check).digest('hex'))
  return params.toString()
}

export async function unlinkTeacher(dataset: MobileDataset, user: SyntheticUser): Promise<void> {
  const current = await dataset.admin.call('GET', `/api/academic/users/${user.id}`)
  await dataset.admin.call('PUT', `/api/academic/users/${user.id}`, {
    lastName: current.lastName,
    firstName: current.firstName,
    middleName: current.middleName,
    role: 'TEACHER',
    groupId: null,
    employeeNumber: current.employeeNumber,
    telegramId: null,
  })
  const actual = await dataset.admin.call('GET', `/api/academic/users/${user.id}`)
  expect(actual.telegramId === null).toBe(true)
}

export { test, expect, admit }
