import { Injectable, computed, signal } from '@angular/core';

export type Theme = 'dark' | 'light';

/**
 * RutCampusTrack — Transit Grid Theme Service (Angular)
 *
 * Manages the active theme via both the `[data-theme]` attribute on <html>
 * (primary mechanism read by tokens.css) and the legacy `.dark` class
 * (retained for Angular Material theming and existing chip utilities).
 *
 * Falls back to `prefers-color-scheme` when no stored value exists and
 * follows OS changes until the user explicitly overrides.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'ruttrack.theme';
  /** Legacy key from the pre-brandbook build — still read on first load. */
  private readonly LEGACY_STORAGE_KEY = 'web-panel.theme';

  private readonly _theme = signal<Theme>('dark');
  readonly theme = this._theme.asReadonly();
  readonly isDark = computed(() => this._theme() === 'dark');

  private userOverrode = false;

  constructor() {
    const stored = this.readStored();
    if (stored) {
      this._theme.set(stored);
      this.userOverrode = true;
    } else {
      this._theme.set(
        window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark',
      );
    }
    this.applyTheme();
    this.watchSystemPreference();
  }

  toggle(): void {
    this.setTheme(this._theme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(next: Theme): void {
    // Brief transition class so colors interpolate instead of snapping.
    const root = document.documentElement;
    root.classList.add('theme-transitioning');
    window.setTimeout(() => root.classList.remove('theme-transitioning'), 320);

    this.userOverrode = true;
    try {
      localStorage.setItem(this.STORAGE_KEY, next);
    } catch {
      // Storage can throw in private mode — ignore.
    }
    this._theme.set(next);
    this.applyTheme();
  }

  private readStored(): Theme | null {
    try {
      const raw = localStorage.getItem(this.STORAGE_KEY);
      if (raw === 'dark' || raw === 'light') return raw;
      // Migrate from legacy `web-panel.theme` key if present.
      const legacy = localStorage.getItem(this.LEGACY_STORAGE_KEY);
      if (legacy === 'dark' || legacy === 'light') {
        localStorage.setItem(this.STORAGE_KEY, legacy);
        localStorage.removeItem(this.LEGACY_STORAGE_KEY);
        return legacy;
      }
      return null;
    } catch {
      return null;
    }
  }

  private watchSystemPreference(): void {
    if (!window.matchMedia) return;
    const mql = window.matchMedia('(prefers-color-scheme: light)');
    mql.addEventListener('change', (e) => {
      if (this.userOverrode) return;
      this._theme.set(e.matches ? 'light' : 'dark');
      this.applyTheme();
    });
  }

  private applyTheme(): void {
    const root = document.documentElement;
    const current = this._theme();
    root.setAttribute('data-theme', current);
    root.classList.toggle('dark', current === 'dark');
  }
}
