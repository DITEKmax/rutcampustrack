import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { render, screen, fireEvent, waitFor } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/auth/auth.service';
import { AuthApi } from '../../core/auth/auth.api';

// Minimal valid JWT helper
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

describe('LoginComponent', () => {
  let mockAuthApi: Partial<AuthApi>;
  let mockAuthService: Partial<AuthService>;
  let mockRouter: Partial<Router>;

  beforeEach(() => {
    mockAuthApi = {
      login: vi.fn(),
    };

    mockAuthService = {
      setTokens: vi.fn(),
      setAccessToken: vi.fn(),
      currentUser: vi.fn().mockReturnValue(null),
      isAuthenticated: vi.fn().mockReturnValue(false) as any,
      resolveDashboardFor: vi.fn((user: any) => {
        if (!user) return '/login';
        if (user.role === 'ADMIN') return '/admin/dashboard';
        if (user.role === 'TEACHER') return '/teacher/dashboard';
        if (user.isHeadman) return '/headman/dashboard';
        return '/student/dashboard';
      }),
    };

    mockRouter = {
      navigate: vi.fn(),
      navigateByUrl: vi.fn(),
    };
  });

  it('renders username field with label "Логин" and password field with label "Пароль"', async () => {
    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    expect(screen.getByLabelText(/логин/i)).toBeTruthy();
    expect(screen.getByLabelText('Пароль')).toBeTruthy();
  });

  it('submit button text is "Войти"', async () => {
    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    expect(screen.getByRole('button', { name: /войти/i })).toBeTruthy();
  });

  it('submit button is disabled when form is empty', async () => {
    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const button = screen.getByRole('button', { name: /войти/i });
    expect(button).toBeDisabled();
  });

  it('on valid submit, calls AuthApi.login with { login, password }', async () => {
    await render(LoginComponent, {
      providers: [AuthApi, AuthService, provideHttpClient(), provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }],
    });
    const http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'teacher00001');
    await user.type(screen.getByLabelText('Пароль'), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({ login: 'teacher00001', password: 'password123' });
    request.flush({ accessToken: TEACHER_TOKEN, expiresIn: 3600 });
    expect(auth.accessToken()).toBe(TEACHER_TOKEN);
    expect(auth.currentUser()).toMatchObject({ id: 1, role: 'TEACHER', isHeadman: false, groupId: null });
    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/teacher/dashboard');
    http.verify();
  });

  it('on successful login, calls AuthService.setTokens and navigates to /teacher/dashboard for TEACHER role', async () => {
    await render(LoginComponent, {
      providers: [AuthApi, AuthService, provideHttpClient(), provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }],
    });
    const http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'teacher00001');
    await user.type(screen.getByLabelText('Пароль'), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({ login: 'teacher00001', password: 'password123' });
    request.flush({ accessToken: TEACHER_TOKEN, expiresIn: 3600 });
    expect(auth.accessToken()).toBe(TEACHER_TOKEN);
    expect(auth.currentUser()).toMatchObject({ id: 1, role: 'TEACHER', isHeadman: false, groupId: null });
    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/teacher/dashboard');
    http.verify();
  });

  it('on successful login with ADMIN role, navigates to /admin/dashboard', async () => {
    await render(LoginComponent, {
      providers: [AuthApi, AuthService, provideHttpClient(), provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }],
    });
    const http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'admin');
    await user.type(screen.getByLabelText('Пароль'), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({ login: 'admin', password: 'password123' });
    request.flush({ accessToken: ADMIN_TOKEN, expiresIn: 3600 });
    expect(auth.accessToken()).toBe(ADMIN_TOKEN);
    expect(auth.currentUser()).toMatchObject({ id: 2, role: 'ADMIN', isHeadman: false, groupId: null });
    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/admin/dashboard');
    http.verify();
  });

  it('on successful login with plain STUDENT role, navigates to /student/dashboard', async () => {
    await render(LoginComponent, {
      providers: [AuthApi, AuthService, provideHttpClient(), provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }],
    });
    const http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'student00001');
    await user.type(screen.getByLabelText('Пароль'), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({ login: 'student00001', password: 'password123' });
    request.flush({ accessToken: STUDENT_TOKEN, expiresIn: 3600 });
    expect(auth.accessToken()).toBe(STUDENT_TOKEN);
    expect(auth.currentUser()).toMatchObject({ id: 3, role: 'STUDENT', isHeadman: false, groupId: 5 });
    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/student/dashboard');
    http.verify();
  });

  it('on successful login with headman STUDENT role, navigates to /headman/dashboard', async () => {
    await render(LoginComponent, {
      providers: [AuthApi, AuthService, provideHttpClient(), provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }],
    });
    const http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'headman01');
    await user.type(screen.getByLabelText('Пароль'), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({ login: 'headman01', password: 'password123' });
    request.flush({ accessToken: HEADMAN_TOKEN, expiresIn: 3600 });
    expect(auth.accessToken()).toBe(HEADMAN_TOKEN);
    expect(auth.currentUser()).toMatchObject({ id: 4, role: 'STUDENT', isHeadman: true, groupId: 5 });
    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/headman/dashboard');
    http.verify();
  });

  it('on 401 error, shows error message for wrong credentials', async () => {
    (mockAuthApi.login as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => ({ status: 401, statusText: 'Unauthorized' }))
    );

    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'teacher00001');
    await user.type(screen.getByLabelText('Пароль'), 'wrongpassword');
    await user.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => {
      expect(screen.getByText(/неверный логин или пароль/i)).toBeTruthy();
    });
  });

  it('on network error, shows server connection error message', async () => {
    (mockAuthApi.login as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => ({ status: 0, statusText: 'Unknown Error' }))
    );

    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'teacher00001');
    await user.type(screen.getByLabelText('Пароль'), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => {
      expect(screen.getByText(/не удалось подключиться/i)).toBeTruthy();
    });
  });

  it('on 401, password field is cleared but username preserved', async () => {
    (mockAuthApi.login as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => ({ status: 401, statusText: 'Unauthorized' }))
    );

    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const user = userEvent.setup();
    const loginInput = screen.getByLabelText(/логин/i) as HTMLInputElement;
    const passwordInput = screen.getByLabelText('Пароль') as HTMLInputElement;

    await user.type(loginInput, 'teacher00001');
    await user.type(passwordInput, 'wrongpassword');
    await user.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => {
      expect(loginInput.value).toBe('teacher00001');
      expect(passwordInput.value).toBe('');
    });
  });

  it('during submit, button shows "Вход..." text', async () => {
    // Use a never-resolving observable to freeze the loading state
    (mockAuthApi.login as ReturnType<typeof vi.fn>).mockReturnValue(
      new (require('rxjs').Observable)(() => {})
    );

    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'teacher00001');
    await user.type(screen.getByLabelText('Пароль'), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => {
      expect(screen.getByText(/вход\.\.\./i)).toBeTruthy();
    });
  });
});
