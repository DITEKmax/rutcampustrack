import type { RouteRecordRaw } from 'vue-router'
import type { ProfileRole } from '../../auth/session'
const requiredRoles: readonly ProfileRole[] = ['STUDENT']
export const studentRoutes: RouteRecordRaw[] = [
  {
    name: 'student-home',
    path: '/student/home',
    component: () => import('./StudentHomePage.vue'),
    meta: { label: 'Главная', requiredRoles },
  },
  {
    name: 'student-schedule',
    path: '/student/schedule',
    component: () => import('./StudentSchedulePage.vue'),
    meta: { label: 'Расписание', requiredRoles },
  },
]
