import { randomUUID } from 'node:crypto';
import { type Page, type Request, type Response } from '@playwright/test';
import { test, expect, admit, selectPwaRole } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { assistantPermissionCodes } from '../fixtures/assistant-permissions';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';

// Reuse the private real-mobile fixture and reporter; generated credentials stay in memory.
test.use({ trace: 'off', video: 'off', screenshot: 'off' });
test.afterEach(async ({}, info) => sanitizeTestErrors(info));

function response(page: Page, path: string, method: string) {
  return page.waitForResponse(
    (value) => new URL(value.url()).pathname === path && value.request().method() === method,
  );
}

// BOUNDARY-23 / JS-HEADMAN-42: one current one-off lesson, unchanged assignment and clock.
// Separate PG/Mongo race and replay proofs remain separate from this actual Vue journey.
test('HEADMAN cancels a marked lesson and restores its journal without reviving homework @real-mobile', async ({
  page,
  dataset,
}) => {
  test.setTimeout(240000);
  const actor = dataset.headmanActor;
  const id = dataset.lesson.physicalLessonId;
  const reportPath = `/api/attendance/reports/lesson/${id}`;
  const actionsPath = `${reportPath}/actions?limit=100`;
  const cancelPath = `/api/schedule/lessons/${id}/cancel`;
  const restorePath = `/api/schedule/lessons/${id}/restore`;
  const schedulePath = `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.today}&dateTo=${dataset.today}&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED&size=100`;
  const homeworkListPath = `/api/academic/homeworks?groupId=${dataset.group.id}&semesterId=${dataset.semester.id}&size=100`;
  const datePath = `/api/academic/homeworks/${dataset.homework.id}`;
  const schedule = async () => {
    const body = await actor.call('GET', schedulePath);
    const rows = body._embedded?.lessonResponseList ?? body.content;
    expect(Array.isArray(rows)).toBe(true);
    expect(rows).toHaveLength(1);
    expect(rows[0].id).toBe(id);
    return rows[0];
  };
  const identity = (lesson: Record<string, unknown>) => ({
    id: lesson.id,
    occurrenceId: lesson.occurrenceId,
    generation: lesson.generation,
    current: lesson.current,
    scheduleItemId: lesson.scheduleItemId,
    assignmentId: lesson.assignmentId,
    assignedTeacherId: lesson.assignedTeacherId,
    semesterId: lesson.semesterId,
    groupId: lesson.groupId,
    subjectId: lesson.subjectId,
    date: lesson.date,
    lessonNumber: lesson.lessonNumber,
    lessonType: lesson.lessonType,
    createdAt: lesson.createdAt,
  });
  await expect.poll(async () => (await schedule()).status, { timeout: 75000 }).toBe('ACTIVE');
  const before = await schedule();
  expect(before).toMatchObject({
    status: 'ACTIVE',
    current: true,
    assignmentId: dataset.subject.assignments[0].id,
    assignedTeacherId: dataset.teacher.id,
    date: dataset.today,
    lessonNumber: 1,
  });
  expect(before.occurrenceId).toBeGreaterThan(0);
  expect(before.generation).toBeGreaterThan(0);
  expect(before.occurrenceRevision).toBeGreaterThan(0);

  const payload = {
    groupId: dataset.group.id,
    subjectId: dataset.subject.id,
    semesterId: dataset.semester.id,
    lessonDate: dataset.today,
    lessonNumber: 1,
    bindingMode: 'LESSON',
    lessonType: 'LECTURE',
    requestKey: randomUUID(),
    title: `${dataset.marker}-lesson`,
    description: 'Синтетическое ДЗ для одной пары',
    link: null,
  };
  let bound = await actor.call('POST', '/api/academic/homeworks', payload, [200, 201, 202]);
  await expect
    .poll(
      async () => {
        if (bound.state === 'PENDING' || !bound.id || !bound.bindingId) {
          bound = await actor.call('POST', '/api/academic/homeworks', payload, [200, 201, 202]);
        }
        return bound.state !== 'PENDING' && bound.id != null && bound.bindingId != null;
      },
      { timeout: 60000 },
    )
    .toBe(true);
  const boundPath = `/api/academic/homeworks/${bound.id}`;
  bound = await actor.call('GET', boundPath);
  expect(bound).toMatchObject({
    archived: false,
    bindingMode: 'LESSON',
    lessonDate: dataset.today,
    lessonNumber: 1,
  });
  const dateControl = await actor.call('GET', datePath);
  expect(dateControl).toMatchObject({
    archived: false,
    bindingMode: 'DATE',
    lessonDate: dataset.tomorrow,
  });

  // Use the actual report's CAS state, never a guessed existing revision.
  const initialReport = await actor.call('GET', reportPath);
  const initialEntry = initialReport.entries.find(
    (row: { userId: number }) => row.userId === dataset.student.id,
  );
  expect(initialEntry).toMatchObject({ status: null, markRevision: null, editable: true });
  await actor.call('PUT', `/api/attendance/lessons/${id}/students/${dataset.student.id}`, {
    status: 'PRESENT',
    expectedStatus: 'EMPTY',
    expectedRevision: 'EMPTY',
  });

  await loginPwa(page, dataset.headman);
  await selectPwaRole(page, 'Староста');
  await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
  await expect(
    page.getByRole('button', {
      name: `Был: ${dataset.student.displayName}. Открыть выбор отметки`,
      exact: true,
    }),
  ).toBeVisible();
  // A second real mark carries an excuse and a fixture attachment before cancellation.
  await page
    .getByRole('button', {
      name: `Уважительная причина: ${dataset.headman.displayName}`,
      exact: true,
    })
    .click();
  await page.locator('.headman-journal__excuse-form select').selectOption('ILLNESS');
  await page
    .getByLabel('Комментарий', { exact: true })
    .fill('Синтетическая причина отменяемой пары');
  await page.locator('.headman-journal__excuse-form input[type="file"]').setInputFiles({
    name: 'синтетическая-справка.png',
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
      'base64',
    ),
  });
  const excuseSaved = response(
    page,
    `/api/attendance/lessons/${id}/students/${dataset.headman.id}`,
    'PUT',
  );
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  expect((await excuseSaved).status()).toBe(200);
  const marked = await actor.call('GET', reportPath);
  const present = marked.entries.find(
    (row: { userId: number }) => row.userId === dataset.student.id,
  );
  const excused = marked.entries.find(
    (row: { userId: number }) => row.userId === dataset.headman.id,
  );
  expect(present.status).toBe('present');
  expect(present.markRevision).toEqual(expect.any(String));
  expect(excused).toMatchObject({
    status: 'excused',
    excuseType: 'ILLNESS',
    comment: 'Синтетическая причина отменяемой пары',
  });
  expect(excused.attachmentId).toEqual(expect.any(String));
  expect(excused.markRevision).toEqual(expect.any(String));

  const helper = await admit(dataset.request, dataset.assistant, 'STUDENT');
  expect(
    assistantPermissionCodes(await helper.call('GET', '/api/academic/assistants/me/permissions')),
  ).toEqual(['MARK_ATTENDANCE']);
  const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
  for (const deniedActor of [helper, foreign]) {
    const denied = await dataset.request.patch(cancelPath, {
      headers: deniedActor.headers(),
      data: { reason: 'Синтетический запрещённый запрос' },
    });
    expect(denied.status()).toBe(403);
    expect(await schedule()).toEqual(before);
    expect(await actor.call('GET', reportPath)).toEqual(marked);
    expect(await actor.call('GET', boundPath)).toEqual(bound);
    expect(await actor.call('GET', datePath)).toEqual(dateControl);
  }

  const reason = 'Синтетическая отмена отмеченной пары';
  await page.getByRole('button', { name: 'Управление парой', exact: true }).click();
  await page.getByLabel('Причина отмены', { exact: true }).fill(reason);
  await page.getByRole('button', { name: 'Отменить пару', exact: true }).click();
  const cancelledResponse = response(page, cancelPath, 'PATCH');
  await page
    .getByRole('dialog', { name: 'Отменить пару?', exact: true })
    .getByRole('button', { name: 'Отменить пару', exact: true })
    .click();
  const cancelledHttp = await cancelledResponse;
  expect(cancelledHttp.status()).toBe(200);
  expect((await cancelledHttp.json()).id).toBe(id);
  const cancelled = await schedule();
  expect(identity(cancelled)).toEqual(identity(before));
  expect(cancelled).toMatchObject({
    status: 'CANCELLED',
    cancelReason: reason,
    cancelledBy: dataset.headman.id,
  });
  expect(cancelled.cancelledAt).toEqual(expect.any(String));
  expect(cancelled.revision).toBeGreaterThan(before.revision);
  expect(cancelled.occurrenceRevision).toBeGreaterThan(before.occurrenceRevision);

  const noLiveMarks = async () => {
    const report = await actor.call('GET', reportPath);
    for (const previous of [present, excused]) {
      const row = report.entries.find(
        (entry: { userId: number }) => entry.userId === previous.userId,
      );
      expect(row).toBeDefined();
      // Cancellation retains a tombstone; it never restores PRESENT/EXCUSED or their revision.
      expect(row).toMatchObject({
        status: 'cancelled',
        excuseReason: null,
        excuseType: null,
        comment: null,
        attachmentId: null,
        attachmentName: null,
        attachmentContentType: null,
        attachmentSize: null,
      });
      expect(row.markRevision).not.toBe(previous.markRevision);
    }
    return report;
  };
  await expect(async () => {
    expect(await noLiveMarks()).toMatchObject({ lessonStatus: 'CANCELLED', editable: false });
  }).toPass({ timeout: 60000 });
  const archivedHomework = async () => {
    const archived = await actor.call('GET', `${boundPath}?archived=true`);
    expect(archived).toMatchObject({
      id: bound.id,
      bindingId: bound.bindingId,
      archived: true,
      requestKey: bound.requestKey,
      title: bound.title,
      lessonDate: bound.lessonDate,
      lessonNumber: bound.lessonNumber,
    });
    const active = await actor.call('GET', homeworkListPath);
    const rows = active._embedded?.homeworkResponseList ?? active.content;
    expect(Array.isArray(rows)).toBe(true);
    expect(rows.some((row: { id: number }) => row.id === bound.id)).toBe(false);
    expect(rows.filter((row: { id: number }) => row.id === dateControl.id)).toHaveLength(1);
    expect(await actor.call('GET', datePath)).toEqual(dateControl);
    return archived;
  };
  await expect(async () => {
    await archivedHomework();
  }).toPass({ timeout: 60000 });
  await page.reload();
  await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
  for (const user of [dataset.student, dataset.headman]) {
    const row = page.locator(`.headman-journal__row[data-user-id="${user.id}"]`);
    await expect(row).toHaveAttribute('data-status', 'cancelled');
    await expect(
      row.getByRole('button', {
        name: `Пара отменена: ${user.displayName}. Открыть выбор отметки`,
        exact: true,
      }),
    ).toBeDisabled();
    await expect(
      row.getByRole('button', { name: `Изменить отметку: ${user.displayName}`, exact: true }),
    ).toBeDisabled();
    await expect(row.getByRole('button', { name: 'Открыть файл', exact: true })).toHaveCount(0);
  }

  // JS-STUDENT-15: the existing student reads this same cancelled lesson before restore.
  await logoutPwa(page);
  let ownAttendance: Response | null = null;
  const observeStudentAttendance = (reply: Response) => {
    const url = new URL(reply.url());
    if (
      reply.request().method() === 'GET' &&
      url.pathname === '/api/v1/student/attendance' &&
      url.searchParams.get('semesterId') === String(dataset.semester.id)
    )
      ownAttendance = reply;
  };
  page.on('response', observeStudentAttendance);
  try {
    const admittedStudent = await loginPwa(page, dataset.student);
    expect(admittedStudent.session.userId).toBe(String(dataset.student.id));
    expect(admittedStudent.session.activeRole).toBe('STUDENT');
    await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
    const read = ownAttendance ?? (await response(page, '/api/v1/student/attendance', 'GET'));
    expect(read.status()).toBe(200);
  } finally {
    page.off('response', observeStudentAttendance);
  }
  await page.getByRole('button', { name: 'По дням', exact: true }).click();
  const studentRow = page.locator(`.attendance-lesson-row[data-lesson-id="${id}"]`);
  await expect(studentRow).toHaveAttribute('data-status', 'cancelled');
  const explanation = studentRow.getByRole('button', {
    name: `Статус: отменена · ${reason}`,
    exact: true,
  });
  let studentRequestWrites = 0;
  const countStudentRequestWrite = (request: Request) => {
    if (
      new URL(request.url()).pathname.startsWith('/api/v1/student/requests') &&
      request.method() !== 'GET'
    )
      studentRequestWrites += 1;
  };
  page.on('request', countStudentRequestWrite);
  try {
    await explanation.focus();
    await expect(studentRow.getByRole('tooltip')).toHaveText(reason);
    await explanation.press('Escape');
    await expect(studentRow.getByRole('tooltip')).toHaveCount(0);
    await explanation.hover();
    await expect(studentRow.getByRole('tooltip')).toHaveText(reason);
    await expect(studentRow.locator('.attendance-lesson-row__actions')).toHaveCount(0);
    expect(studentRequestWrites).toBe(0);
  } finally {
    page.off('request', countStudentRequestWrite);
  }
  await logoutPwa(page);
  await loginPwa(page, dataset.headman);
  await selectPwaRole(page, 'Староста');
  await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
  await expect(
    page.locator(`.headman-journal__row[data-user-id="${dataset.student.id}"]`),
  ).toHaveAttribute('data-status', 'cancelled');

  await page.getByRole('button', { name: 'Управление парой', exact: true }).click();
  await page.getByRole('button', { name: 'Восстановить пару', exact: true }).click();
  const restoredResponse = response(page, restorePath, 'PATCH');
  await page
    .getByRole('dialog', { name: 'Восстановить пару?', exact: true })
    .getByRole('button', { name: 'Восстановить пару', exact: true })
    .click();
  const restoredHttp = await restoredResponse;
  expect(restoredHttp.status()).toBe(200);
  const restoredBody = await restoredHttp.json();
  expect(restoredBody.id).toBe(id);
  expect(['PLANNED', 'ACTIVE']).toContain(restoredBody.status);
  const restored = await schedule();
  expect(identity(restored)).toEqual(identity(before));
  // Restore writes PLANNED; the ordinary minute job can already activate today's slot.
  expect(['PLANNED', 'ACTIVE']).toContain(restored.status);
  expect(restored).toMatchObject({ cancelReason: null, cancelledBy: null, cancelledAt: null });
  expect(restored.revision).toBeGreaterThan(cancelled.revision);
  expect(restored.occurrenceRevision).toBeGreaterThan(cancelled.occurrenceRevision);
  await noLiveMarks();
  const archived = await archivedHomework();

  let facts: Record<string, any>[] = [];
  await expect
    .poll(
      async () => {
        const history = await actor.call('GET', actionsPath);
        expect(history.nextCursor).toBeNull();
        facts = history.items.filter((entry: { action: string }) =>
          ['LESSON_CANCELLED', 'LESSON_RESTORED'].includes(entry.action),
        );
        return facts.length;
      },
      { timeout: 60000 },
    )
    .toBe(2);
  const cancellation = facts.find((entry) => entry.action === 'LESSON_CANCELLED')!;
  const restoration = facts.find((entry) => entry.action === 'LESSON_RESTORED')!;
  expect(cancellation).toMatchObject({
    actor: { userId: dataset.headman.id },
    outcome: 'APPLIED',
    before: { status: 'active' },
    after: { status: 'cancelled', cancel_reason: reason },
  });
  expect(restoration).toMatchObject({
    actor: { userId: dataset.headman.id },
    outcome: 'APPLIED',
    before: { status: 'cancelled', cancel_reason: reason },
    after: { status: 'planned', cancel_reason: null },
  });
  expect(cancellation.after.one_off_lesson_id).toBeGreaterThan(0);
  for (const key of [
    'lesson_id',
    'occurrence_id',
    'one_off_lesson_id',
    'assignment_id',
    'assigned_teacher_id',
    'generation',
  ]) {
    expect(restoration.after[key]).toBe(cancellation.before[key]);
    expect(cancellation.after[key]).toBe(cancellation.before[key]);
  }
  expect(new Set(facts.map((entry) => entry.id)).size).toBe(facts.length);
  await page.reload();
  await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
  await expect(
    page.locator(`.headman-journal__row[data-user-id="${dataset.student.id}"]`),
  ).toHaveAttribute('data-status', 'cancelled');
  const reloaded = await schedule();
  expect(identity(reloaded)).toEqual(identity(before));
  expect(['PLANNED', 'ACTIVE']).toContain(reloaded.status);
  expect(reloaded).toMatchObject({ cancelReason: null, cancelledBy: null, cancelledAt: null });
  expect(reloaded.revision).toBeGreaterThanOrEqual(restored.revision);
  expect(reloaded.occurrenceRevision).toBe(restored.occurrenceRevision);
  await noLiveMarks();
  expect(await archivedHomework()).toEqual(archived);
  const readAgain = await actor.call('GET', actionsPath);
  expect(readAgain.nextCursor).toBeNull();
  expect(
    readAgain.items.filter((entry: { action: string }) =>
      ['LESSON_CANCELLED', 'LESSON_RESTORED'].includes(entry.action),
    ),
  ).toEqual(facts);
});
