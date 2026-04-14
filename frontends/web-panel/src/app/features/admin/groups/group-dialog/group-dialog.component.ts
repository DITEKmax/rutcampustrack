import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { HttpErrorResponse } from '@angular/common/http';

import { AdminApiService } from '../../shared/admin-api.service';
import type { GroupResponse } from '../../shared/types';

export interface GroupDialogData {
  mode: 'create' | 'edit';
  group?: GroupResponse;
}

/**
 * Активный формат имени группы: кириллица, опциональные строчные подспециализации, дефис, 3 цифры.
 * Примеры: УИТ-311, УВП-112, УВПв-511.
 */
const GROUP_NAME_PATTERN = /^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$/;

/**
 * Человеко-читаемые сообщения при 409 ProblemDetail.field (Plan 02 pattern).
 */
const FIELD_MESSAGES: Record<string, string> = {
  name: 'Группа с таким названием уже существует',
};

@Component({
  selector: 'app-group-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSlideToggleModule,
  ],
  templateUrl: './group-dialog.component.html',
})
export class GroupDialogComponent implements OnInit {
  data = inject<GroupDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<GroupDialogComponent>);
  private adminApi = inject(AdminApiService);
  private fb = inject(FormBuilder);

  saving = false;
  submitError = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    name: ['', [
      Validators.required,
      Validators.pattern(GROUP_NAME_PATTERN),
      Validators.maxLength(8),
    ]],
    active: [true],
  });

  get isEdit(): boolean {
    return this.data.mode === 'edit';
  }

  ngOnInit(): void {
    if (this.isEdit && this.data.group) {
      this.form.patchValue({
        name: this.data.group.name,
        active: this.data.group.active,
      });
    }

    // Автоматическая очистка conflict-ошибки при редактировании поля.
    this.form.controls.name.valueChanges.subscribe(() => {
      const ctl = this.form.controls.name;
      if (ctl.errors && ctl.errors['conflict']) {
        const { conflict: _c, ...rest } = ctl.errors;
        ctl.setErrors(Object.keys(rest).length ? rest : null);
      }
      if (this.submitError()) {
        this.submitError.set(null);
      }
    });
  }

  save(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;
    this.submitError.set(null);

    const { name, active } = this.form.getRawValue();

    if (this.isEdit && this.data.group) {
      this.adminApi.updateGroup(this.data.group.id, { name, active }).subscribe({
        next: () => this.dialogRef.close(true),
        error: (err: HttpErrorResponse) => this.handleSaveError(err),
      });
    } else {
      this.adminApi.createGroup({ name }).subscribe({
        next: () => this.dialogRef.close(true),
        error: (err: HttpErrorResponse) => this.handleSaveError(err),
      });
    }
  }

  private handleSaveError(err: HttpErrorResponse): void {
    this.saving = false;
    const field: string | undefined = err?.error?.field;
    if (err.status === 409 && field && FIELD_MESSAGES[field]) {
      const msg = FIELD_MESSAGES[field];
      const ctl: AbstractControl | null = this.form.get(field);
      if (ctl) {
        ctl.setErrors({ ...(ctl.errors ?? {}), conflict: msg });
      }
      this.submitError.set(msg);
    } else {
      this.submitError.set(err?.error?.detail ?? 'Не удалось сохранить группу.');
    }
  }
}
