import { expect, type Page } from '@playwright/test'
import type { TestUser } from './users'

export interface CurrentSession {
  userId: string
  sessionId: string
  sessionVersion: string
  activeRole: 'STUDENT' | 'HEADMAN' | 'TEACHER' | 'ADMIN' | null
  roles: { role: string; selectable: boolean }[]
}

/** UI login against the real Vue client and auth service; no API stubs. */
export async function loginPwa(page: Page, user: TestUser): Promise<{ accessToken: string; session: CurrentSession }> {
  await page.goto('/app/')
  await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible()
  await page.getByLabel('Логин', { exact: true }).fill(user.login)
  await page.getByLabel('Пароль', { exact: true }).fill(user.password)
  const loginResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST')
  const sessionResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/session' && response.request().method() === 'GET')
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  const login = await loginResponse
  expect(login.status()).toBe(200)
  const { accessToken } = await login.json() as { accessToken: string }
  expect(typeof accessToken).toBe('string')
  const current = await sessionResponse
  expect(current.status()).toBe(200)
  const session = await current.json() as CurrentSession
  if (session.activeRole === null) {
    await expect(page.getByRole('heading', { name: 'Сменить роль' })).toBeVisible()
  } else {
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible()
  }
  return { accessToken, session }
}

export async function logoutPwa(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Профиль', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Профиль', exact: true })).toBeVisible()
  const response = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/auth/logout' && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Выйти', exact: true }).click()
  expect((await response).status()).toBe(204)
  await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible()
}
