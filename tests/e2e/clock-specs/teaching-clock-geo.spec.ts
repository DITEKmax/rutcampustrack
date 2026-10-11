import { randomUUID } from 'node:crypto';
import { test, expect } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';

test.afterEach(async ({}, info) => sanitizeTestErrors(info));

test('real geolocation confirms fresh student, then D005 manual absence rejects the blocked attempt', async ({
  page,
  dataset,
  teachingInstant,
}) => {
  if (!teachingInstant) throw new Error('Dedicated teaching-clock config is required');
  await page.clock.setFixedTime(teachingInstant); // Date only; timers and request deadlines run.
  const student = await loginPwa(page, dataset.student);
  const lessonId = String(dataset.lesson.physicalLessonId);
  const today = async (token: string) => {
    const response = await page.request.get('/api/v1/student/today', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.date).toBe(dataset.today);
    expect(Date.parse(body.serverNow)).toBe(teachingInstant.getTime());
    return body.lessons.find((row: { schedule: { id: string } }) => row.schedule.id === lessonId);
  };
  // Real status scheduler/publication/fences stay enabled; no direct database status update.
  await expect
    .poll(
      async () => {
        const lesson = await today(student.accessToken);
        return lesson?.schedule.status === 'ACTIVE' && lesson.checkinEligibility.allowed;
      },
      {
        timeout: 120000,
        intervals: [1000],
        message: 'Real lesson lifecycle and eligible fresh student',
      },
    )
    .toBe(true);
  await page.reload();
  const path = `/api/v1/student/lessons/${lessonId}/checkin`;
  const accepted = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === path && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Отметиться', exact: true }).click();
  const response = await accepted;
  expect(response.status()).toBe(200);
  const ack = await response.json();
  expect(ack.outcome).toBe('PRESENT');
  expect(ack.attendance.status).toBe('PRESENT');
  expect(ack.attendance.source).toBe('STUDENT_GEO');
  expect(ack.request).toBeNull();
  await expect(page.getByRole('button', { name: 'Отмечено', exact: true })).toBeVisible();
  const replay = await page.request.post(path, {
    headers: {
      Authorization: `Bearer ${student.accessToken}`,
      'Idempotency-Key': response.request().headers()['idempotency-key'],
    },
    data: response.request().postDataJSON(),
  });
  expect(replay.status()).toBe(200);
  expect(await replay.json()).toEqual(ack);
  const attendanceBeforeMismatch = (await today(student.accessToken)).attendance;
  const durableMark = async () => {
    const report = await dataset.headmanActor.call(
      'GET',
      `/api/attendance/reports/lesson/${lessonId}`,
    );
    const entry = report.entries.find(
      (row: { userId: number }) => Number(row.userId) === dataset.student.id,
    );
    expect(entry).toBeDefined();
    expect(typeof entry.markRevision).toBe('string');
    expect(entry.markRevision).not.toBe('EMPTY');
    expect(entry.markRevision.length).toBeGreaterThan(0);
    return { status: entry.status, source: entry.source, markRevision: entry.markRevision };
  };
  const durableBeforeMismatch = await durableMark();
  const originalPayload = response.request().postDataJSON();
  expect(originalPayload.geo.kind).toBe('COORDINATES');
  // Still-valid coordinates distinguish immutable key/payload receipts from the PRESENT fallback.
  const changedPayload = {
    geo: { ...originalPayload.geo, latitude: originalPayload.geo.latitude + 0.000001 },
  };
  expect(changedPayload.geo.latitude).not.toBe(originalPayload.geo.latitude);
  const mismatch = await page.request.post(path, {
    headers: {
      Authorization: `Bearer ${student.accessToken}`,
      'Idempotency-Key': response.request().headers()['idempotency-key'],
    },
    data: changedPayload,
  });
  expect(mismatch.status()).toBe(409);
  expect((await mismatch.json()).code).toBe('IDEMPOTENCY_PAYLOAD_MISMATCH');
  expect((await today(student.accessToken)).attendance).toEqual(attendanceBeforeMismatch);
  expect(await durableMark()).toEqual(durableBeforeMismatch);
  const replayAfterMismatch = await page.request.post(path, {
    headers: {
      Authorization: `Bearer ${student.accessToken}`,
      'Idempotency-Key': response.request().headers()['idempotency-key'],
    },
    data: originalPayload,
  });
  expect(replayAfterMismatch.status()).toBe(200);
  expect(await replayAfterMismatch.json()).toEqual(ack);
  expect(await durableMark()).toEqual(durableBeforeMismatch);
  await page.reload();
  expect((await today(student.accessToken)).attendance.status).toBe('PRESENT');
  await expect(page.getByRole('button', { name: 'Отмечено', exact: true })).toBeVisible();
  await logoutPwa(page);

  // A different fresh plain student demonstrates the manual-n boundary without replaying prior geo.
  const manual = await dataset.user('STUDENT', dataset.group.id);
  await dataset.headmanActor.call(
    'PUT',
    `/api/attendance/lessons/${lessonId}/students/${manual.id}`,
    {
      status: 'ABSENT',
      expectedStatus: 'EMPTY',
      expectedRevision: 'EMPTY',
    },
  );
  const manualSession = await loginPwa(page, manual);
  const before = await today(manualSession.accessToken);
  expect(before.checkinEligibility.allowed).toBe(false);
  expect(before.checkinEligibility.reason).toBe('HEADMAN_ABSENT_REQUIRES_APPEAL');
  const requestsPath = '/api/v1/student/requests?bucket=OPEN&page=0&size=50';
  const readRequests = async () => {
    const response = await page.request.get(requestsPath, {
      headers: { Authorization: `Bearer ${manualSession.accessToken}` },
    });
    expect(response.status()).toBe(200);
    return response.json();
  };
  const requestsBefore = await readRequests();
  // No permitted UI command exists; exercise the same protected real command directly.
  for (const geo of [
    { kind: 'COORDINATES', latitude: 55.788204, longitude: 37.606762 },
    { kind: 'UNAVAILABLE', reason: 'POSITION_UNAVAILABLE' },
  ]) {
    const denied = await page.request.post(path, {
      headers: {
        Authorization: `Bearer ${manualSession.accessToken}`,
        'Idempotency-Key': randomUUID(),
      },
      data: { geo },
    });
    expect(denied.status()).toBe(409);
    expect((await denied.json()).code).toBe('MANUAL_ABSENCE_REQUIRES_APPEAL');
  }
  const after = await today(manualSession.accessToken);
  expect(after.attendance.status).toBe('ABSENT');
  expect(after.request).toBeNull();
  expect(await readRequests()).toEqual(requestsBefore);
  await page.reload();
  await expect(page.getByRole('button', { name: 'Отметиться', exact: true })).toHaveCount(0);
});
