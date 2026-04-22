import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';
import path from 'path';

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
  test('student submits excuse with 10MB PDF, headman approves', async ({ page, browser }) => {
    await loginAs(page, TEST_USERS.student);
    await page.goto('/student/excuses');

    await page.getByRole('button', { name: /новый|создать/i }).click();

    // Form fields
    await page.getByLabel(/причина|тип/i).selectOption({ label: /болезн/i });
    await page.getByLabel(/комментарий|описание/i).fill('Справка от врача прилагается.');

    // Lesson picker — выбрать первую доступную
    const firstLesson = page.locator('[data-testid="lesson-picker-item"]').first();
    await firstLesson.check();

    // File upload (10MB PDF — генерируется e2e-setup fixture при необходимости)
    const filePath = path.resolve(__dirname, '../fixtures/test-excuse.pdf');
    await page.getByLabel(/прикрепить|файл/i).setInputFiles(filePath);

    // Submit
    await page.getByRole('button', { name: /отправить|подать/i }).click();

    // Success toast
    await expect(page.getByText(/отправлен|создан/i)).toBeVisible({ timeout: 15_000 });

    await assertNoA11yCriticalOrSerious(page);

    // Headman в отдельном browser context'е approves
    const headmanContext = await browser.newContext();
    const headmanPage = await headmanContext.newPage();
    try {
      await loginAs(headmanPage, TEST_USERS.headman);
      await headmanPage.goto('/headman/excuses');

      const firstExcuse = headmanPage.locator('[data-testid="excuse-card"]').first();
      await expect(firstExcuse).toBeVisible({ timeout: 10_000 });
      await firstExcuse.click();
      await headmanPage.getByRole('button', { name: /одобрить|принять/i }).click();

      await expect(headmanPage.getByText(/одобрен/i)).toBeVisible();
    } finally {
      await headmanContext.close();
    }

    // Student обновляет страницу → видит APPROVED status
    await page.reload();
    await expect(page.getByText(/одобрен/i).first()).toBeVisible({ timeout: 10_000 });
  });
});
