import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { AdminApiService } from '../../shared/admin-api.service';
import type {
  CreateUserRequest,
  GroupResponse,
  PatchUserRequest,
  UserCreatedResponse,
  UserResponse,
  UserRole,
} from '../../shared/types';

export interface UserDialogData {
  mode: 'create' | 'edit';
  user?: UserResponse;
  groups: GroupResponse[];
  /** Optional preselected role when opened via dashboard quick action (BUG-005). */
  presetRole?: string;
}

@Component({
  selector: 'app-user-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCheckboxModule,
  ],
  templateUrl: './user-dialog.component.html',
  styleUrl: './user-dialog.component.css',
})
export class UserDialogComponent {
  private readonly adminApi = inject(AdminApiService);
  private readonly dialogRef = inject(MatDialogRef<UserDialogComponent>);
  readonly data = inject<UserDialogData>(MAT_DIALOG_DATA);

  readonly saving = signal(false);
  readonly apiError = signal(false);
  /**
   * BUG-006-2 / AC-3: human-readable сообщение для текущей попытки сохранения.
   * null — ошибки нет; иначе покажет в template под формой (перекрывает
   * generic apiError-баннер, если тот выставлен).
   */
  readonly submitError = signal<string | null>(null);
  /** После успешного создания — здесь хранится ответ с initialPassword */
  readonly createdUser = signal<UserCreatedResponse | null>(null);

  /**
   * BUG-006-2: map backend field-names → localized сообщение. Ключи совпадают
   * с payload {@code ProblemDetail.field} из {@code GlobalExceptionHandler}.
   */
  private static readonly FIELD_MESSAGES: Record<string, string> = {
    login: 'Логин уже используется. Выберите другой',
    email: 'Email уже зарегистрирован',
    telegramId: 'Telegram ID уже привязан к другой учётной записи',
    employeeNumber: 'Табельный номер уже используется',
    name: 'Название уже используется',
  };

  readonly form = new FormGroup({
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    middleName: new FormControl('', { nonNullable: true }),
    role: new FormControl<UserRole | ''>('', { nonNullable: true, validators: [Validators.required] }),
    groupId: new FormControl<number | null>(null),
    employeeNumber: new FormControl('', { nonNullable: true }),
    // BUG-006-3: optional by default; `Validators.required` is toggled by
    // `role.valueChanges` below — required for STUDENT, optional otherwise.
    telegramId: new FormControl<number | null>(null),
    isHeadman: new FormControl(false, { nonNullable: true }),
  });

  constructor() {
    if (this.data.mode === 'edit' && this.data.user) {
      const u = this.data.user;
      this.form.patchValue({
        lastName: u.lastName,
        firstName: u.firstName,
        middleName: u.middleName ?? '',
        role: u.role,
        groupId: u.groupId,
        isHeadman: u.headman,
        employeeNumber: u.employeeNumber ?? '',
        telegramId: u.telegramId ?? null,
      });
      this.form.get('role')!.disable();
    } else if (this.data.mode === 'create' && this.data.presetRole) {
      const allowed: UserRole[] = ['STUDENT', 'TEACHER', 'ADMIN'];
      const preset = this.data.presetRole.toUpperCase();
      if ((allowed as string[]).includes(preset)) {
        this.form.patchValue({ role: preset as UserRole });
      }
    }

    // BUG-006-3 / D-08..D-11: telegramId is required when role=student and
    // optional otherwise. React to role changes and apply once at startup to
    // cover edit mode + presetRole flow.
    const roleCtrl = this.form.get('role')!;
    roleCtrl.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(role => this.applyTelegramRequiredForRole(role as UserRole | ''));
    this.applyTelegramRequiredForRole(roleCtrl.value as UserRole | '');
  }

