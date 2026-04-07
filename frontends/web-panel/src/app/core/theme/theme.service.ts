import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'web-panel.theme';
  private readonly _isDark = signal(false);
  readonly isDark = this._isDark.asReadonly();

  constructor() {
    // Read persisted preference, fallback to OS preference
    const stored = localStorage.getItem(this.STORAGE_KEY);
    if (stored) {
      this._isDark.set(stored === 'dark');
    } else {
      this._isDark.set(window.matchMedia('(prefers-color-scheme: dark)').matches);
    }
    this.applyTheme();
  }

  toggle(): void {
    this._isDark.update(v => !v);
    localStorage.setItem(this.STORAGE_KEY, this._isDark() ? 'dark' : 'light');
    this.applyTheme();
  }

  private applyTheme(): void {
    document.documentElement.classList.toggle('dark', this._isDark());
  }
}
