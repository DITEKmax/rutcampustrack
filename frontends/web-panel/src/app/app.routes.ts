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
      // Student routes — Phase 51 (STU-WEB-01..03)
      {
        path: 'student',
        canActivate: [studentGuard],
        data: { eyebrow: 'Студент' },
        children: [
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/student/dashboard/student-dashboard.component').then(
                m => m.StudentDashboardComponent,
              ),
            data: { title: 'Главная', eyebrow: 'Студент' },
          },
          {
            path: 'schedule',
            loadComponent: () =>
              import('./features/student/schedule/student-schedule.component').then(
                m => m.StudentScheduleComponent,
              ),
            data: { title: 'Расписание', eyebrow: 'Студент' },
          },
          {
            path: 'checkin',
            loadComponent: () =>
              import('./features/student/checkin/student-checkin.component').then(
                m => m.StudentCheckinComponent,
              ),
            data: { title: 'Отметиться', eyebrow: 'Студент' },
          },
          {
            path: 'homework',
            loadComponent: () =>
              import('./features/student/homework/student-homework.component').then(
                m => m.StudentHomeworkComponent,
              ),
            data: { title: 'Домашние задания', eyebrow: 'Студент' },
          },
          {
            path: 'stats',
            loadComponent: () =>
              import('./features/student/stats/student-stats.component').then(
                m => m.StudentStatsComponent,
              ),
            data: { title: 'Статистика', eyebrow: 'Студент' },
          },
          {
            path: 'notifications',
            loadComponent: () =>
              import('./features/student/notifications/student-notifications.component').then(
                m => m.StudentNotificationsComponent,
              ),
            data: { title: 'Уведомления', eyebrow: 'Студент' },
          },
          {
            path: 'profile',
            loadComponent: () =>
              import('./features/student/profile/student-profile.component').then(
                m => m.StudentProfileComponent,
              ),
            data: { title: 'Профиль', eyebrow: 'Студент' },
          },
          {
            path: 'excuses',
            loadComponent: () =>
              import('./features/student/excuses/student-excuses.component')
                .then(m => m.StudentExcusesComponent),
            data: { title: 'Пропуски', eyebrow: 'Студент' },
          },
          {
            path: 'late-checkin',
            loadComponent: () =>
              import('./features/student/late-checkin/student-late-checkin.component')
                .then(m => m.StudentLateCheckinComponent),
            data: { title: 'Запрос отметки', eyebrow: 'Студент' },
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      // Headman routes (D-07 — Phase 54)
      {
        path: 'headman',
        canActivate: [headmanGuard],
        data: { eyebrow: 'Староста' },
        children: [
          {
            path: 'dashboard',
            canActivate: [headmanGuard],
            loadComponent: () =>
              import('./features/headman/dashboard/headman-dashboard.component').then(
                m => m.HeadmanDashboardComponent,
              ),
            data: { title: 'Кабинет старосты', eyebrow: 'Староста' },
          },
          {
            path: 'group',
            canActivate: [headmanGuard],
            loadComponent: () =>
              import('./features/headman/group/headman-group.component').then(
                m => m.HeadmanGroupComponent,
              ),
            data: { title: 'Группа', eyebrow: 'Староста' },
          },
          {
            path: 'subjects',
            canActivate: [headmanGuard],
            loadComponent: () =>
              import('./features/headman/subjects/headman-subjects.component').then(
                m => m.HeadmanSubjectsComponent,
              ),
            data: { title: 'Предметы', eyebrow: 'Староста' },
          },
          {
            path: 'journal',
            canActivate: [headmanGuard],
            loadComponent: () =>
              import('./features/headman/journal/headman-journal-page.component').then(
                m => m.HeadmanJournalPageComponent,
              ),
            data: { title: 'Журнал', eyebrow: 'Староста' },
          },
          {
            path: 'excuses',
            canActivate: [headmanGuard],
            loadComponent: () =>
              import('./features/headman/excuses/headman-excuses.component').then(
                m => m.HeadmanExcusesComponent,
              ),
            data: { title: 'Пропуски', eyebrow: 'Староста' },
          },
          {
            path: 'late-checkin',
            canActivate: [headmanGuard],
            loadComponent: () =>
              import('./features/headman/late-checkin/headman-late-checkin.component').then(
                m => m.HeadmanLateCheckinComponent,
              ),
            data: { title: 'Запросы отметки', eyebrow: 'Староста' },
          },
          {
            path: 'stats',
            canActivate: [headmanGuard],
            loadComponent: () =>
              import('./features/headman/stats/headman-stats.component').then(
                m => m.HeadmanStatsComponent,
              ),
            data: { title: 'Статистика', eyebrow: 'Староста' },
          },
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
