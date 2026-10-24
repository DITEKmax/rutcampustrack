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
    name: 'student-attendance',
    path: '/student/attendance',
    component: () => import('./StudentAttendancePage.vue'),
    meta: { label: 'Посещаемость', requiredRoles },
  },
  {
    name: 'student-statistics',
    path: '/student/statistics',
    component: () => import('./StudentStatisticsPage.vue'),
    meta: { label: 'Статистика', requiredRoles },
  },
  {
    name: 'student-schedule',
    path: '/student/schedule',
    component: () => import('./StudentSchedulePage.vue'),
    meta: { label: 'Расписание', requiredRoles },
  },
  {
    name: 'student-requests',
    path: '/student/requests',
    component: () => import('./StudentRequestsPage.vue'),
    meta: { label: 'Заявки и тикеты', requiredRoles },
  },
  {
    name: 'student-request',
    path: '/student/requests/:requestId',
    component: () => import('./StudentRequestsPage.vue'),
    meta: { label: 'Моя заявка', requiredRoles, navHidden: true },
  },
]
