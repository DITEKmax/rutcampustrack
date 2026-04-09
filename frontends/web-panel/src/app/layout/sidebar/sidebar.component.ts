import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import {
  trigger,
  state,
  transition,
  style,
  animate,
} from '@angular/animations';
import { AuthService } from '../../core/auth/auth.service';
import { AuthApi } from '../../core/auth/auth.api';
import { ThemeService } from '../../core/theme/theme.service';
import { StudentNotificationBadgeService } from '../../features/student/shared/student-notification-badge.service';

/**
 * Sidebar navigation for the authenticated shell.
 *
 * Per brandbook §4.6: 260px expanded / 72px collapsed, bg-primary with a
 * subtle noise texture, active item styled with a 3px left accent border and
 * rgba tint, Phosphor icons throughout. Collapse state persists in
 * localStorage under `web-panel.sidebar.collapsed` and auto-collapses on
 * viewports narrower than 1024px on first load.
 */
interface NavItem {
  label: string;
  icon: string; // Phosphor icon class name
  route: string;
  roles: ('TEACHER' | 'ADMIN' | 'STUDENT')[];
  isHeadman?: boolean; // if true, only shown when currentUser().isHeadman === true
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css',
  animations: [
    trigger('collapse', [
      state('expanded', style({ width: '260px' })),
      state('collapsed', style({ width: '72px' })),
      transition('expanded <=> collapsed', animate('240ms cubic-bezier(0.16, 1, 0.3, 1)')),
    ]),
    trigger('rotateChevron', [
      state('expanded', style({ transform: 'rotate(0deg)' })),
      state('collapsed', style({ transform: 'rotate(180deg)' })),
      transition('expanded <=> collapsed', animate('200ms cubic-bezier(0.16, 1, 0.3, 1)')),
    ]),
  ],
})
export class SidebarComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  readonly themeService = inject(ThemeService);

  readonly SIDEBAR_KEY = 'web-panel.sidebar.collapsed';

  collapsed = signal(false);
  currentUser = this.authService.currentUser;
  readonly notificationBadge = inject(StudentNotificationBadgeService);
  readonly unreadCount = this.notificationBadge.unreadCount;

  /** Primary nav (dashboards) — always shown first when role matches. */
  readonly primaryItems: NavItem[] = [
    {
      label: 'Дашборд',
      icon: 'ph-squares-four',
      route: '/teacher/dashboard',
      roles: ['TEACHER'],
    },
    {
      label: 'Дашборд',
      icon: 'ph-squares-four',
      route: '/admin/dashboard',
      roles: ['ADMIN'],
    },
    {
      label: 'Главная',
      icon: 'ph-squares-four',
      route: '/student/dashboard',
      roles: ['STUDENT'],
    },
  ];

  /** Secondary nav — work pages under each section. */
  readonly allNavItems: NavItem[] = [
    // Teacher items
    {
      label: 'Журнал посещаемости',
      icon: 'ph-book-open',
      route: '/teacher/journal',
      roles: ['TEACHER'],
    },
    {
      label: 'Статистика',
      icon: 'ph-chart-bar',
      route: '/teacher/stats',
      roles: ['TEACHER'],
    },
    // Admin items
    {
      label: 'Пользователи',
      icon: 'ph-users',
      route: '/admin/users',
      roles: ['ADMIN'],
    },
    {
      label: 'Группы',
      icon: 'ph-users-three',
      route: '/admin/groups',
      roles: ['ADMIN'],
    },
    {
      label: 'Семестры',
      icon: 'ph-calendar',
      route: '/admin/semesters',
      roles: ['ADMIN'],
    },
    // Student items
    {
      label: 'Расписание',
      icon: 'ph-calendar-dots',
      route: '/student/schedule',
      roles: ['STUDENT'],
    },
    {
      label: 'Отметиться',
      icon: 'ph-map-pin',
      route: '/student/checkin',
      roles: ['STUDENT'],
    },
    {
      label: 'Домашние задания',
      icon: 'ph-notebook',
      route: '/student/homework',
      roles: ['STUDENT'],
    },
    {
      label: 'Статистика',
      icon: 'ph-chart-bar',
      route: '/student/stats',
      roles: ['STUDENT'],
    },
    {
      label: 'Уведомления',
      icon: 'ph-bell',
      route: '/student/notifications',
      roles: ['STUDENT'],
    },
    {
      label: 'Профиль',
      icon: 'ph-user-circle',
      route: '/student/profile',
      roles: ['STUDENT'],
    },
    {
      label: 'Пропуски',
      icon: 'ph-file-text',
      route: '/student/excuses',
      roles: ['STUDENT'],
    },
    {
      label: 'Запрос отметки',
      icon: 'ph-clock-countdown',
      route: '/student/late-checkin',
      roles: ['STUDENT'],
    },
    // Headman items (Старостат section) — shown only when isHeadman === true
    {
      label: 'Кабинет старосты',
      icon: 'ph-crown-simple',
      route: '/headman/dashboard',
      roles: ['STUDENT'],
      isHeadman: true,
    },
    {
      label: 'Группа',
      icon: 'ph-users-three',
      route: '/headman/group',
      roles: ['STUDENT'],
      isHeadman: true,
    },
    {
      label: 'Предметы',
      icon: 'ph-books',
      route: '/headman/subjects',
      roles: ['STUDENT'],
      isHeadman: true,
    },
  ];

  readonly filteredPrimaryItems = computed(() => {
    const user = this.currentUser();
    if (!user) return [];
    return this.primaryItems.filter((item) => item.roles.includes(user.role));
  });

  /** Non-headman secondary nav items — work pages for the user's role. */
  readonly filteredNavItems = computed(() => {
    const user = this.currentUser();
    if (!user) return [];
    return this.allNavItems
      .filter((item) => item.roles.includes(user.role))
      .filter((item) => !item.isHeadman);
  });

  /** Headman-only nav items (Старостат section) — shown only when isHeadman === true. */
  readonly filteredHeadmanNavItems = computed(() => {
    const user = this.currentUser();
    if (!user || !user.isHeadman) return [];
    return this.allNavItems.filter(
      (item) => item.roles.includes(user.role) && item.isHeadman === true,
    );
  });

  readonly sectionLabel = computed(() => {
    const user = this.currentUser();
    if (!user) return '';
    if (user.role === 'ADMIN') return 'Администрирование';
    if (user.role === 'STUDENT') return 'Учёба';
    return 'Работа'; // TEACHER
  });

  ngOnInit(): void {
    // Restore collapse state from localStorage
    const stored = localStorage.getItem(this.SIDEBAR_KEY);
    if (stored === 'true') this.collapsed.set(true);

    // Auto-collapse on small screens
    if (typeof window !== 'undefined' && window.innerWidth < 1024) {
      this.collapsed.set(true);
    }
  }

  toggleCollapse(): void {
    this.collapsed.update((v) => !v);
    localStorage.setItem(this.SIDEBAR_KEY, String(this.collapsed()));
  }

  async logout(): Promise<void> {
    await this.authService.logout(this.authApi, this.router);
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }
}
