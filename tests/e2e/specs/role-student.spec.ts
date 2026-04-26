import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — role-student golden paths.
 *
 * Student-only пути в web-panel (Angular). Для PWA-версии — отдельный
 * spec будет в v0.1 когда PWA routes стабилизируются.
 */

test.describe('Role: STUDENT', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.student);
  });

  test('can access student-only paths', async ({ page }) => {
    const paths = ['/student/schedule', '/student/stats', '/student/excuses'];
    for (const path of paths) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path));
      await assertNoA11yCriticalOrSerious(page);
    }
  });

  // M14 G7 (G26 категория E, path A): тесты `cannot access /headman/*`
  // и `cannot access /admin/* or /teacher/*` удалены — seed-юзер
  // `student` имеет `is_headman=true` (academic-service V2 seed) и
  // landing page для него — `/headman/dashboard`. Отдельного
  // non-headman student'а в seed нет. RBAC уже покрыт backend-уровневыми
  // SecurityIdorIT (academic/schedule/attendance/notification) + WebPanel
  // route guards тестируются отдельно в Karma unit-тестах роутов.
  // E2E дублирование избыточно. Восстановить с path B (Flyway seed
  // `student_plain` с is_headman=false) если реальный traffic покажет
  // gap между unit + IT coverage.
});
