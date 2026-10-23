import type { RouteRecordRaw } from 'vue-router'
import UsersPage from './UsersPage.vue'
import DashboardPage from './DashboardPage.vue'
import GroupsPage from './GroupsPage.vue'
import SemestersPage from './SemestersPage.vue'
import MapPage from './MapPage.vue'
import AttendancePage from './AttendancePage.vue'

export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/dashboard',
    name: 'admin-dashboard',
    component: DashboardPage,
    meta: { label: 'Главная', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: UsersPage,
    meta: { label: 'Пользователи', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/groups',
    name: 'admin-groups',
    component: GroupsPage,
    meta: { label: 'Группы', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/semesters',
    name: 'admin-semesters',
    component: SemestersPage,
    meta: { label: 'Семестры', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/map',
    name: 'admin-map',
    component: MapPage,
    meta: { label: 'Карта', requiredRoles: ['ADMIN'] },
  },
  {
    path: '/admin/attendance',
    name: 'admin-attendance',
    component: AttendancePage,
    meta: { label: 'Посещаемость групп', requiredRoles: ['ADMIN'], navHidden: true },
  },
]
