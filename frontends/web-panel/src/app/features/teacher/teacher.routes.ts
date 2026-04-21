import { Routes } from '@angular/router';

/**
 * Teacher-role feature routes (M07 G8 QC5 — lazy-loading per-role).
 *
 * Все leaf-компоненты остаются `loadComponent` — Angular build
 * делит каждый на свой chunk. Сам этот файл — тонкая обёртка,
 * которую приложение грузит через `loadChildren` из `app.routes.ts`,
 * что даёт чистое per-role разделение точки входа.
 */
export const TEACHER_ROUTES: Routes = [
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/teacher-dashboard.component').then(
        m => m.TeacherDashboardComponent,
      ),
    data: { title: 'Дашборд', eyebrow: 'Преподаватель' },
  },
  {
    path: 'journal',
    loadComponent: () =>
      import('./journal/journal-page.component').then(m => m.JournalPageComponent),
    data: { title: 'Журнал посещаемости', eyebrow: 'Преподаватель' },
  },
  {
    path: 'stats',
    loadComponent: () =>
      import('./stats/stats-page.component').then(m => m.StatsPageComponent),
    data: { title: 'Статистика', eyebrow: 'Преподаватель' },
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
