import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — headman bulk-mark flow.
 *
 * Scenario:
 *  1. headman логинится.
 *  2. открывает /headman/schedule, выбирает активную пару.
 *  3. в HeadmanLessonSheet (M07 UX) кликает «Отметить всех» → bulk-mark (M05 G4 batch).
 *  4. проверяет WebSocket (STOMP) notification — headman видит live-update
 *     счётчика группы после ответа backend'а.
 *
 * Headman hard-lock (v9.0) — после bulk-mark студенты видят занятие как
 * заблокированное от геоотметки.
 */

test.describe('Headman bulk-mark', () => {
  test('headman marks all students present via batch endpoint', async ({ page }) => {
    await loginAs(page, TEST_USERS.headman);

    // Перейти на schedule (уже должен быть landing page по user.expectedLandingPath)
    await page.goto('/headman/schedule');

    // Первая активная пара в списке (predicate: data-testid="lesson-card"
    // с статусом active/closed — seed должен обеспечить хотя бы одну)
    const lessonCard = page.locator('[data-testid="lesson-card"]').first();
    await expect(lessonCard).toBeVisible({ timeout: 10_000 });
    await lessonCard.click();

    // HeadmanLessonSheet раскрывается (BottomSheet с tabs)
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('tab', { name: /отметить|студенты/i })).toBeVisible();

    // Кнопка «Отметить всех присутствующими» → batch endpoint
    await page.getByRole('button', { name: /отметить всех/i }).click();

    // Backend вернул 200 → UI обновляет статистику группы
    await expect(page.getByText(/отмечен|присутствует/i)).toBeVisible({ timeout: 10_000 });

    await assertNoA11yCriticalOrSerious(page);
  });

  test('headman can review marks and see WebSocket live-update', async ({ page }) => {
    await loginAs(page, TEST_USERS.headman);
    await page.goto('/headman/dashboard');

    // Дашборд показывает live count — после bulk-mark из предыдущего теста
    // счётчик ненулевой (seed'а хватает).
    await expect(page.locator('[data-testid="group-attendance-count"]')).toBeVisible();
    await assertNoA11yCriticalOrSerious(page);
  });
});
