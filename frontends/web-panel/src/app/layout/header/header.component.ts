import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeToggleComponent } from '../../core/theme/theme-toggle.component';
import { ProfileService } from '../../core/profile/profile.service';
import { ProfileDialogComponent } from '../../core/profile/profile-dialog.component';
import { AvatarComponent } from '../../core/profile/avatar.component';
import { NotificationBellComponent } from '../../core/notifications/notification-bell.component';

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
  imports: [ThemeToggleComponent, MatDialogModule, AvatarComponent, NotificationBellComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
})
export class HeaderComponent {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly profileService = inject(ProfileService);
  private readonly dialog = inject(MatDialog);

  readonly currentUser = this.auth.currentUser;
  readonly profile = this.profileService.profile;
  readonly profileInitials = this.profileService.initials;
  readonly profileDisplayName = this.profileService.displayName;

  constructor() {
    if (this.auth.isAuthenticated()) this.profileService.load();
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.updateTitleFromRoute());
    this.updateTitleFromRoute();
  }

  openProfile(): void {
    this.profileService.load();
    this.dialog.open(ProfileDialogComponent, {
      width: '560px',
      panelClass: 'profile-dialog-panel',
      autoFocus: false,
    });
  }

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
    if (user.role === 'ADMIN') return 'Администратор';
    if (user.role === 'TEACHER') return 'Преподаватель';
    return user.isHeadman ? 'Староста' : 'Студент';
  });

  readonly roleChipClass = computed(() => {
    const user = this.currentUser();
    if (!user) return 'role-chip';
    const suffix =
      user.role === 'ADMIN' ? 'admin' :
      user.role === 'TEACHER' ? 'teacher' : 'student';
    return `role-chip role-chip--${suffix}`;
  });

  readonly userInitial = computed(() => {
    const fromProfile = this.profileInitials();
    if (fromProfile) return fromProfile;
    const user = this.currentUser();
    return user ? user.role[0] : '?';
  });

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
