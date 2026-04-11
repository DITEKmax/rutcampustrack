import { Component, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AuthApi } from '../../core/auth/auth.api';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private authApi = inject(AuthApi);
  private router = inject(Router);

  mode: 'password' | 'otp' = 'password';

  form = this.fb.group({
    login: ['', Validators.required],
    password: ['', Validators.required],
  });

  loading = false;
  errorMessage = '';

  setMode(m: 'password' | 'otp'): void {
    this.mode = m;
    this.errorMessage = '';
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.form.disable();

    const { login, password } = this.form.getRawValue();
    this.authApi.login({ login: login!, password: password! }).subscribe({
      next: (tokens) => {
        this.authService.setTokens(tokens.accessToken, tokens.refreshToken);
        const target = this.authService.resolveDashboardFor(this.authService.currentUser());
        this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.loading = false;
        this.form.enable();
        this.form.patchValue({ password: '' });
        this.errorMessage = err.status === 401
          ? 'Неверный логин или пароль. Проверьте данные и попробуйте снова.'
          : 'Не удалось подключиться к серверу. Проверьте соединение.';
      },
    });
  }
}
