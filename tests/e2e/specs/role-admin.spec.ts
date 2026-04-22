import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — role-admin golden paths.
 *
 * Admin-only пути:
 *  - /admin/dashboard — live-status overview
 *  - /admin/users — CRUD пользователей
 *  - /admin/groups — CRUD групп
 *  - /admin/semesters — управление семестрами
 *  - /admin/settings — глобальные настройки
 *
 * Cross-role guard: admin не должен иметь прямой доступ к /teacher/*
 * или /student/* (role-guard redirect → /admin/dashboard).
 */

test.describe('Role: ADMIN', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.admin);
  });

  test('can access admin-only paths', async ({ page }) => {
    const paths = ['/admin/dashboard', '/admin/users', '/admin/groups', '/admin/semesters'];
    for (const path of paths) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path));
      await assertNoA11yCriticalOrSerious(page);
    }
  });

  test('cannot access /student/* — redirected to /admin/dashboard', async ({ page }) => {
    await page.goto('/student/schedule');
    // Role-guard redirect на admin landing
    await expect(page).toHaveURL(/\/admin\//, { timeout: 5_000 });
  });
});
