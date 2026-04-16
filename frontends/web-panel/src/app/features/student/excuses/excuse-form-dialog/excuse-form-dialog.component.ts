import {
  ChangeDetectionStrategy, Component, Inject, OnInit, computed, inject, signal,
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
import { SubjectCacheService } from '../../shared/subject-cache.service';
import type { AttendanceRecord, ExcuseType } from '../../shared/student-schedule.types';
import { EXCUSE_TYPE_LABELS } from '../../shared/student-schedule.types';

const DAY_NAMES = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
const MONTH_NAMES = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
];

interface DayGroup {
  date: string;
  label: string;
  records: AttendanceRecord[];
}

/**
 * Student-side dialog for creating an excuse ticket (D-21, D-22).
 *
 * Показывает все пропуски («н» absent + «у» excused) с начала семестра,
 * сгруппированные по дням, с названием предмета вместо номера занятия.
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
export class ExcuseFormDialogComponent implements OnInit {
  private readonly apiService = inject(StudentApiService);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);
  readonly dialogRef = inject(MatDialogRef<ExcuseFormDialogComponent>);

  readonly lessons: AttendanceRecord[];

  /** Записи «н» (absent) и «у» (excused), отсортированные по дате от начала семестра. */
  readonly missedRecords: AttendanceRecord[];

  /**
   * When the dialog is opened from a specific lesson card, the submission is
   * locked to that single lesson — other checkboxes are disabled so students
   * cannot piggy-back unrelated absences onto a per-lesson request.
   */
  readonly lockedToLessonIds: Set<number>;

  /** Записи, сгруппированные по дню, отсортированные по возрастанию даты. */
  readonly dayGroups = signal<DayGroup[]>([]);

  /** Словарь subjectId → subject name (заполняется асинхронно). */
  readonly subjectNames = signal<Record<number, string>>({});

  readonly selectedLessonIds = signal<Set<number>>(new Set());
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly validationError = signal<string | null>(null);

  readonly excuseTypeLabels = EXCUSE_TYPE_LABELS;
  readonly excuseTypes = Object.keys(EXCUSE_TYPE_LABELS) as ExcuseType[];

  readonly hasRecords = computed(() => this.dayGroups().length > 0);

  readonly form: FormGroup;
  get excuseTypeControl(): FormControl { return this.form.get('excuseType') as FormControl; }
  get commentControl(): FormControl { return this.form.get('comment') as FormControl; }

  constructor(
    @Inject(MAT_DIALOG_DATA) data: { lessons: AttendanceRecord[]; preselectedLessonIds?: number[] },
  ) {
    this.lessons = data.lessons ?? [];
    this.missedRecords = this.lessons
      .filter(l => l.status === 'absent' || l.status === 'excused')
      .slice()
      .sort((a, b) => a.lessonDate.localeCompare(b.lessonDate) || a.lessonNumber - b.lessonNumber);

    this.dayGroups.set(this.groupByDay(this.missedRecords));

    if (data.preselectedLessonIds?.length) {
      this.selectedLessonIds.set(new Set(data.preselectedLessonIds));
      this.lockedToLessonIds = new Set(data.preselectedLessonIds);
    } else {
      this.lockedToLessonIds = new Set();
    }

    this.form = this.fb.group({
      excuseType: [null, [Validators.required]],
      comment: ['', [Validators.maxLength(1000)]],
    });
  }

  ngOnInit(): void {
    const uniqueSubjectIds = Array.from(new Set(this.missedRecords.map(r => r.subjectId)));
    uniqueSubjectIds.forEach(id => {
      this.subjectCache.getName(id).subscribe(name => {
        this.subjectNames.update(prev => ({ ...prev, [id]: name }));
      });
    });
  }

  subjectName(subjectId: number): string {
    return this.subjectNames()[subjectId] ?? 'Предмет';
  }

  toggleLesson(lessonId: number): void {
    if (this.isLocked()) return;
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

  /** True when the dialog was opened with a pre-selected lesson — selection is frozen. */
  isLocked(): boolean {
    return this.lockedToLessonIds.size > 0;
  }

  /** Disable a checkbox row if the selection is locked and this lesson isn't the locked one. */
  isDisabled(lessonId: number): boolean {
    return this.isLocked() && !this.lockedToLessonIds.has(lessonId);
  }

  readonly attachedFile = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files.length > 0 ? input.files[0] : null;
    this.fileError.set(null);
    if (!file) {
      this.attachedFile.set(null);
      return;
    }
    const MAX_BYTES = 10 * 1024 * 1024;
    if (file.size > MAX_BYTES) {
      this.fileError.set('Файл должен быть не больше 10 МБ.');
      input.value = '';
      this.attachedFile.set(null);
      return;
    }
    this.attachedFile.set(file);
  }

  clearFile(): void {
    this.attachedFile.set(null);
    this.fileError.set(null);
  }

  statusSymbol(status: string): string {
    if (status === 'absent') return 'н';
    if (status === 'excused') return 'у';
    return '';
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
    const file = this.attachedFile();

    const submit$ = file
      ? this.apiService.submitExcuseWithFile(ids, excuseType, comment, file)
      : this.apiService.submitExcuse(ids, excuseType, comment);

    submit$.subscribe({
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

  private groupByDay(records: AttendanceRecord[]): DayGroup[] {
    const map = new Map<string, AttendanceRecord[]>();
    for (const r of records) {
      const arr = map.get(r.lessonDate) ?? [];
      arr.push(r);
      map.set(r.lessonDate, arr);
    }
    return Array.from(map.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([date, recs]) => ({
        date,
        label: this.dayLabel(date),
        records: recs.slice().sort((a, b) => a.lessonNumber - b.lessonNumber),
      }));
  }

  private dayLabel(date: string): string {
    const d = new Date(date + 'T00:00:00');
    const dow = d.getDay();
    return `${DAY_NAMES[dow]}, ${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`;
  }
}
