import type { RouteRecordRaw } from 'vue-router'
import UsersPage from './UsersPage.vue'

export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin/users',
    name: 'admin-users',
    component: UsersPage,
    meta: { label: 'Пользователи', requiredRoles: ['ADMIN'] },
  },
]
