import { randomBytes } from 'node:crypto';
import type { Request, Response, TestInfo } from '@playwright/test';
import {
  test,
  expect,
  http,
  selectRole,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support';
import { loginPwa } from '../fixtures/pwa-auth';

registerPrivateSuite();

// Bound the new positive login without changing the original case or quota.
// Earlier runner/auto-fixture overhead is unmeasured; the case timeout applies.
const retryStartedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async ({}, info) => {
  retryStartedAt.set(info, performance.now());
});

// CLIENT-ADMIN / CLIENT-FE-ADMIN-02,03,07 / BOUNDARY-02,09:
// 2026-10-08-limited-administrators: read, management and credentials are distinct.
test('LIMITED edits through Vue; removed USERS_MANAGE denies an old bearer and preserves the profile @cloud-accepted', async ({
  page,
  dataset,
}) => {
  const user = await dataset.user('ADMIN', undefined, true);
  const accessPath = `/api/academic/users/${user.id}/admin-access`;
  const profile = await dataset.admin.call('GET', accessPath);
  const granted = await dataset.admin.call('PUT', accessPath, {
    mode: 'LIMITED',
    permissions: ['USERS_VIEW', 'USERS_MANAGE'],
    expectedVersion: profile.version,
  });
  await loginPwa(page, user);
  const selected = await selectRole(page, 'Администратор');
  await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
  const target = await dataset.user('STUDENT', dataset.group.id);
  await page.getByRole('textbox', { name: 'Найти пользователя', exact: true }).fill(target.login);
  const found = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/academic/users' &&
      new URL(response.url()).searchParams.get('search') === target.login,
  );
  await page.getByRole('button', { name: 'Найти', exact: true }).click();
  expect((await found).status()).toBe(200);
  await page.locator(`[data-user-id="${target.id}"]`).click();
  await expect(page.getByRole('heading', { name: 'Данные для входа', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Восстановить доступ', exact: true })).toHaveCount(
    0,
  );
  expect(
    await page
      .locator('body')
      .textContent()
      .then((text) => text?.includes(target.password) ?? false),
  ).toBe(false);
  await page.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
  const changedName = 'Проверенный';
  await page.getByLabel('Имя', { exact: true }).fill(changedName);
  const targetPath = `/api/academic/users/${target.id}`;
  const changed = http(page, targetPath, 'PATCH');
  await page.getByRole('button', { name: 'Сохранить данные', exact: true }).click();
  expect((await changed).status()).toBe(200);
  const before = await dataset.admin.call('GET', targetPath);
  expect(before.firstName).toBe(changedName);
  const oldHeaders = { Authorization: `Bearer ${selected.accessToken}` };
  const deniedCredentials = await dataset.request.post(
    `/api/auth/admin/users/${target.id}/password-reset-link`,
    { headers: oldHeaders },
  );
  expect(deniedCredentials.status()).toBe(403);
  const retained = await dataset.admin.call('GET', targetPath);
  expect(retained.initialPassword === before.initialPassword).toBe(true);
  const viewer = await dataset.admin.call('PUT', accessPath, {
    mode: 'LIMITED',
    permissions: ['USERS_VIEW'],
    expectedVersion: granted.version,
  });
  expect(viewer.permissions).toEqual(['USERS_VIEW']);
  expect(
    (
      await dataset.request.patch(targetPath, {
        headers: oldHeaders,
        data: { firstName: 'Запрещённый' },
      })
    ).status(),
  ).toBe(403);
  const after = await dataset.admin.call('GET', targetPath);
  expect(after.firstName).toBe(before.firstName);
  expect(after.lastName).toBe(before.lastName);
  expect(after.middleName).toBe(before.middleName);
  expect(after.groupId).toBe(before.groupId);
  // The account still has ADMIN and USERS_VIEW; current UI must recover as a viewer.
  await page.reload();
  await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Создать пользователя', exact: true })).toHaveCount(
    0,
  );
  await page.getByRole('textbox', { name: 'Найти пользователя', exact: true }).fill(target.login);
  await page.getByRole('button', { name: 'Найти', exact: true }).click();
  await page.locator(`[data-user-id="${target.id}"]`).click();
  await expect(page.getByRole('button', { name: 'Редактировать данные', exact: true })).toHaveCount(
    0,
  );
});

