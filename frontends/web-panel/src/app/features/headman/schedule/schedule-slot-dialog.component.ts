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

export interface ScheduleSlotDialogData {
  mode: 'create' | 'edit';
  /** For create mode — pre-filled day and slot from matrix click. */
  dayOfWeek?: number;
  lessonNumber?: number;
  groupId: number;
  semesterId: number;
  /** For edit mode — the existing ScheduleItemResponse. */
  item?: {
    id: number;
    subjectId: number;
    dayOfWeek: number;
    lessonNumber: number;
    startTime: string;
    endTime: string;
    weekType: string;
    room?: string;
  };
}

/**
 * MatDialog for creating / editing a schedule template slot.
 *
 * D-13: фиксированный набор полей — subject (из каталога группы) + classroom
 * (room) + WeekType (ALL/ODD/EVEN). Преподаватель НЕ выбирается (D-16).
 * D-05: lessonNumber фиксирован выбранной ячейкой матрицы 1..8.
 * D-11: староста правит только свою группу (groupId из JWT передаётся
 *       родительским компонентом через data.groupId).
 */
@Component({
  selector: 'app-schedule-slot-dialog',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Редактировать слот' : 'Новый слот' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form">
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

        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>Кабинет</mat-label>
          <input matInput formControlName="room" maxlength="64" placeholder="Например: 301" />
        </mat-form-field>

        <mat-form-field appearance="outline" style="width:100%">
          <mat-label>Тип недели</mat-label>
          <mat-select formControlName="weekType" aria-label="Тип недели">
            <mat-option value="ALL">Каждую неделю</mat-option>
            <mat-option value="EVEN">1-я (чётная)</mat-option>
            <mat-option value="ODD">2-я (нечётная)</mat-option>
          </mat-select>
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
        {{ isEdit ? 'Сохранить' : 'Создать' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ScheduleSlotDialogComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<ScheduleSlotDialogComponent>);
  readonly data = inject<ScheduleSlotDialogData>(MAT_DIALOG_DATA);

  readonly isEdit: boolean;

  form = new FormGroup({
    subjectId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    room: new FormControl<string>('', { nonNullable: true }),
    weekType: new FormControl<string>('ALL', { nonNullable: true, validators: [Validators.required] }),
  });

  subjects: { id: number; name: string }[] = [];
  subjectsLoading = true;
  subjectsError = false;
  submitting = false;
  apiError: string | null = null;

  constructor() {
    this.isEdit = this.data.mode === 'edit';
    if (this.isEdit && this.data.item) {
      this.form.patchValue({
        subjectId: this.data.item.subjectId,
        room: this.data.item.room ?? '',
        weekType: this.data.item.weekType ?? 'ALL',
      });
    }
  }

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

  private defaultSlotTimes(lessonNumber: number): { startTime: string; endTime: string } {
    // Стандартная сетка слотов 1..8 (используется для создания, сервер валидирует).
    const slots: Record<number, [string, string]> = {
      1: ['09:00', '10:30'],
      2: ['10:40', '12:10'],
      3: ['12:40', '14:10'],
      4: ['14:20', '15:50'],
      5: ['16:00', '17:30'],
      6: ['17:40', '19:10'],
      7: ['19:20', '20:50'],
      8: ['21:00', '22:30'],
    };
    const [s, e] = slots[lessonNumber] ?? ['09:00', '10:30'];
    return { startTime: s, endTime: e };
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.apiError = null;

    const v = this.form.value;
    if (this.isEdit && this.data.item) {
      const body = {
        subjectId: v.subjectId!,
        dayOfWeek: this.data.item.dayOfWeek,
        lessonNumber: this.data.item.lessonNumber,
        startTime: this.data.item.startTime,
        endTime: this.data.item.endTime,
        weekType: v.weekType!,
        room: v.room || undefined,
      };
      this.headmanApi.updateScheduleItem(this.data.item.id, body).subscribe({
        next: () => {
          this.submitting = false;
          this.snackBar.open('Слот обновлён.', undefined, { duration: 4000 });
          this.dialogRef.close(true);
        },
        error: (err) => this.handleError(err),
      });
    } else {
      const lessonNumber = this.data.lessonNumber!;
      const { startTime, endTime } = this.defaultSlotTimes(lessonNumber);
      const body = {
        groupId: this.data.groupId,
        subjectId: v.subjectId!,
        semesterId: this.data.semesterId,
        dayOfWeek: this.data.dayOfWeek!,
        lessonNumber,
        startTime,
        endTime,
        weekType: v.weekType!,
        room: v.room || undefined,
      };
      this.headmanApi.createScheduleItem(body).subscribe({
        next: () => {
          this.submitting = false;
          this.snackBar.open('Слот создан.', undefined, { duration: 4000 });
          this.dialogRef.close(true);
        },
        error: (err) => this.handleError(err),
      });
    }
  }

  private handleError(err: any): void {
    this.submitting = false;
    if (err?.status === 403) {
      this.apiError = 'Недостаточно прав для изменения расписания этой группы.';
    } else if (err?.status === 409) {
      this.apiError = 'Конфликт: слот пересекается с существующим.';
    } else {
      this.apiError = 'Не удалось сохранить слот. Попробуйте ещё раз.';
    }
  }
}
