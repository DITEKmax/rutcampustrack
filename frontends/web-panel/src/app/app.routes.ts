import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/shell/shell.component').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      // Teacher routes
      {
        path: 'teacher',
        canActivate: [roleGuard(['TEACHER'])],
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/teacher/dashboard/teacher-dashboard.component').then(
                m => m.TeacherDashboardComponent,
              ),
          },
          {
            path: 'journal',
            loadComponent: () =>
              import('./features/teacher/journal/journal-page.component').then(
                m => m.JournalPageComponent,
              ),
          },
          {
            path: 'stats',
            loadComponent: () =>
              import('./features/teacher/stats/stats-page.component').then(
                m => m.StatsPageComponent,
              ),
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      // Admin routes
      {
        path: 'admin',
        canActivate: [roleGuard(['ADMIN'])],
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/admin/dashboard/admin-dashboard.component').then(
                m => m.AdminDashboardComponent,
              ),
          },
          {
            path: 'users',
            loadComponent: () =>
              import('./features/admin/users/users-page.component').then(m => m.UsersPageComponent),
          },
          {
            path: 'groups',
            loadComponent: () =>
              import('./features/admin/groups/groups-page.component').then(m => m.GroupsPageComponent),
          },
          {
            path: 'semesters',
            loadComponent: () =>
              import('./features/admin/semesters/semesters-page.component').then(m => m.SemestersPageComponent),
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
