import type { RouteRecordRaw } from 'vue-router'
import HomePage from './HomePage.vue'
import JournalPage from './JournalPage.vue'
import StatisticsPage from './StatisticsPage.vue'
import HomeworkPage from './HomeworkPage.vue'
import RequestsPage from './RequestsPage.vue'
import GroupPage from './GroupPage.vue'
import SubjectsPage from './SubjectsPage.vue'
import SchedulePage from './SchedulePage.vue'
import LessonsPage from './LessonsPage.vue'

export const headmanRoutes: RouteRecordRaw[] = [
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
    meta: {
      label: 'Посещаемость',
      requiredRoles: ['HEADMAN', 'STUDENT'],
      assistantPermission: 'MARK_ATTENDANCE',
    },
  },
  {
    path: 'headman/statistics',
    name: 'headman-statistics',
    component: StatisticsPage,
    meta: {
      label: 'Статистика',
      requiredRoles: ['HEADMAN', 'STUDENT'],
      assistantPermission: 'VIEW_STATS',
    },
  },
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
    path: 'headman/requests',
    name: 'headman-requests',
    component: RequestsPage,
    meta: {
      label: 'Заявки и тикеты',
      requiredRoles: ['HEADMAN', 'STUDENT'],
      assistantPermission: 'MANAGE_EXCUSES',
    },
  },
  {
    path: 'headman/group',
    name: 'headman-group',
    component: GroupPage,
    meta: { label: 'Группа', requiredRoles: ['HEADMAN'] },
  },
  {
    path: 'headman/subjects',
    name: 'headman-subjects',
    component: SubjectsPage,
    meta: { label: 'Предметы', requiredRoles: ['HEADMAN'] },
  },
  {
    path: 'headman/schedule',
    name: 'headman-schedule',
    component: SchedulePage,
    meta: { label: 'Конструктор расписания', requiredRoles: ['HEADMAN'] },
  },
  {
    path: 'headman/lessons',
    name: 'headman-lessons',
    component: LessonsPage,
    meta: {
      label: 'Управление парами',
      requiredRoles: ['HEADMAN', 'STUDENT'],
      assistantPermission: 'CANCEL_LESSONS',
    },
  },
]
