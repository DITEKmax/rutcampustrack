import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  /** После успешного создания — здесь хранится ответ с initialPassword */
  readonly createdUser = signal<UserCreatedResponse | null>(null);

  readonly form = new FormGroup({
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    middleName: new FormControl('', { nonNullable: true }),
    role: new FormControl<UserRole | ''>('', { nonNullable: true, validators: [Validators.required] }),
    groupId: new FormControl<number | null>(null),
    employeeNumber: new FormControl('', { nonNullable: true }),
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
      });
      this.form.get('role')!.disable();
    } else if (this.data.mode === 'create' && this.data.presetRole) {
      const allowed: UserRole[] = ['student', 'teacher', 'admin'];
      if ((allowed as string[]).includes(this.data.presetRole)) {
        this.form.patchValue({ role: this.data.presetRole as UserRole });
      }
    }
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
      if (raw.role === 'student' && raw.groupId != null) req.groupId = raw.groupId;
      // Табельный номер только для преподавателей
      if (raw.role === 'teacher' && raw.employeeNumber) req.employeeNumber = raw.employeeNumber;

      this.adminApi.createUser(req).subscribe({
        next: result => {
          this.saving.set(false);
          this.createdUser.set(result);
          this.dialogRef.close(result);
        },
        error: () => {
          this.apiError.set(true);
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

      this.adminApi.patchUser(u.id, req).subscribe({
        next: () => {
          this.dialogRef.close(true);
        },
        error: () => {
          this.apiError.set(true);
          this.saving.set(false);
        },
      });
    }
  }
}
