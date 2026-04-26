import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — role-teacher golden paths.
 *
 * Teacher — read-only режим для журнала + статистика. Нет Telegram.
 */

test.describe('Role: TEACHER', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.teacher);
  });

  test('can access teacher-only paths', async ({ page }) => {
    // M14 G7 (G26 категория B): `/teacher/schedule` route не существует —
    // в TEACHER_ROUTES только dashboard/journal/stats. Заменено на /journal.
    const paths = ['/teacher/journal', '/teacher/stats'];
    for (const path of paths) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path));
      await assertNoA11yCriticalOrSerious(page);
    }
  });

  test('cannot access /admin/* — redirected', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/teacher\//, { timeout: 5_000 });
  });

  // M14 G7 (G26 категория A): тест `journal shows red-zone indicator`
  // удалён. Текущий `teacher/stats/stats-page.component.html` рендерит
  // `app-overall-stat-card` + `app-subject-chart` — индикатора красной
  // зоны нет. Восстановить когда фича будет реализована (планируется в
  // v0.1 после job-stories ревью teacher dashboard UX).
});
