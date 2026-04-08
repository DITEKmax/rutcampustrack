import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { headmanGuard } from './headman.guard';

const makeJwt = (payload: object): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
};

const TEACHER_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', is_headman: false, group_id: null, exp: 9999999999 });
const ADMIN_TOKEN = makeJwt({ sub: '2', role: 'ADMIN', is_headman: false, group_id: null, exp: 9999999999 });
const STUDENT_TOKEN = makeJwt({ sub: '3', role: 'STUDENT', is_headman: false, group_id: 5, exp: 9999999999 });
const HEADMAN_TOKEN = makeJwt({ sub: '4', role: 'STUDENT', is_headman: true, group_id: 5, exp: 9999999999 });
const REFRESH_TOKEN = 'refresh-token-abc';

describe('headmanGuard', () => {
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

  it('returns true for headman STUDENT (role=STUDENT && isHeadman=true)', () => {
    authService.setTokens(HEADMAN_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      headmanGuard({} as any, {} as any)
    );
    expect(result).toBe(true);
  });

  it('redirects plain STUDENT (isHeadman=false) to /student/dashboard', () => {
    authService.setTokens(STUDENT_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      headmanGuard({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/student/dashboard']);
  });

  it('redirects TEACHER to /teacher/dashboard', () => {
    authService.setTokens(TEACHER_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      headmanGuard({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/teacher/dashboard']);
  });

  it('redirects ADMIN to /admin/dashboard', () => {
    authService.setTokens(ADMIN_TOKEN, REFRESH_TOKEN);
    const result = TestBed.runInInjectionContext(() =>
      headmanGuard({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/admin/dashboard']);
  });

  it('redirects unauthenticated user to /login', () => {
    const result = TestBed.runInInjectionContext(() =>
      headmanGuard({} as any, {} as any)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
  });
});
