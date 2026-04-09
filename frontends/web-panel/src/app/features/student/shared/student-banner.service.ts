import { Injectable, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';

const DISMISSED_KEY = 'pwa-banner-dismissed';
const SHOWN_KEY = 'pwa-banner-shown';

/**
 * Manages the PWA install banner state using localStorage flags.
 *
 * shouldShow logic:
 * 1. If 'pwa-banner-dismissed' === 'true' → never show
 * 2. If user.role !== 'STUDENT' → never show (banner is student-only)
 * 3. Otherwise → show (even on repeat visits until explicitly dismissed)
 *
 * STU-WEB-10: no forced redirect, no auto-dismiss on navigation.
 */
@Injectable({ providedIn: 'root' })
export class StudentBannerService {
  private readonly authService = inject(AuthService);

  readonly shouldShow = signal(false);

  /**
   * Call once after auth resolves (e.g. from StudentPwaBannerComponent.ngOnInit).
   * Safe to call multiple times — idempotent.
   */
  init(): void {
    const user = this.authService.currentUser();
    if (user?.role !== 'STUDENT') {
      this.shouldShow.set(false);
      return;
    }
    if (localStorage.getItem(DISMISSED_KEY) === 'true') {
      this.shouldShow.set(false);
      return;
    }
    localStorage.setItem(SHOWN_KEY, 'true');
    this.shouldShow.set(true);
  }

  /** Permanently suppress banner for this browser. */
  dismiss(): void {
    localStorage.setItem(DISMISSED_KEY, 'true');
    this.shouldShow.set(false);
  }
}
