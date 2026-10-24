import { createRouter, createWebHistory } from 'vue-router'
import type { ProfileRole } from '../auth/session'
import { webSession } from '../auth/session'
import LoginPage from '../auth/LoginPage.vue'
import OtpPage from '../auth/OtpPage.vue'
import QrLoginPage from '../auth/QrLoginPage.vue'
import PasswordRecoveryPage from '../auth/PasswordRecoveryPage.vue'
import AppShell from '../shell/AppShell.vue'
import RoleLanding from '../shell/RoleLanding.vue'
import { webFeatures } from './features'
import { webAssistantAccess } from './assistant'
import { AdminAccessClient } from '../../../mobile-core/src/features/admin-access/admin-access-client'
import { createWebTransport } from './api'
import { chooseRoleHome, internalReturnPath, roleHomeNotice } from './role-home'
import type { HeadmanAssistantPermission } from '../../../mobile-core/src/features/headman-group/headman-group-client'

declare module 'vue-router' {
  interface RouteMeta {
    guest?: boolean
    requiredRoles?: readonly ProfileRole[]
    label?: string
    navHidden?: boolean
    assistantPermission?: HeadmanAssistantPermission
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginPage, meta: { guest: true } },
    { path: '/login/otp', name: 'login-otp', component: OtpPage, meta: { guest: true } },
    { path: '/login/qr', name: 'login-qr', component: QrLoginPage, meta: { guest: true } },
    {
      path: '/password-reset',
      name: 'password-recovery',
      component: PasswordRecoveryPage,
      meta: { guest: true },
    },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', name: 'role-home', component: RoleLanding },
        ...webFeatures,
        { path: ':pathMatch(.*)*', name: 'unavailable', component: RoleLanding },
      ],
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (webSession.phase.value === 'starting') await webSession.bootstrap()
  const session = webSession.snapshot.value
  if (!session) return to.meta.guest ? true : { name: 'login', query: { redirect: to.fullPath } }
  if (to.name !== 'settings') roleHomeNotice.value = ''
  if (to.meta.guest && to.name !== 'password-recovery')
    return { name: 'role-home', query: to.query, hash: to.hash }
  if (to.name === 'role-home' && session.activeRole) {
    const returning = internalReturnPath(to.query.redirect)
    if (returning) {
      const target = router.resolve(returning)
      if (
        target.matched.length &&
        !target.meta.guest &&
        target.name !== 'role-home' &&
        target.name !== 'unavailable'
      )
        return returning
    }
    const query = { ...to.query }
    delete query.redirect
    const owner = webSession.identity.value
    let admin = null
    if (session.activeRole === 'ADMIN') {
      const transport = createWebTransport()
      try {
        admin = await new AdminAccessClient(transport).getMe()
        transport.assertCurrent()
        if (String(admin.userId) !== session.userId) throw new Error('Владелец доступа изменился.')
      } catch {
        if (owner !== webSession.identity.value) return false
        roleHomeNotice.value =
          'Не удалось проверить административный доступ. Здесь доступны настройки твоего аккаунта. Попробуй открыть главную ещё раз.'
        return { name: 'settings', query, hash: to.hash, replace: true }
      }
    }
    if (owner !== webSession.identity.value) return false
    const home = chooseRoleHome(session.activeRole, webFeatures, admin)
    if (!home)
      roleHomeNotice.value =
        'Доступные разделы этой роли сейчас не найдены. Здесь доступны настройки твоего аккаунта.'
    return { name: home?.name ?? 'settings', query, hash: to.hash, replace: true }
  }
  if (
    to.meta.requiredRoles &&
    (!session.activeRole || !to.meta.requiredRoles.includes(session.activeRole))
  )
    return { name: 'role-home', query: to.query, hash: to.hash }
  if (session.activeRole === 'STUDENT' && to.meta.assistantPermission) {
    try {
      if (!(await webAssistantAccess.load()).includes(to.meta.assistantPermission))
        return { name: 'role-home' }
    } catch {
      return { name: 'role-home' }
    }
  }
  return true
})
