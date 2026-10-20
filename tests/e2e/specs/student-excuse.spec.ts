import { randomUUID } from 'node:crypto';
import { test, expect, admit } from '../fixtures/mobile-dataset';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';

test.afterEach(async () => sanitizeTestErrors(test.info()));

test.describe('Student excuse + file upload', () => {
  test('student 10MiB legacy upload is refused without creating a ticket', async ({ page, dataset }) => {
    const marker = `synthetic-upload-${randomUUID()}`;
    const pdf = Buffer.alloc(10 * 1024 * 1024, 0x20);
    Buffer.from(`%PDF-1.4\n% ${marker}\n`).copy(pdf);
    Buffer.from('\n%%EOF\n').copy(pdf, pdf.length - 7);
    const student = { ...dataset.student, expectedLandingPath: '/student/dashboard' };
    const yesterday = new Date(Date.parse(`${dataset.today}T12:00:00Z`) - 86400000).toISOString().slice(0, 10);
    const ownLesson = await dataset.headmanActor.call('POST', '/api/schedule/one-off-lessons', {
      groupId: dataset.group.id, subjectId: dataset.subject.id,
      assignmentId: dataset.subject.assignments[0].id, date: yesterday,
      lessonNumber: 8, classroom: 'Синтетическая аудитория',
    }, [201, 200], { 'Idempotency-Key': randomUUID() });
    const lessonId = String(ownLesson.physicalLessonId);
    const actor = await admit(dataset.request, dataset.student, 'STUDENT');
    await expect.poll(async () => {
      const report = await actor.call('GET', '/api/attendance/reports/student/records');
      return Object.values(report._embedded ?? {}).flat().some((record: any) =>
        String(record.lessonId) === lessonId && record.lessonDate === yesterday && record.status === 'absent');
    }, { timeout: 15000 }).toBe(true);
    const tickets = async () => {
      const body = await actor.call('GET', '/api/attendance/excuses/me');
      return (Array.isArray(body) ? body : Object.values(body._embedded ?? {}).flat()) as any[];
    };
    const before = (await tickets()).map((ticket) => String(ticket.id)).sort();
    await loginAs(page, student);
    await page.goto('/student/excuses');
    await page.getByRole('button', { name: 'Подать тикет', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Новый тикет о пропуске', exact: true });
    // The fresh owned student has only this missed number-8 occurrence.
    const checkbox = dialog.getByRole('checkbox', { name: /№8 / });
    await expect(checkbox).toHaveCount(1);
    await checkbox.check();
    await dialog.getByLabel('Причина пропуска', { exact: true }).click();
    await page.getByRole('option', { name: /болезн|медицин/i }).click();
    await dialog.getByPlaceholder('Укажите дополнительные обстоятельства', { exact: true }).fill(marker);
    await dialog.locator('input[type="file"]').setInputFiles({
      name: `${marker}.pdf`, mimeType: 'application/pdf', buffer: pdf,
    });
    expect(await dialog.locator('input[type="file"]').evaluate((element) =>
      element instanceof HTMLInputElement && element.files?.length === 1 &&
      element.files[0].size === 10 * 1024 * 1024 && element.files[0].type === 'application/pdf'),
      'Actual browser form retains the exact 10MiB PDF selection').toBe(true);
    const submitted = page.waitForResponse((response) => response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/attendance/excuses/with-file');
    await dialog.getByRole('button', { name: 'Подать тикет', exact: true }).click();
    const response = await submitted;
    expect(response.status(), 'Retired legacy multipart route cannot report success').toBe(410);
    expect(/^multipart\/form-data;\s*boundary=/i.test(response.request().headers()['content-type'] ?? ''),
      'Retired response belongs to the actual multipart submission').toBe(true);
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Не удалось подать тикет. Попробуйте ещё раз.', { exact: true })).toBeVisible();
    await expect(page.getByText('Запрос отправлен. Подтверждение придёт в Telegram.', { exact: true })).toHaveCount(0);
    const after = await tickets();
    expect(JSON.stringify(after.map((ticket) => String(ticket.id)).sort()) === JSON.stringify(before),
      'Retired request creates no ticket in the owned student scope').toBe(true);
    expect(after.some((ticket) => ticket.comment === marker)).toBe(false);
    await assertNoA11yCriticalOrSerious(page);
  });
});
