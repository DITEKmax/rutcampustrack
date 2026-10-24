import { createHash, randomUUID } from 'node:crypto';
import { test, expect, admit } from '../fixtures/mobile-dataset';
import { loginAs, logout } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';
import type { StudentRequestOptions } from '../../../frontends/mobile-core/src/api/types';

// The legacy with-file UI no longer exists. Its retired410 handler and domain
// size matrix remain server-owned; this single case checks current WEB wiring.
test.describe('Student excuse + file upload', () => {
  test('current EXCUSE form submits one owned eligible lesson and exact small attachment', async ({ page, dataset }) => {
    const marker = `synthetic-upload-${randomUUID()}`;
    const bytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=', 'base64');
    const file = { name: `${marker}.png`, mimeType: 'image/png', buffer: bytes };
    const student = { ...dataset.student, expectedLandingPath: '/student/home' };
    const yesterday = new Date(Date.parse(`${dataset.today}T12:00:00Z`) - 86400000).toISOString().slice(0, 10);
    const ownLesson = await dataset.headmanActor.call('POST', '/api/schedule/one-off-lessons', {
      groupId: dataset.group.id, subjectId: dataset.subject.id,
      assignmentId: dataset.subject.assignments[0].id, date: yesterday,
      lessonNumber: 8, classroom: 'Синтетическая аудитория',
    }, [201, 200], { 'Idempotency-Key': randomUUID() });
    const lessonId = String(ownLesson.physicalLessonId);
    const actor = await admit(dataset.request, dataset.student, 'STUDENT');
    await expect.poll(async () => {
      const options = await actor.call('GET', '/api/v1/student/requests/options') as StudentRequestOptions;
      return options.lessons.some(option => String(option.lesson.id) === lessonId && option.excuseEligible);
    }, { timeout: 15000, message: 'The exact owned occurrence is authoritatively eligible for EXCUSE' }).toBe(true);
    const options = await actor.call('GET', '/api/v1/student/requests/options') as StudentRequestOptions;
    const eligible = options.lessons.find(option => String(option.lesson.id) === lessonId)!;
    const subjectName = eligible?.lesson.subjectName, startsAt = eligible?.lesson.startsAt, endsAt = eligible?.lesson.endsAt;
    if (!eligible?.excuseEligible || typeof subjectName !== 'string' || !subjectName ||
      typeof startsAt !== 'string' || !startsAt || typeof endsAt !== 'string' || !endsAt)
      throw new Error('The exact owned eligible occurrence must retain its subject and time identity');
    expect(eligible.lesson.date === yesterday && subjectName === dataset.subject.name &&
      options.reasons.some(reason => reason.code === 'OTHER') &&
      options.files.maxBytesPerFile >= bytes.length && options.files.contentTypes.includes(file.mimeType)).toBe(true);
    const before = await actor.call('GET', '/api/v1/student/requests?bucket=OPEN&page=0&size=50');
    await loginAs(page, student);
    await page.goto('/student/requests');
    const owner = page.locator('main#main [data-screen-id="WEB-STUDENT-REQUESTS"]');
    await owner.getByRole('button', { name: 'Подать на «у»', exact: true }).click();
    const form = owner.locator('form.student-request-form');
    await form.getByLabel('С даты', { exact: true }).fill(yesterday);
    await form.getByLabel('По дату', { exact: true }).fill(yesterday);
    const choice = form.locator('label.student-request-option').filter({ has: page.getByText(subjectName, { exact: true }) });
    await expect(choice).toHaveCount(1);
    await expect(choice).toContainText(`${startsAt.slice(0, 5)}–${endsAt.slice(0, 5)}`);
    await choice.getByRole('checkbox').check();
    await form.getByRole('combobox', { name: 'Причина', exact: true }).selectOption('OTHER');
    await form.getByLabel('Комментарий', { exact: true }).fill(marker);
    await form.getByLabel('Выбрать файлы', { exact: true }).setInputFiles(file);
    const preview = form.getByRole('img', { name: file.name, exact: true });
    await expect(preview).toBeVisible();
    expect(await preview.evaluate(async image => {
      if (!(image instanceof HTMLImageElement)) return false;
      await image.decode();
      return image.naturalWidth === 1 && image.naturalHeight === 1;
    }), 'The actual selected image is decoded before upload').toBe(true);
    await form.getByRole('button', { name: 'Отправить заявку', exact: true }).click();
    const confirmation = page.getByRole('dialog', { name: 'Отправить заявку?', exact: true });
    await expect(confirmation.getByRole('listitem')).toHaveCount(1);
    await expect(confirmation.getByRole('listitem')).toContainText(subjectName);
    const origin = new URL(page.url()).origin;
    const submitted = page.waitForResponse(response => new URL(response.url()).origin === origin &&
      new URL(response.url()).pathname === '/api/v1/student/requests/excuse' && response.request().method() === 'POST');
    await confirmation.getByRole('button', { name: 'Подтвердить отправку', exact: true }).click();
    const response = await submitted;
    expect(response.status(), 'Current typed native EXCUSE contract accepts HTTP200').toBe(200);
    expect(await response.finished()).toBeNull();
    expect(/^multipart\/form-data;\s*boundary=/i.test(response.request().headers()['content-type'] ?? '')).toBe(true);
    const created = await response.json();
    expect(typeof created.summary?.id === 'string' && created.summary.id.length > 0 &&
      created.summary.kind === 'EXCUSE' && created.summary.status === 'PENDING' &&
      created.summary.lessons.length === 1 && String(created.summary.lessons[0].id) === lessonId &&
      created.reason === 'OTHER' && created.comment === marker && created.attachments.length === 1).toBe(true);
    const detail = await actor.call('GET', `/api/v1/student/requests/${created.summary.id}`);
    expect(detail.summary.id === created.summary.id && detail.summary.kind === 'EXCUSE' &&
      detail.summary.status === 'PENDING' && detail.reason === 'OTHER' && detail.comment === marker &&
      detail.summary.lessons.length === 1 && String(detail.summary.lessons[0].id) === lessonId &&
      detail.attachments.length === 1).toBe(true);
    const attachment = detail.attachments[0];
    expect(attachment.id === created.attachments[0].id && attachment.name === file.name &&
      attachment.contentType === file.mimeType && attachment.sizeBytes === bytes.length && attachment.state === 'ACTIVE').toBe(true);
    const binary = await dataset.request.get(`/api/v1/student/requests/${detail.summary.id}/attachments/${attachment.id}`, { headers: actor.headers() });
    expect(binary.status()).toBe(200);
    expect(binary.headers()['content-type']?.split(';')[0]).toBe(file.mimeType);
    expect(createHash('sha256').update(await binary.body()).digest('hex') === createHash('sha256').update(bytes).digest('hex'),
      'Owned attachment bytes survive the genuine multipart/readback path').toBe(true);
    const after = await actor.call('GET', '/api/v1/student/requests?bucket=OPEN&page=0&size=50');
    expect(after.totalElements === before.totalElements + 1 &&
      after.content.filter((request: { id: string }) => request.id === detail.summary.id).length === 1).toBe(true);
    await expect(form).toHaveCount(0);
    const card = owner.locator(`[data-request-id="${detail.summary.id}"]`);
    await expect(card).toHaveCount(1);
    await expect(card.getByText('На рассмотрении', { exact: true })).toBeVisible();
    await expect(card).toContainText(marker);
    await card.getByRole('button', { name: `Раскрыть заявку ${detail.summary.id}`, exact: true }).click();
    await expect(card.locator('.student-request-lesson')).toHaveCount(1);
    await expect(card.locator('.student-request-lesson')).toContainText(subjectName);
    await expect(card).toContainText(file.name);
    await assertNoA11yCriticalOrSerious(page);
    await logout(page);
  });
});
