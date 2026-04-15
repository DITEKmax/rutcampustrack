import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
  RouterOutlet,
} from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { HeaderComponent } from '../header/header.component';
import { StudentPwaBannerComponent } from './student-pwa-banner/student-pwa-banner.component';

/**
 * Authenticated shell layout.
 *
 * Grid: [sidebar | (header + main)]. Sidebar is sticky full-height, header is
 * sticky at the top of the content column, main scrolls independently so the
 * header stays anchored while pages scroll. Page content itself is wrapped in
 * a centered max-width container so long tables don't stretch edge-to-edge.
 *
 * A top-of-column progress bar surfaces lazy-chunk loads between route
 * navigations — the routes use `loadComponent()` and on a slow network the
 * main area otherwise sits on the previous page with no feedback.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    SidebarComponent,
    HeaderComponent,
    StudentPwaBannerComponent,
    MatProgressBarModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  private readonly router = inject(Router);

  readonly navigating = signal(false);

  constructor() {
    this.router.events.pipe(takeUntilDestroyed()).subscribe((e) => {
      if (e instanceof NavigationStart) this.navigating.set(true);
      else if (
        e instanceof NavigationEnd ||
        e instanceof NavigationCancel ||
        e instanceof NavigationError
      ) {
        this.navigating.set(false);
      }
    });
  }
}
