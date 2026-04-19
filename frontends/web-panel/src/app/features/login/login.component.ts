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

  otpForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  loading = false;
  errorMessage = '';
  passwordVisible = false;

  setMode(m: 'password' | 'otp'): void {
    this.mode = m;
    this.errorMessage = '';
  }

  togglePasswordVisibility(): void {
    this.passwordVisible = !this.passwordVisible;
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.form.disable();

    const { login, password } = this.form.getRawValue();
    this.authApi.login({ login: login!, password: password! }).subscribe({
      next: (tokens) => {
        this.authService.setAccessToken(tokens.accessToken);
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

  onSubmitOtp(): void {
    if (this.otpForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.otpForm.disable();

    const code = this.otpForm.getRawValue().code!;
    this.authApi.verifyOtpByCode(code).subscribe({
      next: (tokens) => {
        this.authService.setAccessToken(tokens.accessToken);
        const target = this.authService.resolveDashboardFor(this.authService.currentUser());
        this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.loading = false;
        this.otpForm.enable();
        this.otpForm.patchValue({ code: '' });
        if (err.status === 401) {
          this.errorMessage = 'Код неверный или истёк. Запросите новый через бота.';
        } else if (err.status === 429) {
          this.errorMessage = 'Слишком много попыток. Подождите немного и попробуйте снова.';
        } else {
          this.errorMessage = 'Не удалось подключиться к серверу. Проверьте соединение.';
        }
      },
    });
  }
}
