import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface DeleteSubjectDialogData {
  subjectName: string;
  /** Счётчики каскадного удаления. null → обычное подтверждение без данных. */
  cascade?: {
    scheduleItemsCount: number;
    oneOffLessonsCount: number;
    nonPlannedLessonsCount: number;
    totalLessonsCount: number;
  } | null;
}

@Component({
  selector: 'app-delete-subject-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    @if (!data.cascade) {
      <h2 mat-dialog-title>Удалить предмет?</h2>
      <mat-dialog-content>
        Предмет «{{ data.subjectName }}» будет удалён. Это действие нельзя отменить.
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button [mat-dialog-close]="false">Отмена</button>
        <button mat-flat-button class="btn-danger" [mat-dialog-close]="true">Удалить</button>
      </mat-dialog-actions>
    } @else {
      <h2 mat-dialog-title>Внимание: будут удалены данные посещаемости</h2>
      <mat-dialog-content>
        <p>
          У предмета <strong>«{{ data.subjectName }}»</strong> есть история:
        </p>
        <ul class="cascade-list">
          <li>Пар в расписании: <strong>{{ data.cascade.scheduleItemsCount }}</strong></li>
          @if (data.cascade.oneOffLessonsCount > 0) {
            <li>Разовых пар: <strong>{{ data.cascade.oneOffLessonsCount }}</strong></li>
          }
          <li>Занятий всего: <strong>{{ data.cascade.totalLessonsCount }}</strong></li>
          <li class="warning">
            Занятий с посещаемостью: <strong>{{ data.cascade.nonPlannedLessonsCount }}</strong>
          </li>
        </ul>
        <div class="warning-box">
          <i class="ph-fill ph-warning"></i>
          <div>
            <strong>Все отметки студентов по этому предмету будут удалены безвозвратно.</strong>
            Если эти данные ещё нужны — сохраните их скриншотами перед удалением.
          </div>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button [mat-dialog-close]="false">Отмена</button>
        <button mat-flat-button class="btn-danger" [mat-dialog-close]="true">
          Всё равно удалить
        </button>
      </mat-dialog-actions>
    }
  `,
  styles: [`
    .btn-danger {
      background: var(--accent-danger);
      color: white;
    }
    .cascade-list {
      margin: 8px 0 12px;
      padding-left: 20px;
      line-height: 1.8;
    }
    .cascade-list .warning {
      color: var(--accent-danger);
    }
    .warning-box {
      display: flex;
      gap: 12px;
      padding: 12px;
      margin-top: 8px;
      border-radius: var(--radius-md);
      background: color-mix(in oklab, var(--accent-danger) 10%, transparent);
      border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);
      color: var(--text-primary);
      font-size: 0.875rem;
    }
    .warning-box i {
      color: var(--accent-danger);
      font-size: 1.5rem;
      flex-shrink: 0;
    }
  `],
})
export class DeleteSubjectDialogComponent {
  readonly data = inject<DeleteSubjectDialogData>(MAT_DIALOG_DATA);
}
