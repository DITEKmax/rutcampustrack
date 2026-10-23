import type { RouteRecordRaw } from 'vue-router'
import StatisticsPage from './StatisticsPage.vue'
import JournalPage from './JournalPage.vue'
import HomePage from './HomePage.vue'
import RequestsPage from './RequestsPage.vue'

export const headmanRoutes: RouteRecordRaw[] = [
  {
    path: 'headman/statistics',
    name: 'headman-statistics',
    component: StatisticsPage,
    meta: { label: 'Статистика', requiredRoles: ['HEADMAN', 'STUDENT'] },
  },
  {
    path: 'headman',
    name: 'headman-home',
    component: HomePage,
    meta: { label: 'Главная', requiredRoles: ['HEADMAN'] },
  },
  {
    path: 'headman/attendance',
    name: 'headman-attendance',
    component: JournalPage,
    meta: { label: 'Посещаемость', requiredRoles: ['HEADMAN', 'STUDENT'] },
  },
  {
    path: 'headman/requests',
    name: 'headman-requests',
    component: RequestsPage,
    meta: { label: 'Заявки и тикеты', requiredRoles: ['HEADMAN', 'STUDENT'] },
  },
]
