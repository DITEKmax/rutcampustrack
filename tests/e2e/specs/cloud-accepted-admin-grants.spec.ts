import { randomBytes, randomUUID } from 'node:crypto';
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
  // Own multirole target keeps an independent ACTIVE role during the status operation.
  await dataset.admin.call('PUT', `${targetPath}/roles/TEACHER`, {
    status: 'ACTIVE',
    employeeNumber: `T${target.id}`,
  });
  const targetBefore = await dataset.admin.call('GET', targetPath);
  const before = await dataset.admin.call('GET', accessPath);
  expect(before.assigned).toBe(false);
  // ADMIN20/21: one separate owned unbound teacher serves both accepted operations.
  const teacherBefore = await dataset.admin.call(
    'POST',
    '/api/academic/users',
    {
      lastName: `Проверка${randomBytes(5).toString('hex')}`,
      firstName: 'Синтетика',
      middleName: null,
      role: 'TEACHER',
      employeeNumber: `E2E-${randomUUID().slice(0, 20)}`,
      telegramId: null,
      groupId: null,
    },
    [201],
  );
  expect(
    Number.isSafeInteger(teacherBefore.id) &&
      teacherBefore.id > 0 &&
      teacherBefore.telegramId === null &&
      typeof teacherBefore.login === 'string' &&
      teacherBefore.roles.some(
        (role: { role: string; status: string }) =>
          role.role === 'TEACHER' && role.status === 'ACTIVE',
      ),
    'Own teacher exists without a Telegram binding',
  ).toBe(true);
  const teacherPath = `/api/academic/users/${teacherBefore.id}`;
  const teacherTelegramId = 8_000_000_000 + randomBytes(4).readUInt32BE(0);
  // End ADMIN20/21 own teacher.
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
    // ADMIN22: arm the genuine initial activity read before entering the dashboard.
    const activityReply = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/academic/dashboard/activity' &&
        url.searchParams.get('period') === 'today' &&
        url.searchParams.get('bucket') === 'day' &&
        response.request().method() === 'GET'
      );
    });
    // End ADMIN22 initial read.
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
    // ADMIN22: each actual period payload must be represented by the existing metric cards.
    type ActivityMetric = {
      state: 'AVAILABLE' | 'UNAVAILABLE' | 'INSUFFICIENT_HISTORY';
      value: number | null;
      percent: number | null;
      numerator: number | null;
      denominator: number | null;
      comparison: {
        state: string;
        absoluteDelta: number | null;
        percentPointDelta: number | null;
      } | null;
    };
    type Activity = {
      period: string;
      bucket: string;
      summary: { metrics: Record<string, ActivityMetric> };
    };
    const assertActivity = async (response: Response, period: 'today' | 'all_time') => {
      expect(response.status()).toBe(200);
      const activity = (await response.json()) as Activity;
      expect(
        activity.period === period && activity.bucket === 'day',
        'Actual selected activity period',
      ).toBe(true);
      const integer = new Intl.NumberFormat('ru-RU');
      const decimal = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });
      for (const code of [
        'positiveMarks',
        'geoMarks',
        'mapOpens',
        'requests',
        'homeworkPublications',
      ]) {
        const metric = activity.summary.metrics[code];
        expect(['AVAILABLE', 'UNAVAILABLE', 'INSUFFICIENT_HISTORY']).toContain(metric.state);
        const row = page.locator(`[data-admin-activity-metric="${code}"]`);
        await expect(row).toHaveAttribute('data-state', metric.state);
        const measured =
          metric.state === 'AVAILABLE' &&
          (code === 'geoMarks' ? metric.percent !== null : metric.value !== null);
        const expected = !measured
          ? '—'
          : code === 'geoMarks'
            ? `${decimal.format(metric.percent!)}%`
            : integer.format(metric.value!);
        await expect(row.locator('dd > span')).toHaveText(expected);
        const fraction = row.locator('dd > small').filter({ hasText: ' из ' });
        if (code === 'geoMarks' && metric.state === 'AVAILABLE') {
          expect(
            Number.isSafeInteger(metric.numerator) && Number.isSafeInteger(metric.denominator),
            'Actual geo absolute counts are known',
          ).toBe(true);
          await expect(fraction).toHaveText(
            `${integer.format(metric.numerator!)} из ${integer.format(metric.denominator!)}`,
          );
        } else await expect(fraction).toHaveCount(0);
        const delta =
          period === 'today' && measured && metric.comparison?.state === 'AVAILABLE'
            ? code === 'geoMarks'
              ? metric.comparison.percentPointDelta
              : metric.comparison.absoluteDelta
            : null;
        const deltaText =
          delta === null
            ? '—'
            : `${delta > 0 ? '+' : delta < 0 ? '−' : ''}${decimal.format(Math.abs(delta))}${code === 'geoMarks' ? '%' : ''}`;
        await expect(row.locator('.admin-dashboard-screen__delta')).toHaveText(deltaText);
        if (delta !== null)
          await expect(row.locator('.admin-dashboard-screen__delta')).toHaveAttribute(
            'aria-label',
            /относительно вчера/,
          );
      }
      await expect(
        page.getByRole('button', {
          name: period === 'today' ? 'Сегодня' : 'Всё время',
          exact: true,
        }),
      ).toHaveAttribute('aria-pressed', 'true');
    };
    await assertActivity(await activityReply, 'today');
    for (const period of ['all_time', 'today'] as const) {
      const next = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return (
          url.pathname === '/api/academic/dashboard/activity' &&
          url.searchParams.get('period') === period &&
          url.searchParams.get('bucket') === 'day' &&
          response.request().method() === 'GET'
        );
      });
      await page
        .getByRole('button', { name: period === 'today' ? 'Сегодня' : 'Всё время', exact: true })
        .click();
      await assertActivity(await next, period);
    }
    // End ADMIN22 activity witness.
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
    // ADMIN21: bind the existing own teacher with a partial profile edit and authoritative readback.
    await users.getByRole('button', { name: 'Назад', exact: true }).click();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(teacherBefore.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${teacherBefore.id}"]`).click();
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    await users.getByLabel('Telegram ID', { exact: true }).fill(String(teacherTelegramId));
    const bindingSaved = http(page, teacherPath, 'PATCH');
    await users.getByRole('button', { name: 'Сохранить данные', exact: true }).click();
    const bindingResponse = await bindingSaved;
    expect(bindingResponse.status()).toBe(200);
    const bindingInput = bindingResponse.request().postDataJSON();
    expect(
      Object.keys(bindingInput).length === 1 && bindingInput.telegramId === teacherTelegramId,
      'Binding sends only the intended Telegram field',
    ).toBe(true);
    const bound = await dataset.admin.call('GET', teacherPath);
    expect(
      bound.id === teacherBefore.id &&
        bound.telegramId === teacherTelegramId &&
        bound.login === teacherBefore.login &&
        bound.employeeNumber === teacherBefore.employeeNumber &&
        bound.lastName === teacherBefore.lastName &&
        bound.firstName === teacherBefore.firstName &&
        bound.middleName === teacherBefore.middleName &&
        JSON.stringify(bound.roles) === JSON.stringify(teacherBefore.roles),
      'Binding preserves the teacher account and unrelated profile data',
    ).toBe(true);
    await expect(users.getByText('Данные пользователя сохранены.', { exact: true })).toBeVisible();
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    expect(
      (await users.getByLabel('Telegram ID', { exact: true }).inputValue()) ===
        String(teacherTelegramId),
      'Reopened teacher editor retains the binding',
    ).toBe(true);
    // End ADMIN21 binding witness.

    // ADMIN20: issue the existing recovery alternative, then use it in an independent owner context.
    await users.getByRole('button', { name: 'Восстановить доступ', exact: true }).click();
    const issuedReply = http(
      page,
      `/api/auth/admin/users/${teacherBefore.id}/password-reset-link`,
      'POST',
    );
    await users.getByRole('button', { name: 'Создать ссылку восстановления', exact: true }).click();
    const issuedResponse = await issuedReply;
    expect(issuedResponse.status()).toBe(201);
    const issued = await issuedResponse.json();
    expect(
      typeof issued.url === 'string' &&
        new URL(issued.url).origin === origin &&
        new URL(issued.url).pathname.endsWith('/password-reset') &&
        Date.parse(issued.expiresAt) > Date.now(),
      'Own recovery link has the authoritative public target and unexpired lifetime',
    ).toBe(true);
    expect(
      (await users.locator('.recovery-link-panel__field input[readonly]').inputValue()) ===
        issued.url,
      'The administrator sees the issued one-time link',
    ).toBe(true);
    const browser = page.context().browser();
    expect(Boolean(browser), 'Existing browser carrier is available').toBe(true);
    const recoveryContext = await browser!.newContext({ baseURL: origin, ignoreHTTPSErrors: true });
    try {
      const recoveryPage = await recoveryContext.newPage();
      await recoveryPage.goto(issued.url);
      await expect(
        recoveryPage.getByRole('heading', { name: 'Задай новый пароль', exact: true }),
      ).toBeVisible();
      expect(new URL(recoveryPage.url()).hash === '', 'Recovery consumes its secret fragment').toBe(
        true,
      );
      const replacementPassword = `Reset-${randomUUID()}!1`;
      await recoveryPage.getByLabel('Новый пароль', { exact: true }).fill(replacementPassword);
      await recoveryPage
        .getByLabel('Повтори новый пароль', { exact: true })
        .fill(replacementPassword);
      const completed = http(recoveryPage, '/api/auth/password-reset/complete', 'POST');
      await recoveryPage.getByRole('button', { name: 'Сохранить пароль', exact: true }).click();
      expect((await completed).status()).toBe(204);
      await expect(
        recoveryPage.getByRole('heading', { name: 'Войти в приложение', exact: true }),
      ).toBeVisible();
      const remainingMs = Math.floor(retryCutoffMs - performance.now());
      expect(remainingMs, 'Replacement login retains the original positive budget').toBeGreaterThan(
        0,
      );
      const replacement = await loginPwa(
        recoveryPage,
        {
          login: teacherBefore.login,
          password: replacementPassword,
          role: 'TEACHER',
          expectedLandingPath: '/app/',
        },
        { retryUntilMs: Date.now() + Math.min(65000, remainingMs) },
      );
      expect(
        replacement.session.userId === String(teacherBefore.id) &&
          replacement.session.activeRole === 'TEACHER',
        'Replacement credential admits the exact recovered teacher',
      ).toBe(true);
    } finally {
      await recoveryContext.close().catch(() => {
        expect.soft(false, 'Own recovery browser context cleanup failed').toBe(true);
      });
    }
    // Existing own-account status and transfer editors; no shared recipient is changed.
    await expect(users).toHaveAttribute('data-view', 'recovery-issued');
    await users.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(users).toHaveAttribute('data-view', 'detail');
    await users.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(users).toHaveAttribute('data-view', 'list');
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(target.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${target.id}"]`).click();
    const unchangedTeacher = (user: typeof targetBefore) =>
      JSON.stringify(user.roles.filter((grant: { role: string }) => grant.role !== 'STUDENT')) ===
      JSON.stringify(
        targetBefore.roles.filter((grant: { role: string }) => grant.role !== 'STUDENT'),
      );
    for (const status of ['SUSPENDED', 'ACTIVE'] as const) {
      await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
      const studentRole = users.locator('.admin-users-role').filter({
        has: users.locator('.admin-users-role-heading').filter({ hasText: /^Студент$/ }),
      });
      await studentRole.getByRole('button', { name: 'Изменить статус', exact: true }).click();
      await users.getByLabel('Статус роли', { exact: true }).selectOption(status);
      const saved = http(page, `${targetPath}/roles/STUDENT`, 'PUT');
      await users.getByRole('button', { name: 'Сохранить статус', exact: true }).click();
      const response = await saved;
      expect(response.status()).toBe(200);
      const input = response.request().postDataJSON();
      expect(
        input.status === status &&
          input.groupId === targetBefore.groupId &&
          input.telegramId === targetBefore.telegramId &&
          Object.keys(input).sort().join(',') === 'groupId,status,telegramId',
        'Status editor preserves the current student identity and group',
      ).toBe(true);
      const updated = await response.json();
      const persisted = await dataset.admin.call('GET', targetPath);
      for (const user of [updated, persisted])
        expect(
          user.id === targetBefore.id &&
            user.login === targetBefore.login &&
            user.telegramId === targetBefore.telegramId &&
            user.employeeNumber === targetBefore.employeeNumber &&
            user.groupId === targetBefore.groupId &&
            user.roles.some(
              (grant: { role: string; status: string }) =>
                grant.role === 'STUDENT' && grant.status === status,
            ) &&
            unchangedTeacher(user),
          'Only the own student role changes; the independent teacher role is retained',
        ).toBe(true);
      await expect(users.getByText('Роль сохранена.', { exact: true })).toBeVisible();
    }
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    await users.getByRole('button', { name: 'Перевести в другую группу', exact: true }).click();
    const groupPicker = users.locator('details[data-picker="group"]');
    await groupPicker.locator('summary').click();
    await groupPicker
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(actorGroup.name);
    await groupPicker.getByRole('button', { name: actorGroup.name, exact: true }).click();
    const transferReason = `Синтетический перевод ${randomUUID()}`;
    await users.getByLabel('Причина перевода', { exact: true }).fill(transferReason);
    const transferred = http(page, `${targetPath}/transfer`, 'POST');
    await users.getByRole('button', { name: 'Перевести студента', exact: true }).click();
    const transferResponse = await transferred;
    expect(transferResponse.status()).toBe(200);
    const transferInput = transferResponse.request().postDataJSON();
    expect(
      transferInput.newGroupId === actorGroup.id &&
        transferInput.reason === transferReason &&
        Object.keys(transferInput).sort().join(',') === 'newGroupId,reason',
      'Transfer sends the selected own destination and reason',
    ).toBe(true);
    for (const user of [await transferResponse.json(), await dataset.admin.call('GET', targetPath)])
      expect(
        user.id === targetBefore.id &&
          user.login === targetBefore.login &&
          user.telegramId === targetBefore.telegramId &&
          user.employeeNumber === targetBefore.employeeNumber &&
          user.groupId === actorGroup.id &&
          user.roles.some(
            (grant: { role: string; status: string; groupId: number | null }) =>
              grant.role === 'STUDENT' &&
              grant.status === 'ACTIVE' &&
              grant.groupId === actorGroup.id,
          ) &&
          unchangedTeacher(user),
        'The real transfer persists on the same multirole account',
      ).toBe(true);
    await expect(
      users.getByText('Студент переведён в другую группу.', { exact: true }),
    ).toBeVisible();
    // End own-account status and transfer witness.
    // End ADMIN20 recovery witness.
    const currentSession = await page.request.get('/api/auth/session', {
      headers: { Authorization: `Bearer ${selected.accessToken}` },
    });
    expect(currentSession.status()).toBe(200);
    expect((await currentSession.json()).activeRole).toBe('ADMIN');
    await expect(users).toBeVisible();
    await expect(foreignScreens).toHaveCount(0);
    expect(foreignFeatureRequested).toBe(false);
  } finally {
    // A failed status flow must not leave the own student role suspended.
    try {
      const currentTarget = await dataset.admin.call('GET', targetPath);
      const studentGrant = currentTarget.roles.find(
        (grant: { role: string }) => grant.role === 'STUDENT',
      );
      if (studentGrant?.status === 'SUSPENDED')
        await dataset.admin.call('PUT', `${targetPath}/roles/STUDENT`, { status: 'ACTIVE' });
      else
        expect
          .soft(studentGrant?.status === 'ACTIVE', 'Own student role cleanup state is known')
          .toBe(true);
    } catch {
      expect.soft(false, 'Own student role status cleanup failed').toBe(true);
    }
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

// D026/D027: real LIMITED report-only admission, filtered data and async delivery.
test('FULL exports five group attendance formats; dashboard-only LIMITED reads until revocation @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  test.setTimeout(120000);
  const cutoff = (retryStartedAt.get(info) ?? performance.now()) + 90000;
  const group = await dataset.admin.call(
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
  expect(
    typeof group.name === 'string' && group.name.length > 0,
    'Own empty group has a report name',
  ).toBe(true);
  const user = await dataset.user('ADMIN');
  const accessPath = `/api/academic/users/${user.id}/admin-access`;
  const before = await dataset.admin.call('GET', accessPath);
  const remaining = Math.floor(cutoff - performance.now());
  expect(remaining, 'Positive report login retry budget remains').toBeGreaterThan(0);
  await loginPwa(page, user, { retryUntilMs: Date.now() + Math.min(65000, remaining) });
  await selectRole(page, 'Администратор');
  const reportPath = '/api/academic/admin/attendance';
  let registryCalled = false;
  const observe = (request: Request) => {
    const path = new URL(request.url()).pathname;
    if (
      request.method() === 'GET' &&
      ['/api/academic/semesters', '/api/academic/groups', '/api/academic/groups/registry'].includes(
        path,
      )
    )
      registryCalled = true;
  };
  page.on('request', observe);
  try {
    const readOwnedReport = async () => {
      const semesters = http(page, `${reportPath}/semesters`, 'GET');
      const initial = http(page, reportPath, 'GET');
      await page.getByRole('button', { name: 'Посещаемость групп', exact: true }).click();
      expect((await semesters).status()).toBe(200);
      expect((await initial).status()).toBe(200);
      await expect(
        page.getByRole('heading', { name: 'Посещаемость групп', exact: true }),
      ).toBeVisible();
      for (const [selector, value, key] of [
        ['#admin-attendance-sort', 'COUNTED_LESSONS', 'sort'],
        ['#admin-attendance-direction', 'DESC', 'direction'],
      ] as const) {
        const changed = page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            url.pathname === reportPath &&
            url.searchParams.get(key) === value &&
            response.request().method() === 'GET'
          );
        });
        await page.locator(`.admin-attendance-screen ${selector}`).selectOption(value);
        expect((await changed).status()).toBe(200);
      }
      await page.getByLabel('Найти группу', { exact: true }).fill(group.name);
      const filtered = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return (
          url.pathname === reportPath &&
          url.searchParams.get('search') === group.name &&
          response.request().method() === 'GET'
        );
      });
      await page.getByRole('button', { name: 'Найти', exact: true }).click();
      const response = await filtered;
      expect(response.status()).toBe(200);
      const report = await response.json();
      expect(
        report.rows.length === 1 && String(report.rows[0].groupId) === String(group.id),
        'Only the own filtered group is returned',
      ).toBe(true);
      const row = report.rows[0];
      expect(
        row.dataState === 'NO_DATA' &&
          row.opportunities === 0 &&
          row.presentPercent === null &&
          row.presentOrExcusedPercent === null,
        'An empty own group has no denominator, not zero attendance',
      ).toBe(true);
      const visibleRow = page.locator(`.admin-attendance-screen [data-group-id="${group.id}"]`);
      await expect(visibleRow).toContainText(group.name);
      await expect(visibleRow.getByText('Нет данных', { exact: true })).toHaveCount(2);
      const numbers = [row.countedLessons, row.presentCount, row.absentCount, row.excusedCount].map(
        (value) => new Intl.NumberFormat('ru-RU').format(value),
      );
      expect(
        await visibleRow
          .locator('td')
          .allTextContents()
          .then(
            (cells) =>
              JSON.stringify(cells.slice(2).map((value) => value.trim())) ===
              JSON.stringify(numbers),
          ),
        'Visible counts are the real server counts',
      ).toBe(true);

      return report;
    };
    const report = await readOwnedReport();
    const formats = http(page, `${reportPath}/formats`, 'GET');
    await page.getByRole('button', { name: 'Выгрузка', exact: true }).click();
    const catalogue = await formats;
    expect(catalogue.status()).toBe(200);
    expect((await catalogue.json()).map((item: { code: string }) => item.code).sort()).toEqual([
      'docx',
      'html',
      'pdf',
      'png',
      'xlsx',
    ]);
    let lastJobId = '';
    const downloadOwnedReport = async (
      format: 'html' | 'docx' | 'xlsx' | 'pdf' | 'png',
      currentReport: { semester: { id: string }; sort: string; direction: string },
    ) => {
      await page.getByLabel(/^Формат выгрузки/).selectOption(format);
      const created = http(page, '/api/auth/export-jobs', 'POST');
      const issued = page.waitForResponse(
        (result) =>
          /^\/api\/auth\/export-jobs\/[0-9a-f-]+\/ticket$/.test(new URL(result.url()).pathname) &&
          result.request().method() === 'POST',
      );
      const saved = page.waitForEvent('download');
      await page.getByRole('button', { name: 'Скачать', exact: true }).click();
      const job = await created;
      expect(job.status()).toBe(202);
      const selector = job.request().postDataJSON().report;
      expect(
        selector.kind === 'ADMIN_GROUP_ATTENDANCE' &&
          selector.adminGroupAttendance.semesterId === currentReport.semester.id &&
          selector.adminGroupAttendance.search === group.name &&
          selector.adminGroupAttendance.sort === currentReport.sort &&
          selector.adminGroupAttendance.direction === currentReport.direction &&
          selector.adminGroupAttendance.format === format &&
          !Object.hasOwn(selector.adminGroupAttendance, 'page') &&
          !Object.hasOwn(selector.adminGroupAttendance, 'size'),
        'Async export uses the complete current filtered report',
      ).toBe(true);
      lastJobId = (await job.json()).jobId;
      expect((await issued).status()).toBe(200);
      const download = await saved;
      expect(await download.failure()).toBeNull();
      expect(
        download.suggestedFilename().endsWith(format === 'png' ? '.zip' : '.' + format),
        'Real report filename matches the selected format',
      ).toBe(true);
      const stream = await download.createReadStream();
      expect(stream !== null, 'Genuine report download is readable').toBe(true);
      const chunks: Buffer[] = [];
      let size = 0;
      for await (const part of stream!) {
        const bytes = Buffer.from(part);
        size += bytes.length;
        expect(
          size <= 20 * 1024 * 1024,
          'Own generated report remains within the existing stored-file bound',
        ).toBe(true);
        chunks.push(bytes);
      }
      const content = Buffer.concat(chunks);
      expect(content.length > 0, 'Real generated report contains bytes').toBe(true);
      if (format === 'html') {
        const html = content.toString('utf8');
        expect(
          html.includes(group.name) &&
            !html.includes(dataset.group.name) &&
            (html.match(/<tbody>[\s\S]*?<\/tbody>/)?.[0].match(/<tr>/g)?.length ?? 0) === 1,
          'Downloaded HTML contains exactly the filtered group',
        ).toBe(true);
        expect(
          !html.includes(user.password) && !/Telegram ID|password-reset/i.test(html),
          'Group report contains no credentials or Telegram identity',
        ).toBe(true);
      } else if (format === 'pdf') {
        expect(
          content.subarray(0, 5).toString('ascii') === '%PDF-' &&
            content.includes(Buffer.from('%%EOF')),
          'Real renderer produced a PDF document',
        ).toBe(true);
      } else {
        const member =
          format === 'docx' ? 'word/document.xml' : format === 'xlsx' ? 'xl/workbook.xml' : '.png';
        expect(
          content.length >= 4 &&
            content.readUInt32LE(0) === 0x04034b50 &&
            content.includes(Buffer.from(member)),
          'Real generated archive contains its declared report format',
        ).toBe(true);
      }
      await expect(page.getByRole('button', { name: 'Скачать', exact: true })).toBeEnabled();
    };
    for (const format of ['html', 'docx', 'xlsx', 'pdf', 'png'] as const)
      await downloadOwnedReport(format, report);
    const limited = await dataset.admin.call('PUT', accessPath, {
      mode: 'LIMITED',
      permissions: ['DASHBOARD_VIEW'],
      expectedVersion: before.version,
    });
    const limitedRefresh = await page.request.post('/api/auth/refresh');
    expect(limitedRefresh.status()).toBe(200);
    await page.reload();
    const limitedReport = await readOwnedReport();
    expect(
      limitedReport.rows.length === 1 && String(limitedReport.rows[0].groupId) === String(group.id),
      'Current dashboard-only administrator reads the same filtered own group',
    ).toBe(true);
    const limitedFormats = http(page, `${reportPath}/formats`, 'GET');
    await page.getByRole('button', { name: 'Выгрузка', exact: true }).click();
    expect((await limitedFormats).status()).toBe(200);
    await downloadOwnedReport('html', limitedReport);
    expect(registryCalled, 'Report does not borrow registry permissions').toBe(false);
    await dataset.admin.call('PUT', accessPath, {
      mode: 'LIMITED',
      permissions: ['USERS_VIEW'],
      expectedVersion: limited.version,
    });
    const refresh = await page.request.post('/api/auth/refresh');
    expect(refresh.status()).toBe(200);
    const currentHeaders = { Authorization: `Bearer ${(await refresh.json()).accessToken}` };
    for (const path of [
      reportPath + `?semesterId=${report.semester.id}`,
      `${reportPath}/semesters`,
      `/api/auth/export-jobs/${lastJobId}`,
    ])
      expect((await page.request.get(path, { headers: currentHeaders })).status()).toBe(403);
    await page.reload();
    await expect(page.getByRole('button', { name: 'Посещаемость групп', exact: true })).toHaveCount(
      0,
    );
    await expect(page.locator('.admin-attendance-screen')).toHaveCount(0);
  } finally {
    page.off('request', observe);
  }
});
