import { Routes } from '@angular/router';

/**
 * Admin-role feature routes (M07 G8 QC5 — lazy-loading per-role).
 */
export const ADMIN_ROUTES: Routes = [
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/admin-dashboard.component').then(
        m => m.AdminDashboardComponent,
      ),
    data: { title: 'Дашборд', eyebrow: 'Администратор' },
  },
  {
    path: 'users',
    loadComponent: () =>
      import('./users/users-page.component').then(m => m.UsersPageComponent),
    data: { title: 'Пользователи', eyebrow: 'Администратор' },
  },
  {
    path: 'groups',
    loadComponent: () =>
      import('./groups/groups-page.component').then(m => m.GroupsPageComponent),
    data: { title: 'Группы', eyebrow: 'Администратор' },
  },
  {
    // BUG-006-6 / plan 58-08: read-only история архивной группы.
    path: 'groups/:id/history',
    loadComponent: () =>
      import('./groups/group-history/group-history-page.component').then(
        m => m.GroupHistoryPageComponent,
      ),
    data: { title: 'История группы', eyebrow: 'Администратор' },
  },
  {
    path: 'semesters',
    loadComponent: () =>
      import('./semesters/semesters-page.component').then(
        m => m.SemestersPageComponent,
      ),
    data: { title: 'Семестры', eyebrow: 'Администратор' },
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
