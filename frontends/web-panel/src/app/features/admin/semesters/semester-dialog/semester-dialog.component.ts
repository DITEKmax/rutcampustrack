import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';

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

  form = this.fb.group(
    {
      name: ['', Validators.required],
      dateFrom: [null as Date | null, Validators.required],
      dateTo: [null as Date | null, Validators.required],
    },
    { validators: [this.dateRangeValidator] },
  );

  get isEdit(): boolean {
    return this.data.mode === 'edit';
  }

  ngOnInit(): void {
    if (this.isEdit && this.data.semester) {
      this.form.patchValue({
        name: this.data.semester.name,
        dateFrom: parseDateString(this.data.semester.dateFrom),
        dateTo: parseDateString(this.data.semester.dateTo),
      });
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

  save(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;

    const { name, dateFrom, dateTo } = this.form.value;
    const payload = {
      name: name!,
      dateFrom: formatDateToString(dateFrom!),
      dateTo: formatDateToString(dateTo!),
    };

    if (this.isEdit && this.data.semester) {
      this.adminApi.updateSemester(this.data.semester.id, payload).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => (this.saving = false),
      });
    } else {
      this.adminApi.createSemester(payload).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => (this.saving = false),
      });
    }
  }
}
