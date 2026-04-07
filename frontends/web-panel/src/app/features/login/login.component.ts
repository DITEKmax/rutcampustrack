import { Component, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { AuthApi } from '../../core/auth/auth.api';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private authApi = inject(AuthApi);
  private router = inject(Router);

  form = this.fb.group({
    login: ['', Validators.required],
    password: ['', Validators.required],
  });

  loading = false;
  errorMessage = '';

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.form.disable();

    const { login, password } = this.form.getRawValue();
    this.authApi.login({ login: login!, password: password! }).subscribe({
      next: (tokens) => {
        this.authService.setTokens(tokens.accessToken, tokens.refreshToken);
        const role = this.authService.currentUser()?.role;
        this.router.navigate([role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        this.form.enable();
        this.form.patchValue({ password: '' });
        if (err.status === 401) {
          this.errorMessage = 'Неверный логин или пароль. Проверьте данные и попробуйте снова.';
        } else {
          this.errorMessage = 'Не удалось подключиться к серверу. Проверьте соединение.';
        }
      },
    });
  }
}
