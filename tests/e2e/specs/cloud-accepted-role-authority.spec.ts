import { randomUUID } from 'node:crypto'
import {
  test,
  expect,
  selectRole,
  reloadSession,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support'
import { loginPwa } from '../fixtures/pwa-auth'

registerPrivateSuite()

// CLIENT-AUTH-ROLES / CLIENT-FE-AUTH-02 / BOUNDARY-01:
// JS-SYSTEM-02, JS-SYSTEM-14; canonical job-stories:325,874.
test('four durable grants default to STUDENT; explicit role survives reload and stale version cannot switch @cloud-accepted', async ({
  page,
  dataset,
}) => {
  const user = await dataset.user('STUDENT', dataset.otherGroup.id)
  await dataset.admin.call('PUT', `/api/academic/groups/${dataset.otherGroup.id}/headman`, {
    studentId: user.id,
    expectedHeadmanId: null,
  })
  await dataset.admin.call('PUT', `/api/academic/users/${user.id}/roles/TEACHER`, {
    status: 'ACTIVE',
    employeeNumber: `E2E-${randomUUID().slice(0, 20)}`,
  })
  const profile = await dataset.admin.call('GET', `/api/academic/users/${user.id}/admin-access`)
  await dataset.admin.call('PUT', `/api/academic/users/${user.id}/admin-access`, {
    mode: 'FULL',
    permissions: [],
    expectedVersion: profile.version,
  })
  const { accessToken, session } = await loginPwa(page, user)
  expect(session.activeRole).toBe('STUDENT')
  expect(
    session.roles
      .filter((grant) => grant.selectable)
      .map((grant) => grant.role)
      .sort(),
  ).toEqual(['ADMIN', 'HEADMAN', 'STUDENT', 'TEACHER'])
  const helper = await dataset.user('STUDENT', dataset.otherGroup.id)
  const command = {
    groupId: dataset.otherGroup.id,
    studentId: helper.id,
    permissions: ['VIEW_STATS'],
  }
  const denied = await dataset.request.post('/api/academic/assistants', {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: command,
  })
  expect(denied.status()).toBe(403)
  const headman = await selectRole(page, 'Староста')
  expect(headman.session.activeRole).toBe('HEADMAN')
  const assistants = await dataset.request.get(
    `/api/academic/assistants?groupId=${dataset.otherGroup.id}&size=100`,
    {
      headers: { Authorization: `Bearer ${headman.accessToken}` },
    },
  )
  expect(assistants.status()).toBe(200)
  const list = await assistants.json()
  const grants = list._embedded?.assistantResponseList ?? list.content ?? []
  expect(grants.some((grant: { studentId: number }) => grant.studentId === helper.id)).toBe(false)
  const assigned = await dataset.request.post('/api/academic/assistants', {
    headers: { Authorization: `Bearer ${headman.accessToken}` },
    data: command,
  })
  expect(assigned.status()).toBe(201)
  const stale = await dataset.request.put('/api/auth/session/active-role', {
    headers: { Authorization: `Bearer ${headman.accessToken}` },
    data: { role: 'ADMIN', expectedSessionVersion: session.sessionVersion },
  })
  expect(stale.status()).toBe(409)
  expect((await stale.json()).extras.code).toBe('SESSION_VERSION_CONFLICT')
  expect((await reloadSession(page)).activeRole).toBe('HEADMAN')
  const admin = await selectRole(page, 'Администратор')
  expect(admin.session.activeRole).toBe('ADMIN')
  expect((await reloadSession(page)).activeRole).toBe('ADMIN')
  await page.getByRole('button', { name: 'Аккаунты', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Создать пользователя', exact: true }),
  ).toBeVisible()
})
