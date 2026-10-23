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
  if (!session) return to.meta.guest ? true : { name: 'login' }
  if (to.meta.guest) return { name: 'role-home' }
  if (
    to.meta.requiredRoles &&
    (!session.activeRole || !to.meta.requiredRoles.includes(session.activeRole))
  )
    return { name: 'role-home' }
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
