import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HeadmanApiService } from '../shared/headman-api.service';

export interface SubjectDialogData {
  mode: 'create' | 'edit';
  subject?: any;
}

/**
 * MatDialog component for creating or editing a subject.
 *
 * Create mode: empty form, heading "Новый предмет", button "Создать предмет"
 * Edit mode: pre-populated form, heading "Редактировать предмет", button "Сохранить предмет"
 *
 * Form fields (D-02, D-19):
 * - `name` — subject name (required, max 120 chars)
 * - `type` — required enum: LECTURE / PRACTICE / LAB
 * - `teacherIds` — multi-select list of teacher IDs (may be empty — subject
 *   without teachers is allowed per backend Plan 60-01 contract).
 *
 * All assigned teachers are equal read-only observers (D-15) — there is no
 * "primary" teacher. Teacher select loads from GET /api/academic/users/teachers
 * (listTeachers). Handles empty/error teacher list with "Преподаватели не
 * найдены" hint.
 */
@Component({
  selector: 'app-subject-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatInputModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Редактировать предмет' : 'Новый предмет' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form">

        <!-- Subject name -->
        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>Название предмета</mat-label>
          <input matInput formControlName="name"
                 maxlength="120"
                 placeholder="Например: Линейная алгебра" />
          <mat-error *ngIf="form.get('name')?.hasError('required')">
            Обязательное поле
          </mat-error>
          <mat-error *ngIf="form.get('name')?.hasError('maxlength')">
            Не более 120 символов
          </mat-error>
        </mat-form-field>

        <!-- Subject type (D-02) -->
        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>Тип занятия</mat-label>
          <mat-select formControlName="type" aria-label="Выберите тип занятия">
            <mat-option value="LECTURE">Лекция</mat-option>
            <mat-option value="PRACTICE">Практика</mat-option>
            <mat-option value="LAB">Лабораторная</mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('type')?.hasError('required')">
            Обязательное поле
          </mat-error>
        </mat-form-field>

        <!-- Teacher multi-select (D-19) -->
        <mat-form-field appearance="outline" style="width:100%">
          <mat-label>Преподаватели</mat-label>
          <mat-select formControlName="teacherIds"
                      multiple
                      aria-label="Выберите преподавателей"
                      [disabled]="teachersLoading || teachersError || teachers.length === 0">
            @for (t of teachers; track t.id) {
              <mat-option [value]="t.id">{{ t.lastName ?? t.fullName }}</mat-option>
            }
          </mat-select>
          @if (teachersError || (teachers.length === 0 && !teachersLoading)) {
            <mat-hint>
              Преподаватели не найдены. Обратитесь к администратору.
            </mat-hint>
          } @else {
            <mat-hint>
              Можно выбрать нескольких; все — равноправные наблюдатели.
            </mat-hint>
          }
        </mat-form-field>

        @if (apiError) {
          <div class="page-error" style="margin-top: var(--space-4)">{{ apiError }}</div>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="false">Отмена</button>
      <button class="btn-brand" type="button"
              [disabled]="submitting || form.invalid"
              (click)="onSubmit()">
        @if (submitting) {
          <mat-spinner diameter="16"></mat-spinner>
        }
        {{ isEdit ? 'Сохранить предмет' : 'Создать предмет' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content {
      padding-top: var(--space-4);
    }
  `],
})
export class SubjectDialogComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<SubjectDialogComponent>);
  readonly data = inject<SubjectDialogData>(MAT_DIALOG_DATA);

  readonly isEdit: boolean;

  form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    type: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    teacherIds: new FormControl<number[]>([], { nonNullable: true }),
  });

  teachers: any[] = [];
  teachersLoading = true;
  teachersError = false;
  submitting = false;
  apiError: string | null = null;

  constructor() {
    this.isEdit = this.data.mode === 'edit';
    if (this.isEdit && this.data.subject) {
      this.form.patchValue({
        name: this.data.subject.name,
        type: this.data.subject.type ?? '',
        teacherIds: this.data.subject.teacherIds ?? [],
      });
    }
  }

  ngOnInit(): void {
    this.headmanApi.listTeachers().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        let list: any[];
        if (embedded) {
          list = (Object.values(embedded)[0] as any[]) ?? [];
        } else if (Array.isArray(resp)) {
          list = resp;
        } else {
          list = [];
        }
        const collator = new Intl.Collator('ru', { sensitivity: 'base' });
        this.teachers = list.slice().sort((a, b) =>
          collator.compare(a?.lastName ?? a?.fullName ?? '', b?.lastName ?? b?.fullName ?? '')
        );
        this.teachersLoading = false;
      },
      error: () => {
        this.teachers = [];
        this.teachersLoading = false;
        this.teachersError = true;
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.apiError = null;
    const body = {
      name: this.form.value.name!,
      type: this.form.value.type!,
      teacherIds: this.form.value.teacherIds ?? [],
    };
    const call = this.isEdit
      ? this.headmanApi.updateSubject(this.data.subject!.id, body)
      : this.headmanApi.createSubject(body);

    call.subscribe({
      next: () => {
        this.submitting = false;
        const msg = this.isEdit ? 'Предмет обновлён.' : 'Предмет создан.';
        this.snackBar.open(msg, undefined, { duration: 4000 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.submitting = false;
        this.apiError = 'Не удалось сохранить предмет. Попробуйте ещё раз.';
      },
    });
  }
}