  /**
   * Toggles {@link Validators.required} on the telegramId control based on
   * the active role. Called on init and on every role change.
   */
  private applyTelegramRequiredForRole(role: UserRole | ''): void {
    const tg = this.form.get('telegramId')!;
    if (role === 'STUDENT') {
      tg.addValidators(Validators.required);
    } else {
      tg.removeValidators(Validators.required);
    }
    tg.updateValueAndValidity({ emitEvent: false });
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    this.saving.set(true);
    this.apiError.set(false);

    if (this.data.mode === 'create') {
      const raw = this.form.getRawValue();
      const req: CreateUserRequest = {
        lastName: raw.lastName.trim(),
        firstName: raw.firstName.trim(),
        role: raw.role as UserRole,
      };
      if (raw.middleName.trim()) req.middleName = raw.middleName.trim();
      // Группа только для студентов
      if (raw.role === 'STUDENT' && raw.groupId != null) req.groupId = raw.groupId;
      // Табельный номер только для преподавателей
      if (raw.role === 'TEACHER' && raw.employeeNumber) req.employeeNumber = raw.employeeNumber;
      // Telegram ID — обязательно для студента (BUG-006-3), опционально иначе
      if (raw.telegramId != null) req.telegramId = raw.telegramId;

      this.adminApi.createUser(req).subscribe({
        next: result => {
          this.saving.set(false);
          this.createdUser.set(result);
          this.dialogRef.close(result);
        },
        error: (err: HttpErrorResponse) => {
          this.handleSaveError(err);
          this.saving.set(false);
        },
      });
    } else {
      const raw = this.form.getRawValue();
      const req: PatchUserRequest = {};
      const u = this.data.user!;

      if (raw.lastName.trim() !== u.lastName) req.lastName = raw.lastName.trim();
      if (raw.firstName.trim() !== u.firstName) req.firstName = raw.firstName.trim();
      if (raw.middleName.trim() !== (u.middleName ?? '')) req.middleName = raw.middleName.trim();
      if (raw.groupId !== u.groupId) req.groupId = raw.groupId ?? undefined;
      if (raw.isHeadman !== u.headman) req.isHeadman = raw.isHeadman;
      if (raw.employeeNumber !== (u.employeeNumber ?? '')) req.employeeNumber = raw.employeeNumber;
      if (raw.telegramId !== (u.telegramId ?? null)) {
        req.telegramId = raw.telegramId ?? undefined;
      }

      this.adminApi.patchUser(u.id, req).subscribe({
        next: () => {
          this.dialogRef.close(true);
        },
        error: (err: HttpErrorResponse) => {
          this.handleSaveError(err);
          this.saving.set(false);
        },
      });
    }
  }

  /**
   * BUG-006-2 / AC-3: translates HTTP error into field-level + form-level UI
   * state. 409 с payload {@code field} → маркирует конкретный control и
   * сохраняет локализованное сообщение в {@link submitError}. Остальные
   * ошибки — generic fallback.
   */
  private handleSaveError(err: HttpErrorResponse): void {
    const status = err.status;
    const field = (err.error && typeof err.error === 'object' && typeof err.error.field === 'string')
      ? err.error.field as string
      : null;

    if (status === 409 && field) {
      const msg = UserDialogComponent.FIELD_MESSAGES[field]
        ?? `Поле «${field}» уже используется`;
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ ...(control.errors ?? {}), conflict: msg });
        control.markAsTouched();
        // Clear the field-level conflict on the next change so the user can
        // retry without stale validation state.
        const sub = control.valueChanges.subscribe(() => {
          const errors = control.errors;
          if (errors && 'conflict' in errors) {
            const { conflict: _conflict, ...rest } = errors;
            control.setErrors(Object.keys(rest).length > 0 ? rest : null);
          }
          sub.unsubscribe();
        });
      }
      this.submitError.set(msg);
      this.apiError.set(false);
      return;
    }

    if (status >= 400 && status < 500) {
      this.submitError.set('Не удалось сохранить. Проверьте введённые данные');
    } else {
      this.submitError.set('Ошибка сервера. Попробуйте ещё раз');
    }
    this.apiError.set(true);
  }
}
