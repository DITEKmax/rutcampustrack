import { Page, expect } from '@playwright/test';
import type { TestUser } from './users';

/**
 * Shared auth helper — кликает по login-форме web-panel'а и ждёт
 * redirect на роль-specific landing path.
 *
 * Web-panel (Angular) — single SPA на baseHref `/`. После login
 * role-guard роутит на `/student/*`, `/teacher/*`, `/admin/*`,
 * `/headman/*` в зависимости от `role` + `is_headman`.
 */
export async function loginAs(page: Page, user: TestUser): Promise<void> {
  await page.goto('/login');

  await page.getByRole('textbox', { name: /логин/i }).fill(user.login);
  await page.getByRole('textbox', { name: /пароль/i }).fill(user.password);
  await page.getByRole('button', { name: /войти/i }).click();

  await expect(page).toHaveURL(
    new RegExp(user.expectedLandingPath.replace(/\//g, '\\/')),
    { timeout: 15_000 }
  );
}

export async function logout(page: Page): Promise<void> {
  // Menu drawer → logout (структура web-panel'а из M07).
  await page.getByRole('button', { name: /меню/i }).click();
  await page.getByRole('menuitem', { name: /выйти/i }).click();

  // Ожидаем redirect на /login + очистку state.
  await expect(page).toHaveURL(/\/login$/, { timeout: 10_000 });
}
