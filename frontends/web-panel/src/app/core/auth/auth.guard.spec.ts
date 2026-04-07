import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';

// Minimal valid JWT helper
const makeJwt = (payload: object): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
};

const TEACHER_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', exp: 9999999999 });
const ADMIN_TOKEN = makeJwt({ sub: '2', role: 'ADMIN', exp: 9999999999 });
const REFRESH_TOKEN = 'refresh-token-abc';

describe('authGuard', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {
          provide: Router,
          useValue: {
            createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree),
            navigate: vi.fn(),
          },
        },
      ],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('returns true when isAuthenticated() is true', () => {
    authService.setTokens(TEACHER_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any)
    );
    expect(result).toBe(true);
  });

  it('returns UrlTree to /login when isAuthenticated() is false', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
  });
});

describe('roleGuard', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        {
          provide: Router,
          useValue: {
            createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree),
            navigate: vi.fn(),
          },
        },
      ],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('returns true when user.role is in allowedRoles', () => {
    authService.setTokens(ADMIN_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(['ADMIN'])({} as any, {} as any)
    );
    expect(result).toBe(true);
  });

  it('roleGuard([ADMIN]) redirects to /teacher/dashboard when user.role is TEACHER', () => {
    authService.setTokens(TEACHER_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(['ADMIN'])({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/teacher/dashboard']);
  });

  it('roleGuard([TEACHER]) redirects to /admin/dashboard when user.role is ADMIN', () => {
    authService.setTokens(ADMIN_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(['TEACHER'])({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/admin/dashboard']);
  });

  it('redirects to /login when user is null (not authenticated)', () => {
    // No tokens set
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(['ADMIN'])({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
  });
});
