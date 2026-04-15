import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { HeadmanApiService } from '../../shared/headman-api.service';
import type { JournalCell, JournalColumn, JournalResponse, JournalStudentRow } from '../../../teacher/journal/types';

type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled';

const STATUS_SYMBOLS: Record<AttendanceStatus, string> = {
  present: '+',
  absent: 'Н',
  excused: 'У',
  free_attendance: 'СП',
  cancelled: '--',
};

const WEEKDAY_LABELS = ['ВС', 'ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ'] as const;

const QUICK_STATUSES: AttendanceStatus[] = ['present', 'absent', 'excused'];

@Component({
  selector: 'app-headman-journal-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  styles: [`
    :host { display: block; }

    .grid-wrapper {
      overflow-x: auto;
      overflow-y: visible;
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-xl);
      background: var(--bg-secondary);
      position: relative;
    }

    .journal-table {
      border-collapse: separate;
      border-spacing: 0;
      width: max-content;
      min-width: 100%;
      font-size: 13px;
    }

    /* Student column — sticky left */
    .col-student {
      position: sticky; left: 0; z-index: 3;
      width: 220px; min-width: 220px; max-width: 220px;
      padding: var(--space-3) var(--space-4);
      background: var(--bg-secondary);
      border-right: 1px solid var(--border-subtle);
      text-align: left;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      vertical-align: middle;
    }
    .col-student--head {
      z-index: 6;
      background: var(--bg-surface);
      font: 500 var(--text-xs)/1 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
      color: var(--text-muted);
      border-bottom: 1px solid var(--border-subtle);
    }

    /* Date column header */
    .col-date {
      min-width: 72px; width: 72px;
      padding: var(--space-3) 4px;
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-subtle);
      border-right: 1px solid var(--border-subtle);
      text-align: center;
      font: 500 var(--text-xs)/1.2 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
      color: var(--text-muted);
      vertical-align: middle;
      white-space: nowrap;
    }
    .col-date.today-column {
      color: var(--accent-primary);
      background: color-mix(in oklab, var(--accent-primary) 8%, var(--bg-surface));
    }
    .col-date__weekday { display: block; opacity: 0.8; margin-bottom: 2px; }
    .col-date__date { display: block; color: var(--text-secondary); }

    /* Percent column — sticky right */
    .col-percent {
      position: sticky; right: 0; z-index: 3;
      width: 72px; min-width: 72px; max-width: 72px;
      padding: var(--space-3) var(--space-3);
      background: var(--bg-secondary);
      border-left: 1px solid var(--border-subtle);
      text-align: center;
      vertical-align: middle;
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
      font-weight: 600;
    }
    .col-percent--head {
      z-index: 6;
      background: var(--bg-surface);
      font: 500 var(--text-xs)/1 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
      color: var(--text-muted);
      font-weight: 500;
      border-bottom: 1px solid var(--border-subtle);
    }
    .col-percent--good { color: var(--accent-primary); }
    .col-percent--warn { color: var(--accent-warning); }
    .col-percent--bad  { color: var(--accent-danger); }

    /* Body */
    .row-student { transition: background var(--duration-base) var(--ease-out); }
    .row-student td {
      border-bottom: 1px solid var(--border-subtle);
      vertical-align: middle;
    }
    .row-student:hover { background: var(--bg-elevated); }
    .row-student:hover .col-student,
    .row-student:hover .col-percent { background: var(--bg-elevated); }

    .cell {
      min-width: 72px; width: 72px;
      padding: 4px;
      text-align: center;
      border-right: 1px solid var(--border-subtle);
      vertical-align: middle;
    }

    /* Three mini-buttons per cell */
    .cell-buttons {
      display: inline-flex;
      gap: 3px;
      align-items: center;
      justify-content: center;
    }
    .dot {
      width: 20px; height: 20px;
      border-radius: 50%;
      border: 1px solid var(--border-subtle);
      background: transparent;
      color: var(--text-muted);
      font-size: 10px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      opacity: 0.45;
      transition: transform 0.1s ease, opacity 0.15s ease, background 0.15s ease;
      padding: 0;
    }
    .dot:hover { opacity: 1; transform: scale(1.1); }
    .dot--active {
      opacity: 1;
      color: var(--accent-primary-contrast, #fff);
      border-color: transparent;
    }
    .dot--present.dot--active { background: var(--status-present); }
    .dot--absent.dot--active  { background: var(--status-absent); color: #fff; }
    .dot--excused.dot--active { background: var(--status-excused); color: #0A0E17; }

    .cell-cancelled {
      color: var(--text-muted);
      opacity: 0.5;
      font-size: 12px;
    }
  `],
  template: `
    <div class="grid-wrapper"
         [attr.aria-busy]="loading"
         [style.pointer-events]="loading ? 'none' : ''">
      <table class="journal-table">
        <thead>
          <tr>
            <th class="col-student col-student--head">Студент</th>
            @for (col of columns(); track col.id) {
              <th class="col-date" [class.today-column]="col.isToday">
                <span class="col-date__weekday">{{ col.weekday }}</span>
                <span class="col-date__date">{{ col.displayDate }}</span>
              </th>
            }
            <th class="col-percent col-percent--head">%</th>
          </tr>
        </thead>
        <tbody>
          @for (row of dataSource(); track row.userId) {
            <tr class="row-student">
              <td class="col-student" [title]="row.displayName">{{ row.displayName }}</td>
              @for (col of columns(); track col.id) {
                <td class="cell">
                  @let cell = getCell(row.userId, col.id);
                  @if (cell && cell.status === 'cancelled') {
                    <span class="cell-cancelled"
                          [attr.aria-label]="'Отменена, ' + row.displayName + ', ' + col.displayDate">
                      {{ cell.symbol }}
                    </span>
                  } @else if (cell) {
                    <span class="cell-buttons">
                      @for (s of quickStatuses; track s) {
                        <button
                          type="button"
                          class="dot dot--{{ s }}"
                          [class.dot--active]="cell.status === s"
                          [attr.aria-label]="statusAriaLabel(s) + ', ' + row.displayName + ', ' + col.displayDate"
                          [attr.aria-pressed]="cell.status === s"
                          (click)="setStatus(cell, row, s)"
                        >{{ statusSymbol(s) }}</button>
                      }
                    </span>
                  } @else {
                    <span class="cell-cancelled" aria-label="Нет занятия">—</span>
                  }
                </td>
              }
              <td class="col-percent" [class]="percentClass(getPercent(row.userId))">
                {{ getPercent(row.userId) }}%
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class HeadmanJournalGridComponent implements OnChanges {
  @Input({ required: true }) journalData!: JournalResponse;
  @Input() loading = false;

  private readonly headmanApi = inject(HeadmanApiService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly today = new Date().toISOString().slice(0, 10);

  readonly quickStatuses = QUICK_STATUSES;

  readonly cellMap = signal<Map<string, JournalCell>>(new Map());

  readonly columns = computed<(JournalColumn & { weekday: string })[]>(() => {
    void this.cellMap();
    if (!this.journalData) return [];
    return this.buildColumns(this.journalData);
  });

  readonly dataSource = computed<JournalStudentRow[]>(() => {
    if (!this.journalData) return [];
    return [...this.journalData.students].sort((a, b) =>
      a.displayName.localeCompare(b.displayName, 'ru'),
    );
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['journalData'] && this.journalData) {
      this.cellMap.set(this.buildCellMap(this.journalData));
    }
  }

  statusSymbol(s: AttendanceStatus): string {
    return STATUS_SYMBOLS[s];
  }
  statusAriaLabel(s: AttendanceStatus): string {
    switch (s) {
      case 'present': return 'Присутствовал';
      case 'absent': return 'Не был';
      case 'excused': return 'Уважительная причина';
      case 'free_attendance': return 'Свободное посещение';
      case 'cancelled': return 'Отменена';
    }
  }

  percentClass(p: number): string {
    if (p >= 80) return 'col-percent--good';
    if (p >= 50) return 'col-percent--warn';
    return 'col-percent--bad';
  }

  private buildColumns(journal: JournalResponse): (JournalColumn & { weekday: string })[] {
    const colSet = new Map<string, JournalColumn & { weekday: string }>();
    journal.students.forEach(row =>
      row.records.forEach(cell => {
        if (cell.date > this.today) return;
        const id = `${cell.date}_lesson${cell.lessonNumber}`;
        if (!colSet.has(id)) {
          const parts = cell.date.split('-');
          const dayIdx = new Date(cell.date + 'T00:00:00').getDay();
          colSet.set(id, {
            id,
            date: cell.date,
            lessonNumber: cell.lessonNumber,
            displayDate: `${parts[2]}.${parts[1]}`,
            isToday: cell.date === this.today,
            weekday: WEEKDAY_LABELS[dayIdx],
          });
        }
      }),
    );
    return Array.from(colSet.values()).sort(
      (a, b) => a.date.localeCompare(b.date) || a.lessonNumber - b.lessonNumber,
    );
  }

  private buildCellMap(journal: JournalResponse): Map<string, JournalCell> {
    const map = new Map<string, JournalCell>();
    journal.students.forEach(row =>
      row.records.forEach(cell => {
        const colId = `${cell.date}_lesson${cell.lessonNumber}`;
        map.set(`${row.userId}_${colId}`, { ...cell });
      }),
    );
    return map;
  }

  getCell(userId: number, colId: string): JournalCell | undefined {
    return this.cellMap().get(`${userId}_${colId}`);
  }

  getPercent(userId: number): number {
    const cols = this.columns();
    let counted = 0;
    let attended = 0;
    for (const col of cols) {
      const cell = this.cellMap().get(`${userId}_${col.id}`);
      if (!cell || cell.status === 'cancelled') continue;
      counted++;
      if (cell.status === 'present' || cell.status === 'excused' || cell.status === 'free_attendance') {
        attended++;
      }
    }
    if (counted === 0) return 0;
    return Math.round((attended / counted) * 100);
  }

  setStatus(cell: JournalCell, row: JournalStudentRow, next: AttendanceStatus): void {
    if (!cell.lessonId || cell.status === next) return;
    const prev = cell.status as AttendanceStatus;

    cell.status = next;
    cell.symbol = STATUS_SYMBOLS[next];
    this.cellMap.set(new Map(this.cellMap()));

    this.headmanApi
      .markAttendance(cell.lessonId, row.userId, next)
      .pipe(
        catchError(() => {
          cell.status = prev;
          cell.symbol = STATUS_SYMBOLS[prev];
          this.cellMap.set(new Map(this.cellMap()));
          this.snackBar.open('Не удалось изменить статус. Попробуйте снова.', undefined, {
            duration: 4000,
          });
          return of(null);
        }),
      )
      .subscribe();
  }
}
