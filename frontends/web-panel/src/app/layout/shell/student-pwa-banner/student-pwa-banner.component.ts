import {
  ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  trigger, transition, style, animate,
} from '@angular/animations';
import { StudentBannerService } from '../../../features/student/shared/student-banner.service';

/** Chrome/Edge beforeinstallprompt — not in @types/web for all targets */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * PWA install banner — STU-WEB-10.
 *
 * Renders only for STUDENT role when pwa-banner-dismissed is not set.
 * Supports three install paths:
 * 1. Chrome/Edge: native beforeinstallprompt → calls deferredPrompt.prompt()
 * 2. iOS Safari: shows instruction text + link to /app/
 * 3. Other browsers: shows generic link to /app/
 *
 * Dismiss (× button) persists pwa-banner-dismissed=true via StudentBannerService.
 */
@Component({
  selector: 'app-student-pwa-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  animations: [
    trigger('bannerSlide', [
      transition(':enter', [
        style({ transform: 'translateY(-100%)', opacity: 0 }),
        animate('200ms ease-out', style({ transform: 'translateY(0)', opacity: 1 })),
      ]),
      transition(':leave', [
        animate('150ms ease-in', style({ opacity: 0, transform: 'translateY(-8px)' })),
      ]),
    ]),
  ],
  templateUrl: './student-pwa-banner.component.html',
  styleUrl: './student-pwa-banner.component.css',
})
export class StudentPwaBannerComponent implements OnInit, OnDestroy {
  readonly bannerService = inject(StudentBannerService);

  readonly isIos = signal(false);
  readonly hasPrompt = signal(false);

  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  private readonly promptHandler = (e: Event): void => {
    e.preventDefault();
    this.deferredPrompt = e as BeforeInstallPromptEvent;
    this.hasPrompt.set(true);
  };

  ngOnInit(): void {
    this.bannerService.init();

    // Detect iOS (no beforeinstallprompt support)
    const ua = navigator.userAgent.toLowerCase();
    const isIosBrowser =
      /iphone|ipad|ipod/.test(ua) && !(window as unknown as Record<string, unknown>)['MSStream'];
    this.isIos.set(isIosBrowser);

    // Listen for Chrome/Edge install prompt
    if (!isIosBrowser) {
      window.addEventListener('beforeinstallprompt', this.promptHandler);
    }
  }

  ngOnDestroy(): void {
    window.removeEventListener('beforeinstallprompt', this.promptHandler);
  }

  async install(): Promise<void> {
    if (this.deferredPrompt) {
      this.deferredPrompt.prompt();
      const { outcome } = await this.deferredPrompt.userChoice;
      if (outcome === 'accepted') {
        this.bannerService.dismiss();
      }
      // Clear prompt regardless of outcome (browser won't re-fire)
      this.deferredPrompt = null;
      this.hasPrompt.set(false);
      // Keep banner visible if dismissed — user can still use the × button
    }
    // iOS / other: button is an <a> link to /app/ — no JS action needed
  }

  dismiss(): void {
    this.bannerService.dismiss();
  }

  /** Used to skip animation when user prefers-reduced-motion. */
  prefersReducedMotion(): boolean {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
