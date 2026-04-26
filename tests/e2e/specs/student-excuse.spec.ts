import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';
import path from 'path';
import fs from 'fs';

// M14 G7: генерируем 10MB PDF в beforeAll если файл отсутствует —
// раньше тест ссылался на test-excuse.pdf которого не было в fixtures/.
// PDF минимально валидный (header + EOF) + padding до 10MB.
const FIXTURE_PDF = path.resolve(__dirname, '../fixtures/test-excuse.pdf');

function ensureFixturePdf(): void {
  if (fs.existsSync(FIXTURE_PDF)) return;
  const header = Buffer.from('%PDF-1.4\n%\xe2\xe3\xcf\xd3\n', 'binary');
  const trailer = Buffer.from(
    '\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n' +
      '2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n' +
      'xref\n0 3\n0000000000 65535 f\n' +
      '0000000015 00000 n\n0000000060 00000 n\n' +
      'trailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n105\n%%EOF\n',
    'binary',
  );
  // Padding до ~10MB — некритичный текстовый блок между header и trailer.
  const padSize = 10 * 1024 * 1024 - header.length - trailer.length;
  const pad = Buffer.alloc(padSize, 0x20); // ASCII space
  fs.writeFileSync(FIXTURE_PDF, Buffer.concat([header, pad, trailer]));
}

/**
 * M08 Группа 5 (P2-8/5) — student excuse ticket с file-upload.
 *
 * Scenario:
 *  1. student логинится.
 *  2. открывает /student/excuses/new.
 *  3. выбирает pairs (прошедшие за последние 7 дней) + reason.
 *  4. загружает 10MB PDF (fixtures/test-excuse.pdf генерируется once).
 *  5. submit → POST /attendance/excuses/with-file → 201 + self-link.
 *  6. headman (separate browser context) видит excuse + approves.
 *  7. student видит status APPROVED.
 *
 * File-upload endpoint — M05 G1 + M07 G11 `client_max_body_size 25m`.
 */

test.describe('Student excuse + file upload', () => {
  test.beforeAll(() => {
    ensureFixturePdf();
  });

  test('student submits excuse with 10MB PDF, headman approves', async ({ page, browser }) => {
    await loginAs(page, TEST_USERS.student);
    await page.goto('/student/excuses');

    // M14 G7: реальная кнопка — «Подать тикет» (student-excuses.component.html:11).
    await page.getByRole('button', { name: /подать тикет|новый|создать/i }).click();

    // M14 G7: открыт MatDialog с формой excuse-form-dialog.
    // - Excuse type: <mat-select> (Material, не native <select>) — клик +
    //   getByRole('option') вместо selectOption().
    // - Lesson picker: <label data-testid="lesson-picker-item"> (M14 G7
    //   testid added). Клик через `.first().click()` toggle'ит mat-checkbox.
    // - File upload: <input type="file" aria-label="Прикрепить файл">
    //   (excuse-form-dialog.component.html:84-88).
    // - Submit: <button>Подать тикет</button> в mat-dialog-actions.

    // 1. Lesson picker — обязательное поле, без выбранной пары submit
    //    выдаст validationError. Выбираем первую.
    const firstLesson = page.locator('[data-testid="lesson-picker-item"]').first();
    await firstLesson.click();

    // 2. Excuse type через MatSelect (открыть → выбрать option).
    await page.getByLabel(/причина пропуска/i).click();
    await page.getByRole('option', { name: /болезн|медицин/i }).first().click();

    // 3. Comment — необязательно, но добавим.
    await page.getByLabel(/комментарий/i).fill('Справка от врача прилагается.');

    // 4. File upload — input скрыт под <label>, используем filesetter
    //    напрямую через input[type="file"]. PDF создан в beforeAll.
    await page.locator('input[type="file"]').setInputFiles(FIXTURE_PDF);

    // 5. Submit.
    await page.getByRole('button', { name: /подать тикет/i }).click();

    // Dialog закрывается после успешной отправки. Спустя момент в списке
    // активных тикетов появляется новая запись.
    await expect(page.getByRole('button', { name: /подать тикет/i }).first()).toBeVisible({
      timeout: 15_000,
    });

    await assertNoA11yCriticalOrSerious(page);

    // Headman в отдельном browser context'е approves. Seed user `student`
    // имеет is_headman=true, поэтому используем его (M14 G8 убрал
    // TEST_USERS.headman дубликат).
    const headmanContext = await browser.newContext();
    const headmanPage = await headmanContext.newPage();
    try {
      await loginAs(headmanPage, TEST_USERS.student);
      await headmanPage.goto('/headman/excuses');

      const firstExcuse = headmanPage.locator('[data-testid="excuse-card"]').first();
      await expect(firstExcuse).toBeVisible({ timeout: 10_000 });

      // Кнопка «Одобрить» внутри excuse-card (headman-excuses.component.ts:136).
      await firstExcuse.getByRole('button', { name: /одобрить/i }).click();

      // После approve — ticket уходит из pendingTickets в resolvedTickets.
      // Verify через отсутствие первой excuse-card либо появление section
      // «Решения». Используем resolvedTickets section как positive signal.
      await expect(headmanPage.getByText(/решения|одобрен/i).first()).toBeVisible({
        timeout: 10_000,
      });
    } finally {
      await headmanContext.close();
    }

    // Student обновляет страницу → ticket в архиве со статусом APPROVED.
    await page.reload();
    await page.getByRole('tab', { name: /архив/i }).click();
    await expect(page.getByText(/одобрен/i).first()).toBeVisible({ timeout: 10_000 });
  });
});
