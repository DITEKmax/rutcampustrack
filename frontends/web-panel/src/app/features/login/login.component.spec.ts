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

const TEACHER_TOKEN = makeJwt({ sub: '1', role: 'TEACHER', exp: 9999999999 });
const ADMIN_TOKEN = makeJwt({ sub: '2', role: 'ADMIN', exp: 9999999999 });
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
      currentUser: vi.fn().mockReturnValue(null),
      isAuthenticated: vi.fn().mockReturnValue(false) as any,
    };

    mockRouter = {
      navigate: vi.fn(),
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
    expect(screen.getByLabelText(/пароль/i)).toBeTruthy();
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
    (mockAuthApi.login as ReturnType<typeof vi.fn>).mockReturnValue(
      of({ accessToken: TEACHER_TOKEN, refreshToken: REFRESH_TOKEN, expiresIn: 3600 })
    );
    (mockAuthService.currentUser as ReturnType<typeof vi.fn>).mockReturnValue({
      id: 1,
      role: 'TEACHER',
    });

    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'teacher00001');
    await user.type(screen.getByLabelText(/пароль/i), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));

    expect(mockAuthApi.login).toHaveBeenCalledWith({
      login: 'teacher00001',
      password: 'password123',
    });
  });

  it('on successful login, calls AuthService.setTokens and navigates to /teacher/dashboard for TEACHER role', async () => {
    (mockAuthApi.login as ReturnType<typeof vi.fn>).mockReturnValue(
      of({ accessToken: TEACHER_TOKEN, refreshToken: REFRESH_TOKEN, expiresIn: 3600 })
    );
    (mockAuthService.currentUser as ReturnType<typeof vi.fn>).mockReturnValue({
      id: 1,
      role: 'TEACHER',
    });

    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'teacher00001');
    await user.type(screen.getByLabelText(/пароль/i), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));

    expect(mockAuthService.setTokens).toHaveBeenCalledWith(TEACHER_TOKEN, REFRESH_TOKEN);
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/teacher/dashboard']);
  });

  it('on successful login with ADMIN role, navigates to /admin/dashboard', async () => {
    (mockAuthApi.login as ReturnType<typeof vi.fn>).mockReturnValue(
      of({ accessToken: ADMIN_TOKEN, refreshToken: REFRESH_TOKEN, expiresIn: 3600 })
    );
    (mockAuthService.currentUser as ReturnType<typeof vi.fn>).mockReturnValue({
      id: 2,
      role: 'ADMIN',
    });

    await render(LoginComponent, {
      providers: [
        { provide: AuthApi, useValue: mockAuthApi },
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/логин/i), 'admin');
    await user.type(screen.getByLabelText(/пароль/i), 'password123');
    await user.click(screen.getByRole('button', { name: /войти/i }));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/admin/dashboard']);
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
    await user.type(screen.getByLabelText(/пароль/i), 'wrongpassword');
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
    await user.type(screen.getByLabelText(/пароль/i), 'password123');
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
    const passwordInput = screen.getByLabelText(/пароль/i) as HTMLInputElement;

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
    await user.type(screen.getByLabelText(/пароль/i), 'password123');
    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText(/вход\.\.\./i)).toBeTruthy();
    });
  });
});
