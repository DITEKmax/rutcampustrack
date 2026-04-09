import {
  ChangeDetectionStrategy, Component, OnInit, computed, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl, ReactiveFormsModule, FormBuilder, FormGroup, Validators,
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { trigger, transition, style, animate } from '@angular/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthApi } from '../../../core/auth/auth.api';

@Component({
  selector: 'app-student-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms var(--ease-out, ease-out)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
    trigger('toastSlide', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms var(--ease-out, ease-out)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './student-profile.component.html',
  styleUrl: './student-profile.component.css',
})
export class StudentProfileComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly authApi = inject(AuthApi);
  private readonly fb = inject(FormBuilder);

  readonly currentUser = this.authService.currentUser;

  readonly avatarInitials = computed(() => {
    const id = this.currentUser()?.id;
    if (id == null) return '??';
    return String(id).slice(-2).padStart(2, '0');
  });

  form!: FormGroup;
  readonly submitting = signal(false);
  readonly successVisible = signal(false);
  readonly apiError = signal<string | null>(null);
  readonly showCurrentPassword = signal(false);
  readonly showNewPassword = signal(false);

  // Live validation hint state
  readonly newPasswordValue = signal('');

  readonly hintMinLength = computed(() => this.newPasswordValue().length >= 8);
  readonly hintUppercase = computed(() => /[A-Z]/.test(this.newPasswordValue()));
  readonly hintDigit = computed(() => /\d/.test(this.newPasswordValue()));

  ngOnInit(): void {
    this.form = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          (c: AbstractControl) =>
            /[A-Z]/.test(c.value) && /\d/.test(c.value) ? null : { pattern: true },
        ],
      ],
    });

    this.form.get('newPassword')?.valueChanges.subscribe((v: string) => {
      this.newPasswordValue.set(v ?? '');
    });
  }

  toggleCurrentPasswordVisibility(): void {
    this.showCurrentPassword.update(v => !v);
  }

  toggleNewPasswordVisibility(): void {
    this.showNewPassword.update(v => !v);
  }

  onSubmit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.apiError.set(null);
    this.form.get('currentPassword')?.setErrors(null);

    const { currentPassword, newPassword } = this.form.value as {
      currentPassword: string;
      newPassword: string;
    };

    this.authApi.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.successVisible.set(true);
        this.form.reset();
        this.newPasswordValue.set('');
        setTimeout(() => this.successVisible.set(false), 3000);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        if (err.status === 401) {
          this.form.get('currentPassword')?.setErrors({ wrongPassword: true });
        } else {
          this.apiError.set('Не удалось изменить пароль. Попробуйте позже.');
        }
      },
    });
  }
}
