import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ThemeService } from './theme.service';

const STORAGE_KEY = 'ruttrack.theme';
const LEGACY_STORAGE_KEY = 'web-panel.theme';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    document.documentElement.removeAttribute('data-theme');
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    document.documentElement.removeAttribute('data-theme');
    vi.restoreAllMocks();
  });

  it('initial theme reads from localStorage if "dark" stored', () => {
    localStorage.setItem(STORAGE_KEY, 'dark');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('initial theme reads "light" from localStorage if "light" stored', () => {
    localStorage.setItem(STORAGE_KEY, 'light');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('migrates legacy "web-panel.theme" key on first boot', () => {
    localStorage.setItem(LEGACY_STORAGE_KEY, 'light');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light');
    expect(localStorage.getItem(LEGACY_STORAGE_KEY)).toBeNull();
  });

  it('initial theme falls back to prefers-color-scheme when no localStorage entry', () => {
    // jsdom does not implement window.matchMedia, stub it.
    const originalMatchMedia = window.matchMedia;
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockReturnValue({
        // Service queries prefers-color-scheme: light → matches=false → dark
        matches: false,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }),
    });
    try {
      TestBed.configureTestingModule({ providers: [ThemeService] });
      const service = TestBed.inject(ThemeService);
      expect(service.isDark()).toBe(true);
    } finally {
      // Restore whatever value existed before this test so subsequent tests
      // (which also construct the service and may touch matchMedia) aren't
      // broken by a lingering undefined.
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        configurable: true,
        value: originalMatchMedia,
      });
    }
  });

  it('toggle() switches isDark and persists to localStorage', () => {
    localStorage.setItem(STORAGE_KEY, 'light');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);

    service.toggle();

    expect(service.isDark()).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
  });

  it('toggle() adds "dark" class and data-theme="dark" when switching to dark', () => {
    localStorage.setItem(STORAGE_KEY, 'light');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);

    service.toggle();

    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('toggle() removes "dark" class and sets data-theme="light" when switching to light', () => {
    localStorage.setItem(STORAGE_KEY, 'dark');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    service.toggle();

    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light');
  });

  it('setTheme() allows setting theme directly', () => {
    // Seed a stored value so the constructor skips the matchMedia fallback.
    localStorage.setItem(STORAGE_KEY, 'dark');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);

    service.setTheme('light');
    expect(service.theme()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');

    service.setTheme('dark');
    expect(service.theme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
