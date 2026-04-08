import { ChangeDetectionStrategy, Component, inject, Input } from '@angular/core';
import { ThemeService } from './theme.service';

/**
 * RutCampusTrack — Theme Toggle (Angular)
 *
 * Pill button that switches between dark and light themes. Uses Transit Grid
 * CSS variables for all colors so it auto-theme-matches. Accessible via
 * role="switch" and aria-checked.
 */
@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      role="switch"
      [attr.aria-checked]="themeService.isDark()"
      [attr.aria-label]="themeService.isDark() ? 'Включить светлую тему' : 'Включить тёмную тему'"
      [title]="themeService.isDark() ? 'Светлая тема' : 'Тёмная тема'"
      [class.compact]="compact"
      class="theme-toggle"
      (click)="themeService.toggle()"
    >
      @if (themeService.isDark()) {
        <!-- Moon -->
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      } @else {
        <!-- Sun -->
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2" />
          <path d="M12 20v2" />
          <path d="m4.93 4.93 1.41 1.41" />
          <path d="m17.66 17.66 1.41 1.41" />
          <path d="M2 12h2" />
          <path d="M20 12h2" />
          <path d="m6.34 17.66-1.41 1.41" />
          <path d="m19.07 4.93-1.41 1.41" />
        </svg>
      }
    </button>
  `,
  styles: [
    `
      .theme-toggle {
        width: 40px;
        height: 40px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--radius-full);
        background: var(--bg-secondary);
        border: 1px solid var(--border-subtle);
        color: var(--text-primary);
        cursor: pointer;
        padding: 0;
        transition:
          background-color var(--duration-base) var(--ease-out),
          border-color var(--duration-base) var(--ease-out),
          transform var(--duration-fast) var(--ease-out),
          box-shadow var(--duration-base) var(--ease-out);
      }
      .theme-toggle.compact {
        width: 32px;
        height: 32px;
      }
      .theme-toggle svg {
        width: 20px;
        height: 20px;
      }
      .theme-toggle.compact svg {
        width: 16px;
        height: 16px;
      }
      .theme-toggle:hover {
        border-color: var(--border-accent);
        box-shadow: var(--glow-primary);
      }
      .theme-toggle:active {
        transform: scale(0.94);
      }
      .theme-toggle:focus-visible {
        outline: 2px solid var(--accent-primary);
        outline-offset: 2px;
      }
    `,
  ],
})
export class ThemeToggleComponent {
  themeService = inject(ThemeService);
  @Input() compact = false;
}
