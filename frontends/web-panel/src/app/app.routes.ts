import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { studentGuard } from './core/auth/student.guard';
import { headmanGuard } from './core/auth/headman.guard';
import { guestGuard } from './core/auth/guest.guard';

/**
 * Top-level routes. Per-role feature routes moved to
 * `features/{role}/{role}.routes.ts` (M07 G8 QC5) — Angular build
 * emits each role's subtree as a separate chunk группа. Guards живут
 * на parent-level: canActivate срабатывает до того, как
 * loadChildren тащит role-chunk, поэтому чужую роль клиент даже не
 * скачивает.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/shell/shell.component').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'teacher',
        canActivate: [roleGuard(['TEACHER'])],
        data: { eyebrow: 'Преподаватель' },
        loadChildren: () =>
          import('./features/teacher/teacher.routes').then(m => m.TEACHER_ROUTES),
      },
      {
        path: 'admin',
        canActivate: [roleGuard(['ADMIN'])],
        data: { eyebrow: 'Администратор' },
        loadChildren: () =>
          import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES),
      },
      {
        path: 'student',
        canActivate: [studentGuard],
        data: { eyebrow: 'Студент' },
        loadChildren: () =>
          import('./features/student/student.routes').then(m => m.STUDENT_ROUTES),
      },
      {
        path: 'headman',
        canActivate: [headmanGuard],
        data: { eyebrow: 'Староста' },
        loadChildren: () =>
          import('./features/headman/headman.routes').then(m => m.HEADMAN_ROUTES),
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' },
      // Authenticated 404: any unknown path under the shell shows NotFound
      // instead of silently bouncing the user back to login.
      {
        path: '**',
        loadComponent: () =>
          import('./features/not-found/not-found.component').then(m => m.NotFoundComponent),
        data: { title: 'Страница не найдена', eyebrow: 'Ошибка' },
      },
    ],
  },
];
