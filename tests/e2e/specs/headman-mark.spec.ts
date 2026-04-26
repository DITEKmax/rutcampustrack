import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — headman bulk-mark flow.
 *
 * ============================================================================
 * SKIPPED by design — bulk-mark в web-panel НЕ ПЛАНИРУЕТСЯ.
 *
 * Архитектурное решение (M14 G7, owner): web-panel не получает
 * bulk-mark UI. Староста делает массовую отметку через PWA
 * (`frontends/pwa/src/features/schedule/HeadmanLessonSheet.tsx` —
 * `useHeadmanMarkBatch`), которая уже реализована и работает в
 * production. Web-panel остаётся desktop-инструментом для журналов и
 * ручных action'ов, mass-mark — мобильный flow.
 *
 * Web-panel UI отсутствует by design:
 *  - нет lesson-card listings в `headman-dashboard.component.ts`
 *  - нет BottomSheet «Отметить всех» в `headman-schedule.component.ts`
 *  - нет group-attendance-count stat
 *
 * Этот spec из M08 G5 написан под web-panel UX, который никогда не будет
 * реализован. Не удаляем (audit trail), но `test.describe.skip`
 * permanent — не «временный TODO».
 *
 * Аналогичный e2e flow для PWA — см. `frontends/pwa/src/features/schedule/`
 * unit + integration тесты + manual UAT через Telegram-клиент.
 * ============================================================================
 */

test.describe.skip('Headman bulk-mark (web-panel — by-design out of scope)', () => {
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
