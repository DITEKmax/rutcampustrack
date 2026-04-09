import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';

export interface AssignAssistantDialogData {
  students: any[];
  assistantIds: Set<number>;
}

@Component({
  selector: 'app-assign-assistant-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatSelectModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Назначить помощника старосты</h2>
    <mat-dialog-content>
      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <!-- Student select -->
        <mat-form-field appearance="outline" style="width:100%">
          <mat-label>Выберите студента</mat-label>
          <mat-select formControlName="studentId" aria-label="Выберите студента" required>
            @for (s of eligibleStudents; track s.id) {
              <mat-option [value]="s.id">{{ s.fullName }} ({{ s.login }})</mat-option>
            }
          </mat-select>
          <mat-error *ngIf="form.get('studentId')?.invalid">Выберите студента</mat-error>
        </mat-form-field>

        <!-- Permission checkboxes -->
        <p class="permissions-label">Права помощника</p>
        <div formGroupName="permissions">
          @for (p of permissionList; track p.value) {
            <div class="permission-row" style="min-height:44px">
              <mat-checkbox [formControlName]="p.value">{{ p.label }}</mat-checkbox>
            </div>
          }
        </div>
        <mat-error *ngIf="showPermissionError" class="permission-error">
          Выберите хотя бы одно право
        </mat-error>
        <p class="permissions-hint">
          Можно выбрать несколько прав. Права можно изменить позже через переназначение.
        </p>

        @if (apiError) {
          <div class="page-error">{{ apiError }}</div>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="false">Отмена</button>
      <button class="btn-brand" type="button" [disabled]="submitting" (click)="onSubmit()">
        @if (submitting) { <mat-spinner diameter="16"></mat-spinner> }
        Назначить помощника
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .permissions-label {
      font-weight: 600;
      font-size: var(--text-sm);
      margin-bottom: var(--space-2);
      color: var(--text-primary);
    }

    .permissions-hint {
      font-size: var(--text-xs);
      color: var(--text-muted);
      margin-top: var(--space-2);
    }

    .permission-row {
      display: flex;
      align-items: center;
    }

    .permission-error {
      display: block;
      font-size: var(--text-xs);
      color: var(--accent-danger);
      margin-top: var(--space-1);
    }
  `],
})
export class AssignAssistantDialogComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject<MatDialogRef<AssignAssistantDialogComponent>>(MatDialogRef);
  private readonly auth = inject(AuthService);
  readonly data = inject<AssignAssistantDialogData>(MAT_DIALOG_DATA);

  readonly permissionList = [
    { value: 'excuse_approve', label: 'Одобрять тикеты о пропусках' },
    { value: 'late_checkin_approve', label: 'Одобрять запросы поздней отметки' },
    { value: 'subject_manage', label: 'Управлять предметами группы' },
    { value: 'attendance_mark', label: 'Проставлять посещаемость' },
  ];

  eligibleStudents: any[] = [];

  submitting = false;
  showPermissionError = false;
  apiError: string | null = null;

  form = new FormGroup({
    studentId: new FormControl<number | null>(null, Validators.required),
    permissions: new FormGroup(
      Object.fromEntries(
        this.permissionList.map(p => [p.value, new FormControl<boolean>(false)])
      )
    ),
  });

  ngOnInit(): void {
    const currentUserId = this.auth.currentUser()?.id;
    this.eligibleStudents = this.data.students.filter(
      s => !this.data.assistantIds.has(s.id) && s.id !== currentUserId
    );
  }

  onSubmit(): void {
    this.showPermissionError = false;
    if (this.form.get('studentId')?.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const selectedPermissions = Object.entries(this.form.value.permissions ?? {})
      .filter(([, v]) => v)
      .map(([k]) => k);
    if (selectedPermissions.length === 0) {
      this.showPermissionError = true;
      return;
    }
    this.submitting = true;
    this.apiError = null;
    this.headmanApi.assignAssistant({
      studentId: this.form.value.studentId!,
      permissions: selectedPermissions,
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.snackBar.open('Помощник назначен.', undefined, { duration: 4000 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.submitting = false;
        this.apiError = 'Не удалось назначить помощника. Попробуйте ещё раз.';
      },
    });
  }
}
