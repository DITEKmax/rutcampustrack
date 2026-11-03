import { diagnosticCase, diagnosticHttpFailure } from '../fixtures/diagnostic-events';
import { randomUUID } from 'node:crypto';
import type { Request, Response, TestInfo } from '@playwright/test';
import { Actor, admit, expect, test as base, MobileDataset } from '../fixtures/mobile-dataset';
import { registerPrivateSuite } from '../fixtures/cloud-accepted-support';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { todayAfterAction } from '../fixtures/student-today-response';

registerPrivateSuite();

// The existing lesson-lifecycle spec uses a worker-scoped dataset and restores
// its physical lesson as a new generation. Each scenario here owns a fresh
// dataset: template/headman mutations never change that worker or the other case.
const scenarioStartedAt = new WeakMap<TestInfo, number>();
const test = base.extend<{ scenario: MobileDataset }>({
  scenario: async ({ dataset, teachingInstant }, use, info) => {
    scenarioStartedAt.set(info, performance.now());
    const own = new MobileDataset(dataset.request, dataset.admin, teachingInstant);
    await own.initialize();
    await use(own);
  },
});

interface PhysicalLesson {
  id: number;
  scheduleItemId: number | null;
  occurrenceId: number;
  assignmentId: number;
  generation: number;
  revision: number;
  occurrenceRevision: number;
  date: string;
  status: string;
  room: string | null;
  current: boolean;
}

async function physicalLessons(dataset: MobileDataset, actor = dataset.headmanActor) {
  const response = await actor.call(
    'GET',
    `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.semester.dateFrom}&dateTo=${dataset.semester.dateTo}&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED&size=100`,
  );
  const rows: PhysicalLesson[] = response._embedded?.lessonResponseList ?? [];
  // This finite, otherwise empty group must fit the single inspected page.
  expect(Array.isArray(rows) && response.page.totalElements === rows.length).toBe(true);
  return rows;
}

function identities(rows: PhysicalLesson[]) {
  return JSON.stringify(
    rows.map(
      ({
        id,
        occurrenceId,
        assignmentId,
        generation,
        revision,
        occurrenceRevision,
        date,
        status,
        room,
        current,
      }) => ({
        id,
        occurrenceId,
        assignmentId,
        generation,
        revision,
        occurrenceRevision,
        date,
        status,
        room,
        current,
      }),
    ),
  );
}

