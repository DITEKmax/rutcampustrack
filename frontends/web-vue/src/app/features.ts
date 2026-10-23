import type { RouteRecordRaw } from 'vue-router'
import ProfilePage from '../features/profile/ProfilePage.vue'
import HomeworkPage from '../features/student-homework/HomeworkPage.vue'
import { headmanRoutes } from '../features/headman/routes'
import { adminRoutes } from '../features/admin/routes'
import { teacherRoutes } from '../features/teacher/routes'
import { webSession } from '../auth/session'
import { webAssistantAccess } from './assistant'
import type { HeadmanAssistantPermission } from '../../../mobile-core/src/features/headman-group/headman-group-client'

const helperPermissions: Record<string, HeadmanAssistantPermission> = {
  'headman-attendance': 'MARK_ATTENDANCE',
  'headman-requests': 'MANAGE_EXCUSES',
  'headman-statistics': 'VIEW_STATS',
}

function integrateRoute(route: RouteRecordRaw): RouteRecordRaw {
  const assistantPermission =
    route.meta?.assistantPermission ?? helperPermissions[String(route.name)]
  return {
    ...route,
    path: `/${route.path.replace(/^\//u, '')}`,
    meta: { ...route.meta, ...(assistantPermission ? { assistantPermission } : {}) },
  }
}

/** A role feature owns its pages and exports records; only this registry integrates them. */
export const webFeatures: RouteRecordRaw[] = [
  { name: 'settings', path: '/settings', component: ProfilePage, meta: { label: 'Настройки', navHidden: true, requiredRoles: ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'] } },
  ...headmanRoutes.map(integrateRoute),
  ...adminRoutes.map(integrateRoute),
  ...teacherRoutes.map(integrateRoute),
  {
    name: 'student-homework',
    path: '/student/homework',
    meta: { label: 'Домашнее задание', requiredRoles: ['STUDENT'] },
    component: HomeworkPage,
  },
]

export function canShowFeature(feature: RouteRecordRaw): boolean {
  const role = webSession.snapshot.value?.activeRole
  if (
    !role ||
    !feature.meta?.requiredRoles?.includes(role) ||
    feature.meta.navHidden ||
    feature.path.includes(':')
  )
    return false
  return (
    role !== 'STUDENT' ||
    !feature.meta.assistantPermission ||
    (webAssistantAccess.loaded.value &&
      webAssistantAccess.permissions.value.includes(feature.meta.assistantPermission))
  )
}
