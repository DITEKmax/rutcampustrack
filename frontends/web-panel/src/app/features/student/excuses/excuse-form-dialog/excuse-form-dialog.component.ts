import {
  ChangeDetectionStrategy, Component, Inject, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule, FormBuilder, FormGroup, Validators,
} from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { StudentApiService } from '../../shared/student-api.service';
import type { AttendanceRecord } from '../../shared/student-schedule.types';

// File size / count limits — T-53-02-01 mitigation
// Note: backend MUST also validate file type/size when endpoint is activated.
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
const MAX_FILE_COUNT = 5;

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
  readonly files = signal<File[]>([]);
  readonly fileErrors = signal<string[]>([]);
  readonly dragOver = signal(false);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly validationError = signal<string | null>(null);

  readonly commentForm: FormGroup;

  constructor(
    @Inject(MAT_DIALOG_DATA) data: { lessons: AttendanceRecord[] },
  ) {
    this.lessons = data.lessons ?? [];
    // Last 30 days
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - 30);
    this.recentLessons = this.lessons.filter(
      l => new Date(l.lessonDate) >= cutoff,
    );
    this.commentForm = this.fb.group({
      comment: ['', [Validators.maxLength(500)]],
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

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave(): void {
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const dropped = Array.from(event.dataTransfer?.files ?? []);
    this.addFiles(dropped);
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addFiles(Array.from(input.files ?? []));
    // Reset input so same file can be re-selected
    input.value = '';
  }

  private addFiles(newFiles: File[]): void {
    const errors: string[] = [];
    const valid: File[] = [];
    for (const f of newFiles) {
      if (f.size > MAX_FILE_SIZE_BYTES) {
        errors.push(`"${f.name}": Файл превышает 10 МБ`);
        continue;
      }
      valid.push(f);
    }
    const combined = [...this.files(), ...valid].slice(0, MAX_FILE_COUNT);
    this.files.set(combined);
    this.fileErrors.set(errors);
  }

  removeFile(index: number): void {
    this.files.update(arr => arr.filter((_, i) => i !== index));
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} Б`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} КБ`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
  }

  submit(): void {
    const ids = Array.from(this.selectedLessonIds());
    if (ids.length === 0) {
      this.validationError.set('Выберите хотя бы одно занятие');
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);
    const comment = this.commentForm.value['comment']?.trim() || null;

    // submitExcuse converts HTTP 404 → of(undefined) (graceful degradation).
    // HTTP 5xx propagates to error() handler below.
    this.apiService.submitExcuse(ids, comment, this.files()).subscribe({
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
