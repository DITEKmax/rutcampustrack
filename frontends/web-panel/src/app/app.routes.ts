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
              import('./shared/empty/empty.component').then(m => m.EmptyComponent),
          },
          {
            path: 'stats',
            loadComponent: () =>
              import('./shared/empty/empty.component').then(m => m.EmptyComponent),
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
              import('./shared/empty/empty.component').then(m => m.EmptyComponent),
          },
          {
            path: 'groups',
            loadComponent: () =>
              import('./shared/empty/empty.component').then(m => m.EmptyComponent),
          },
          {
            path: 'semesters',
            loadComponent: () =>
              import('./shared/empty/empty.component').then(m => m.EmptyComponent),
          },
          {
            path: 'stats',
            loadComponent: () =>
              import('./shared/empty/empty.component').then(m => m.EmptyComponent),
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
