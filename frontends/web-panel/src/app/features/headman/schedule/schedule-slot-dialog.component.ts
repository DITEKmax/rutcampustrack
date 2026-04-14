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
    <mat-dialog-actions align="end" class="dialog-actions">
      @if (isEdit) {
        <button mat-stroked-button type="button"
                class="btn-danger"
                [disabled]="submitting || deleting"
                (click)="onDelete()"
                aria-label="Удалить слот из расписания">
          @if (deleting) { <mat-spinner diameter="16"></mat-spinner> }
          <i class="ph ph-trash"></i> Удалить
        </button>
        <span class="dialog-actions__spacer"></span>
      }
      <button mat-stroked-button type="button" [mat-dialog-close]="false" [disabled]="submitting || deleting">Отмена</button>
      <button class="btn-brand" type="button"
              [disabled]="submitting || deleting || form.invalid"
              (click)="onSubmit()">
        @if (submitting) { <mat-spinner diameter="16"></mat-spinner> }
        {{ isEdit ? 'Сохранить' : 'Создать' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-actions { gap: 8px; }
    .dialog-actions__spacer { flex: 1; }
    .btn-danger {
      color: #c62828;
      border-color: #ef9a9a !important;
    }
    .btn-danger:hover:not([disabled]) {
      background: #ffebee;
    }
    .btn-danger i { margin-right: 4px; }
  `],
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
  deleting = false;
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
    // Сетка пар РУТ МИИТ (8 слотов). Используется как дефолт; сервер валидирует.
    const slots: Record<number, [string, string]> = {
      1: ['08:30', '09:50'],
      2: ['10:05', '11:25'],
      3: ['11:40', '13:00'],
      4: ['13:45', '15:05'],
      5: ['15:20', '16:40'],
      6: ['16:55', '18:15'],
      7: ['18:30', '19:50'],
      8: ['20:00', '21:20'],
    };
    const [s, e] = slots[lessonNumber] ?? ['08:30', '09:50'];
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

  onDelete(): void {
    if (!this.isEdit || !this.data.item) return;
    const ok = window.confirm(
      'Удалить этот слот из расписания на оставшиеся недели семестра?\n' +
      'Это действие нельзя отменить через интерфейс.',
    );
    if (!ok) return;
    this.deleting = true;
    this.apiError = null;
    this.headmanApi.deleteScheduleItem(this.data.item.id).subscribe({
      next: () => {
        this.deleting = false;
        this.snackBar.open('Слот удалён.', undefined, { duration: 4000 });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.deleting = false;
        if (err?.status === 403) {
          this.apiError = 'Недостаточно прав для удаления этой пары.';
        } else if (err?.status === 404) {
          this.apiError = 'Слот уже удалён.';
        } else {
          this.apiError = 'Не удалось удалить слот. Попробуйте ещё раз.';
        }
      },
    });
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
