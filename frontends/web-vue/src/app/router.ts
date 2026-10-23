import { createRouter, createWebHistory } from 'vue-router'
import type { ProfileRole } from '../auth/session'
import { webSession } from '../auth/session'
import LoginPage from '../auth/LoginPage.vue'
import AppShell from '../shell/AppShell.vue'
import RoleLanding from '../shell/RoleLanding.vue'
import { webFeatures } from './features'

declare module 'vue-router' {
  interface RouteMeta {
    requiredRoles?: readonly ProfileRole[]
    label?: string
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginPage },
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
  if (!session) return to.name === 'login' ? true : { name: 'login' }
  if (to.name === 'login') return { name: 'role-home' }
  if (
    to.meta.requiredRoles &&
    (!session.activeRole || !to.meta.requiredRoles.includes(session.activeRole))
  )
    return { name: 'role-home' }
  return true
})
