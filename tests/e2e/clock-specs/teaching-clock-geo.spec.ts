import type { Request } from '@playwright/test';
import { test, expect } from '../fixtures/mobile-dataset';
import { loginPwa } from '../fixtures/pwa-auth';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';
import { advanceStudentQuickRequestClock } from '../fixtures/teaching-clock';

test.afterEach(async ({}, info) => sanitizeTestErrors(info));

test('student uses Today quick request after the genuine first lesson closes @cloud-accepted', async ({
  page,
  dataset,
  teachingInstant,
}, info) => {
  const mainDeadline = performance.now() + Math.min(info.timeout, 180000) - 30000;
  const remaining = () => Math.floor(mainDeadline - performance.now());
  const bounded = (maximum = 10000) => {
    const value = Math.min(maximum, remaining());
    if (value <= 0) throw new Error('Student quick-request main budget exhausted');
    return value;
  };
  if (!teachingInstant) throw new Error('Dedicated teaching-clock config is required');
  const lessonId = String(dataset.lesson.physicalLessonId);
  const checkinPath = `/api/v1/student/lessons/${lessonId}/checkin`;
  let checkinRequests = 0;
  const observeCheckin = (request: Request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === checkinPath)
      checkinRequests += 1;
  };
  page.on('request', observeCheckin);
  try {
    await page.clock.setFixedTime(teachingInstant);
    const student = await loginPwa(page, dataset.student, {
      retryUntilMs: Date.now() + bounded(65000),
    });
    expect(student.session.activeRole).toBe('STUDENT');
    const studentHeaders = { Authorization: `Bearer ${student.accessToken}` };
    const read = async (path: string, headers: Record<string, string>) => {
      const response = await page.request.get(path, { headers, timeout: bounded(5000) });
      try {
        expect(response.status()).toBe(200);
        return await response.json();
      } finally {
        await response.dispose();
      }
    };
    const today = async (instant: Date) => {
      const body = await read('/api/v1/student/today', studentHeaders);
      expect(body.date === dataset.today).toBe(true);
      expect(Date.parse(body.serverNow)).toBe(instant.getTime());
      const own = body.lessons.filter(
        (row: { schedule: { id: string } }) => row.schedule.id === lessonId,
      );
      expect(own.length).toBe(1);
      return own[0];
    };
    const before = await today(teachingInstant);
    expect(before.attendance).toBeNull();
    expect(before.request).toBeNull();
    expect(checkinRequests).toBe(0);

    // The worker fixture has already committed its own fresh first lesson at 09:00.
    // This is the last educational lane: neither success nor failure rewinds the shared clock.
    bounded();
    const advanced = advanceStudentQuickRequestClock(
      process.env.RCT_E2E_TEACHING_CLOCK_DIR,
      teachingInstant,
    );
    await page.clock.setFixedTime(advanced);
    const reportPath = `/api/attendance/reports/lesson/${lessonId}`;
    const mark = async () => {
      const report = await read(reportPath, dataset.headmanActor.headers());
      const own = report.entries.filter(
        (entry: { userId: number }) => Number(entry.userId) === dataset.student.id,
      );
      expect(own.length).toBe(1);
      return own[0];
    };
    let beforeRequest: Awaited<ReturnType<typeof mark>>;
    await expect
      .poll(
        async () => {
          const lesson = await today(advanced);
          if (
            lesson.schedule.status !== 'CLOSED' ||
            lesson.attendance?.status !== 'ABSENT' ||
            lesson.attendance?.source !== 'SYSTEM' ||
            lesson.request !== null
          )
            return false;
          const options = await read('/api/v1/student/requests/options', studentHeaders);
          const own = options.lessons.filter(
            (option: { lesson: { id: string } }) => option.lesson.id === lessonId,
          );
          if (
            !(
              own.length === 1 &&
              own[0].lateCheckinEligible === true &&
              Array.isArray(own[0].pendingRequests) &&
              own[0].pendingRequests.length === 0
            )
          )
            return false;
          beforeRequest = await mark();
          return (
            beforeRequest.status === 'absent' &&
            beforeRequest.source === 'auto_scheduler' &&
            typeof beforeRequest.markRevision === 'string' &&
            beforeRequest.markRevision.length > 0 &&
            beforeRequest.markRevision !== 'EMPTY'
          );
        },
        {
          timeout: bounded(90000),
          intervals: [1000],
          message: 'Real closed lesson permits Today request',
        },
      )
      .toBe(true);
    expect(beforeRequest.status).toBe('absent');
    expect(beforeRequest.source).toBe('auto_scheduler');
    expect(
      typeof beforeRequest.markRevision === 'string' &&
        beforeRequest.markRevision.length > 0 &&
        beforeRequest.markRevision !== 'EMPTY',
    ).toBe(true);
    expect(checkinRequests).toBe(0);

    await page.reload({ timeout: bounded() });
    const card = page.locator('.today-list .student-lesson__card').filter({
      has: page.getByRole('heading', { name: dataset.subject.name, exact: true }),
    });
    await expect(card).toHaveCount(1, { timeout: bounded() });
    await card
      .getByRole('button', { name: 'Пропуск. Действия по пропуску', exact: true })
      .click({ timeout: bounded() });
    const quick = card.getByRole('button', { name: 'Забыл отметиться', exact: true });
    await expect(quick).toBeEnabled({ timeout: bounded() });
    await quick.click({ timeout: bounded() });
    const confirmation = page.getByRole('main', { name: 'Забыл отметиться', exact: true });
    await expect(confirmation).toBeVisible({ timeout: bounded() });
    const selected = confirmation.getByRole('article', { name: 'Выбранная пара', exact: true });
    await expect(selected).toHaveCount(1);
    await expect(
      selected.getByRole('heading', { name: dataset.subject.name, exact: true }),
    ).toBeVisible();
    await expect(selected.getByLabel('Пропуск', { exact: true })).toBeVisible();
    await expect(confirmation.locator('.request-lesson-selector')).toHaveCount(0);
    await expect(
      confirmation.getByRole('heading', { name: 'Отправить запрос?', exact: true }),
    ).toBeVisible();
    const [response] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/v1/student/requests/late-checkin' &&
          response.request().method() === 'POST',
        { timeout: bounded() },
      ),
      confirmation
        .getByRole('button', { name: 'Отправить запрос', exact: true })
        .click({ timeout: bounded() }),
    ]);
    expect(response.status()).toBe(200);
    const submittedBody = response.request().postDataJSON();
    expect(Object.keys(submittedBody).length === 1 && submittedBody.lessonId === lessonId).toBe(
      true,
    );
    const ack = await response.json();
    const summary = ack.summary;
    expect(typeof summary.id === 'string' && /^[0-9a-f]{24}$/.test(summary.id)).toBe(true);
    expect(summary.kind).toBe('LATE_CHECKIN');
    expect(summary.origin).toBe('MANUAL');
    expect(summary.status).toBe('PENDING');
    expect(summary.lessons.length).toBe(1);
    expect(summary.lessons[0].id === lessonId).toBe(true);
    const open = await read('/api/v1/student/requests?bucket=OPEN&page=0&size=50', studentHeaders);
    expect(open.totalElements).toBe(1);
    expect(open.content.length).toBe(1);
    expect(open.content[0].id === summary.id).toBe(true);
    const headman = await read(
      `/api/attendance/requests/${summary.id}`,
      dataset.headmanActor.headers(),
    );
    expect(headman.summary.id === summary.id).toBe(true);
    expect(headman.summary.kind).toBe('LATE_CHECKIN');
    expect(headman.summary.status).toBe('PENDING');
    expect(Number(headman.summary.studentId) === dataset.student.id).toBe(true);
    expect(headman.summary.lessonCount).toBe(1);
    expect(headman.lessons.length).toBe(1);
    const ownedLesson = headman.lessons[0];
    expect(
      String(ownedLesson.lessonId) === lessonId &&
        Number(ownedLesson.groupId) === dataset.group.id &&
        Number(ownedLesson.subjectId) === dataset.subject.id &&
        Number(ownedLesson.semesterId) === dataset.semester.id &&
        ownedLesson.date === dataset.today,
    ).toBe(true);
    const afterRequest = await mark();
    expect(
      afterRequest.status === beforeRequest.status &&
        afterRequest.source === beforeRequest.source &&
        afterRequest.markRevision === beforeRequest.markRevision,
    ).toBe(true);
    expect(checkinRequests).toBe(0);
    bounded();
    // Keep the owned pending ticket and its immutable history until disposable stand teardown.
  } finally {
    page.off('request', observeCheckin);
  }
});
