import { test, expect, http, selectRole } from '../fixtures/cloud-accepted-support'
import { loginPwa } from '../fixtures/pwa-auth'

// CLIENT-ADMIN / CLIENT-FE-ADMIN-02,03,07 / BOUNDARY-02,09:
// 2026-10-08-limited-administrators: read, management and credentials are distinct.
test('LIMITED edits through Vue; removed USERS_MANAGE denies an old bearer and preserves the profile @cloud-accepted', async ({
  page,
  dataset,
}) => {
  const user = await dataset.user('ADMIN', undefined, true)
  const accessPath = `/api/academic/users/${user.id}/admin-access`
  const profile = await dataset.admin.call('GET', accessPath)
  const granted = await dataset.admin.call('PUT', accessPath, {
    mode: 'LIMITED',
    permissions: ['USERS_VIEW', 'USERS_MANAGE'],
    expectedVersion: profile.version,
  })
  await loginPwa(page, user)
  const selected = await selectRole(page, 'Администратор')
  await page.getByRole('button', { name: 'Аккаунты', exact: true }).click()
  const target = await dataset.user('STUDENT', dataset.group.id)
  await page.getByRole('textbox', { name: 'Найти пользователя', exact: true }).fill(target.login)
  const found = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/academic/users' &&
      new URL(response.url()).searchParams.get('search') === target.login,
  )
  await page.getByRole('button', { name: 'Найти', exact: true }).click()
  expect((await found).status()).toBe(200)
  await page.locator(`[data-user-id="${target.id}"]`).click()
  await expect(page.getByRole('heading', { name: 'Данные для входа', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Восстановить доступ', exact: true })).toHaveCount(
    0,
  )
  expect(
    await page
      .locator('body')
      .textContent()
      .then((text) => text?.includes(target.password) ?? false),
  ).toBe(false)
  await page.getByRole('button', { name: 'Редактировать данные', exact: true }).click()
  const changedName = 'Проверенный'
  await page.getByLabel('Имя', { exact: true }).fill(changedName)
  const targetPath = `/api/academic/users/${target.id}`
  const changed = http(page, targetPath, 'PATCH')
  await page.getByRole('button', { name: 'Сохранить данные', exact: true }).click()
  expect((await changed).status()).toBe(200)
  const before = await dataset.admin.call('GET', targetPath)
  expect(before.firstName).toBe(changedName)
  const oldHeaders = { Authorization: `Bearer ${selected.accessToken}` }
  const deniedCredentials = await dataset.request.post(
    `/api/auth/admin/users/${target.id}/password-reset-link`,
    { headers: oldHeaders },
  )
  expect(deniedCredentials.status()).toBe(403)
  const retained = await dataset.admin.call('GET', targetPath)
  expect(retained.initialPassword === before.initialPassword).toBe(true)
  const viewer = await dataset.admin.call('PUT', accessPath, {
    mode: 'LIMITED',
    permissions: ['USERS_VIEW'],
    expectedVersion: granted.version,
  })
  expect(viewer.permissions).toEqual(['USERS_VIEW'])
  expect(
    (
      await dataset.request.patch(targetPath, {
        headers: oldHeaders,
        data: { firstName: 'Запрещённый' },
      })
    ).status(),
  ).toBe(403)
  const after = await dataset.admin.call('GET', targetPath)
  expect(after.firstName).toBe(before.firstName)
  expect(after.lastName).toBe(before.lastName)
  expect(after.middleName).toBe(before.middleName)
  expect(after.groupId).toBe(before.groupId)
  // The account still has ADMIN and USERS_VIEW; current UI must recover as a viewer.
  await page.reload()
  await page.getByRole('button', { name: 'Аккаунты', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Создать пользователя', exact: true })).toHaveCount(
    0,
  )
  await page.getByRole('textbox', { name: 'Найти пользователя', exact: true }).fill(target.login)
  await page.getByRole('button', { name: 'Найти', exact: true }).click()
  await page.locator(`[data-user-id="${target.id}"]`).click()
  await expect(page.getByRole('button', { name: 'Редактировать данные', exact: true })).toHaveCount(
    0,
  )
})
