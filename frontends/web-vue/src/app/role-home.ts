import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { ProfileRole } from '../auth/session'
import {
  adminHasPermission,
  type AdminAccessProfile,
} from '../../../mobile-core/src/features/admin-access/admin-access-client'

export const roleHomeNotice = ref('')
const homeNames: Record<ProfileRole, string> = {
  STUDENT: 'student-home',
  HEADMAN: 'headman-home',
  TEACHER: 'teacher-home',
  ADMIN: 'admin-dashboard',
}
// These are the existing page-entry permissions, not additional grants.
const adminEntryPermissions: Record<string, string> = {
  'admin-dashboard': 'DASHBOARD_VIEW',
  'admin-users': 'USERS_VIEW',
  'admin-groups': 'GROUPS_VIEW',
  'admin-semesters': 'SEMESTERS_VIEW',
  'admin-map': 'CAMPUS_MAP_MANAGE',
  'admin-attendance': 'DASHBOARD_VIEW',
}

export function chooseRoleHome(
  role: ProfileRole,
  routes: readonly RouteRecordRaw[],
  admin: AdminAccessProfile | null = null,
): RouteRecordRaw | undefined {
  const own = routes.filter((route) => {
    if (
      typeof route.name !== 'string' ||
      !route.name.startsWith(role.toLowerCase() + '-') ||
      !route.meta?.requiredRoles?.includes(role) ||
      route.meta.navHidden ||
      route.path.includes(':')
    )
      return false
    if (role !== 'ADMIN') return true
    const permission = adminEntryPermissions[route.name]
    return !!permission && adminHasPermission(admin, permission)
  })
  return own.find((route) => route.name === homeNames[role]) ?? own[0]
}

/** Only same-origin app paths may be resumed; router guards still admit the target. */
export function internalReturnPath(value: unknown): string | null {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\') ||
    [...value].some((character) => character.charCodeAt(0) <= 32 || character.charCodeAt(0) === 127)
  )
    return null
  return value
}
