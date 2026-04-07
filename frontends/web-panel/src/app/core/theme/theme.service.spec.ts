import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    // Clear localStorage and html class before each test
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    vi.restoreAllMocks();
  });

  it('initial theme reads from localStorage if "dark" stored', () => {
    localStorage.setItem('web-panel.theme', 'dark');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('initial theme reads "light" from localStorage if "light" stored', () => {
    localStorage.setItem('web-panel.theme', 'light');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('initial theme falls back to prefers-color-scheme when no localStorage entry', () => {
    // jsdom does not implement window.matchMedia, so define it via Object.defineProperty
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockReturnValue({
        matches: true,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }),
    });
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(true);
    // Restore
    Object.defineProperty(window, 'matchMedia', { writable: true, configurable: true, value: undefined });
  });

  it('toggle() switches isDark and persists to localStorage', () => {
    localStorage.setItem('web-panel.theme', 'light');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(service.isDark()).toBe(false);

    service.toggle();

    expect(service.isDark()).toBe(true);
    expect(localStorage.getItem('web-panel.theme')).toBe('dark');
  });

  it('toggle() adds "dark" class to document.documentElement when switching to dark', () => {
    localStorage.setItem('web-panel.theme', 'light');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);

    service.toggle();

    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('toggle() removes "dark" class from document.documentElement when switching to light', () => {
    localStorage.setItem('web-panel.theme', 'dark');
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    service.toggle();

    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('web-panel.theme')).toBe('light');
  });
});
