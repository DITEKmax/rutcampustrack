import { Page, expect } from '@playwright/test';
import type { TestUser } from './users';

const roleLabels = { ADMIN: 'Администратор', TEACHER: 'Преподаватель', STUDENT: 'Студент', HEADMAN: 'Староста' };

/** A fixture must name the intended current role landing, rather than infer grants from isHeadman. */
export function webLoginIntent(user: TestUser) {
  const path = user.expectedLandingPath;
  const role = path === '/headman' ? 'HEADMAN' : path === '/teacher/home' ? 'TEACHER' :
    path === '/student/home' ? 'STUDENT' : /^\/admin\/(dashboard|users|groups|semesters|map)$/.test(path) ? 'ADMIN' : null;
  if (!role) throw new Error('PENDING_VUE_AUTH_FIXTURE: update the legacy expectedLandingPath and explicit role intent before browser actions');
  if (role !== user.role && !(role === 'HEADMAN' && user.role === 'STUDENT'))
    throw new Error('Conflicting fixture role and intended landing');
  return { path, role, label: roleLabels[role] };
}

/** Vue root password login, authoritative granted-role selection and exact intended landing. */
export async function loginAs(page: Page, user: TestUser): Promise<void> {
  const intent = webLoginIntent(user);
  await page.goto('/login');
  await page.getByLabel('Логин *', { exact: true }).fill(user.login);
  await page.getByLabel('Пароль *', { exact: true }).fill(user.password);
  const origin = new URL(page.url()).origin;
  const signedIn = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.origin === origin && url.pathname === '/api/auth/login' && response.request().method() === 'POST';
  });
  await page.getByRole('button', { name: 'Войти', exact: true }).click();
  expect((await signedIn).status(), 'Password login is server-confirmed').toBe(200);
  const header = page.locator('.web-header');
  await expect(header).toBeVisible();
  const role = header.locator('.web-header-role');
  const label = new RegExp('^' + intent.label + '(?: · Только чтение)?$');
  const choice = role.getByRole('button', { name: label });
  if (await choice.count()) {
    if (await choice.getAttribute('aria-pressed') !== 'true') {
      await expect(choice).toBeEnabled();
      await choice.click();
    }
    await expect(choice).toHaveAttribute('aria-pressed', 'true');
  } else {
    await expect(role.locator('.web-role-label')).toHaveText(label);
  }
  await expect(page).toHaveURL(url => url.pathname === intent.path && !url.search && !url.hash, { timeout: 15_000 });
}

export async function logout(page: Page): Promise<void> {
  const profile = page.getByRole('button', { name: /^Профиль:/ });
  if (await profile.getAttribute('aria-expanded') !== 'true') await profile.click();
  await page.locator('#web-profile-menu').getByRole('button', { name: 'Выйти', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/, { timeout: 10_000 });
  await expect(page.locator('.web-shell')).toHaveCount(0);
  await expect(page.getByLabel('Пароль *', { exact: true })).toHaveValue('');
}
