import { Routes } from '@angular/router';

/**
 * Student-role feature routes (M07 G8 QC5 — lazy-loading per-role).
 * Phase 51 (STU-WEB-01..03) original definitions preserved.
 */
export const STUDENT_ROUTES: Routes = [
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/student-dashboard.component').then(
        m => m.StudentDashboardComponent,
      ),
    data: { title: 'Главная', eyebrow: 'Студент' },
  },
  {
    path: 'schedule',
    loadComponent: () =>
      import('./schedule/student-schedule.component').then(
        m => m.StudentScheduleComponent,
      ),
    data: { title: 'Расписание', eyebrow: 'Студент' },
  },
  {
    path: 'checkin',
    loadComponent: () =>
      import('./checkin/student-checkin.component').then(
        m => m.StudentCheckinComponent,
      ),
    data: { title: 'Отметиться', eyebrow: 'Студент' },
  },
  {
    path: 'homework',
    loadComponent: () =>
      import('./homework/student-homework.component').then(
        m => m.StudentHomeworkComponent,
      ),
    data: { title: 'Домашние задания', eyebrow: 'Студент' },
  },
  {
    path: 'stats',
    loadComponent: () =>
      import('./stats/student-stats.component').then(
        m => m.StudentStatsComponent,
      ),
    data: { title: 'Статистика', eyebrow: 'Студент' },
  },
  {
    path: 'notifications',
    loadComponent: () =>
      import('./notifications/student-notifications.component').then(
        m => m.StudentNotificationsComponent,
      ),
    data: { title: 'Уведомления', eyebrow: 'Студент' },
  },
  {
    path: 'profile',
    loadComponent: () =>
      import('./profile/student-profile.component').then(
        m => m.StudentProfileComponent,
      ),
    data: { title: 'Профиль', eyebrow: 'Студент' },
  },
  {
    path: 'excuses',
    loadComponent: () =>
      import('./excuses/student-excuses.component').then(
        m => m.StudentExcusesComponent,
      ),
    data: { title: 'Пропуски', eyebrow: 'Студент' },
  },
  {
    path: 'late-checkin',
    loadComponent: () =>
      import('./late-checkin/student-late-checkin.component').then(
        m => m.StudentLateCheckinComponent,
      ),
    data: { title: 'Запрос отметки', eyebrow: 'Студент' },
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
