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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HeadmanApiService } from '../shared/headman-api.service';

export interface OneOffDialogData {
  groupId: number;
}

/**
 * MatDialog для создания разовой пары (D-08 — любая дата; D-09 — 409 при конфликте).
 *
 * Поля:
 * - `date`  — MatDatepicker без ограничений (прошлая/сегодня/будущая, D-08).
 * - `lessonNumber` — select 1..8 (D-05).
 * - `subjectId` — select из каталога предметов группы (D-03).
 * - `classroom` — необязательный input.
 *
 * 409 → snackBar «Слот занят. Сначала отмените шаблонную пару на эту дату.»
 * 201 → snackBar «Разовая пара добавлена.» + dialogRef.close(true)
 */
@Component({
  selector: 'app-one-off-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatInputModule,
    MatSelectModule,
    MatFormFieldModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Добавить разовую пару</h2>
    <mat-dialog-content>
      <form [formGroup]="form">
        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>Дата</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="date" />
          <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
          <mat-error *ngIf="form.get('date')?.hasError('required')">
            Укажите дату
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>Номер пары</mat-label>
          <mat-select formControlName="lessonNumber" aria-label="Выберите номер пары">
            @for (n of lessonNumbers; track n) {
              <mat-option [value]="n">{{ n }}-я пара</mat-option>
            }
          </mat-select>
          <mat-error *ngIf="form.get('lessonNumber')?.hasError('required')">
            Обязательное поле
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>Предмет</mat-label>
          <mat-select formControlName="subjectId" aria-label="Выберите предмет">
            @for (s of subjects; track s.id) {
              <mat-option [value]="s.id">{{ s.name }}</mat-option>
            }
          </mat-select>
          @if (subjectsError || (subjects.length === 0 && !subjectsLoading)) {
            <mat-hint>Нет предметов. Сначала создайте их в разделе «Предметы».</mat-hint>
          }
          <mat-error *ngIf="form.get('subjectId')?.hasError('required')">
            Обязательное поле
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" style="width:100%">
          <mat-label>Кабинет (необязательно)</mat-label>
          <input matInput formControlName="classroom" maxlength="64" placeholder="Например: 305" />
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
        @if (submitting) { <mat-spinner diameter="16"></mat-spinner> }
        Создать разовую пару
      </button>
    </mat-dialog-actions>
  `,
})
export class OneOffDialogComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<OneOffDialogComponent>);
  readonly data = inject<OneOffDialogData>(MAT_DIALOG_DATA);

  readonly lessonNumbers = [1, 2, 3, 4, 5, 6, 7, 8];

  form = new FormGroup({
    date: new FormControl<Date | null>(null, { validators: [Validators.required] }),
    lessonNumber: new FormControl<number | null>(null, { validators: [Validators.required] }),
    subjectId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    classroom: new FormControl<string>('', { nonNullable: true }),
  });

  subjects: { id: number; name: string }[] = [];
  subjectsLoading = true;
  subjectsError = false;
  submitting = false;
  apiError: string | null = null;

  ngOnInit(): void {
    this.headmanApi.listSubjects().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? ((Object.values(embedded)[0] as any[]) ?? []) : (Array.isArray(resp) ? resp : []);
        this.subjects = list;
        this.subjectsLoading = false;
      },
      error: () => {
        this.subjects = [];
        this.subjectsLoading = false;
        this.subjectsError = true;
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

    const v = this.form.value;
    const d: Date = v.date!;
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const body = {
      groupId: this.data.groupId,
      subjectId: v.subjectId!,
      date: iso,
      lessonNumber: v.lessonNumber!,
      classroom: v.classroom || undefined,
    };

    this.headmanApi.createOneOffLesson(body).subscribe({
      next: () => {
        this.submitting = false;
        this.snackBar.open('Разовая пара добавлена.', undefined, { duration: 4000 });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.submitting = false;
        if (err?.status === 409) {
          this.apiError = 'Слот занят. Сначала отмените шаблонную пару на эту дату.';
        } else if (err?.status === 403) {
          this.apiError = 'Недостаточно прав для создания пары в этой группе.';
        } else {
          this.apiError = 'Не удалось создать разовую пару. Попробуйте ещё раз.';
        }
      },
    });
  }
}
