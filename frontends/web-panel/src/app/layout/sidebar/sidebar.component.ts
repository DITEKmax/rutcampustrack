import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Router } from '@angular/router';
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
  animations: [
    trigger('collapse', [
      state('expanded', style({ width: '240px' })),
      state('collapsed', style({ width: '64px' })),
      transition('expanded <=> collapsed', animate('200ms ease-in-out')),
    ]),
    trigger('rotateChevron', [
      state('expanded', style({ transform: 'rotate(0deg)' })),
      state('collapsed', style({ transform: 'rotate(180deg)' })),
      transition('expanded <=> collapsed', animate('200ms ease-in-out')),
    ]),
  ],
})
export class SidebarComponent implements OnInit {
  private authService = inject(AuthService);
  private authApi = inject(AuthApi);
  private router = inject(Router);
  themeService = inject(ThemeService);

  readonly SIDEBAR_KEY = 'web-panel.sidebar.collapsed';

  collapsed = signal(false);
  currentUser = this.authService.currentUser;

  allNavItems: NavItem[] = [
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

  filteredNavItems = computed(() => {
    const user = this.currentUser();
    if (!user) return [];
    return this.allNavItems.filter(item => item.roles.includes(user.role));
  });

  ngOnInit(): void {
    // Restore collapse state from localStorage
    const stored = localStorage.getItem(this.SIDEBAR_KEY);
    if (stored === 'true') this.collapsed.set(true);

    // Auto-collapse on small screens
    if (window.innerWidth < 1024) this.collapsed.set(true);
  }

  toggleCollapse(): void {
    this.collapsed.update(v => !v);
    localStorage.setItem(this.SIDEBAR_KEY, String(this.collapsed()));
  }

  async logout(): Promise<void> {
    await this.authService.logout(this.authApi, this.router);
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }
}
