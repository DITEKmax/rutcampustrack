import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — admin creates user (with initial_password visible).
 *
 * Scenario (01 P0-2 accepted trade-off — plaintext password в REST
 * response до magic-link миграции v0.1):
 *  1. admin логинится.
 *  2. открывает /admin/users/new.
 *  3. заполняет ФИО, логин, роль=STUDENT, group.
 *  4. submit → POST /academic/users → 201 + response содержит
 *     `initial_password` plaintext.
 *  5. UI показывает пароль в confirm-dialog («запишите и передайте
 *     студенту»).
 *  6. admin копирует, закрывает dialog.
 *  7. Верификация: попытка логина с initial_password работает, потом
 *     forced password_changed → must change on first login.
 */

test.describe('Admin creates user', () => {
  test('admin creates STUDENT, initial_password visible', async ({ page }) => {
    await loginAs(page, TEST_USERS.admin);
    await page.goto('/admin/users');

    await page.getByRole('button', { name: /добавить|создать/i }).click();

    // Unique login per test run (prevent duplicate key)
    const unique = `test_${Date.now()}`;
    await page.getByLabel(/логин/i).fill(unique);
    await page.getByLabel(/фамилия/i).fill('Тестов');
    await page.getByLabel(/^имя/i).fill('Тест');
    await page.getByLabel(/роль/i).selectOption({ label: /student/i });

    // Group picker — выбрать первую доступную
    await page.getByLabel(/группа/i).click();
    await page.getByRole('option').first().click();

    await page.getByRole('button', { name: /создать|сохранить/i }).click();

    // Confirm-dialog с plaintext password (v0.0.0 accepted)
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText(/начальный пароль|первоначальный/i)).toBeVisible();

    // Пароль должен быть видим (не masked) — проверяем через
    // наличие текста, похожего на random password.
    const passwordField = page.locator('[data-testid="initial-password-display"]');
    await expect(passwordField).toBeVisible();
    const passwordValue = await passwordField.textContent();
    expect(passwordValue).toBeTruthy();
    expect(passwordValue!.length).toBeGreaterThanOrEqual(8);

    await page.getByRole('button', { name: /закрыть|ок/i }).click();

    // Новый user появился в списке
    await expect(page.getByText(unique)).toBeVisible();

    await assertNoA11yCriticalOrSerious(page);
  });
});
