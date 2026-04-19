import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthService } from './auth.service';
import { AuthApi } from './auth.api';
import { Router } from '@angular/router';

// Minimal valid JWT with payload { sub: "1", role: "TEACHER" }
const makeJwt = (payload: object): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
};

const ACCESS_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', is_headman: false, group_id: null, exp: 9999999999 });
const ADMIN_ACCESS_TOKEN = makeJwt({ sub: '2', role: 'ADMIN', is_headman: false, group_id: null, exp: 9999999999 });
const STUDENT_ACCESS_TOKEN = makeJwt({ sub: '3', role: 'STUDENT', is_headman: false, group_id: 5, exp: 9999999999 });
const HEADMAN_ACCESS_TOKEN = makeJwt({ sub: '4', role: 'STUDENT', is_headman: true, group_id: 5, exp: 9999999999 });
const TOKEN_WITHOUT_HEADMAN = makeJwt({ sub: '5', role: 'STUDENT', group_id: 7, exp: 9999999999 });
const TOKEN_WITHOUT_GROUP = makeJwt({ sub: '6', role: 'ADMIN', is_headman: false, exp: 9999999999 });
const REFRESH_TOKEN = 'refresh-token-abc';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [AuthService],
    });
    service = TestBed.inject(AuthService);
  });

  it('isAuthenticated() returns false initially', () => {
    expect(service.isAuthenticated()).toBe(false);
  });

  it('setTokens(access, refresh) makes isAuthenticated() return true', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('accessToken() returns the access token after setTokens', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);
    expect(service.accessToken()).toBe(ACCESS_TOKEN);
  });

  it('clearTokens() makes isAuthenticated() return false', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);
    service.clearTokens();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('clearTokens() makes accessToken() return null', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);
    service.clearTokens();
    expect(service.accessToken()).toBeNull();
  });

  it('currentUser() returns null when no token set', () => {
    expect(service.currentUser()).toBeNull();
  });

  it('currentUser() returns { id, role } parsed from JWT payload after setTokens', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user).not.toBeNull();
    expect(user!.id).toBe(1);
    expect(user!.role).toBe('TEACHER');
  });

  it('currentUser() parses ADMIN role correctly', () => {
    service.setTokens(ADMIN_ACCESS_TOKEN, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user!.role).toBe('ADMIN');
  });

  it('currentUser() normalizes lowercase role to uppercase', () => {
    const tokenWithLowercaseRole = makeJwt({ sub: '1', role: 'admin', exp: 9999999999 });
    service.setTokens(tokenWithLowercaseRole, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user!.role).toBe('ADMIN');
  });

  it('logout() calls AuthApi.logout (cookie-based), then clearTokens', async () => {
    const mockRouter = { navigate: vi.fn() } as unknown as Router;
    const { of } = await import('rxjs');
    const mockAuthApi = {
      logout: vi.fn().mockReturnValue(of(undefined)),
    } as unknown as AuthApi;

    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);
    await service.logout(mockAuthApi, mockRouter);

    expect(mockAuthApi.logout).toHaveBeenCalledWith();
    expect(service.isAuthenticated()).toBe(false);
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('currentUser() returns STUDENT role with isHeadman=false and groupId from JWT', () => {
    service.setTokens(STUDENT_ACCESS_TOKEN, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user).not.toBeNull();
    expect(user!.id).toBe(3);
    expect(user!.role).toBe('STUDENT');
    expect(user!.isHeadman).toBe(false);
    expect(user!.groupId).toBe(5);
  });

  it('currentUser() returns STUDENT role with isHeadman=true for headman token', () => {
    service.setTokens(HEADMAN_ACCESS_TOKEN, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user!.id).toBe(4);
    expect(user!.role).toBe('STUDENT');
    expect(user!.isHeadman).toBe(true);
    expect(user!.groupId).toBe(5);
  });

  it('currentUser() defaults isHeadman to false when claim is absent', () => {
    service.setTokens(TOKEN_WITHOUT_HEADMAN, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user!.isHeadman).toBe(false);
  });

  it('currentUser() defaults groupId to null when claim is absent', () => {
    service.setTokens(TOKEN_WITHOUT_GROUP, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user!.groupId).toBeNull();
  });

  it('currentUser() for TEACHER returns isHeadman=false and groupId=null', () => {
    service.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);
    const user = service.currentUser();
    expect(user!.isHeadman).toBe(false);
    expect(user!.groupId).toBeNull();
  });

  it('resolveDashboardFor(null) returns /login', () => {
    expect(service.resolveDashboardFor(null)).toBe('/login');
  });

  it('resolveDashboardFor(ADMIN) returns /admin/dashboard', () => {
    expect(
      service.resolveDashboardFor({ id: 2, role: 'ADMIN', isHeadman: false, groupId: null }),
    ).toBe('/admin/dashboard');
  });

  it('resolveDashboardFor(TEACHER) returns /teacher/dashboard', () => {
    expect(
      service.resolveDashboardFor({ id: 1, role: 'TEACHER', isHeadman: false, groupId: null }),
    ).toBe('/teacher/dashboard');
  });

  it('resolveDashboardFor(plain STUDENT) returns /student/dashboard', () => {
    expect(
      service.resolveDashboardFor({ id: 3, role: 'STUDENT', isHeadman: false, groupId: 5 }),
    ).toBe('/student/dashboard');
  });

  it('resolveDashboardFor(headman STUDENT) returns /headman/dashboard', () => {
    expect(
      service.resolveDashboardFor({ id: 4, role: 'STUDENT', isHeadman: true, groupId: 5 }),
    ).toBe('/headman/dashboard');
  });

  // M03b Группа 7: refresh token больше не в localStorage (HttpOnly cookie).
  // Tests на persist/restore удалены — session восстанавливается через
  // APP_INITIALIZER + AuthService.bootstrap() вызовом /auth/refresh.

  it('removes legacy rct.auth.v1 blob on construction (migration helper)', () => {
    localStorage.setItem(
      'rct.auth.v1',
      JSON.stringify({ accessToken: 'legacy', refreshToken: 'legacy' }),
    );
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [AuthService] });
    TestBed.inject(AuthService);
    expect(localStorage.getItem('rct.auth.v1')).toBeNull();
  });
});