// The ready conditional route is the semester BFF. Today is the existing Vue
// room consumer; a scoped HTTP PATCH is the accepted cache-invalidation trigger.
test('STUDENT warm semester ETag stays scoped and refreshes the same physical room in Today @real-mobile', { annotation: diagnosticCase('STUDENT_SEMESTER_ROOM_REFRESH') }, async ({
  page,
  scenario: dataset,
}, info) => {
  // This existing student's otherGroup is its own genuinely empty scope.
  // Keep Today unavailable while its error and manual retry action are observed;
  // the manual retry below returns to the real server after this route is removed.
  const todayPath = '/api/v1/student/today';
  const admissionCutoff =
    (scenarioStartedAt.get(info) ?? performance.now()) +
    Math.max(0, Math.min(info.timeout, 240000) - 30000);
  const [emptyTodayResponse, emptyScopeResponse, emptyAdmission] = await (async () => {
    let observedToday: Response | undefined;
    let observedScope: Response | undefined;
    const todayMatch = (response: Response) =>
      response.request().method() === 'GET' && new URL(response.url()).pathname === todayPath;
    const scopeMatch = (response: Response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/student/session';
    const observe = (response: Response) => {
      if (todayMatch(response)) observedToday ??= response;
      if (scopeMatch(response)) observedScope ??= response;
    };
    // Observe without response timers while the existing helper obeys Retry-After.
    page.on('response', observe);
    try {
      const remaining = Math.floor(admissionCutoff - performance.now());
      expect(remaining, 'Positive empty-scope login retry budget remains').toBeGreaterThan(0);
      const admission = await loginPwa(page, dataset.foreignStudent, {
        retryUntilMs: Date.now() + Math.min(65000, remaining),
      });
      return [
        observedToday ?? (await page.waitForResponse(todayMatch)),
        observedScope ?? (await page.waitForResponse(scopeMatch)),
        admission,
      ] as const;
    } finally {
      page.off('response', observe);
    }
  })();
  expect(
    emptyAdmission.session.activeRole === 'STUDENT' &&
      String(emptyAdmission.session.userId) === String(dataset.foreignStudent.id),
  ).toBe(true);
  expect(emptyScopeResponse.status()).toBe(200);
  const emptyScope = await emptyScopeResponse.json();
  expect(
    String(emptyScope.user.id) === String(dataset.foreignStudent.id) &&
      String(emptyScope.group.id) === String(dataset.otherGroup.id) &&
      String(emptyScope.semester.id) === String(dataset.semester.id),
  ).toBe(true);
  expect(emptyTodayResponse.status()).toBe(200);
  const emptyToday = await emptyTodayResponse.json();
  expect(
    emptyToday.date === dataset.today &&
      emptyToday.timeZone === 'Europe/Moscow' &&
      emptyToday.lessons.length === 0,
  ).toBe(true);
  const todayScreen = page.getByRole('main', { name: 'Сегодня', exact: true });
  const emptyState = todayScreen.getByRole('status').filter({
    has: page.getByRole('button', { name: 'Обновить', exact: true }),
  });
  const error = todayScreen.getByRole('alert');
  await expect(emptyState).toBeVisible();
  await expect(page.locator('.today-list .student-lesson')).toHaveCount(0);
  const todayUrl = new URL(todayPath, page.url()).href;
  let interceptedToday: Request | undefined;
  let failedStatus = 0;
  const failToday: Parameters<typeof page.route>[1] = async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue();
      return;
    }
    interceptedToday ??= route.request();
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      headers: { 'cache-control': 'no-store' },
      json: {
        type: 'urn:rct:problem:dependency-unavailable',
        title: 'Service Unavailable',
        status: 503,
        code: 'DEPENDENCY_UNAVAILABLE',
        detail: 'Синтетическая ошибка чтения расписания.',
      },
    });
  };
  await page.route(todayUrl, failToday);
  try {
    const failed = await todayAfterAction(
      page,
      () => emptyState.getByRole('button', { name: 'Обновить', exact: true }).click(),
      () => interceptedToday,
    );
    failedStatus = failed.status();
    expect(failed.status()).toBe(503);
    await expect(error).toBeVisible();
    await expect(error.getByRole('button', { name: 'Повторить', exact: true })).toBeEnabled();
    await expect(emptyState).toHaveCount(0);
  } catch (primary) {
    if (primary instanceof Error)
      throw diagnosticHttpFailure(primary, 'STUDENT_TODAY_SYNTHETIC_READ', failedStatus);
    throw primary;
  } finally {
    await page.unroute(todayUrl, failToday);
  }
  let recoveredTodayResponse: Response;
  let recoveredStatus = 0;
  try {
    recoveredTodayResponse = await todayAfterAction(
      page,
      () => error.getByRole('button', { name: 'Повторить', exact: true }).click(),
    );
    recoveredStatus = recoveredTodayResponse.status();
    expect(recoveredTodayResponse.status()).toBe(200);
  } catch (primary) {
    if (primary instanceof Error)
      throw diagnosticHttpFailure(primary, 'STUDENT_TODAY_RECOVERY_READ', recoveredStatus);
    throw primary;
  }
  const recoveredToday = await recoveredTodayResponse.json();
  expect(
    recoveredToday.date === dataset.today &&
      recoveredToday.timeZone === 'Europe/Moscow' &&
      recoveredToday.lessons.length === 0,
  ).toBe(true);
  await expect(error).toHaveCount(0);
  await expect(emptyState).toBeVisible();
  await expect(page.locator('.today-list .student-lesson')).toHaveCount(0);
  await logoutPwa(page);
  expect((await page.context().cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(
    false,
  );

  const oldRoom = 'Синтетическая кэш-аудитория А';
  const newRoom = 'Синтетическая кэш-аудитория Б';
  const physical = await dataset.headmanActor.call(
    'POST',
    '/api/schedule/one-off-lessons',
    {
      groupId: dataset.group.id,
      subjectId: dataset.subject.id,
      assignmentId: dataset.subject.assignments[0].id,
      date: dataset.today,
      lessonNumber: 8,
      classroom: oldRoom,
    },
    [201],
    { 'Idempotency-Key': randomUUID() },
  );
  // Settle the existing 09:00 teaching-clock transition before warming ETags;
  // a still-PLANNED current lesson would make both the hero and cache unstable.
  await expect
    .poll(
      async () =>
        (await physicalLessons(dataset)).some(
          (row) => row.id === dataset.lesson.physicalLessonId && row.status === 'ACTIVE',
        ),
      {
        timeout: 75000,
        message: 'Own Today lesson becomes active through the normal educational job',
      },
    )
    .toBe(true);
  const admitted = await loginPwa(page, dataset.student);
  const student = new Actor(dataset.request, admitted.accessToken, admitted.session);
  expect(admitted.session.activeRole).toBe('STUDENT');
  await expect(page.getByText(oldRoom, { exact: true })).toBeVisible();
  const schedulePath = `/api/v1/student/schedule?semesterId=${dataset.semester.id}`;
  const warm = await dataset.request.get(schedulePath, { headers: student.headers() });
  expect(warm.status()).toBe(200);
  const etag = warm.headers().etag;
  expect(typeof etag === 'string' && etag.length > 0).toBe(true);
  expect(warm.headers()['cache-control']).toContain('private');
  const before = await warm.json();
  const projected = before.lessons.filter(
    (row: { id: string }) => row.id === String(physical.physicalLessonId),
  );
  expect(projected.length === 1 && projected[0].room.current === oldRoom).toBe(true);
  const conditional = await dataset.request.get(schedulePath, {
    headers: { ...student.headers(), 'If-None-Match': etag! },
  });
  expect(conditional.status()).toBe(304);
  expect(conditional.headers().etag === etag).toBe(true);
  const browserWarm = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname + new URL(response.url()).search === schedulePath,
  );
  await page.reload();
  const browserConditional = await browserWarm;
  expect(browserConditional.status()).toBe(304);
  expect(browserConditional.request().headers()['if-none-match'] === etag).toBe(true);
  await expect(page.getByText(oldRoom, { exact: true })).toBeVisible();

  const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
  const isolated = await dataset.request.get(schedulePath, {
    headers: { ...foreign.headers(), 'If-None-Match': etag! },
  });
  expect(isolated.status()).toBe(200);
  const foreignBody = await isolated.json();
  const foreignEtag = isolated.headers().etag;
  expect(
    foreignBody.lessons.length === 0 && typeof foreignEtag === 'string' && foreignEtag !== etag,
  ).toBe(true);
  expect(
    (
      await dataset.request.get(schedulePath, {
        headers: { ...student.headers(), 'If-None-Match': foreignEtag! },
      })
    ).status(),
  ).toBe(200);
  const original = (await physicalLessons(dataset)).find(
    (row) => row.id === physical.physicalLessonId,
  )!;
  expect(original.current && original.status === 'PLANNED' && original.occurrenceId > 0).toBe(true);
  const roomPath = `/api/schedule/lessons/${original.id}/room`;
  const mutation = { room: newRoom, expectedRevision: String(original.occurrenceRevision) };
  expect(
    (
      await dataset.request.patch(roomPath, {
        headers: { ...foreign.headers(), 'Idempotency-Key': randomUUID() },
        data: mutation,
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await dataset.request.patch(roomPath, {
        headers: { ...student.headers(), 'Idempotency-Key': randomUUID() },
        data: mutation,
      })
    ).status(),
  ).toBe(403);
  expect(
    identities([(await physicalLessons(dataset)).find((row) => row.id === original.id)!]) ===
      identities([original]),
  ).toBe(true);
  expect(
    (
      await dataset.request.get(schedulePath, {
        headers: { ...student.headers(), 'If-None-Match': etag! },
      })
    ).status(),
  ).toBe(304);
  const changed = await dataset.headmanActor.call('PATCH', roomPath, mutation, [200], {
    'Idempotency-Key': randomUUID(),
  });
  expect(
    changed.state === 'COMPLETED' &&
      changed.lessonId === String(original.id) &&
      changed.occurrenceId === String(original.occurrenceId) &&
      changed.room === newRoom,
  ).toBe(true);
  const samePhysical = (await physicalLessons(dataset)).find((row) => row.id === original.id)!;
  expect(
    samePhysical.occurrenceId === original.occurrenceId &&
      samePhysical.generation === original.generation &&
      samePhysical.room === newRoom &&
      samePhysical.occurrenceRevision > original.occurrenceRevision &&
      changed.revision === String(samePhysical.occurrenceRevision),
  ).toBe(true);
  const fresh = await dataset.request.get(schedulePath, {
    headers: { ...student.headers(), 'If-None-Match': etag! },
  });
  expect(fresh.status()).toBe(200);
  const newEtag = fresh.headers().etag;
  expect(typeof newEtag === 'string' && newEtag !== etag).toBe(true);
  const updated = await fresh.json();
  expect(updated.lessons.length === before.lessons.length).toBe(true);
  const changedProjection = updated.lessons.filter(
    (row: { id: string }) => row.id === String(original.id),
  );
  expect(
    changedProjection.length === 1 &&
      changedProjection[0].room.current === newRoom &&
      changedProjection[0].room.previous === oldRoom,
  ).toBe(true);
  const browserRefresh = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname + new URL(response.url()).search === schedulePath,
  );
  await page.reload();
  const current = await browserRefresh;
  expect(current.status()).toBe(200);
  expect(
    current.request().headers()['if-none-match'] === etag && current.headers().etag === newEtag,
  ).toBe(true);
  const card = page
    .locator('.student-lesson__card')
    .filter({ has: page.getByText(newRoom, { exact: true }) });
  await expect(card).toBeVisible();
  await expect(
    card.getByRole('heading', { name: dataset.subject.name, exact: true }),
  ).toBeVisible();
  await expect(card.locator('.student-lesson__room--changed')).toHaveText(newRoom);
  await expect(card.getByLabel(`Прежняя аудитория: ${oldRoom}`, { exact: true })).toHaveText(
    oldRoom,
  );
  expect(
    (
      await dataset.request.get(schedulePath, {
        headers: { ...student.headers(), 'If-None-Match': newEtag! },
      })
    ).status(),
  ).toBe(304);
});
