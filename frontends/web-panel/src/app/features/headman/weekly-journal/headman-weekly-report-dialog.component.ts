import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, of } from 'rxjs';

import { HeadmanApiService } from '../shared/headman-api.service';
import type {
  HeadmanWeeklyReportFormat,
  HeadmanWeeklyWeekOption,
  ReportBlobResponse,
} from '../shared/headman-report.types';

@Component({
  selector: 'app-headman-weekly-report-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Скачать несколько недель</h2>
    <mat-dialog-content>
      @if (loading()) {
        <div class="report-dialog__loader">
          <mat-spinner diameter="28"></mat-spinner>
        </div>
      } @else if (error()) {
        <div class="page-error" role="alert">
          <i class="ph ph-warning-circle"></i>
          {{ error() }}
        </div>
      } @else {
        <div class="report-dialog__section">
          <span class="report-dialog__label">Формат</span>
          <div class="report-format-toggle" role="group" aria-label="Формат отчета">
            @for (f of formats; track f) {
              <button
                type="button"
                class="report-format-toggle__button"
                [class.report-format-toggle__button--active]="format() === f"
                [attr.aria-pressed]="format() === f"
                (click)="format.set(f)"
              >
                {{ f.toUpperCase() }}
              </button>
            }
          </div>
        </div>

        <div class="report-dialog__section">
          <span class="report-dialog__label">Учебные недели</span>
          <div class="report-week-list">
            @for (week of weeks(); track week.weekStart) {
              <div
                class="report-week-option"
                [class.report-week-option--selected]="isSelected(week.weekStart)"
              >
                <mat-checkbox
                  [checked]="isSelected(week.weekStart)"
                  (change)="toggleWeek(week.weekStart, $event)"
                >
                  <span class="report-week-option__main">
                    <span class="report-week-option__label">
                      {{ week.label || ('Н' + week.weekOfSemester) }}
                    </span>
                    <span class="report-week-option__range">
                      {{ formatDateRange(week) }}
                    </span>
                  </span>
                  @if (week.current) {
                    <span class="report-week-option__badge">текущая</span>
                  }
                </mat-checkbox>
              </div>
            }
          </div>
        </div>

        @if (selectionError()) {
          <div class="page-error" role="alert">
            <i class="ph ph-warning-circle"></i>
            {{ selectionError() }}
          </div>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="null">Отмена</button>
      <button
        class="btn-brand"
        type="button"
        [disabled]="loading() || submitting() || selectedWeekStarts().length === 0"
        (click)="exportSelected()"
      >
        @if (submitting()) {
          <mat-spinner diameter="16"></mat-spinner>
        } @else {
          <i class="ph ph-download-simple"></i>
        }
        Скачать
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content {
      min-width: min(560px, 82vw);
      padding-top: var(--space-3);
    }

    .report-dialog__loader {
      display: flex;
      justify-content: center;
      padding: var(--space-7) var(--space-4);
    }

    .report-dialog__section {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin-bottom: var(--space-5);
    }

    .report-dialog__label {
      font: 600 0.75rem/1 var(--font-mono);
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--text-muted);
    }

    .report-format-toggle {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      width: fit-content;
      padding: 4px;
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      background: var(--bg-surface);
    }

    .report-format-toggle__button {
      border: 0;
      border-radius: var(--radius-sm);
      padding: 8px 14px;
      min-width: 62px;
      background: transparent;
      color: var(--text-secondary);
      font: 700 0.75rem/1 var(--font-mono);
      cursor: pointer;
      transition:
        background-color var(--duration-base) var(--ease-out),
        color var(--duration-base) var(--ease-out);
    }

    .report-format-toggle__button--active {
      background: var(--accent-primary);
      color: var(--accent-primary-contrast);
    }

    .report-week-list {
      display: grid;
      gap: var(--space-2);
      max-height: 420px;
      overflow: auto;
      padding-right: var(--space-1);
    }

    .report-week-option {
      display: block;
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      background: var(--bg-surface);
      padding: var(--space-3);
      transition:
        background-color var(--duration-base) var(--ease-out),
        border-color var(--duration-base) var(--ease-out);
    }

    .report-week-option--selected {
      border-color: color-mix(in oklab, var(--accent-primary) 48%, transparent);
      background: color-mix(in oklab, var(--accent-primary) 9%, transparent);
    }

    .report-week-option__main {
      display: inline-flex;
      align-items: baseline;
      gap: var(--space-3);
      min-width: 280px;
    }

    .report-week-option__label {
      font-family: var(--font-heading);
      font-weight: 700;
      color: var(--text-primary);
      min-width: 42px;
    }

    .report-week-option__range {
      font-family: var(--font-mono);
      font-size: 0.8125rem;
      color: var(--text-secondary);
    }

    .report-week-option__badge {
      display: inline-flex;
      align-items: center;
      margin-left: var(--space-3);
      padding: 2px 8px;
      border-radius: var(--radius-full);
      background: color-mix(in oklab, var(--accent-secondary) 14%, transparent);
      color: var(--accent-secondary);
      font: 600 0.6875rem/1.4 var(--font-mono);
      letter-spacing: 0.03em;
      text-transform: uppercase;
    }
  `],
})
export class HeadmanWeeklyReportDialogComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly dialogRef = inject(MatDialogRef<HeadmanWeeklyReportDialogComponent, ReportBlobResponse | null>);

  readonly formats: HeadmanWeeklyReportFormat[] = ['docx', 'pdf', 'png'];
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectionError = signal<string | null>(null);
  readonly weeks = signal<HeadmanWeeklyWeekOption[]>([]);
  readonly selectedWeekStarts = signal<string[]>([]);
  readonly format = signal<HeadmanWeeklyReportFormat>('docx');

  ngOnInit(): void {
    this.headmanApi
      .listHeadmanWeeklyReportWeeks()
      .pipe(
        catchError(() => {
          this.error.set('Не удалось загрузить недели активного семестра.');
          return of(null);
        }),
      )
      .subscribe(resp => {
        this.loading.set(false);
        if (!resp) return;
        this.weeks.set(resp.weeks ?? []);
        const current = resp.weeks?.find(w => w.current) ?? resp.weeks?.[0];
        if (current) {
          this.selectedWeekStarts.set([current.weekStart]);
        }
      });
  }

  isSelected(weekStart: string): boolean {
    return this.selectedWeekStarts().includes(weekStart);
  }

  toggleWeek(weekStart: string, event: MatCheckboxChange): void {
    const selected = new Set(this.selectedWeekStarts());
    if (event.checked) {
      selected.add(weekStart);
    } else {
      selected.delete(weekStart);
    }
    this.selectedWeekStarts.set(this.weeks()
      .map(w => w.weekStart)
      .filter(start => selected.has(start)));
    this.selectionError.set(null);
  }

  formatDateRange(week: HeadmanWeeklyWeekOption): string {
    return `${this.formatIsoDate(week.weekStart)} - ${this.formatIsoDate(week.weekEnd)}`;
  }

  exportSelected(): void {
    const weekStarts = this.selectedWeekStarts();
    if (weekStarts.length === 0) {
      this.selectionError.set('Выберите хотя бы одну неделю.');
      return;
    }
    this.submitting.set(true);
    this.selectionError.set(null);
    this.headmanApi
      .exportHeadmanWeeklyReport({ weekStarts, format: this.format() })
      .subscribe({
        next: response => {
          this.submitting.set(false);
          this.dialogRef.close(response);
        },
        error: err => {
          this.submitting.set(false);
          this.selectionError.set(this.errorMessageFor(err));
        },
      });
  }

  private formatIsoDate(iso: string): string {
    const [year, month, day] = iso.split('-');
    return `${day}.${month}.${year}`;
  }

  private errorMessageFor(err: any): string {
    if (err?.status === 422) return 'Отчет не помещается в текущий шаблон или неделя вне семестра.';
    if (err?.status === 503) return 'Сервис подготовки PDF/PNG временно недоступен.';
    if (err?.status === 403) return 'Скачивание доступно только старосте группы.';
    return 'Не удалось скачать отчет. Попробуйте позже.';
  }
}
