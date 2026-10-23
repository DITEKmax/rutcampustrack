import type { RouteRecordRaw } from 'vue-router'
import HomeworkPage from '../features/student-homework/HomeworkPage.vue'

/** A role feature owns its pages and exports records; only this registry integrates them. */
export const webFeatures: RouteRecordRaw[] = [
  {
    name: 'student-homework',
    path: '/student/homework',
    meta: { label: 'Домашнее задание', requiredRoles: ['STUDENT'] },
    component: HomeworkPage,
  },
]
