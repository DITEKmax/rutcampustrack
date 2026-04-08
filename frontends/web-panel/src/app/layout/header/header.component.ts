import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeToggleComponent } from '../../core/theme/theme-toggle.component';

/**
 * RutCampusTrack — Shell Header
 *
 * Sticky top header shown inside the authenticated shell. Exposes the current
 * page title (derived from route data), a theme toggle, and a user chip with
 * role + ID. Per brandbook §7 / §4.6 — surfaces sit on bg-secondary with a
 * subtle border-bottom and backdrop blur so content scrolls behind.
 */
@Component({
  selector: 'app-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ThemeToggleComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly currentUser = this.auth.currentUser;

  /** Page title + eyebrow derived from the currently activated route's `data`. */
  private readonly routeState = signal<{ title: string; eyebrow: string }>({
    title: 'Панель управления',
    eyebrow: 'RutCampusTrack',
  });

  readonly pageTitle = computed(() => this.routeState().title);
  readonly pageEyebrow = computed(() => this.routeState().eyebrow);

  readonly roleLabel = computed(() => {
    const user = this.currentUser();
    if (!user) return '';
    return user.role === 'ADMIN' ? 'Администратор' : 'Преподаватель';
  });

  readonly roleChipClass = computed(() => {
    const user = this.currentUser();
    if (!user) return 'role-chip';
    return user.role === 'ADMIN' ? 'role-chip role-chip--admin' : 'role-chip role-chip--teacher';
  });

  readonly userInitial = computed(() => {
    const user = this.currentUser();
    if (!user) return '?';
    return String(user.id).slice(0, 2).toUpperCase();
  });

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.updateTitleFromRoute());
    // Initial resolution (in case the first event fired before subscription).
    this.updateTitleFromRoute();
  }

  private updateTitleFromRoute(): void {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) route = route.firstChild;
    const data = route.data as { title?: string; eyebrow?: string };
    this.routeState.set({
      title: data.title ?? 'Панель управления',
      eyebrow: data.eyebrow ?? 'RutCampusTrack',
    });
  }
}