// CLIENT-FE-ADMIN-07 / JS-ADMIN-26: existing FULL authority edits another
// account through the real rights editor while the selected role stays ADMIN.
test('FULL multirole ADMIN assigns updates and revokes LIMITED rights through Vue without foreign-role calls @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  const retryCutoffMs =
    (retryStartedAt.get(info) ?? Number.NEGATIVE_INFINITY) +
    Math.max(0, Math.min(info.timeout, 120000) - 30000);
  // Use the existing dataset's registry-code pattern for a case-owned group.
  // Its headman authority is retained for disposable-stack cleanup, never reset.
  const actorGroup = await dataset.admin.call(
    'POST',
    '/api/academic/groups/registry',
    {
      alphabeticCode: [...randomBytes(4)]
        .map((byte) => 'АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЭЮЯ'[byte % 27])
        .join(''),
      numericCode: `1${10 + (randomBytes(1)[0] % 90)}`,
      trainingDurationYears: 4,
    },
    [201],
  );
  const actor = await dataset.user('STUDENT', actorGroup.id);
  await dataset.admin.call('PUT', `/api/academic/groups/${actorGroup.id}/headman`, {
    studentId: actor.id,
    expectedHeadmanId: null,
  });
  await dataset.admin.call('PUT', `/api/academic/users/${actor.id}/roles/TEACHER`, {
    status: 'ACTIVE',
    employeeNumber: `E2E-${actor.id}`,
  });
  const actorPath = `/api/academic/users/${actor.id}/admin-access`;
  const actorBefore = await dataset.admin.call('GET', actorPath);
  await dataset.admin.call('PUT', actorPath, {
    mode: 'FULL',
    permissions: [],
    expectedVersion: actorBefore.version,
  });
  // The fixture's separate FULL administrator is retained throughout this flow.
  const target = await dataset.user('STUDENT', dataset.group.id);
  const targetPath = `/api/academic/users/${target.id}`;
  const accessPath = `${targetPath}/admin-access`;
  const targetBefore = await dataset.admin.call('GET', targetPath);
  const before = await dataset.admin.call('GET', accessPath);
  expect(before.assigned).toBe(false);
  const retryRemainingMs = Math.floor(retryCutoffMs - performance.now());
  expect(retryRemainingMs, 'Positive ADMIN login retry budget remains').toBeGreaterThan(0);
  const { session } = await loginPwa(page, actor, {
    retryUntilMs: Date.now() + Math.min(65000, retryRemainingMs),
  });
  expect(
    session.roles
      .filter((role) => role.selectable)
      .map((role) => role.role)
      .sort(),
  ).toEqual(['ADMIN', 'HEADMAN', 'STUDENT', 'TEACHER']);
  let adminAdmitted = false;
  let foreignFeatureRequested = false;
  const origin = new URL(page.url()).origin;
  const observeAdmission = (response: Response) => {
    if (
      new URL(response.url()).origin === origin &&
      new URL(response.url()).pathname === '/api/auth/session/active-role' &&
      response.request().method() === 'PUT' &&
      response.status() === 200
    )
      adminAdmitted = true;
  };
  const observeFeatureStart = (request: Request) => {
    if (!adminAdmitted) return;
    const url = new URL(request.url());
    if (url.origin !== origin) return;
    // Finite current role-owned carriers. Shared authentication/profile,
    // notifications and administrative dashboard/map requests are permitted.
    if (
      /^\/api\/v1\/(?:student|teacher)(?:\/|$)/.test(url.pathname) ||
      /^\/api\/academic\/(?:subjects|assistants|homeworks|assignments)(?:\/|$)/.test(
        url.pathname,
      ) ||
      url.pathname === '/api/academic/groups/my/members' ||
      /^\/api\/schedule\/(?:lessons|lesson-slots|items|lesson-transfers|one-off-lessons|recurring-lessons)(?:\/|$)/.test(
        url.pathname,
      ) ||
      /^\/api\/schedule\/groups\/[1-9][0-9]*\/lessons$/.test(url.pathname) ||
      /^\/api\/attendance\/(?:lessons|requests)(?:\/|$)/.test(url.pathname) ||
      /^\/api\/attendance\/reports\/(?:lesson|headman|headman-weekly|journal)(?:\/|$)/.test(
        url.pathname,
      )
    )
      foreignFeatureRequested = true;
  };
  page.on('response', observeAdmission);
  page.on('request', observeFeatureStart);
  try {
    const statsLoaded = http(page, '/api/academic/dashboard/stats', 'GET');
    const selected = await selectRole(page, 'Администратор');
    expect(selected.session.activeRole).toBe('ADMIN');
    expect(adminAdmitted).toBe(true);
    // JS-ADMIN-11: bind the real server summary to the three visible scale cards.
    const statsResponse = await statsLoaded;
    expect(statsResponse.status()).toBe(200);
    const summary = (await statsResponse.json()) as {
      totalStudents: number;
      totalTeachers: number;
      totalGroups: number;
      activeGroups: number;
      dailyGrowth: {
        status: 'AVAILABLE' | 'UNKNOWN_HISTORY';
        baselineDate: string;
        studentsDelta: number | null;
        teachersDelta: number | null;
        groupsDelta: number | null;
      };
    };
    for (const value of [summary.totalStudents, summary.totalTeachers, summary.activeGroups]) {
      expect(Number.isSafeInteger(value)).toBe(true);
      expect(value).toBeGreaterThanOrEqual(0);
    }
    expect(summary.totalGroups).toBe(summary.activeGroups);
    const growth = summary.dailyGrowth;
    expect(growth).toBeTruthy();
    expect(['AVAILABLE', 'UNKNOWN_HISTORY']).toContain(growth.status);
    expect(growth.baselineDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(new Date(`${growth.baselineDate}T00:00:00Z`).toISOString().slice(0, 10)).toBe(
      growth.baselineDate,
    );
    const scaleCards = page.locator('dl[aria-label="Активные аккаунты и группы"]');
    await expect(scaleCards).toBeVisible();
    const rows = scaleCards.locator(':scope > .admin-dashboard-screen__stat');
    await expect(rows).toHaveCount(3);
    const formatCount = (value: number) => new Intl.NumberFormat('ru-RU').format(value);
    for (const { label, count, delta } of [
      { label: 'Студенты', count: summary.totalStudents, delta: growth.studentsDelta },
      { label: 'Преподаватели', count: summary.totalTeachers, delta: growth.teachersDelta },
      { label: 'Группы', count: summary.activeGroups, delta: growth.groupsDelta },
    ]) {
      let expectedGrowth = 'нет данных за вчера';
      if (growth.status === 'AVAILABLE') {
        expect(Number.isSafeInteger(delta)).toBe(true);
        const value = delta as number;
        expectedGrowth =
          value === 0
            ? 'без изменений за сутки'
            : value > 0
              ? `прирост +${formatCount(value)} за сутки`
              : `снижение −${formatCount(-value)} за сутки`;
      } else {
        expect(delta).toBeNull();
      }
      const row = rows.filter({ has: page.getByText(label, { exact: true }) });
      await expect(row.locator('dt')).toHaveText(label);
      await expect(row.locator('dd > span')).toHaveText(formatCount(count));
      await expect(row.locator('.admin-dashboard-screen__delta')).toHaveText(expectedGrowth);
    }
    const foreignScreens = page.locator(
      '.today-shell, .semester-screen, .attendance-screen, .headman-home, .headman-journal, .headman-group, .headman-actions, .teacher-feature-owner',
    );
    await expect(foreignScreens).toHaveCount(0);
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    const users = page.locator('.admin-users-screen');
    await expect(users).toBeVisible();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(target.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${target.id}"]`).click();
    const openEditor = async (assigned: boolean) => {
      await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
      await users
        .getByRole('button', {
          name: assigned ? 'Настроить права администратора' : 'Назначить права администратора',
          exact: true,
        })
        .click();
      await expect(
        users.getByRole('heading', { name: 'Права доступа', exact: true }),
      ).toBeVisible();
      await expect(
        users.getByRole('radio', { name: 'Выбранные права', exact: true }),
      ).toBeChecked();
    };
    const confirm = async (grant: boolean, method: 'PUT' | 'DELETE', labels: string[]) => {
      const dialog = page.getByRole('dialog', {
        name: grant ? 'Применить права?' : 'Отозвать права?',
        exact: true,
      });
      await expect(dialog.locator('.admin-users-permission-diff li')).toHaveCount(labels.length);
      for (const label of labels)
        await expect(dialog.getByText(label, { exact: true })).toBeVisible();
      await dialog.getByRole('checkbox', { name: 'Подтверждаю', exact: true }).check();
      const saved = http(page, accessPath, method);
      await dialog
        .getByRole('button', { name: grant ? 'Применить права' : 'Отозвать права', exact: true })
        .click();
      const response = await saved;
      expect(response.status()).toBe(method === 'PUT' ? 200 : 204);
      return response.request().postDataJSON();
    };
    await openEditor(false);
    const view = users.getByRole('checkbox', { name: 'Просмотр пользователей', exact: true });
    const manage = users.getByRole('checkbox', { name: 'Управление пользователями', exact: true });
    await manage.check();
    await expect(view).toBeChecked();
    await users.getByRole('button', { name: 'Проверить изменения', exact: true }).click();
    const assignedPayload = await confirm(true, 'PUT', [
      'Просмотр пользователей',
      'Управление пользователями',
    ]);
    expect(assignedPayload).toMatchObject({ mode: 'LIMITED', expectedVersion: before.version });
    expect([...assignedPayload.permissions].sort()).toEqual(['USERS_MANAGE', 'USERS_VIEW']);
    const assigned = await dataset.admin.call('GET', accessPath);
    expect(assigned).toMatchObject({ userId: target.id, assigned: true, mode: 'LIMITED' });
    expect([...assigned.permissions].sort()).toEqual(['USERS_MANAGE', 'USERS_VIEW']);
    expect(assigned.version).toBeGreaterThan(before.version);
    await expect(users.getByText('Права администратора сохранены.', { exact: true })).toBeVisible();
    await openEditor(true);
    await expect(view).toBeChecked();
    await expect(manage).toBeChecked();
    await manage.uncheck();
    await expect(view).toBeChecked();
    await users.getByRole('button', { name: 'Проверить изменения', exact: true }).click();
    const updatedPayload = await confirm(false, 'PUT', ['Управление пользователями']);
    expect(updatedPayload).toEqual({
      mode: 'LIMITED',
      permissions: ['USERS_VIEW'],
      expectedVersion: assigned.version,
    });
    const updated = await dataset.admin.call('GET', accessPath);
    expect(updated).toMatchObject({ userId: target.id, assigned: true, mode: 'LIMITED' });
    expect(updated.permissions).toEqual(['USERS_VIEW']);
    expect(updated.version).toBeGreaterThan(assigned.version);
    await expect(users.getByText('Права администратора сохранены.', { exact: true })).toBeVisible();
    await openEditor(true);
    await expect(view).toBeChecked();
    await expect(manage).not.toBeChecked();
    await users.getByRole('button', { name: 'Отозвать права администратора', exact: true }).click();
    const revokedPayload = await confirm(false, 'DELETE', ['Просмотр пользователей']);
    expect(revokedPayload).toEqual({ expectedVersion: updated.version });
    const revoked = await dataset.admin.call('GET', accessPath);
    expect(revoked).toMatchObject({
      userId: target.id,
      assigned: false,
      mode: null,
      permissions: [],
    });
    expect(revoked.version).toBeGreaterThan(updated.version);
    await expect(
      users.getByText('Права администратора отозваны. Аккаунт и остальные роли сохранены.', {
        exact: true,
      }),
    ).toBeVisible();
    const retained = await dataset.admin.call('GET', targetPath);
    expect(retained).toMatchObject({
      id: target.id,
      status: targetBefore.status,
      groupId: targetBefore.groupId,
    });
    const otherRoles = (roles: { role: string; status: string; groupId: number | null }[]) =>
      roles
        .filter((role) => role.role !== 'ADMIN')
        .map(({ role, status, groupId }) => ({ role, status, groupId }));
    expect(otherRoles(retained.roles)).toEqual(otherRoles(targetBefore.roles));
    expect(
      retained.roles.some(
        (role: { role: string; status: string }) =>
          role.role === 'ADMIN' && role.status === 'ACTIVE',
      ),
    ).toBe(false);
    const currentSession = await page.request.get('/api/auth/session', {
      headers: { Authorization: `Bearer ${selected.accessToken}` },
    });
    expect(currentSession.status()).toBe(200);
    expect((await currentSession.json()).activeRole).toBe('ADMIN');
    await expect(users).toBeVisible();
    await expect(foreignScreens).toHaveCount(0);
    expect(foreignFeatureRequested).toBe(false);
  } finally {
    page.off('response', observeAdmission);
    page.off('request', observeFeatureStart);
    try {
      const current = await dataset.admin.call('GET', accessPath);
      if (current.assigned)
        await dataset.admin.call('DELETE', accessPath, { expectedVersion: current.version }, [204]);
    } catch {
      // Preserve a primary UI/server failure and still fail cleanup on its own.
      expect.soft(false, 'Admin rights own-target cleanup failed').toBe(true);
    }
  }
});
