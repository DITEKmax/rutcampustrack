import type { Request, Response, Route } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { test, expect, admit, selectPwaRole } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';

test.afterEach(async () => sanitizeTestErrors(test.info()));

test('exhausted manual budget still permits a failed-geo AUTO request that the headman approves', async ({
  page,
  context,
  dataset,
  teachingInstant,
}) => {
  test.setTimeout(240000);
  if (!teachingInstant) throw new Error('Dedicated teaching-clock config is required');
  await page.clock.setFixedTime(teachingInstant);
  const student = await admit(dataset.request, dataset.student, 'STUDENT');
  const lessonId = String(dataset.lesson.physicalLessonId);
  const yesterday = new Date(Date.parse(`${dataset.today}T12:00:00Z`) - 86400000)
    .toISOString()
    .slice(0, 10);
  // Fresh STUDENT enrollment is dated from the active semester start (UserService.createUser).
  expect(yesterday >= dataset.semester.dateFrom && yesterday <= dataset.semester.dateTo).toBe(true);
  const assignment = dataset.subject.assignments[0];
  expect(
    assignment.validFrom <= yesterday &&
      (assignment.validUntilExclusive == null || yesterday < assignment.validUntilExclusive),
  ).toBe(true);
  const openStudentRequests = async () => {
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Заявки', exact: true }).click();
  };

  const options = () => student.call('GET', '/api/v1/student/requests/options');
  const today = async () => {
    const snapshot = await student.call('GET', '/api/v1/student/today');
    expect(snapshot.date).toBe(dataset.today);
    expect(Date.parse(snapshot.serverNow)).toBe(teachingInstant.getTime());
    return snapshot.lessons.find(
      (row: { schedule: { id: string } }) => row.schedule.id === lessonId,
    );
  };
  const manualLessons: string[] = [];
  // Ordinary API setup in this dataset's group; real schedulers/events close the past lessons.
  for (let lessonNumber = 1; lessonNumber <= 5; lessonNumber++) {
    const lesson = await dataset.headmanActor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: yesterday,
        lessonNumber,
        classroom: 'Синтетическая аудитория',
      },
      [201, 200],
      { 'Idempotency-Key': randomUUID() },
    );
    manualLessons.push(String(lesson.physicalLessonId));
  }
  // D019: separate own lessons preserve the original five manual-budget lessons.
  const multipleExcuseLessonIds: string[] = [];
  for (const lessonNumber of [6, 7]) {
    const lesson = await dataset.headmanActor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: yesterday,
        lessonNumber,
        classroom: 'Синтетическая аудитория',
      },
      [201, 200],
      { 'Idempotency-Key': randomUUID() },
    );
    multipleExcuseLessonIds.push(String(lesson.physicalLessonId));
  }
  expect(new Set([...manualLessons, ...multipleExcuseLessonIds]).size).toBe(7);
  type PairMark = { status: string | null; source: string | null; markRevision: string | null };
  const readPairMark = async (id: string): Promise<PairMark> => {
    const report = await dataset.headmanActor.call('GET', `/api/attendance/reports/lesson/${id}`);
    const entry = report.entries.find(
      (row: { userId: number }) => Number(row.userId) === dataset.student.id,
    );
    return {
      status: entry?.status ?? null,
      source: entry?.source ?? null,
      markRevision: entry?.markRevision ?? null,
    };
  };
  const multipleBefore = new Map<string, PairMark>();
  // End D019 own lesson setup.
  await expect
    .poll(
      async () => {
        const available = await options();
        const allClosed = manualLessons.every((id) =>
          available.lessons.some(
            (option: { lesson: { id: string; status: string }; lateCheckinEligible: boolean }) =>
              option.lesson.id === id &&
              option.lesson.status === 'closed' &&
              option.lateCheckinEligible,
          ),
        );
        if (!allClosed) return false;
        // D019: reuse this readiness budget for both additional closed lessons.
        for (const id of multipleExcuseLessonIds) {
          const eligible = available.lessons.some(
            (option: {
              lesson: { id: string; status: string };
              excuseEligible: boolean;
              pendingRequests: unknown[];
            }) =>
              option.lesson.id === id &&
              option.lesson.status === 'closed' &&
              option.excuseEligible &&
              option.pendingRequests.length === 0,
          );
          if (!eligible) return false;
          const mark = await readPairMark(id);
          if (
            mark.status !== 'absent' ||
            mark.source !== 'auto_scheduler' ||
            typeof mark.markRevision !== 'string' ||
            !mark.markRevision
          )
            return false;
          multipleBefore.set(id, mark);
        }
        // End D019 readiness.
        const report = await dataset.headmanActor.call(
          'GET',
          `/api/attendance/reports/lesson/${manualLessons[0]}`,
        );
        const entry = report.entries.find(
          (row: { userId: number }) => Number(row.userId) === dataset.student.id,
        );
        if (
          entry?.status !== 'absent' ||
          entry.source !== 'auto_scheduler' ||
          typeof entry.markRevision !== 'string' ||
          !entry.markRevision
        )
          return false;
        const actions = await dataset.headmanActor.call(
          'GET',
          `/api/attendance/reports/lesson/${manualLessons[0]}/actions?limit=50`,
        );
        return (
          actions.items.filter(
            (action: {
              action: string;
              outcome: string;
              student: { userId: number } | null;
              actor: unknown;
              after: { status: string; source: string } | null;
            }) =>
              action.action === 'AUTO_ABSENT' &&
              action.outcome === 'APPLIED' &&
              Number(action.student?.userId) === dataset.student.id &&
              action.actor === null &&
              action.after?.status === 'ABSENT' &&
              action.after.source === 'AUTO_SCHEDULER',
          ).length === 1
        );
      },
      { timeout: 120000, intervals: [1000], message: 'Own closed lessons become request-eligible' },
    )
    .toBe(true);
  expect((await options()).budget).toMatchObject({ limit: 5, used: 0, remaining: 5 });
  for (const id of manualLessons.slice(0, 4)) {
    const request = await student.call(
      'POST',
      '/api/v1/student/requests/late-checkin',
      { lessonId: id },
      [200],
      { 'Idempotency-Key': randomUUID() },
    );
    expect(request.summary.origin).toBe('MANUAL');
    expect(request.summary.status).toBe('PENDING');
  }

  await loginPwa(page, dataset.student);
  await openStudentRequests();

  // Exercise the implemented excuse form without changing the manual/AUTO setup below.
  const excuseLessonId = manualLessons[4];
  const availableBeforeExcuse = await options();
  const expectedReasonCodes = ['ILLNESS', 'UNIVERSITY_ORDER', 'SUMMONS', 'OTHER'];
  const expectedReasonLabels = [
    'Болезнь',
    'По приказу вуза или иному документу',
    'Повестка от военкомата',
    'Другое',
  ];
  expect(availableBeforeExcuse.reasons.map((reason: { code: string }) => reason.code)).toEqual(
    expectedReasonCodes,
  );
  expect(availableBeforeExcuse.reasons.map((reason: { label: string }) => reason.label)).toEqual(
    expectedReasonLabels,
  );
  const otherReason = availableBeforeExcuse.reasons.find(
    (reason: { code: string; label: string; commentRequired: boolean }) => reason.code === 'OTHER',
  );
  expect(Boolean(otherReason?.commentRequired && otherReason.label)).toBe(true);
  const assertExcuseLessonFree = (available: typeof availableBeforeExcuse) => {
    const option = available.lessons.find(
      (row: { lesson: { id: string } }) => row.lesson.id === excuseLessonId,
    );
    expect(
      option?.lesson.status === 'closed' &&
        option.excuseEligible === true &&
        option.lateCheckinEligible === true &&
        option.pendingRequests.length === 0,
    ).toBe(true);
  };
  expect(new Set(manualLessons).size).toBe(5);
  assertExcuseLessonFree(availableBeforeExcuse);
  const openBeforeExcuse = await student.call(
    'GET',
    '/api/v1/student/requests?bucket=OPEN&page=0&size=50',
  );
  const originalOpenIds = openBeforeExcuse.content.map((row: { id: string }) => row.id).sort();
  const assertOpenCount = async (count: number) => {
    await expect(page.getByRole('tab', { name: `Открытые (${count})`, exact: true })).toBeVisible();
  };
  await assertOpenCount(openBeforeExcuse.totalElements);
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
    'base64',
  );
  const file = { name: 'request-form.png', mimeType: 'image/png', buffer: png };
  const excuseForm = page.locator('main.requests-excuse-screen form');
  const fileInput = page.locator('#request-attachments-input');
  const preview = page.getByRole('img', {
    name: 'Предварительный просмотр изображения: request-form.png',
    exact: true,
  });
  const openExcuseForm = async (comment: string) => {
    await page.getByRole('button', { name: 'Подать', exact: true }).click();
    await page.getByRole('button', { name: 'Уважительная причина', exact: true }).click();
    await page.locator(`input[type="checkbox"][value="${excuseLessonId}"]`).check();
    await page.getByRole('button', { name: 'Продолжить', exact: true }).click();
    await page.locator('#request-excuse-reason').click();
    await expect(
      page.getByRole('listbox', { name: 'Причина', exact: true }).getByRole('option'),
    ).toHaveText(expectedReasonLabels);
    await page.getByRole('option', { name: otherReason.label, exact: true }).click();
    await expect(page.getByLabel('Комментарий', { exact: true })).toHaveValue('');
    await expect(
      page.getByRole('button', { name: 'Отправить заявку', exact: true }),
    ).toBeDisabled();
    await page.getByLabel('Комментарий', { exact: true }).fill(comment);
  };
  const submitOwnExcuse = async (comment: string): Promise<string> => {
    const [reply] = await Promise.all([
      page.waitForResponse(
        (item) =>
          new URL(item.url()).pathname === '/api/v1/student/requests/excuse' &&
          item.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Отправить заявку', exact: true }).click(),
    ]);
    expect(reply.status()).toBe(200);
    const detail = await reply.json();
    expect(
      typeof detail.summary.id === 'string' &&
        /^[a-f0-9]{24}$/.test(detail.summary.id) &&
        detail.summary.kind === 'EXCUSE' &&
        detail.summary.status === 'PENDING' &&
        detail.summary.lessons.length === 1 &&
        detail.summary.lessons[0].id === excuseLessonId &&
        detail.reason === 'OTHER' &&
        detail.comment === comment &&
        detail.attachments.length === 1 &&
        detail.attachments[0].name === file.name &&
        detail.attachments[0].state === 'ACTIVE',
    ).toBe(true);
    const card = page
      .getByRole('article')
      .filter({ has: page.locator(`[id="request-title-${detail.summary.id}"]`) });
    await expect(card.getByText('На рассмотрении', { exact: true })).toBeVisible();
    await assertOpenCount(openBeforeExcuse.totalElements + 1);
    return detail.summary.id;
  };

  const cancelledComment = 'Синтетическая причина для отменяемой заявки';
  await openExcuseForm(cancelledComment);
  await fileInput.setInputFiles({
    name: 'unsupported.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('synthetic unsupported format'),
  });
  await expect(excuseForm.getByRole('alert')).toContainText('формат не поддерживается');
  await expect(excuseForm.getByRole('list', { name: 'Выбранные вложения' })).toHaveCount(0);
  await fileInput.setInputFiles(file);
  await expect(excuseForm.getByRole('alert')).toHaveCount(0);
  await expect(preview).toBeVisible();
  expect(
    await preview.evaluate(async (image) => {
      if (!(image instanceof HTMLImageElement)) return false;
      await image.decode();
      return image.naturalWidth === 1 && image.naturalHeight === 1;
    }),
  ).toBe(true);
  await page
    .getByRole('button', { name: 'Удалить вложение request-form.png', exact: true })
    .click();
  await expect(preview).toHaveCount(0);
  await expect(excuseForm.getByRole('list', { name: 'Выбранные вложения' })).toHaveCount(0);
  await fileInput.setInputFiles(file);
  await expect(preview).toBeVisible();
  // One controlled upload transport failure occurs before delivery; keep the genuine success below.
  const uploadOrigin = new URL(page.url()).origin;
  const uploadRoute = (url: URL) =>
    url.origin === uploadOrigin && url.pathname === '/api/v1/student/requests/excuse';
  let uploadAborted = false;
  const abortOwnUpload = async (route: Route) => {
    if (!uploadAborted && route.request().method() === 'POST') {
      uploadAborted = true;
      await route.abort('failed');
    } else {
      await route.fallback();
    }
  };
  await page.route(uploadRoute, abortOwnUpload);
  try {
    const submitUpload = excuseForm.getByRole('button', { name: 'Отправить заявку', exact: true });
    await submitUpload.click();
    await expect(excuseForm.getByRole('alert')).toContainText('Не удалось отправить заявку');
    await expect(excuseForm.getByRole('alert')).toContainText(
      'Не удалось выполнить действие с заявкой.',
    );
    expect(uploadAborted).toBe(true);
    await expect(excuseForm).toBeVisible();
    await expect(submitUpload).toBeDisabled();
    await expect(excuseForm.getByRole('list', { name: 'Выбранные вложения' })).toContainText(
      file.name,
    );
    await expect(preview).toBeVisible();
    const abandonUpload = excuseForm.getByRole('button', {
      name: 'Отказаться от неподтверждённой отправки',
      exact: true,
    });
    await abandonUpload.click();
    await expect(excuseForm.getByRole('alert')).toHaveCount(0);
    await expect(abandonUpload).toHaveCount(0);
    await expect(submitUpload).toBeEnabled();
    await expect(excuseForm.getByRole('list', { name: 'Выбранные вложения' })).toContainText(
      file.name,
    );
    await expect(preview).toBeVisible();
  } finally {
    try {
      await page.unroute(uploadRoute, abortOwnUpload);
    } catch {
      expect.soft(false, 'Owned upload fault route cleanup failed').toBe(true);
    }
  }
  // End controlled transport failure; the original successful submission and its oracles follow.
  const submitting = await excuseForm.evaluateHandle((form) => {
    let seen = false;
    const observer = new MutationObserver(() => {
      if (
        form.getAttribute('aria-busy') === 'true' &&
        form.querySelector('button[type="submit"]')?.textContent?.trim() === 'Отправляем…'
      )
        seen = true;
    });
    observer.observe(form, { attributes: true, attributeFilter: ['aria-busy'] });
    return { read: () => seen, stop: () => observer.disconnect() };
  });
  let cancelledExcuseId: string;
  try {
    cancelledExcuseId = await submitOwnExcuse(cancelledComment);
    expect(await submitting.evaluate((value) => value.read())).toBe(true);
  } finally {
    await submitting
      .evaluate((value) => value.stop())
      .catch(() => {
        expect.soft(false, 'Request form submitting observer was not disconnected').toBe(true);
      });
    await submitting.dispose().catch(() => {});
  }
  const cancelledCard = page
    .getByRole('article')
    .filter({ has: page.locator(`[id="request-title-${cancelledExcuseId}"]`) });
  const [cancelledReply] = await Promise.all([
    page.waitForResponse(
      (item) =>
        new URL(item.url()).pathname === `/api/v1/student/requests/${cancelledExcuseId}/cancel` &&
        item.request().method() === 'POST',
    ),
    cancelledCard.getByRole('button', { name: 'Отменить заявку', exact: true }).click(),
  ]);
  expect(cancelledReply.status()).toBe(200);
  expect((await cancelledReply.json()).summary.status).toBe('CANCELLED');
  await expect(cancelledCard).toHaveCount(0);
  assertExcuseLessonFree(await options());
  await assertOpenCount(openBeforeExcuse.totalElements);
  await page.getByRole('tab', { name: 'Архив', exact: true }).click();
  await expect(cancelledCard.getByText('Отменена', { exact: true })).toBeVisible();
  await assertOpenCount(openBeforeExcuse.totalElements);
  await page
    .getByRole('tab', { name: `Открытые (${openBeforeExcuse.totalElements})`, exact: true })
    .click();

  const rejectedComment = 'Синтетическая причина для отклоняемой заявки';
  await openExcuseForm(rejectedComment);
  await fileInput.setInputFiles(file);
  const rejectedExcuseId = await submitOwnExcuse(rejectedComment);
  const rejectionReason = 'Синтетическое основание отказа';
  const excuseDecisionPath = `/api/attendance/requests/${rejectedExcuseId}`;
  const pendingExcuse = await dataset.headmanActor.call('GET', excuseDecisionPath);
  const rejected = await dataset.headmanActor.call('POST', `${excuseDecisionPath}/decision`, {
    decision: 'REJECTED',
    reason: rejectionReason,
    expectedRevision: pendingExcuse.decisionRevision,
    excludedOccurrenceIds: [],
    requestKey: randomUUID(),
  });
  expect(rejected.summary.status).toBe('REJECTED');
  await page.getByRole('tab', { name: 'Архив', exact: true }).click();
  const rejectedCard = page
    .getByRole('article')
    .filter({ has: page.locator(`[id="request-title-${rejectedExcuseId}"]`) });
  await expect(rejectedCard.getByText('Отклонена', { exact: true })).toBeVisible();
  await expect(rejectedCard).toContainText(rejectionReason);
  await expect(
    rejectedCard.getByRole('button', { name: 'Отменить заявку', exact: true }),
  ).toHaveCount(0);
  await assertOpenCount(openBeforeExcuse.totalElements);
  await page
    .getByRole('tab', { name: `Открытые (${openBeforeExcuse.totalElements})`, exact: true })
    .click();
  assertExcuseLessonFree(await options());
  const openAfterExcuse = await student.call(
    'GET',
    '/api/v1/student/requests?bucket=OPEN&page=0&size=50',
  );
  expect(
    JSON.stringify(openAfterExcuse.content.map((row: { id: string }) => row.id).sort()) ===
      JSON.stringify(originalOpenIds),
  ).toBe(true);

  // D019: the full form submits both selected lessons as one genuine ticket.
  const multipleComment = 'Синтетическая общая причина для двух пар';
  await page.getByRole('button', { name: 'Подать', exact: true }).click();
  await page.getByRole('button', { name: 'Уважительная причина', exact: true }).click();
  // JS-STUDENT-23: inclusive date controls narrow the existing authoritative options.
  await expect(page.locator(`input[type="checkbox"][value="${lessonId}"]`)).toHaveCount(1);
  await page.getByLabel('С даты', { exact: true }).fill(yesterday);
  await page.getByLabel('По дату', { exact: true }).fill(yesterday);
  await expect(page.locator(`input[type="checkbox"][value="${lessonId}"]`)).toHaveCount(0);
  for (const id of multipleExcuseLessonIds)
    await page.locator(`input[type="checkbox"][value="${id}"]`).check();
  await expect(page.locator('input[type="checkbox"]:checked')).toHaveCount(2);
  await page.getByRole('button', { name: 'Продолжить', exact: true }).click();
  await page.locator('#request-excuse-reason').click();
  await page.getByRole('option', { name: otherReason.label, exact: true }).click();
  await page.getByLabel('Комментарий', { exact: true }).fill(multipleComment);
  let multipleSubmissions = 0;
  const countMultipleSubmission = (request: Request) => {
    if (
      new URL(request.url()).pathname === '/api/v1/student/requests/excuse' &&
      request.method() === 'POST'
    )
      multipleSubmissions++;
  };
  page.on('request', countMultipleSubmission);
  const multipleReply = await (async () => {
    try {
      const [reply] = await Promise.all([
        page.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === '/api/v1/student/requests/excuse' &&
            item.request().method() === 'POST',
        ),
        page.getByRole('button', { name: 'Отправить заявку', exact: true }).click(),
      ]);
      return reply;
    } finally {
      page.off('request', countMultipleSubmission);
    }
  })();
  expect(multipleReply.status()).toBe(200);
  expect(multipleSubmissions).toBe(1);
  expect(
    /^multipart\/form-data;\s*boundary=/i.test(
      multipleReply.request().headers()['content-type'] ?? '',
    ),
  ).toBe(true);
  const samePair = (ids: unknown[]) =>
    JSON.stringify(ids.map(String).sort()) === JSON.stringify([...multipleExcuseLessonIds].sort());
  const multipleCreated = await multipleReply.json();
  const multipleExcuseId: string = multipleCreated.summary.id;
  expect(
    /^[a-f0-9]{24}$/.test(multipleExcuseId) &&
      multipleCreated.summary.kind === 'EXCUSE' &&
      multipleCreated.summary.status === 'PENDING' &&
      samePair(multipleCreated.summary.lessons.map((row: { id: string }) => row.id)) &&
      multipleCreated.reason === 'OTHER' &&
      multipleCreated.comment === multipleComment,
  ).toBe(true);
  const multipleCard = page.getByRole('article').filter({
    has: page.locator(`[id="request-title-${multipleExcuseId}"]`),
  });
  await expect(multipleCard).toHaveCount(1);
  await expect(multipleCard.getByText('На рассмотрении', { exact: true })).toBeVisible();
  await expect(multipleCard.locator('.request-card__lessons > li')).toHaveCount(2);
  const multiplePending = await student.call('GET', `/api/v1/student/requests/${multipleExcuseId}`);
  expect(
    multiplePending.summary.id === multipleExcuseId &&
      multiplePending.summary.status === 'PENDING' &&
      samePair(multiplePending.summary.lessons.map((row: { id: string }) => row.id)) &&
      multiplePending.reason === 'OTHER' &&
      multiplePending.comment === multipleComment,
  ).toBe(true);
  const multipleOpen = await student.call(
    'GET',
    '/api/v1/student/requests?bucket=OPEN&page=0&size=50',
  );
  expect(
    multipleOpen.content.filter((row: { id: string }) => row.id === multipleExcuseId).length,
  ).toBe(1);
  for (const id of multipleExcuseLessonIds)
    expect(JSON.stringify(await readPairMark(id)) === JSON.stringify(multipleBefore.get(id))).toBe(
      true,
    );
  expect((await options()).budget).toMatchObject({ limit: 5, used: 4, remaining: 1 });
  await assertOpenCount(openBeforeExcuse.totalElements + 1);
  // End D019 student submission.

  await page.getByRole('button', { name: 'Подать', exact: true }).click();
  await page.getByRole('button', { name: 'Забыл отметиться', exact: true }).click();
  await page.locator(`input[type="checkbox"][value="${manualLessons[4]}"]`).check();
  await page.getByRole('button', { name: 'Продолжить', exact: true }).click();
  await expect(page.getByText('Осталось попыток', { exact: true })).toBeVisible();
  await expect(page.getByText('1 из 5', { exact: true })).toBeVisible();
  const manualSubmitted = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/student/requests/late-checkin' &&
      response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Отправить запрос', exact: true }).click();
  expect((await manualSubmitted).status()).toBe(200);
  const exhausted = (await options()).budget;
  expect(exhausted).toMatchObject({ limit: 5, used: 5, remaining: 0 });
  await page.reload();
  await openStudentRequests();
  await page.getByRole('button', { name: 'Подать', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Забыл отметиться', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Вернуться к заявкам', exact: true }).click();

  await expect
    .poll(
      async () => {
        const lesson = await today();
        return (
          lesson?.schedule.status === 'ACTIVE' &&
          lesson.attendance === null &&
          lesson.request === null &&
          lesson.checkinEligibility.allowed
        );
      },
      {
        timeout: 120000,
        intervals: [1000],
        message: 'Fresh own lesson remains blank and geo-eligible',
      },
    )
    .toBe(true);
  await context.grantPermissions(['geolocation']);
  await context.setGeolocation({ latitude: 0, longitude: 0, accuracy: 1 });
  await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
  const checkinPath = `/api/v1/student/lessons/${lessonId}/checkin`;
  const failedGeo = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === checkinPath && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Отметиться', exact: true }).click();
  const response = await failedGeo;
  expect(response.status()).toBe(200);
  const ack = await response.json();
  expect(ack.outcome).toBe('PENDING_CONFIRMATION');
  expect(ack.attendance).toBeNull();
  expect(ack.request.status).toBe('PENDING');
  expect(ack.request.origin).toBe('AUTO_GEO_FAILURE');
  expect(response.request().postDataJSON().geo.kind).toBe('COORDINATES');
  const requestId: string = ack.request.id;
  expect(typeof requestId === 'string' && /^[a-f0-9]{24}$/.test(requestId)).toBe(true);
  const replay = await page.request.post(checkinPath, {
    headers: {
      ...student.headers(),
      'Idempotency-Key': response.request().headers()['idempotency-key'],
    },
    data: response.request().postDataJSON(),
  });
  expect(replay.status()).toBe(200);
  expect(await replay.json()).toEqual(ack);
  const open = await student.call('GET', '/api/v1/student/requests?bucket=OPEN&page=0&size=50');
  expect(open.content.filter((row: { id: string }) => row.id === requestId).length).toBe(1);
  expect(
    open.content.filter((row: { origin: string }) => row.origin === 'AUTO_GEO_FAILURE').length,
  ).toBe(1);
  const automatic = await student.call('GET', `/api/v1/student/requests/${requestId}`);
  expect(automatic.summary).toMatchObject({
    id: requestId,
    origin: 'AUTO_GEO_FAILURE',
    status: 'PENDING',
  });
  expect((await options()).budget).toEqual(exhausted);
  const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
  const denied = await dataset.request.get(`/api/v1/student/requests/${requestId}`, {
    headers: foreign.headers(),
  });
  expect(denied.status()).toBe(403);
  // D019: the already-admitted foreign student cannot read the aggregate.
  const deniedMultiple = await dataset.request.get(`/api/v1/student/requests/${multipleExcuseId}`, {
    headers: foreign.headers(),
  });
  expect(deniedMultiple.status()).toBe(403);
  await deniedMultiple.dispose();
  // End D019 foreign boundary.
  await openStudentRequests();
  const autoCard = page
    .getByRole('article')
    .filter({ has: page.locator(`[id="request-title-${requestId}"]`) });
  await expect(autoCard.getByText('На рассмотрении', { exact: true })).toBeVisible();
  await logoutPwa(page);

  const headman = await loginPwa(page, dataset.headman);
  if (headman.session.activeRole !== 'HEADMAN') await selectPwaRole(page, 'Староста');
  await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
  const yesterdayDate = new Date(`${yesterday}T12:00:00Z`);
  if (yesterdayDate.getUTCDay() === 0)
    await page.getByRole('button', { name: 'Предыдущая неделя', exact: true }).click();
  await page.getByRole('button', { name: yesterday, exact: true }).click();
  const pastReport = page.waitForResponse(
    (item) =>
      new URL(item.url()).pathname === `/api/attendance/reports/lesson/${manualLessons[0]}` &&
      item.request().method() === 'GET',
  );
  await page.getByRole('button', { name: 'Открыть учёт выбранной пары', exact: true }).click();
  expect((await pastReport).status()).toBe(200);
  await expect(
    page.locator(`.headman-journal__roster-list [data-user-id="${dataset.student.id}"]`),
  ).toHaveAttribute('data-status', 'absent');
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await page.getByRole('button', { name: 'Заявки', exact: true }).click();
  // D019: this existing HEADMAN session approves the whole two-lesson ticket once.
  const multipleDetailPath = `/api/attendance/requests/${multipleExcuseId}`;
  const multipleQueueCard = page.locator(`[data-request-id="${multipleExcuseId}"]`).first();
  const coverageLabel = new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(`${yesterday}T00:00:00Z`));
  // This genuine seed has same-day coverage; the distinct-end branch has a renderer witness.
  await expect(multipleQueueCard).toContainText(coverageLabel);
  await expect(multipleQueueCard).toContainText('Пар: 2');
  const [multipleDetailReply] = await Promise.all([
    page.waitForResponse(
      (item) =>
        new URL(item.url()).pathname === multipleDetailPath && item.request().method() === 'GET',
    ),
    multipleQueueCard.click(),
  ]);
  expect(multipleDetailReply.status()).toBe(200);
  const multipleDetail = await multipleDetailReply.json();
  expect(
    multipleDetail.summary.id === multipleExcuseId &&
      multipleDetail.summary.status === 'PENDING' &&
      typeof multipleDetail.decisionRevision === 'string' &&
      Boolean(multipleDetail.decisionRevision) &&
      samePair(
        multipleDetail.currentAffectedLessons.map((row: { lessonId: number }) => row.lessonId),
      ),
  ).toBe(true);
  const markedSummary = `${multipleDetail.summary.alreadyMarkedCount} из ${multipleDetail.summary.lessonCount} пар уже отмечены`;
  expect(multipleDetail.summary.lessonCount).toBe(2);
  await expect(page.getByRole('status').filter({ hasText: markedSummary })).toBeVisible();
  const affectedRows = page.locator('.headman-requests__lessons > li');
  await expect(affectedRows).toHaveCount(2);
  for (const row of await affectedRows.all())
    await expect(row).toHaveAttribute('data-excluded', 'false');
  await page.getByRole('button', { name: 'Одобрить', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: markedSummary })).toBeVisible();
  let multipleDecisions = 0;
  const countMultipleDecision = (request: Request) => {
    if (
      new URL(request.url()).pathname === `${multipleDetailPath}/decision` &&
      request.method() === 'POST'
    )
      multipleDecisions++;
  };
  page.on('request', countMultipleDecision);
  const multipleDecisionReply = await (async () => {
    try {
      const [reply] = await Promise.all([
        page.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === `${multipleDetailPath}/decision` &&
            item.request().method() === 'POST',
        ),
        page.getByRole('button', { name: 'Одобрить 2 пары', exact: true }).click(),
      ]);
      return reply;
    } finally {
      page.off('request', countMultipleDecision);
    }
  })();
  expect(multipleDecisionReply.status()).toBe(200);
  expect(multipleDecisions).toBe(1);
  const multipleDecisionCommand = multipleDecisionReply.request().postDataJSON();
  expect(
    multipleDecisionCommand.decision === 'APPROVED' &&
      multipleDecisionCommand.expectedRevision === multipleDetail.decisionRevision &&
      multipleDecisionCommand.excludedOccurrenceIds.length === 0 &&
      typeof multipleDecisionCommand.requestKey === 'string' &&
      Boolean(multipleDecisionCommand.requestKey),
  ).toBe(true);
  const multipleApproved = await multipleDecisionReply.json();
  expect(
    multipleApproved.summary.id === multipleExcuseId &&
      multipleApproved.summary.status === 'APPROVED' &&
      multipleApproved.decisionSelection.expectedRevision === multipleDetail.decisionRevision &&
      multipleApproved.decisionSelection.requestKey === multipleDecisionCommand.requestKey &&
      multipleApproved.decisionSelection.excludedLessons.length === 0 &&
      samePair(
        multipleApproved.decisionSelection.includedLessons.map(
          (row: { lessonId: number }) => row.lessonId,
        ),
      ),
  ).toBe(true);
  await expect(page.getByText('Одобрена', { exact: true })).toBeVisible();
  const multipleDurable = await student.call('GET', `/api/v1/student/requests/${multipleExcuseId}`);
  expect(
    multipleDurable.summary.id === multipleExcuseId &&
      multipleDurable.summary.status === 'APPROVED' &&
      samePair(
        multipleDurable.decisionSelection.includedLessons.map((row: { id: string }) => row.id),
      ) &&
      multipleDurable.decisionSelection.excludedLessons.length === 0,
  ).toBe(true);
  for (const id of multipleExcuseLessonIds) {
    const mark = await readPairMark(id);
    expect(
      mark.status === 'excused' &&
        mark.source === 'headman_excuse' &&
        typeof mark.markRevision === 'string' &&
        Boolean(mark.markRevision) &&
        mark.markRevision !== multipleBefore.get(id)!.markRevision,
    ).toBe(true);
    const history = await dataset.headmanActor.call(
      'GET',
      `/api/attendance/reports/lesson/${id}/actions?limit=50`,
    );
    type TicketAction = {
      action: string;
      outcome: string;
      actor: { userId: number; role: string } | null;
      student: { userId: number } | null;
      ticket: { kind: string; id: string; status: string } | null;
      before: { status: string } | null;
      after: { status: string; source?: string } | null;
    };
    const ticketActions = (history.items as TicketAction[]).filter(
      (event) =>
        event.outcome === 'APPLIED' &&
        event.actor?.userId === dataset.headman.id &&
        event.actor.role === 'HEADMAN' &&
        event.student?.userId === dataset.student.id &&
        event.ticket?.kind === 'EXCUSE' &&
        event.ticket.id === multipleExcuseId &&
        event.ticket.status === 'APPROVED',
    );
    expect(
      ticketActions.filter(
        (event) =>
          event.action === 'REQUEST_DECIDED' &&
          event.before?.status === 'SUBMITTED' &&
          event.after?.status === 'APPROVED',
      ).length,
    ).toBe(1);
    expect(
      ticketActions.filter(
        (event) =>
          event.action === 'ATTENDANCE_SET' &&
          event.before?.status === 'ABSENT' &&
          event.after?.status === 'EXCUSED' &&
          event.after.source === 'HEADMAN_EXCUSE',
      ).length,
    ).toBe(1);
  }
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  // End D019 whole-ticket decision.
  await page.locator(`[data-request-id="${requestId}"]`).click();
  await expect(
    page.getByRole('main').getByText(dataset.student.displayName, { exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Одобрить', exact: true }).click();
  const decisionPath = `/api/attendance/requests/${requestId}/decision`;
  const decided = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === decisionPath && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Одобрить 1 пары', exact: true }).click();
  const decision = await decided;
  expect(decision.status()).toBe(200);
  expect((await decision.json()).summary.status).toBe('APPROVED');
  await expect(page.getByText('Одобрена', { exact: true })).toBeVisible();
  const approved = await student.call('GET', `/api/v1/student/requests/${requestId}`);
  expect(approved.summary.status).toBe('APPROVED');
  expect((await today()).attendance).toMatchObject({ status: 'PRESENT', source: 'LATE_CHECKIN' });
  expect((await options()).budget).toEqual(exhausted);
  await page.getByRole('button', { name: 'Назад', exact: true }).click();

  // WEB07: the AUTO ticket belongs to today's lesson, not yesterday's manual ABSENT row.
  await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
  if (yesterdayDate.getUTCDay() === 0)
    await page.getByRole('button', { name: 'Следующая неделя', exact: true }).click();
  await page.getByRole('button', { name: dataset.today, exact: true }).click();
  const approvedJournalRead = page.waitForResponse(
    (reply) =>
      reply.request().method() === 'GET' &&
      new URL(reply.url()).pathname === `/api/attendance/reports/lesson/${lessonId}`,
  );
  await page.getByRole('button', { name: 'Открыть учёт выбранной пары', exact: true }).click();
  const approvedJournal = await approvedJournalRead;
  expect(approvedJournal.status()).toBe(200);
  expect(await approvedJournal.json()).toMatchObject({
    lessonId: Number(lessonId),
    groupId: dataset.group.id,
    lessonDate: dataset.today,
  });
  await expect(
    page.locator(`.headman-journal__roster-list [data-user-id="${dataset.student.id}"]`),
  ).toHaveAttribute('data-status', 'present');
  expect(new URL(page.url()).pathname).toBe('/app/');
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await page.getByRole('button', { name: 'Заявки', exact: true }).click();
  // End same-session journal witness; restore the original requests queue.

  // One accepted HEADMAN archive filter uses these original own tickets and coverage date.
  await page.getByRole('button', { name: 'Архив', exact: true }).click();
  const archiveMain = page.locator('main.headman-requests[data-request-view="archive"]');
  await expect(archiveMain).toBeVisible();
  await page.getByRole('button', { name: 'Поиск и период', exact: true }).click();
  await page.getByLabel('Тип заявки', { exact: true }).selectOption('EXCUSE');
  await page.getByLabel('ФИО', { exact: true }).fill(dataset.student.displayName);
  await page.getByLabel('Период от', { exact: true }).fill(yesterday);
  await page.getByLabel('Период до', { exact: true }).fill(yesterday);
  const [filteredReply] = await Promise.all([
    page.waitForResponse((reply) => {
      const url = new URL(reply.url());
      return (
        reply.request().method() === 'GET' &&
        url.pathname === '/api/attendance/requests' &&
        url.searchParams.get('bucket') === 'ARCHIVE' &&
        url.searchParams.get('type') === 'EXCUSE' &&
        url.searchParams.get('studentName') === dataset.student.displayName &&
        url.searchParams.get('coverageDateFrom') === yesterday &&
        url.searchParams.get('coverageDateTo') === yesterday &&
        url.searchParams.get('page') === '0' &&
        url.searchParams.get('size') === '20'
      );
    }),
    page.getByRole('button', { name: 'Применить', exact: true }).click(),
  ]);
  expect(filteredReply.status()).toBe(200);
  const filtered = await filteredReply.json();
  const expectedArchiveIds = [cancelledExcuseId, rejectedExcuseId, multipleExcuseId].sort();
  expect(
    filtered.page === 0 &&
      filtered.totalElements === expectedArchiveIds.length &&
      filtered.content.length === expectedArchiveIds.length &&
      JSON.stringify(filtered.content.map((row: { id: string }) => row.id).sort()) ===
        JSON.stringify(expectedArchiveIds) &&
      filtered.content.every(
        (row: { studentId: number; kind: string; coverageStart: string; coverageEnd: string }) =>
          row.studentId === dataset.student.id &&
          row.kind === 'EXCUSE' &&
          row.coverageStart <= yesterday &&
          row.coverageEnd >= yesterday,
      ),
  ).toBe(true);
  await expect(archiveMain.locator('[data-request-id]')).toHaveCount(expectedArchiveIds.length);
  expect(
    JSON.stringify(
      await archiveMain
        .locator('[data-request-id]')
        .evaluateAll((rows) => rows.map((row) => row.getAttribute('data-request-id')).sort()),
    ) === JSON.stringify(expectedArchiveIds),
  ).toBe(true);
  // The same archive scope contains EXCUSE tickets, so a different type must exclude them.
  await page.getByRole('button', { name: 'Поиск и период', exact: true }).click();
  await page.getByLabel('Тип заявки', { exact: true }).selectOption('LATE_CHECKIN');
  const [lateArchiveReply] = await Promise.all([
    page.waitForResponse((reply) => {
      const url = new URL(reply.url());
      return (
        reply.request().method() === 'GET' &&
        url.pathname === '/api/attendance/requests' &&
        url.searchParams.get('bucket') === 'ARCHIVE' &&
        url.searchParams.get('type') === 'LATE_CHECKIN' &&
        url.searchParams.get('studentName') === dataset.student.displayName &&
        url.searchParams.get('coverageDateFrom') === yesterday &&
        url.searchParams.get('coverageDateTo') === yesterday &&
        url.searchParams.get('page') === '0' &&
        url.searchParams.get('size') === '20'
      );
    }),
    page.getByRole('button', { name: 'Применить', exact: true }).click(),
  ]);
  expect(lateArchiveReply.status()).toBe(200);
  const lateArchive = await lateArchiveReply.json();
  expect(lateArchive.page).toBe(0);
  expect(lateArchive.totalElements).toBe(0);
  expect(lateArchive.content).toEqual([]);
  await expect(archiveMain.locator('[data-request-id]')).toHaveCount(0);
  await expect(archiveMain.getByText('В архиве пока нет заявок.', { exact: true })).toBeVisible();
  // Restore the original queue context before its unchanged logout and student archive assertions.
  await page.getByRole('button', { name: 'Поиск и период', exact: true }).click();
  await page.getByLabel('Тип заявки', { exact: true }).selectOption('');
  await page.getByLabel('ФИО', { exact: true }).fill('');
  await page.getByLabel('Период от', { exact: true }).fill('');
  await page.getByLabel('Период до', { exact: true }).fill('');
  await page.getByRole('button', { name: 'Применить', exact: true }).click();
  await page.getByRole('button', { name: /^Все(?: \d+)?$/ }).click();
  // End one actual archive filter witness.

  // HEADMAN33: this same student's canonical rank and subject/type counts reach the real drilldown.
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  const statsEntry = page
    .locator('.headman-more')
    .getByRole('button', { name: 'Статистика', exact: true });
  await expect(statsEntry).toBeVisible();
  await expect(statsEntry).toBeEnabled();
  const [statsReply] = await Promise.all([
    page.waitForResponse(
      (reply) =>
        reply.request().method() === 'POST' &&
        new URL(reply.url()).pathname === '/api/attendance/reports/headman/stats/query',
    ),
    statsEntry.click(),
  ]);
  expect(statsReply.status()).toBe(200);
  const stats = await statsReply.json();
  expect(
    stats.context.groupId === dataset.group.id && stats.context.semesterId === dataset.semester.id,
  ).toBe(true);
  const statsStudent = stats.rows.find(
    (row: { studentId: number }) => row.studentId === dataset.student.id,
  );
  expect(statsStudent !== undefined).toBe(true);
  expect(statsStudent?.displayName === dataset.student.displayName).toBe(true);
  const detailPath = `/api/attendance/reports/headman/stats/students/${dataset.student.id}/detail`;
  const [detailReply] = await Promise.all([
    page.waitForResponse((reply) => {
      const url = new URL(reply.url());
      return (
        reply.request().method() === 'GET' &&
        url.pathname === detailPath &&
        url.searchParams.get('semesterId') === String(dataset.semester.id) &&
        url.searchParams.get('latePage') === '0' &&
        url.searchParams.get('excusePage') === '0' &&
        url.searchParams.get('size') === '20'
      );
    }),
    page
      .getByRole('region', { name: 'Студенты группы', exact: true })
      .getByRole('button', {
        name: new RegExp('^' + statsStudent.displayName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
      })
      .click(),
  ]);
  expect(detailReply.status()).toBe(200);
  const detail = await detailReply.json();
  expect(
    detail.student.id === dataset.student.id &&
      detail.context.groupId === dataset.group.id &&
      detail.context.semesterId === dataset.semester.id,
  ).toBe(true);
  const ownStats = await student.call(
    'GET',
    `/api/v1/student/statistics?semesterId=${dataset.semester.id}`,
  );
  expect(
    detail.rank?.visibility === 'VISIBLE' &&
      detail.rank.available === true &&
      detail.rank.position === ownStats.ownRank.position &&
      detail.rank.participantCount === ownStats.ownRank.participantCount &&
      ownStats.ownRank.available === true,
  ).toBe(true);
  await expect(page.getByRole('region', { name: 'Рейтинг студента', exact: true })).toContainText(
    `${detail.rank.position} из ${detail.rank.participantCount}`,
  );
  await expect(page.locator('.headman-stats-mobile__personal > div > small')).toHaveText(
    [detail.metrics.present, detail.metrics.presentOrExcused].map(
      (metric: { numerator: number; denominator: number }) =>
        `${metric.numerator} из ${metric.denominator}`,
    ),
  );
  const detailSubject = detail.subjects.find(
    (item: { subjectId: number }) => item.subjectId === dataset.subject.id,
  );
  expect(detailSubject !== undefined && detailSubject.metrics.present.denominator > 0).toBe(true);
  const subjectCard = page
    .getByRole('region', { name: 'Посещаемость по предметам и типам занятий', exact: true })
    .locator(`details[data-subject-id="${dataset.subject.id}"]`);
  await subjectCard.locator('summary').click();
  await expect(subjectCard.locator('summary')).toHaveText(detailSubject.subjectName);
  const lecture = detailSubject.lessonTypes.find(
    (type: { code: string }) => type.code === 'lecture',
  );
  expect(lecture !== undefined && lecture.metrics.present.denominator > 0).toBe(true);
  for (const [scope, metrics] of [
    [subjectCard.locator(':scope > dl'), detailSubject.metrics],
    [subjectCard.locator('[data-lesson-type="lecture"] > dl'), lecture.metrics],
  ] as const) {
    const counts = await scope.locator('dd').allTextContents();
    expect(counts.map((value) => value.trim().split(' · ')[0])).toEqual(
      ['present', 'presentOrExcused', 'excused', 'absent'].map(
        (kind) => `${metrics[kind].numerator} из ${metrics[kind].denominator}`,
      ),
    );
  }
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Студенты группы', exact: true })).toBeVisible();

  // WEB08: choose the existing subject and explicitly reselect its actual lesson type.
  const statsSubject = stats.subjects.find(
    (item: { id: number }) => item.id === dataset.subject.id,
  );
  expect(statsSubject !== undefined).toBe(true);
  const matchesSubjectStats = (reply: Response) => {
    if (
      reply.request().method() !== 'POST' ||
      new URL(reply.url()).pathname !== '/api/attendance/reports/headman/stats/query'
    )
      return false;
    const query = reply.request().postDataJSON();
    return (
      query.semesterId === dataset.semester.id &&
      query.subjectId === dataset.subject.id &&
      JSON.stringify(query.lessonTypes) === JSON.stringify(['lecture'])
    );
  };
  await page.getByRole('button', { name: 'По предметам', exact: true }).click();
  const [subjectReply] = await Promise.all([
    page.waitForResponse(matchesSubjectStats),
    page
      .getByRole('region', { name: 'Предметы группы', exact: true })
      .getByRole('button', {
        name: new RegExp('^' + statsSubject.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
      })
      .click(),
  ]);
  expect(subjectReply.status()).toBe(200);
  const subjectTable = page.getByRole('table', {
    name: 'Посещаемость студентов по выбранному предмету, проценты',
    exact: true,
  });
  await expect(subjectTable).toBeVisible();
  const advertisedLecture = statsSubject.lessonTypes.find(
    (type: { code: string }) => type.code === 'lecture',
  );
  expect(
    typeof advertisedLecture?.label === 'string' && advertisedLecture.label.trim().length > 0,
  ).toBe(true);
  const lectureType = page
    .getByRole('group', { name: 'Типы занятий', exact: true })
    .getByRole('button', {
      name: advertisedLecture.label,
      exact: true,
    });
  await expect(lectureType).toHaveAttribute('aria-pressed', 'true');
  await lectureType.click();
  await expect(page.getByText('Выбери хотя бы один тип занятия.', { exact: true })).toBeVisible();
  const [filteredStatsReply] = await Promise.all([
    page.waitForResponse(matchesSubjectStats),
    lectureType.click(),
  ]);
  expect(filteredStatsReply.status()).toBe(200);
  const filteredStats = await filteredStatsReply.json();
  expect(filteredStats.context).toMatchObject({
    groupId: dataset.group.id,
    semesterId: dataset.semester.id,
    subjectId: dataset.subject.id,
    lessonTypes: ['lecture'],
  });
  const filteredStudent = filteredStats.rows.find(
    (row: { studentId: number }) => row.studentId === dataset.student.id,
  );
  expect(
    filteredStudent?.displayName === dataset.student.displayName &&
      filteredStudent.metrics.present.denominator > 0,
  ).toBe(true);
  await expect(page.getByRole('heading', { name: statsSubject.label, exact: true })).toBeVisible();
  const filteredRow = subjectTable.getByRole('row').filter({
    has: page.getByRole('button', { name: dataset.student.displayName, exact: true }),
  });
  await expect(filteredRow.getByRole('cell')).toHaveText([
    ...['present', 'excused', 'absent'].map(
      (kind) => `${Math.round(filteredStudent.metrics[kind].percent)}%`,
    ),
    String(filteredStudent.lateCheckin.submitted),
  ]);

  // JS-HEADMAN-30/19: the advertised column filter changes the real slice before export.
  const nameColumn = filteredStats.columns.find(
    (column: { field: string; filterKind: string }) =>
      column.field === 'displayName' && column.filterKind === 'TEXT',
  );
  expect(typeof nameColumn?.label === 'string' && nameColumn.label.trim().length > 0).toBe(true);
  const applyStatsName = async (contains: string) => {
    await page.getByRole('button', { name: 'Фильтры', exact: true }).click();
    const sheet = page.getByRole('dialog', { name: 'Фильтры статистики', exact: true });
    await sheet.getByLabel(`Фильтр: ${nameColumn.label}`, { exact: true }).fill(contains);
    const [reply] = await Promise.all([
      page.waitForResponse(matchesSubjectStats),
      sheet.getByRole('button', { name: 'Применить', exact: true }).click(),
    ]);
    expect(reply.status()).toBe(200);
    expect(reply.request().postDataJSON().filters).toEqual(
      contains ? [{ field: nameColumn.field, contains }] : [],
    );
    return reply.json();
  };
  const noMemberName = randomUUID();
  expect(
    filteredStats.rows.every(
      (row: { displayName: string }) => !row.displayName.includes(noMemberName),
    ),
  ).toBe(true);
  const noStats = await applyStatsName(noMemberName);
  expect(noStats.emptyState).toBe('FILTERED_EMPTY');
  expect(noStats.totalElements).toBe(0);
  expect(noStats.rows).toEqual([]);
  await expect(subjectTable.locator('tbody > tr')).toHaveCount(0);
  const namedStats = await applyStatsName(dataset.student.displayName);
  expect(namedStats.emptyState).toBe('NONE');
  expect(
    namedStats.rows.length > 0 &&
      namedStats.totalElements === namedStats.rows.length &&
      namedStats.rows.some((row: { studentId: number }) => row.studentId === dataset.student.id) &&
      namedStats.rows.every((row: { displayName: string }) =>
        row.displayName.includes(dataset.student.displayName),
      ),
  ).toBe(true);
  await expect(subjectTable.locator('tbody > tr > th')).toHaveText(
    namedStats.rows.map((row: { displayName: string }) => row.displayName),
  );

  // Auth tickets use real time; restore the unchanged educational browser clock even on failure.
  await page.clock.setSystemTime(new Date());
  try {
    await page.getByRole('button', { name: 'Выгрузка', exact: true }).click();
    const exportSheet = page.getByRole('dialog', { name: 'Выгрузка', exact: true });
    const htmlFormat = namedStats.formats.find(
      (format: { code: string }) => format.code === 'html',
    );
    expect(typeof htmlFormat?.label === 'string' && htmlFormat.label.length > 0).toBe(true);
    await exportSheet.getByRole('button', { name: 'Формат выгрузки', exact: true }).click();
    await exportSheet.getByRole('option', { name: htmlFormat.label, exact: true }).click();
    const created = page.waitForResponse(
      (reply) =>
        reply.request().method() === 'POST' &&
        new URL(reply.url()).pathname === '/api/auth/export-jobs',
    );
    const issued = page.waitForResponse(
      (reply) =>
        reply.request().method() === 'POST' &&
        /^\/api\/auth\/export-jobs\/[0-9a-f-]+\/ticket$/.test(new URL(reply.url()).pathname),
    );
    const saved = page.waitForEvent('download');
    await exportSheet.getByRole('button', { name: 'Скачать статистику', exact: true }).click();
    const job = await created;
    expect(job.status()).toBe(202);
    const selector = job.request().postDataJSON().report;
    expect(
      selector.kind === 'HEADMAN_STATS' &&
        selector.headmanStats.semesterId === dataset.semester.id &&
        selector.headmanStats.subjectId === dataset.subject.id &&
        JSON.stringify(selector.headmanStats.lessonTypes) === JSON.stringify(['lecture']) &&
        JSON.stringify(selector.headmanStats.filters) ===
          JSON.stringify([{ field: nameColumn.field, contains: dataset.student.displayName }]) &&
        JSON.stringify(selector.headmanStats.sorts) ===
          JSON.stringify(filteredStatsReply.request().postDataJSON().sorts) &&
        selector.headmanStats.format === 'html' &&
        !Object.hasOwn(selector.headmanStats, 'page') &&
        !Object.hasOwn(selector.headmanStats, 'size'),
      'The actual button exports the complete current subject/type/name-filtered slice',
    ).toBe(true);
    const jobId = (await job.json()).jobId;
    const ticket = await issued;
    expect(ticket.status()).toBe(200);
    expect(new URL(ticket.url()).pathname === `/api/auth/export-jobs/${jobId}/ticket`).toBe(true);
    const download = await saved;
    expect(await download.failure()).toBeNull();
    expect(download.suggestedFilename().endsWith('.html')).toBe(true);
    const stream = await download.createReadStream();
    expect(stream !== null).toBe(true);
    const chunks: Buffer[] = [];
    let size = 0;
    for await (const part of stream!) {
      const bytes = Buffer.from(part);
      size += bytes.length;
      expect(size <= 20 * 1024 * 1024).toBe(true);
      chunks.push(bytes);
    }
    expect(size > 0).toBe(true);
    const html = Buffer.concat(chunks).toString('utf8');
    const exported = await page.evaluate((markup) => {
      const document = new DOMParser().parseFromString(markup, 'text/html');
      const context = Object.fromEntries(
        Array.from(document.querySelectorAll('body > dl > dt'), (term) => [
          term.textContent,
          term.nextElementSibling?.textContent,
        ]),
      );
      const table = document.querySelectorAll('table')[1];
      return {
        context,
        rows: Array.from(table?.querySelectorAll('tbody > tr') ?? [], (row) =>
          Array.from(row.querySelectorAll('th,td'), (cell) => cell.textContent),
        ),
      };
    }, html);
    expect(
      exported.context['Группа'] === dataset.group.name &&
        exported.context['Разрез'] === statsSubject.label &&
        exported.context['Типы занятий'] === 'lecture' &&
        exported.context['Студентов после фильтра'] === String(namedStats.totalElements) &&
        exported.context['Фильтры']?.includes(dataset.student.displayName),
      'Downloaded HTML retains the exact own filtered report context',
    ).toBe(true);
    const expectedRows = namedStats.rows.map(
      (row: {
        displayName: string;
        metrics: Record<
          string,
          {
            numerator: number;
            denominator: number;
            percent: number;
          }
        >;
      }) => [
        row.displayName,
        ...['present', 'presentOrExcused', 'excused', 'absent'].map((kind) => {
          const metric = row.metrics[kind];
          return metric.denominator === 0
            ? '— (0/0)'
            : `${metric.numerator} / ${metric.denominator} / ${metric.percent.toFixed(1)}%`;
        }),
      ],
    );
    expect(JSON.stringify(exported.rows) === JSON.stringify(expectedRows)).toBe(true);
    expect(
      !html.includes(dataset.student.password) && !html.includes(dataset.foreignStudent.login),
    ).toBe(true);
    await exportSheet.getByRole('button', { name: 'Закрыть настройки', exact: true }).click();
  } finally {
    await page.clock.setFixedTime(teachingInstant);
  }

  await applyStatsName('');
  await expect(subjectTable.locator('tbody > tr')).toHaveCount(filteredStats.rows.length);

  // JS-HEADMAN-31: a real period/scope switch renders both authoritative series anew.
  const trendPath = '/api/attendance/reports/headman/stats/trend';
  const isTrend = (reply: Response, mode: 'SEMESTER' | 'SUBJECT') =>
    reply.request().method() === 'POST' &&
    new URL(reply.url()).pathname === trendPath &&
    reply.request().postDataJSON().mode === mode;
  const [semesterTrendReply] = await Promise.all([
    page.waitForResponse((reply) => isTrend(reply, 'SEMESTER')),
    page.getByRole('button', { name: 'Динамика', exact: true }).click(),
  ]);
  expect(semesterTrendReply.status()).toBe(200);
  expect(semesterTrendReply.request().postDataJSON().mode).toBe('SEMESTER');
  expect((await semesterTrendReply.json()).mode).toBe('SEMESTER');
  const dynamics = page.getByRole('region', { name: 'Динамика посещаемости', exact: true });
  const [subjectTrendReply] = await Promise.all([
    page.waitForResponse((reply) => isTrend(reply, 'SUBJECT')),
    dynamics
      .getByRole('navigation', { name: 'Период динамики', exact: true })
      .getByRole('button', { name: 'Предмет', exact: true })
      .click(),
  ]);
  expect(subjectTrendReply.status()).toBe(200);
  expect(subjectTrendReply.request().postDataJSON()).toMatchObject({
    mode: 'SUBJECT',
    semesterId: dataset.semester.id,
    subjectId: dataset.subject.id,
  });
  const subjectTrend = await subjectTrendReply.json();
  expect(
    subjectTrend.mode === 'SUBJECT' &&
      subjectTrend.context.groupId === dataset.group.id &&
      subjectTrend.context.semesterId === dataset.semester.id &&
      subjectTrend.context.subjectId === dataset.subject.id &&
      subjectTrend.emptyState === 'NONE',
  ).toBe(true);
  type TrendMetric = {
    percent: number | null;
    numerator: number;
    denominator: number;
  };
  type TrendPoint = {
    label: string;
    present: TrendMetric;
    presentOrExcused: TrendMetric;
  };
  const trendMetricText = (metric: TrendMetric) =>
    metric.percent === null
      ? 'Нет данных'
      : `${metric.percent.toFixed(1)}% (${metric.numerator} из ${metric.denominator})`;
  for (const [metric, suffix, label] of [
    ['present', 'present', 'Присутствовал'],
    ['presentOrExcused', 'excused', 'Присутствовал или отсутствовал по уважительной причине'],
  ] as const) {
    const points: TrendPoint[] = subjectTrend.points.filter(
      (point: TrendPoint) => point[metric].percent !== null,
    );
    expect(points.length > 0).toBe(true);
    const circles = dynamics.locator(`circle.headman-stats-trend__point--${suffix}`);
    await expect(circles).toHaveCount(points.length);
    expect(
      await circles.evaluateAll((nodes) => nodes.map((node) => node.getAttribute('aria-label'))),
    ).toEqual(points.map((point) => `${point.label}, ${label}: ${trendMetricText(point[metric])}`));
  }
  const firstPoint = subjectTrend.points.find(
    (point: { present: { percent: number | null } }) => point.present.percent !== null,
  );
  await dynamics.locator('circle.headman-stats-trend__point--present').first().focus();
  const readout = dynamics.locator('.headman-stats-trend__readout');
  await expect(readout).toContainText(`«+» ${trendMetricText(firstPoint.present)}`);
  await expect(readout).toContainText(`«+ и у» ${trendMetricText(firstPoint.presentOrExcused)}`);

  // The accepted table repeats every point from this same authoritative response.
  const trendData = dynamics.locator('details.headman-stats-trend__data');
  const summary = trendData.locator('summary');
  await expect(summary).toHaveText('Данные графика');
  await expect(trendData).toHaveJSProperty('open', false);
  await summary.focus();
  await page.keyboard.press('Enter');
  const tableRegion = dynamics.getByRole('region', {
    name: 'Табличные данные динамики',
    exact: true,
  });
  await expect(tableRegion).toBeVisible();
  await expect(tableRegion).toHaveAttribute('tabindex', '0');
  const trendTable = tableRegion.getByRole('table', {
    name: 'Динамика посещаемости по периодам',
    exact: true,
  });
  await expect(trendTable.getByRole('columnheader')).toHaveText([
    'Период',
    'Присутствовал',
    'Присутствовал или отсутствовал по уважительной причине',
  ]);
  const tablePoints: (TrendPoint & { from: string; to: string })[] = subjectTrend.points;
  const tableRows = trendTable.locator('tbody > tr');
  await expect(tableRows).toHaveCount(tablePoints.length);
  const periodDate = (value: string) =>
    new Date(`${value}T12:00:00Z`).toLocaleDateString('ru-RU', {
      day: 'numeric',
      month: 'short',
      timeZone: 'UTC',
    });
  for (const [index, point] of tablePoints.entries()) {
    const row = tableRows.nth(index);
    const period =
      point.from === point.to
        ? periodDate(point.from)
        : `${periodDate(point.from)} — ${periodDate(point.to)}`;
    expect((await row.getByRole('rowheader').textContent())?.replace(/\s+/g, '')).toBe(
      `${point.label}${period}`.replace(/\s+/g, ''),
    );
    await expect(row.getByRole('cell')).toHaveText([
      trendMetricText(point.present),
      trendMetricText(point.presentOrExcused),
    ]);
  }
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await expect(subjectTable).toBeVisible();
  // End bounded column/export/dynamics witnesses; all original following checks remain.
  await page.getByRole('button', { name: 'Индивидуально', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Студенты группы', exact: true })).toBeVisible();
  // End subject/type witness; the original group overview and Back path are restored.
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  // End HEADMAN33; original logout and all following student/history assertions remain unchanged.
  await logoutPwa(page);
  await loginPwa(page, dataset.student);
  await expect(page.getByRole('button', { name: 'Отмечено', exact: true })).toBeVisible();
  // STUDENT15: the existing approved ticket explains the student's own status on focus.
  await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
  await page.getByRole('button', { name: 'По дням', exact: true }).click();
  if (yesterdayDate.getUTCDay() === 0) {
    await page.getByRole('button', { name: 'Предыдущая неделя', exact: true }).click();
    await page.locator('.attendance-sunday').click();
  } else {
    await page.getByRole('button', { name: coverageLabel, exact: true }).click();
  }
  const explainedRow = page.locator(
    `.attendance-lesson-row[data-lesson-id="${multipleExcuseLessonIds[0]}"]`,
  );
  const explanation = `${otherReason.label} · ${multipleComment}`;
  const explainedStatus = explainedRow.getByRole('button', {
    name: `Статус: уважительная причина · ${explanation}`,
    exact: true,
  });
  await explainedStatus.focus();
  await expect(explainedRow.getByRole('tooltip')).toHaveText(explanation);
  await explainedStatus.press('Escape');
  await expect(explainedRow.getByRole('tooltip')).toHaveCount(0);
  await openStudentRequests();
  await page.getByRole('tab', { name: 'Архив', exact: true }).click();
  const approvedCard = page
    .getByRole('article')
    .filter({ has: page.locator(`[id="request-title-${requestId}"]`) });
  await expect(approvedCard.getByText('Одобрена', { exact: true })).toBeVisible();

  // D019: the same student sees one approved ticket containing both lessons.
  await expect(multipleCard).toHaveCount(1);
  await expect(multipleCard.getByText('Одобрена', { exact: true })).toBeVisible();
  await expect(multipleCard.locator('.request-card__lessons > li')).toHaveCount(2);
  // End D019 student archive.

  // Accepted AT-11: a headman adds attempts without resetting the exhausted student's usage.
  await logoutPwa(page);
  const budgetHeadman = await loginPwa(page, dataset.headman);
  if (budgetHeadman.session.activeRole !== 'HEADMAN') await selectPwaRole(page, 'Староста');
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await page.getByRole('button', { name: 'Группа', exact: true }).click();
  await page.locator(`[data-member-id="${dataset.student.id}"]`).click();
  await expect(
    page.getByText('Осталось попыток: 0 из 5. Использовано: 5.', { exact: true }),
  ).toBeVisible();
  await page.getByLabel('Добавить попытки', { exact: true }).fill('2');
  const budgetPath = `/api/attendance/requests/students/${dataset.student.id}/late-checkin-budget`;
  const increased = page.waitForResponse(
    (reply) => new URL(reply.url()).pathname === budgetPath && reply.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Увеличить лимит', exact: true }).click();
  expect((await increased).status()).toBe(200);
  await expect(
    page.getByText('Осталось попыток: 2 из 7. Использовано: 5.', { exact: true }),
  ).toBeVisible();
  expect((await options()).budget).toMatchObject({ limit: 7, used: 5, remaining: 2 });
  const foreignBudget = await dataset.request.get(budgetPath, { headers: foreign.headers() });
  expect(foreignBudget.status()).toBe(403);
  const manual = open.content.find(
    (row: { kind: string; origin: string }) =>
      row.kind === 'LATE_CHECKIN' && row.origin === 'MANUAL',
  );
  expect(Boolean(manual)).toBe(true);
  const retryLessonId = manual.lessons[0].id;
  await student.call('POST', `/api/v1/student/requests/${manual.id}/cancel`);
  expect((await options()).budget).toMatchObject({ limit: 7, used: 5, remaining: 2 });
  await expect(
    page.getByText('Лимит увеличен. Остаток подтверждён сервером.', { exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await expect(page.locator('.headman-group')).toHaveAttribute('data-group-view', 'list');
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
  await logoutPwa(page);
  await loginPwa(page, dataset.student);
  await openStudentRequests();
  await page.getByRole('button', { name: 'Подать', exact: true }).click();
  await page.getByRole('button', { name: 'Забыл отметиться', exact: true }).click();
  await page.locator(`input[type="checkbox"][value="${retryLessonId}"]`).check();
  await page.getByRole('button', { name: 'Продолжить', exact: true }).click();
  await expect(page.getByText('2 из 7', { exact: true })).toBeVisible();
  const resubmitted = page.waitForResponse(
    (reply) =>
      new URL(reply.url()).pathname === '/api/v1/student/requests/late-checkin' &&
      reply.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Отправить запрос', exact: true }).click();
  expect((await resubmitted).status()).toBe(200);
  expect((await options()).budget).toMatchObject({ limit: 7, used: 6, remaining: 1 });
});
