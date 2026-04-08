import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { studentGuard } from './core/auth/student.guard';
import { headmanGuard } from './core/auth/headman.guard';
import { guestGuard } from './core/auth/guest.guard';

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
      // Teacher routes
      {
        path: 'teacher',
        canActivate: [roleGuard(['TEACHER'])],
        data: { eyebrow: 'Преподаватель' },
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/teacher/dashboard/teacher-dashboard.component').then(
                m => m.TeacherDashboardComponent,
              ),
            data: { title: 'Дашборд', eyebrow: 'Преподаватель' },
          },
          {
            path: 'journal',
            loadComponent: () =>
              import('./features/teacher/journal/journal-page.component').then(
                m => m.JournalPageComponent,
              ),
            data: { title: 'Журнал посещаемости', eyebrow: 'Преподаватель' },
          },
          {
            path: 'stats',
            loadComponent: () =>
              import('./features/teacher/stats/stats-page.component').then(
                m => m.StatsPageComponent,
              ),
            data: { title: 'Статистика', eyebrow: 'Преподаватель' },
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      // Admin routes
      {
        path: 'admin',
        canActivate: [roleGuard(['ADMIN'])],
        data: { eyebrow: 'Администратор' },
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/admin/dashboard/admin-dashboard.component').then(
                m => m.AdminDashboardComponent,
              ),
            data: { title: 'Дашборд', eyebrow: 'Администратор' },
          },
          {
            path: 'users',
            loadComponent: () =>
              import('./features/admin/users/users-page.component').then(m => m.UsersPageComponent),
            data: { title: 'Пользователи', eyebrow: 'Администратор' },
          },
          {
            path: 'groups',
            loadComponent: () =>
              import('./features/admin/groups/groups-page.component').then(m => m.GroupsPageComponent),
            data: { title: 'Группы', eyebrow: 'Администратор' },
          },
          {
            path: 'semesters',
            loadComponent: () =>
              import('./features/admin/semesters/semesters-page.component').then(m => m.SemestersPageComponent),
            data: { title: 'Семестры', eyebrow: 'Администратор' },
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      // Student routes (D-07 — placeholder Phase 50, real в Phase 51-53)
      {
        path: 'student',
        canActivate: [studentGuard],
        data: { eyebrow: 'Студент' },
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/student/student-placeholder/student-placeholder.component').then(
                m => m.StudentPlaceholderComponent,
              ),
            data: { title: 'Личный кабинет', eyebrow: 'Студент' },
          },
          {
            path: 'schedule',
            loadComponent: () =>
              import('./features/student/student-placeholder/student-placeholder.component').then(
                m => m.StudentPlaceholderComponent,
              ),
            data: { title: 'Расписание', eyebrow: 'Студент' },
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      // Headman routes (D-07 — placeholder Phase 50, real в Phase 54-55)
      {
        path: 'headman',
        canActivate: [headmanGuard],
        data: { eyebrow: 'Староста' },
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/headman/headman-placeholder/headman-placeholder.component').then(
                m => m.HeadmanPlaceholderComponent,
              ),
            data: { title: 'Кабинет старосты', eyebrow: 'Староста' },
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
