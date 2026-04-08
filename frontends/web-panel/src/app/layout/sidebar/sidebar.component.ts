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
  roles: ('TEACHER' | 'ADMIN')[];
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
  ];

  readonly filteredPrimaryItems = computed(() => {
    const user = this.currentUser();
    if (!user) return [];
    return this.primaryItems.filter((item) => item.roles.includes(user.role));
  });

  readonly filteredNavItems = computed(() => {
    const user = this.currentUser();
    if (!user) return [];
    return this.allNavItems.filter((item) => item.roles.includes(user.role));
  });

  readonly sectionLabel = computed(() => {
    const user = this.currentUser();
    if (!user) return '';
    return user.role === 'ADMIN' ? 'Администрирование' : 'Работа';
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
