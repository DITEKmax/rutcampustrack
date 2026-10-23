import type { RouteRecordRaw } from 'vue-router'
import HomeworkPage from './HomeworkPage.vue'
import GroupPage from './GroupPage.vue'
import StatisticsPage from './StatisticsPage.vue'
import JournalPage from './JournalPage.vue'
import HomePage from './HomePage.vue'
import RequestsPage from './RequestsPage.vue'

export const headmanRoutes: RouteRecordRaw[] = [
  {
    path: 'headman/homework',
    name: 'headman-homework',
    component: HomeworkPage,
    meta: {
      label: 'Домашнее задание',
      requiredRoles: ['HEADMAN', 'STUDENT'],
      assistantPermission: 'MANAGE_HOMEWORK',
    },
  },
  {
    path: 'headman/group',
    name: 'headman-group',
    component: GroupPage,
    meta: { label: 'Группа', requiredRoles: ['HEADMAN'] },
  },
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
