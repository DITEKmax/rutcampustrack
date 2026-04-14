import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators,
  AbstractControl,
  ValidationErrors,
  AsyncValidatorFn,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { Observable, of, timer } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import { AdminApiService } from '../../shared/admin-api.service';
import type { SemesterResponse } from '../../shared/types';

export interface SemesterDialogData {
  mode: 'create' | 'edit';
  semester?: SemesterResponse;
}

function formatDateToString(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseDateString(s: string): Date {
  const [year, month, day] = s.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function startOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

@Component({
  selector: 'app-semester-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatDatepickerModule,
  ],
  templateUrl: './semester-dialog.component.html',
})
export class SemesterDialogComponent implements OnInit {
  data = inject<SemesterDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<SemesterDialogComponent>);
  private adminApi = inject(AdminApiService);
  private fb = inject(FormBuilder);

  saving = false;
  submitError = signal<string | null>(null);

  /** min attribute for dateFrom datepicker — запрещает выбрать прошлое (BUG-006-7). */
  readonly minDate = signal<Date>(startOfToday());

  /** Предупреждение при редактировании уже завершённого семестра (dateTo<today). */
  readonly readOnlyWarning = signal<string | null>(null);

  form = this.fb.group(
    {
      name: ['', Validators.required],
      dateFrom: [null as Date | null, Validators.required],
      dateTo: [null as Date | null, Validators.required],
    },
    {
      validators: [this.dateRangeValidator],
      asyncValidators: [this.overlapValidator()],
    },
  );

  get isEdit(): boolean {
    return this.data.mode === 'edit';
  }

  ngOnInit(): void {
    if (this.isEdit && this.data.semester) {
      const dateFrom = parseDateString(this.data.semester.dateFrom);
      const dateTo = parseDateString(this.data.semester.dateTo);

      this.form.patchValue({
        name: this.data.semester.name,
        dateFrom,
        dateTo,
      });

      // BUG-006-7: завершённый семестр (dateTo<today) — только чтение.
      if (dateTo < startOfToday()) {
        this.readOnlyWarning.set(
          'Завершённые семестры нельзя редактировать',
        );
        this.form.disable();
      }
    }
  }

  dateRangeValidator(control: AbstractControl): ValidationErrors | null {
    const from = control.get('dateFrom')?.value;
    const to = control.get('dateTo')?.value;
    if (from && to && to <= from) {
      return { dateRange: true };
    }
    return null;
  }

  /**
   * Async validator вызывает backend {@code GET /academic/semesters/overlap}
   * с debounce 300мс (предотвращает спам при выборе в datepicker).
   * В edit-режиме передаёт excludeId=this.data.semester.id.
   */
  overlapValidator(): AsyncValidatorFn {
    return (group: AbstractControl): Observable<ValidationErrors | null> => {
      const from = group.get('dateFrom')?.value as Date | null;
      const to = group.get('dateTo')?.value as Date | null;
      if (!from || !to || to <= from) return of(null);

      const excludeId = this.isEdit ? this.data.semester?.id : undefined;
      return timer(300).pipe(
        switchMap(() =>
          this.adminApi.checkSemesterOverlap(
            formatDateToString(from),
            formatDateToString(to),
            excludeId,
          ),
        ),
        map(res =>
          res.overlaps
            ? { overlap: res.conflictingName ?? 'существующим семестром' }
            : null,
        ),
        catchError(() => of(null)),
      );
    };
  }

  save(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;
    this.submitError.set(null);

    const { name, dateFrom, dateTo } = this.form.value;
    const payload = {
      name: name!,
      dateFrom: formatDateToString(dateFrom!),
      dateTo: formatDateToString(dateTo!),
    };

    const request$ =
      this.isEdit && this.data.semester
        ? this.adminApi.updateSemester(this.data.semester.id, payload)
        : this.adminApi.createSemester(payload);

    request$.subscribe({
      next: () => this.dialogRef.close(true),
      error: (err: HttpErrorResponse) => {
        this.saving = false;
        this.handleSaveError(err);
      },
    });
  }

  private handleSaveError(err: HttpErrorResponse): void {
    const field = err?.error?.field as string | undefined;
    const detail = err?.error?.detail as string | undefined;
    if (err.status === 409 && field === 'dates') {
      this.form.setErrors({ overlap: detail ?? 'Даты пересекаются' });
      this.submitError.set(detail ?? 'Даты пересекаются с существующим семестром');
    } else if (err.status === 409 && field === 'status') {
      this.submitError.set(detail ?? 'Нельзя редактировать завершённый семестр');
    } else if (err.status === 400 && field) {
      const control = this.form.get(field);
      if (control) control.setErrors({ badRequest: detail });
      this.submitError.set(detail ?? 'Неверный запрос');
    } else {
      this.submitError.set(detail ?? 'Не удалось сохранить семестр');
    }
  }
}
