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
        return manualLessons.every((id) =>
          available.lessons.some(
            (option: { lesson: { id: string; status: string }; lateCheckinEligible: boolean }) =>
              option.lesson.id === id &&
              option.lesson.status === 'closed' &&
              option.lateCheckinEligible,
          ),
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
