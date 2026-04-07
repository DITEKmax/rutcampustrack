import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { render, screen } from '@testing-library/angular';
import { SidebarComponent } from './sidebar.component';
import { AuthService, AuthUser } from '../../core/auth/auth.service';
import { AuthApi } from '../../core/auth/auth.api';
import { ThemeService } from '../../core/theme/theme.service';
import { signal, computed } from '@angular/core';

const makeJwt = (payload: object): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
};

const TEACHER_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', exp: 9999999999 });
const ADMIN_TOKEN = makeJwt({ sub: '2', role: 'ADMIN', exp: 9999999999 });

function makeAuthServiceMock(user: AuthUser | null) {
  const _accessToken = signal<string | null>(user ? TEACHER_TOKEN : null);
  return {
    currentUser: computed((): AuthUser | null => user),
    isAuthenticated: computed(() => user !== null),
    accessToken: _accessToken.asReadonly(),
    logout: vi.fn().mockResolvedValue(undefined),
    setTokens: vi.fn(),
    clearTokens: vi.fn(),
    getRefreshToken: vi.fn().mockReturnValue(null),
  };
}

const mockAuthApi = {
  login: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
} as unknown as AuthApi;

const mockThemeService = {
  isDark: signal(false).asReadonly(),
  toggle: vi.fn(),
};

describe('SidebarComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('renders filtered nav items for TEACHER role', async () => {
    const teacherUser: AuthUser = { id: 1, role: 'TEACHER' };
    const authServiceMock = makeAuthServiceMock(teacherUser);

    await render(SidebarComponent, {
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceMock },
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: ThemeService, useValue: mockThemeService },
      ],
    });

    expect(screen.getByText('Журнал посещаемости')).toBeTruthy();
    expect(screen.getByText('Статистика')).toBeTruthy();
    // Admin items should not be present
    expect(screen.queryByText('Пользователи')).toBeNull();
    expect(screen.queryByText('Группы')).toBeNull();
    expect(screen.queryByText('Семестры')).toBeNull();
  });

  it('renders filtered nav items for ADMIN role', async () => {
    const adminUser: AuthUser = { id: 2, role: 'ADMIN' };
    const authServiceMock = makeAuthServiceMock(adminUser);

    await render(SidebarComponent, {
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceMock },
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: ThemeService, useValue: mockThemeService },
      ],
    });

    expect(screen.getByText('Пользователи')).toBeTruthy();
    expect(screen.getByText('Группы')).toBeTruthy();
    expect(screen.getByText('Семестры')).toBeTruthy();
    expect(screen.getByText('Статистика')).toBeTruthy();
    // Teacher-only items should not be present
    expect(screen.queryByText('Журнал посещаемости')).toBeNull();
  });

  it('collapse toggle updates collapsed signal and persists to localStorage', async () => {
    const teacherUser: AuthUser = { id: 1, role: 'TEACHER' };
    const authServiceMock = makeAuthServiceMock(teacherUser);

    const { fixture } = await render(SidebarComponent, {
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceMock },
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: ThemeService, useValue: mockThemeService },
      ],
    });

    const component = fixture.componentInstance;
    expect(component.collapsed()).toBe(false);

    component.toggleCollapse();
    fixture.detectChanges();

    expect(component.collapsed()).toBe(true);
    expect(localStorage.getItem('web-panel.sidebar.collapsed')).toBe('true');
  });

  it('logout button calls authService.logout', async () => {
    const teacherUser: AuthUser = { id: 1, role: 'TEACHER' };
    const authServiceMock = makeAuthServiceMock(teacherUser);

    const { fixture } = await render(SidebarComponent, {
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceMock },
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: ThemeService, useValue: mockThemeService },
      ],
    });

    const component = fixture.componentInstance;
    await component.logout();

    expect(authServiceMock.logout).toHaveBeenCalled();
  });
});
