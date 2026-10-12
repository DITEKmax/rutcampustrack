import {
  test,
  expect,
  admit,
  http,
  selectRole,
  registerPrivateSuite,
  registryHtmlBoundary,
} from '../fixtures/cloud-accepted-support'
import { loginPwa } from '../fixtures/pwa-auth'

registerPrivateSuite()

// CLIENT-MAP-FILES-EXPORTS + CLIENT-ADMIN / D006,D007 / JS-ADMIN-13 / BOUNDARY-46.
// This finite HTML carrier verifies the actual choose → durable ready → ticket →
// browser download sequence. Other four formats/native Telegram remain separate.
for (const mode of ['FULL', 'LIMITED'] as const) {
  test(`${mode} ordinary user export downloads filtered HTML with exact safe fields @cloud-accepted`, async ({
    page,
    dataset,
  }) => {
    const user = await dataset.user('ADMIN', undefined, mode === 'LIMITED')
    const target = await dataset.user('STUDENT', dataset.group.id)
    const nonmatching = await dataset.user('STUDENT', dataset.otherGroup.id)
    expect(nonmatching.login !== target.login).toBe(true)
    const staffNumber = `E2E-${target.id}`
    await dataset.admin.call('PUT', `/api/academic/users/${target.id}/roles/TEACHER`, {
      status: 'ACTIVE',
      employeeNumber: staffNumber,
    })
    const profile = await dataset.admin.call('GET', `/api/academic/users/${target.id}`)
    expect(profile.fullName === target.displayName).toBe(true)
    expect(profile.employeeNumber === staffNumber).toBe(true)
    expect(
      profile.roles
        .map((grant: { role: string; status: string }) => `${grant.role}: ${grant.status}`)
        .join('; ') === 'STUDENT: ACTIVE; TEACHER: ACTIVE',
    ).toBe(true)
    expect(typeof dataset.group.name === 'string' && dataset.group.name.length > 0).toBe(true)
    await loginPwa(page, user)
    const selected = await selectRole(page, 'Администратор')
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click()
    await page.getByRole('textbox', { name: 'Найти пользователя', exact: true }).fill(target.login)
    await page.getByRole('button', { name: 'Найти', exact: true }).click()
    await expect(page.locator(`[data-user-id="${target.id}"]`)).toBeVisible()
    await expect(page.locator('.admin-users-card')).toHaveCount(1)
    const catalogue = http(page, '/api/academic/registry-exports/users/formats', 'GET')
    await page.getByRole('button', { name: 'Выгрузка', exact: true }).click()
    const formats = await catalogue
    expect(formats.status()).toBe(200)
    expect(
      (await formats.json()).formats.map((value: { code: string }) => value.code).sort(),
    ).toEqual(['docx', 'html', 'pdf', 'png', 'xlsx'])
    await page.getByLabel('Формат выгрузки', { exact: true }).selectOption('html')
    const created = http(page, '/api/auth/export-jobs', 'POST')
    const capability = page.waitForResponse(
      (response) =>
        /^\/api\/auth\/export-jobs\/[0-9a-f-]+\/ticket$/.test(new URL(response.url()).pathname) &&
        response.request().method() === 'POST',
    )
    const bytes = page.waitForResponse((response) =>
      /^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(new URL(response.url()).pathname),
    )
    const saved = page.waitForEvent('download')
    await page.getByRole('button', { name: 'Скачать', exact: true }).click()
    const job = await created
    expect(job.status()).toBe(202)
    const jobId: string = (await job.json()).jobId
    const issued = await capability
    expect(issued.status()).toBe(200)
    const ticket: { downloadPath: string; suggestedFilename: string } = await issued.json()
    const file = await bytes
    expect(file.status()).toBe(200)
    expect(file.headers()['content-type'].split(';')[0]).toBe('text/html')
    const content = (await file.body()).toString('utf8')
    expect(content.startsWith('<!doctype html>')).toBe(true)
    const boundary = registryHtmlBoundary(
      content,
      mode === 'FULL',
      [
        target.displayName,
        target.login,
        'STUDENT: ACTIVE; TEACHER: ACTIVE',
        dataset.group.name,
        staffNumber,
        'Привязан',
        ...(mode === 'FULL' ? [String(target.telegramId)] : []),
      ],
      nonmatching,
    )
    expect(boundary.tableShape).toBe(true)
    expect(boundary.columnsExact).toBe(true)
    expect(boundary.oneRow).toBe(true)
    expect(boundary.valuesExact).toBe(true)
    expect(boundary.knownNonmatchAbsent).toBe(true)
    expect(content.includes('Telegram ID')).toBe(mode === 'FULL')
    expect(content.includes(String(target.telegramId))).toBe(mode === 'FULL')
    expect(content.includes(target.password)).toBe(false)
    expect(/парол|password|password-reset/i.test(content)).toBe(false)
    const download = await saved
    expect(download.suggestedFilename() === ticket.suggestedFilename).toBe(true)
    expect(download.suggestedFilename().endsWith('.html')).toBe(true)
    expect(await download.failure()).toBeNull()
    await expect(
      page.getByRole('status').filter({ hasText: 'Файл готов. Скачивание передано браузеру.' }),
    ).toBeVisible()
    const headers = { Authorization: `Bearer ${selected.accessToken}` }
    expect(
      (await dataset.request.get(`/api/auth/export-jobs/${jobId}`, { headers })).status(),
    ).toBe(200)
    const foreignUser = await dataset.user('ADMIN')
    const foreign = await admit(dataset.request, foreignUser, 'ADMIN')
    expect(
      (
        await dataset.request.get(`/api/auth/export-jobs/${jobId}`, { headers: foreign.headers() })
      ).status(),
    ).toBe(403)
    expect(
      (
        await dataset.request.get(`/api/auth/export-jobs/${jobId}/file`, {
          headers: foreign.headers(),
        })
      ).status(),
    ).toBe(403)
    if (mode === 'LIMITED') {
      const path = `/api/academic/users/${user.id}/admin-access`
      const profile = await dataset.admin.call('GET', path)
      await dataset.admin.call('PUT', path, {
        mode: 'LIMITED',
        permissions: ['GROUPS_VIEW'],
        expectedVersion: profile.version,
      })
      for (const operation of [
        () => dataset.request.get(`/api/auth/export-jobs/${jobId}`, { headers }),
        () => dataset.request.post(`/api/auth/export-jobs/${jobId}/ticket`, { headers }),
        () => dataset.request.get(`/api/auth/export-jobs/${jobId}/file`, { headers }),
        () => dataset.request.get(ticket.downloadPath),
      ])
        expect((await operation()).status()).toBe(403)
      const unchanged = await dataset.admin.call('GET', `/api/academic/users/${target.id}`)
      expect(unchanged.initialPassword === target.password).toBe(true)
    }
    // Download bodies/tickets/identities are never attached or persisted by this test.
    await download.delete()
  })
}
