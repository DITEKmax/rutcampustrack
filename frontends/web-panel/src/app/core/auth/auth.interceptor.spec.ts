import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthApi } from './auth.api';
import { authInterceptor, resetInterceptorState } from './auth.interceptor';

const makeJwt = (payload: object): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
};

const ACCESS_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', exp: 9999999999 });
const NEW_ACCESS_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', exp: 9999999999 });
const REFRESH_TOKEN = 'refresh-token-abc';
const NEW_REFRESH_TOKEN = 'new-refresh-token-xyz';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    resetInterceptorState();

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        AuthApi,
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: Router,
          useValue: { navigate: vi.fn() },
        },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches Authorization Bearer header when token exists', () => {
    authService.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);

    httpClient.get('/api/users').subscribe();

    const req = httpMock.expectOne('/api/users');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${ACCESS_TOKEN}`);
    req.flush({});
  });

  it('does not attach Authorization header when no token', () => {
    httpClient.get('/api/users').subscribe();

    const req = httpMock.expectOne('/api/users');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('on 401, calls refresh and retries original request with new token', () => {
    authService.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);

    let result: unknown;
    httpClient.get('/api/users').subscribe((data) => (result = data));

    // First request triggers 401
    const firstReq = httpMock.expectOne('/api/users');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    // Interceptor calls refresh
    const refreshReq = httpMock.expectOne('/api/auth/refresh');
    expect(refreshReq.request.body).toEqual({ refreshToken: REFRESH_TOKEN });
    refreshReq.flush({ accessToken: NEW_ACCESS_TOKEN, refreshToken: NEW_REFRESH_TOKEN, expiresIn: 3600 });

    // Interceptor replays original with new token
    const retryReq = httpMock.expectOne('/api/users');
    expect(retryReq.request.headers.get('Authorization')).toBe(`Bearer ${NEW_ACCESS_TOKEN}`);
    retryReq.flush({ data: 'ok' });

    expect(result).toEqual({ data: 'ok' });
  });

  it('on 401 during refresh (double 401), navigates to /login', () => {
    authService.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);

    let error: unknown;
    httpClient.get('/api/users').subscribe({
      next: () => {},
      error: (err) => (error = err),
    });

    // First request 401
    const firstReq = httpMock.expectOne('/api/users');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    // Refresh also fails with 401
    const refreshReq = httpMock.expectOne('/api/auth/refresh');
    refreshReq.flush({ message: 'Refresh expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('queues concurrent requests during refresh and replays all on success', () => {
    authService.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);

    const results: unknown[] = [];
    httpClient.get('/api/users').subscribe((d) => results.push(d));
    httpClient.get('/api/groups').subscribe((d) => results.push(d));

    // Both requests fail with 401
    const req1 = httpMock.expectOne('/api/users');
    req1.flush({}, { status: 401, statusText: 'Unauthorized' });

    const req2 = httpMock.expectOne('/api/groups');
    req2.flush({}, { status: 401, statusText: 'Unauthorized' });

    // Only one refresh call
    const refreshReq = httpMock.expectOne('/api/auth/refresh');
    refreshReq.flush({ accessToken: NEW_ACCESS_TOKEN, refreshToken: NEW_REFRESH_TOKEN, expiresIn: 3600 });

    // Both retried
    const retry1 = httpMock.expectOne('/api/users');
    retry1.flush({ users: [] });
    const retry2 = httpMock.expectOne('/api/groups');
    retry2.flush({ groups: [] });

    expect(results).toHaveLength(2);
  });

  it('does not attach Bearer header to /api/auth/login endpoint', () => {
    authService.setTokens(ACCESS_TOKEN, REFRESH_TOKEN);

    httpClient.post('/api/auth/login', { login: 'test', password: 'pass' }).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ accessToken: ACCESS_TOKEN, refreshToken: REFRESH_TOKEN, expiresIn: 3600 });
  });
});
