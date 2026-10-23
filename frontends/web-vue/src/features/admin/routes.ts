import type { RouteRecordRaw } from 'vue-router'

export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/dashboard',
    name: 'admin-dashboard',
    component: () => import('./DashboardPage.vue'),
    meta: { label: 'Главная', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('./UsersPage.vue'),
    meta: { label: 'Пользователи', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/groups',
    name: 'admin-groups',
    component: () => import('./GroupsPage.vue'),
    meta: { label: 'Группы', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/semesters',
    name: 'admin-semesters',
    component: () => import('./SemestersPage.vue'),
    meta: { label: 'Семестры', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/map',
    name: 'admin-map',
    component: () => import('./MapPage.vue'),
    meta: { label: 'Карта', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/attendance',
    name: 'admin-attendance',
    component: () => import('./AttendancePage.vue'),
    meta: { label: 'Посещаемость групп', requiredRoles: ['ADMIN'], navHidden: true },
  },
]
