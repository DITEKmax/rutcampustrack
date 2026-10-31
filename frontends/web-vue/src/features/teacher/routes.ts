import type { RouteRecordRaw } from 'vue-router'
import type { ProfileRole } from '../../auth/session'
import TeacherHomePage from './TeacherHomePage.vue'
import TeacherJournalPage from './TeacherJournalPage.vue'
import TeacherLessonPage from './TeacherLessonPage.vue'
import TeacherStatsPage from './TeacherStatsPage.vue'

const requiredRoles: readonly ProfileRole[] = ['TEACHER']
export const teacherRoutes: RouteRecordRaw[] = [
  {
    path: '/teacher/home',
    name: 'teacher-home',
    component: TeacherHomePage,
    meta: { requiredRoles, label: 'Главная' },
  },
  {
    path: '/teacher/journal',
    name: 'teacher-journal',
    component: TeacherJournalPage,
    meta: { requiredRoles, label: 'Посещаемость' },
  },
  {
    path: '/teacher/lessons/:lessonId',
    name: 'teacher-lesson',
    component: TeacherLessonPage,
    meta: { requiredRoles, label: 'Одна пара', navHidden: true },
  },
  {
    path: '/teacher/statistics',
    name: 'teacher-stats',
    component: TeacherStatsPage,
    meta: { requiredRoles, label: 'Статистика' },
  },
]
