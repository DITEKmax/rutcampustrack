import { Routes } from '@angular/router';

/**
 * Headman-role feature routes (M07 G8 QC5 — lazy-loading per-role).
 * D-07 / Phase 54 original structure preserved.
 *
 * Примечание: внутренние `canActivate: [headmanGuard]` на каждом child
 * убраны — parent `canActivate` в app.routes.ts уже блокирует всю ветку,
 * двойная проверка не добавляла гарантий.
 */
export const HEADMAN_ROUTES: Routes = [
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/headman-dashboard.component').then(
        m => m.HeadmanDashboardComponent,
      ),
    data: { title: 'Кабинет старосты', eyebrow: 'Староста' },
  },
  {
    path: 'group',
    loadComponent: () =>
      import('./group/headman-group.component').then(
        m => m.HeadmanGroupComponent,
      ),
    data: { title: 'Группа', eyebrow: 'Староста' },
  },
  {
    path: 'subjects',
    loadComponent: () =>
      import('./subjects/headman-subjects.component').then(
        m => m.HeadmanSubjectsComponent,
      ),
    data: { title: 'Предметы', eyebrow: 'Староста' },
  },
  {
    path: 'homework',
    loadComponent: () =>
      import('./homework/headman-homework.component').then(
        m => m.HeadmanHomeworkComponent,
      ),
    data: { title: 'Домашние задания', eyebrow: 'Староста' },
  },
  {
    path: 'journal',
    loadComponent: () =>
      import('./journal/headman-journal-page.component').then(
        m => m.HeadmanJournalPageComponent,
      ),
    data: { title: 'Журнал', eyebrow: 'Староста' },
  },
  {
    path: 'weekly-journal',
    loadComponent: () =>
      import('./weekly-journal/headman-weekly-journal.component').then(
        m => m.HeadmanWeeklyJournalComponent,
      ),
    data: { title: 'Журнал посещаемости', eyebrow: 'Староста' },
  },
  {
    path: 'schedule',
    loadComponent: () =>
      import('./schedule/headman-schedule.component').then(
        m => m.HeadmanScheduleComponent,
      ),
    data: { title: 'Расписание', eyebrow: 'Староста' },
  },
  {
    path: 'lessons',
    loadComponent: () =>
      import('./lessons/headman-lessons.component').then(
        m => m.HeadmanLessonsComponent,
      ),
    data: { title: 'Пары на 2 недели', eyebrow: 'Староста' },
  },
  {
    path: 'excuses',
    loadComponent: () =>
      import('./excuses/headman-excuses.component').then(
        m => m.HeadmanExcusesComponent,
      ),
    data: { title: 'Пропуски', eyebrow: 'Староста' },
  },
  {
    path: 'late-checkin',
    loadComponent: () =>
      import('./late-checkin/headman-late-checkin.component').then(
        m => m.HeadmanLateCheckinComponent,
      ),
    data: { title: 'Запросы отметки', eyebrow: 'Староста' },
  },
  {
    path: 'stats',
    loadComponent: () =>
      import('./stats/headman-stats.component').then(
        m => m.HeadmanStatsComponent,
      ),
    data: { title: 'Статистика', eyebrow: 'Староста' },
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
