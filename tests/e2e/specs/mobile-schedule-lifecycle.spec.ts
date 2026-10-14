import { randomUUID } from 'node:crypto';
import type { Page } from '@playwright/test';
import { Actor, admit, expect, test as base, MobileDataset } from '../fixtures/mobile-dataset';
import { http, registerPrivateSuite, selectRole } from '../fixtures/cloud-accepted-support';
import { loginPwa } from '../fixtures/pwa-auth';

registerPrivateSuite();

// The existing lesson-lifecycle spec uses a worker-scoped dataset and restores
// its physical lesson as a new generation. Each scenario here owns a fresh
// dataset: template/headman mutations never change that worker or the other case.
const test = base.extend<{ scenario: MobileDataset }>({
  scenario: async ({ dataset, teachingInstant }, use) => {
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

async function fillSlot(page: Page, dataset: MobileDataset, labels: string[], room: string) {
  const form = page.locator('.headman-schedule__form');
  for (const label of labels) {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const choice = form
      .getByRole('group', { name: 'Учебные недели', exact: true })
      .getByRole('button', { name: new RegExp(`^${escaped}`) });
    if ((await choice.getAttribute('aria-pressed')) === 'false') await choice.click();
    await expect(choice).toHaveAttribute('aria-pressed', 'true');
  }
  await form.getByRole('button', { name: 'Номер пары: выбери номер пары', exact: true }).click();
  await form.locator('button[data-slot="7"]').click();
  await form.getByLabel('Предмет', { exact: true }).selectOption(String(dataset.subject.id));
  await form
    .getByLabel('Тип занятия', { exact: true })
    .selectOption(String(dataset.subject.assignments[0].id));
  await form.getByLabel('Аудитория', { exact: true }).fill(room);
}

// JS-HEADMAN-40 / DF-PWA-BUILDER-REMOVE-001. Actual Vue DELETE/return and parity
// changes; the already accepted real-PG elapsed-gap/no-backfill rule is reused.
// No clock advance or claim of an elapsed absence interval in this browser case.
test('HEADMAN removes and returns a recurring slot without losing past identity or marks @real-mobile', async ({
  page,
  scenario: dataset,
}) => {
  expect(dataset.semester.dateFrom < dataset.today).toBe(true);
  const day = new Date(`${dataset.semester.dateFrom}T12:00:00Z`).getUTCDay() || 7;
  const dayLabel = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'][day - 1]!;
  const room = 'Синтетическая аудитория конструктора';
  const policy = await dataset.headmanActor.call(
    'GET',
    `/api/schedule/academic-week-policy?date=${dataset.today}`,
  );
  const weeks: { weekType: string; label: string }[] = policy.weekTypes.filter(
    (option: { parityNumber: number | null }) => option.parityNumber !== null,
  );
  expect(weeks.length === 2 && new Set(weeks.map((week) => week.weekType)).size === 2).toBe(true);
  await loginPwa(page, dataset.headman);
  await selectRole(page, 'Староста');
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await page.getByRole('button', { name: 'Конструктор расписания', exact: true }).click();
  await page.getByRole('tab', { name: dayLabel, exact: true }).click();
  await page
    .locator('.headman-schedule__weeks')
    .getByRole('button', { name: weeks[0]!.label, exact: true })
    .click();
  await page.getByRole('button', { name: 'Добавить пару', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Удалить пару', exact: true })).toBeDisabled();
  await fillSlot(
    page,
    dataset,
    weeks.map((week) => week.label),
    room,
  );
  const created = http(page, '/api/schedule/items', 'POST');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  const createResponse = await created;
  expect(createResponse.status()).toBe(201);
  const item = await createResponse.json();
  const itemPath = `/api/schedule/items/${item.id}`;
  expect(
    item.groupId === dataset.group.id &&
      item.semesterId === dataset.semester.id &&
      item.weekType === 'ALL',
  ).toBe(true);
  await expect(page.getByRole('status').filter({ hasText: 'Слот сохранён.' })).toBeVisible();
  let own: PhysicalLesson[] = [];
  await expect
    .poll(
      async () => {
        own = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
        return (
          own.some((row) => row.date < dataset.today) &&
          own.filter((row) => row.date < dataset.today).every((row) => row.status === 'CLOSED')
        );
      },
      {
        timeout: 90000,
        message: 'Own recurring history closes through the normal educational job',
      },
    )
    .toBe(true);
  const past = own.filter((row) => row.date < dataset.today);
  const future = own.filter((row) => row.date >= dataset.today);
  expect(future.length > 1 && future.every((row) => row.status === 'PLANNED' && row.current)).toBe(
    true,
  );
  expect(
    own.length === item.generatedCount &&
      own.every((row) => row.occurrenceId > 0 && row.generation === 1),
  ).toBe(true);
  const reportPath = `/api/attendance/reports/lesson/${past[0]!.id}`;
  let mark: { status: string; markRevision: string } | undefined;
  await expect
    .poll(
      async () => {
        const report = await dataset.headmanActor.call('GET', reportPath);
        mark = report.entries.find(
          (entry: { userId: number }) => entry.userId === dataset.student.id,
        );
        return (
          mark?.status === 'absent' &&
          typeof mark.markRevision === 'string' &&
          mark.markRevision.trim().length > 0
        );
      },
      { timeout: 60000, message: 'Own closed mark materializes before its exact CAS' },
    )
    .toBe(true);
  await dataset.headmanActor.call(
    'PUT',
    `/api/attendance/lessons/${past[0]!.id}/students/${dataset.student.id}`,
    {
      status: 'PRESENT',
      expectedStatus: 'ABSENT',
      expectedRevision: mark!.markRevision,
    },
  );
  const marked = await dataset.headmanActor.call('GET', reportPath);
  const savedMark = marked.entries.find(
    (entry: { userId: number }) => entry.userId === dataset.student.id,
  );
  expect(savedMark.status === 'present' && typeof savedMark.markRevision === 'string').toBe(true);
  const pastIdentity = identities(past);

  const slot = page.locator('.headman-schedule__slot').filter({ hasText: room });
  await slot.click();
  const remove = page.getByRole('button', { name: 'Удалить пару', exact: true });
  const previewResponse = http(page, `${itemPath}/lifecycle-preview`, 'POST');
  await remove.click();
  const preview = await previewResponse;
  expect(preview.status()).toBe(200);
  expect(new URL(preview.url()).search === '?delete=true').toBe(true);
  const proposed = await preview.json();
  expect(proposed.removedCount === future.length).toBe(true);
  const confirmation = page.getByRole('dialog', {
    name: 'Удалить пару из конструктора?',
    exact: true,
  });
  await expect(confirmation).toBeVisible();
  await expect(confirmation.getByRole('status')).toContainText(String(future.length));
  await confirmation.getByRole('button', { name: 'Отмена', exact: true }).press('Escape');
  await expect(confirmation).not.toBeVisible();
  await expect(remove).toBeFocused();
  expect((await dataset.headmanActor.call('GET', itemPath)).active).toBe(true);
  expect(
    identities((await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id)) ===
      identities(own),
  ).toBe(true);

  // A helper grant never confers builder authority; another actual HEADMAN is
  // still outside this group. Valid preview revision/key avoid malformed denials.
  const helper = await admit(dataset.request, dataset.assistant, 'STUDENT');
  await dataset.admin.call('PUT', `/api/academic/groups/${dataset.otherGroup.id}/headman`, {
    studentId: dataset.foreignStudent.id,
    expectedHeadmanId: null,
  });
  const foreign = await admit(dataset.request, dataset.foreignStudent, 'HEADMAN');
  for (const actor of [helper, foreign]) {
    expect(
      (
        await dataset.request.post(`${itemPath}/lifecycle-preview?delete=true`, {
          headers: actor.headers(),
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await dataset.request.delete(itemPath, {
          headers: {
            ...actor.headers(),
            'Idempotency-Key': randomUUID(),
            'If-Match': proposed.revision,
          },
        })
      ).status(),
    ).toBe(403);
  }
  expect((await dataset.headmanActor.call('GET', itemPath)).active).toBe(true);
  expect(
    identities((await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id)) ===
      identities(own),
  ).toBe(true);

  const recalculated = http(page, `${itemPath}/lifecycle-preview`, 'POST');
  await remove.click();
  expect((await recalculated).status()).toBe(200);
  const deleted = http(page, itemPath, 'DELETE');
  await confirmation.getByRole('button', { name: 'Удалить пару', exact: true }).click();
  const deletedResponse = await deleted;
  expect(deletedResponse.status()).toBe(204);
  expect(/^[0-9a-f-]{36}$/.test(deletedResponse.request().headers()['idempotency-key'] ?? '')).toBe(
    true,
  );
  await expect(confirmation).not.toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Слот деактивирован.' })).toBeVisible();
  expect((await dataset.headmanActor.call('GET', itemPath)).active).toBe(false);
  const stopped = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(identities(stopped.filter((row) => row.date < dataset.today)) === pastIdentity).toBe(true);
  const stoppedFuture = stopped.filter((row) => row.date >= dataset.today);
  expect(
    stoppedFuture.length === future.length &&
      stoppedFuture.every(
        (row) =>
          row.status === 'CANCELLED' &&
          future.some(
            (old) =>
              old.id === row.id &&
              old.occurrenceId === row.occurrenceId &&
              old.generation === row.generation,
          ),
      ),
  ).toBe(true);
  await expect(slot).toHaveCount(0);
  await page.getByRole('button', { name: 'Добавить пару', exact: true }).click();
  await fillSlot(
    page,
    dataset,
    weeks.map((week) => week.label),
    room,
  );
  const returned = http(page, '/api/schedule/items', 'POST');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  const returnedResponse = await returned;
  expect(returnedResponse.status()).toBe(201);
  const returnedItem = await returnedResponse.json();
  expect(returnedItem.id === item.id && returnedItem.active === true).toBe(true);
  await expect(slot).toBeVisible();
  const resumed = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(resumed.length === own.length).toBe(true);
  expect(identities(resumed.filter((row) => row.date < dataset.today)) === pastIdentity).toBe(true);
  expect(
    resumed
      .filter((row) => row.date >= dataset.today)
      .every((row) => {
        const old = future.find((candidate) => candidate.occurrenceId === row.occurrenceId);
        return (
          old &&
          row.id !== old.id &&
          row.generation === old.generation + 1 &&
          row.status === 'PLANNED' &&
          row.current
        );
      }),
  ).toBe(true);

  // UI parity removal/return uses the server's labels and mapping, with no
  // independent parity/calendar formula or local-PG rule replay.
  await slot.click();
  const otherWeek = page
    .getByRole('group', { name: 'Учебные недели', exact: true })
    .getByRole('button', {
      name: new RegExp(`^${weeks[1]!.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`),
    });
  await otherWeek.click();
  const narrowed = http(page, itemPath, 'PUT');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  expect((await narrowed).status()).toBe(200);
  await expect(slot).toBeVisible();
  expect((await dataset.headmanActor.call('GET', itemPath)).weekType === weeks[0]!.weekType).toBe(
    true,
  );
  const half = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(half.some((row) => row.date >= dataset.today && row.status === 'CANCELLED')).toBe(true);
  expect(half.some((row) => row.date >= dataset.today && row.status === 'PLANNED')).toBe(true);
  await slot.click();
  await otherWeek.click();
  const both = http(page, itemPath, 'PUT');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  expect((await both).status()).toBe(200);
  await expect(slot).toBeVisible();
  expect((await dataset.headmanActor.call('GET', itemPath)).weekType).toBe('ALL');
  const allAgain = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(
    allAgain.length === own.length &&
      identities(allAgain.filter((row) => row.date < dataset.today)) === pastIdentity,
  ).toBe(true);
  expect(
    allAgain
      .filter((row) => row.date >= dataset.today)
      .every((row) => {
        const old = half.find((candidate) => candidate.occurrenceId === row.occurrenceId);
        return (
          old &&
          row.status === 'PLANNED' &&
          row.current &&
          (old.status === 'CANCELLED'
            ? row.id !== old.id && row.generation === old.generation + 1
            : row.id === old.id && row.generation === old.generation)
        );
      }),
  ).toBe(true);
  const retained = await dataset.headmanActor.call('GET', reportPath);
  expect(
    JSON.stringify(
      retained.entries.find((entry: { userId: number }) => entry.userId === dataset.student.id),
    ) === JSON.stringify(savedMark),
  ).toBe(true);

  // Revocation after a real preview cannot turn a retained editor into authority.
  await slot.click();
  const finalPreview = http(page, `${itemPath}/lifecycle-preview`, 'POST');
  await remove.click();
  expect((await finalPreview).status()).toBe(200);
  await expect(confirmation).toBeVisible();
  await dataset.admin.call('PUT', `/api/academic/groups/${dataset.group.id}/headman`, {
    studentId: dataset.student.id,
    expectedHeadmanId: dataset.headman.id,
  });
  const refused = http(page, itemPath, 'DELETE');
  await confirmation.getByRole('button', { name: 'Удалить пару', exact: true }).click();
  expect((await refused).status()).toBe(403);
  await expect(confirmation).not.toBeVisible();
  expect((await dataset.admin.call('GET', itemPath)).active).toBe(true);
  expect(
    identities(
      (await physicalLessons(dataset, dataset.admin)).filter(
        (row) => row.scheduleItemId === item.id,
      ),
    ) === identities(allAgain),
  ).toBe(true);
});

// The ready conditional route is the semester BFF. Today is the existing Vue
// room consumer; a scoped HTTP PATCH is the accepted cache-invalidation trigger.
test('STUDENT warm semester ETag stays scoped and refreshes the same physical room in Today @real-mobile', async ({
  page,
  scenario: dataset,
}) => {
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
