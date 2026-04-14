import {
  ChangeDetectionStrategy, Component, Inject, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule, FormBuilder, FormGroup, FormControl, Validators,
} from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { StudentApiService } from '../../shared/student-api.service';
import type { AttendanceRecord, ExcuseType } from '../../shared/student-schedule.types';
import { EXCUSE_TYPE_LABELS } from '../../shared/student-schedule.types';

/**
 * Student-side dialog for creating an excuse ticket (D-21, D-22).
 *
 * Scope (Phase 59-07):
 *  - Dropdown «Причина пропуска» with 6 ExcuseType values (Russian labels)
 *  - Checkbox list of last-30-days lessons for lesson selection
 *  - Optional comment (max 1000 chars — backend validation)
 *  - JSON submit via StudentApiService.submitExcuse(ids, excuseType, comment)
 *
 * Out of scope (D-03): file attachments — deferred to future Telegram flow.
 */
@Component({
  selector: 'app-excuse-form-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './excuse-form-dialog.component.html',
  styleUrl: './excuse-form-dialog.component.css',
})
export class ExcuseFormDialogComponent {
  private readonly apiService = inject(StudentApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);
  readonly dialogRef = inject(MatDialogRef<ExcuseFormDialogComponent>);

  readonly lessons: AttendanceRecord[];

  // Filter to last 30 days for lesson selection
  readonly recentLessons: AttendanceRecord[];

  readonly selectedLessonIds = signal<Set<number>>(new Set());
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly validationError = signal<string | null>(null);

  readonly excuseTypeLabels = EXCUSE_TYPE_LABELS;
  readonly excuseTypes = Object.keys(EXCUSE_TYPE_LABELS) as ExcuseType[];

  readonly form: FormGroup;
  get excuseTypeControl(): FormControl { return this.form.get('excuseType') as FormControl; }
  get commentControl(): FormControl { return this.form.get('comment') as FormControl; }

  constructor(
    @Inject(MAT_DIALOG_DATA) data: { lessons: AttendanceRecord[] },
  ) {
    this.lessons = data.lessons ?? [];
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - 30);
    this.recentLessons = this.lessons.filter(
      l => new Date(l.lessonDate) >= cutoff,
    );
    this.form = this.fb.group({
      excuseType: [null, [Validators.required]],
      comment: ['', [Validators.maxLength(1000)]],
    });
  }

  toggleLesson(lessonId: number): void {
    this.selectedLessonIds.update(set => {
      const next = new Set(set);
      if (next.has(lessonId)) next.delete(lessonId);
      else next.add(lessonId);
      return next;
    });
    this.validationError.set(null);
  }

  isSelected(lessonId: number): boolean {
    return this.selectedLessonIds().has(lessonId);
  }

  submit(): void {
    const ids = Array.from(this.selectedLessonIds());
    if (ids.length === 0) {
      this.validationError.set('Выберите хотя бы одно занятие');
      return;
    }
    const excuseType = this.excuseTypeControl.value as ExcuseType | null;
    if (!excuseType) {
      this.validationError.set('Выберите причину пропуска');
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);
    const comment = (this.commentControl.value as string)?.trim() || null;

    this.apiService.submitExcuse(ids, excuseType, comment).subscribe({
      next: () => {
        this.submitting.set(false);
        this.dialogRef.close(true);
        this.snackBar.open(
          'Запрос отправлен. Подтверждение придёт в Telegram.',
          'Закрыть',
          { duration: 5000, panelClass: ['snack-success'] },
        );
      },
      error: () => {
        this.submitting.set(false);
        this.submitError.set('Не удалось подать тикет. Попробуйте ещё раз.');
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
