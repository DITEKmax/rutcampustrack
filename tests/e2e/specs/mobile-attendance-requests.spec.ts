import { randomUUID } from 'node:crypto';
import { test, expect, admit, selectPwaRole } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';

test.afterEach(async ({}, info) => sanitizeTestErrors(info));

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
  await page.getByRole('tab', { name: 'Архив', exact: true }).click();
  await expect(cancelledCard.getByText('Отменена', { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Открытые', exact: true }).click();

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
  await page.getByRole('tab', { name: 'Открытые', exact: true }).click();
  assertExcuseLessonFree(await options());
  const openAfterExcuse = await student.call(
    'GET',
    '/api/v1/student/requests?bucket=OPEN&page=0&size=50',
  );
  expect(
    JSON.stringify(openAfterExcuse.content.map((row: { id: string }) => row.id).sort()) ===
      JSON.stringify(originalOpenIds),
  ).toBe(true);

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
  await logoutPwa(page);
  await loginPwa(page, dataset.student);
  await expect(page.getByRole('button', { name: 'Отмечено', exact: true })).toBeVisible();
  await openStudentRequests();
  await page.getByRole('tab', { name: 'Архив', exact: true }).click();
  const approvedCard = page
    .getByRole('article')
    .filter({ has: page.locator(`[id="request-title-${requestId}"]`) });
  await expect(approvedCard.getByText('Одобрена', { exact: true })).toBeVisible();

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
  const manual = open.content.find((row: { origin: string }) => row.origin === 'MANUAL');
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
