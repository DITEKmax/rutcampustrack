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
import { CdkTableModule } from '@angular/cdk/table';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
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
const EXTRA_STATUSES: { value: AttendanceStatus; label: string }[] = [
  { value: 'free_attendance', label: 'Свободное посещение' },
  { value: 'cancelled', label: 'Отменена' },
];

@Component({
  selector: 'app-headman-journal-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkTableModule, MatMenuModule, MatIconModule],
  styles: [`
    :host { display: block; }

    .journal-grid-wrapper {
      overflow-x: auto;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 8px;
      background: var(--mat-sys-surface);
    }

    table { border-collapse: separate; border-spacing: 0; width: 100%; }

    :host ::ng-deep cdk-header-row,
    :host ::ng-deep cdk-row {
      display: flex;
      align-items: stretch;
    }

    /* Student column — sticky left */
    .student-header, .student-cell {
      position: sticky; left: 0; z-index: 4;
      width: 220px; min-width: 220px; max-width: 220px;
      padding: 8px 12px;
      background: var(--mat-sys-surface-container);
      border-right: 1px solid var(--mat-sys-outline-variant);
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      font-size: 14px;
      display: flex; align-items: center;
    }
    .student-header { font-weight: 600; z-index: 6; }

    /* Percentage column — sticky right */
    .percent-header, .percent-cell {
      position: sticky; right: 0; z-index: 4;
      width: 72px; min-width: 72px; max-width: 72px;
      padding: 8px;
      background: var(--mat-sys-surface-container);
      border-left: 1px solid var(--mat-sys-outline-variant);
      text-align: center;
      display: flex; align-items: center; justify-content: center;
      font-weight: 600;
      font-size: 13px;
    }
    .percent-header { font-weight: 600; z-index: 6; }

    /* Date header */
    .date-header {
      width: 60px; min-width: 60px; max-width: 60px;
      padding: 4px 2px;
      background: var(--mat-sys-surface-container);
      display: flex; flex-direction: column;
      align-items: center; justify-content: flex-start;
      font-size: 11px; font-weight: 600;
      border-right: 1px solid var(--mat-sys-outline-variant);
      gap: 2px;
    }
    .date-header.today-column { background: color-mix(in srgb, var(--mat-sys-primary) 12%, transparent); }
    .date-label { line-height: 1.2; letter-spacing: 0.3px; }
    .date-label__weekday { opacity: 0.7; font-weight: 500; }

    .bulk-row { display: flex; gap: 2px; margin-top: 2px; align-items: center; }
    .bulk-btn {
      border: none; background: transparent;
      width: 16px; height: 16px; padding: 0;
      font-size: 10px; font-weight: 700;
      border-radius: 3px;
      cursor: pointer;
      display: flex; align-items: center; justify-content: center;
    }
    .bulk-btn:hover { background: var(--mat-sys-surface-variant); }
    .bulk-btn--present { color: var(--status-present); }
    .bulk-btn--absent  { color: var(--status-absent); }
    .bulk-btn--excused { color: var(--status-excused); }
    .bulk-more {
      border: none; background: transparent;
      width: 16px; height: 16px; padding: 0;
      cursor: pointer; border-radius: 3px;
      display: flex; align-items: center; justify-content: center;
      color: var(--mat-sys-on-surface-variant);
    }
    .bulk-more:hover { background: var(--mat-sys-surface-variant); }
    .bulk-more mat-icon { font-size: 14px; width: 14px; height: 14px; }

    /* Data cells */
    .status-cell {
      width: 60px; min-width: 60px; max-width: 60px;
      padding: 4px;
      display: flex; align-items: center; justify-content: center;
      border-right: 1px solid var(--mat-sys-outline-variant);
    }

    :host ::ng-deep cdk-row { min-height: 44px; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    :host ::ng-deep cdk-row:hover { background: color-mix(in srgb, var(--mat-sys-primary) 6%, transparent); }
    :host ::ng-deep cdk-row:hover .student-cell,
    :host ::ng-deep cdk-row:hover .percent-cell {
      background: color-mix(in srgb, var(--mat-sys-primary) 8%, var(--mat-sys-surface-container));
    }

    .status-btn, .status-empty {
      width: 28px; height: 28px;
      border: none; border-radius: 50%;
      cursor: pointer;
      font-size: 12px; font-weight: 700;
      display: flex; align-items: center; justify-content: center;
      transition: transform 0.12s ease, box-shadow 0.12s ease;
    }
    .status-btn:hover { transform: scale(1.1); box-shadow: 0 2px 6px rgba(0,0,0,0.15); }

    .status-chip--present { background: var(--status-present); color: var(--accent-primary-contrast, #fff); }
    .status-chip--absent { background: var(--status-absent); color: #fff; }
    .status-chip--excused { background: var(--status-excused); color: #0A0E17; }
    .status-chip--free_attendance { background: var(--status-free-attendance); color: #fff; }

    .status-empty {
      background: transparent;
      border: 1px dashed var(--mat-sys-outline);
      color: var(--mat-sys-on-surface-variant);
      cursor: pointer;
    }
    .status-empty:hover { background: var(--mat-sys-surface-variant); }

    .status-cancelled {
      width: 28px; height: 28px;
      font-size: 11px; color: var(--mat-sys-on-surface);
      opacity: 0.4; display: flex; align-items: center; justify-content: center;
      cursor: default;
    }

    :host ::ng-deep cdk-header-row {
      position: sticky; top: 0; z-index: 5;
      background: var(--mat-sys-surface-container);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      min-height: 56px;
    }
  `],
  template: `
    <div class="journal-grid-wrapper"
         [attr.aria-busy]="loading"
         [style.pointer-events]="loading ? 'none' : ''">
      <table cdk-table [dataSource]="dataSource()">

        <!-- Sticky student name column -->
        <ng-container cdkColumnDef="student" sticky>
          <th cdk-header-cell *cdkHeaderCellDef class="student-header">Студент</th>
          <td cdk-cell *cdkCellDef="let row" class="student-cell" [title]="row.displayName">
            {{ row.displayName }}
          </td>
        </ng-container>

        <!-- Dynamic date/lesson columns -->
        @for (col of columns(); track col.id) {
          <ng-container [cdkColumnDef]="col.id">
            <th cdk-header-cell *cdkHeaderCellDef class="date-header"
                [class.today-column]="col.isToday">
              <div class="date-label">
                <span class="date-label__weekday">{{ col.weekday }}</span>
                {{ col.displayDate }}
              </div>
              <div class="bulk-row" [attr.aria-label]="'Массовая отметка ' + col.weekday + ' ' + col.displayDate">
                <button type="button" class="bulk-btn bulk-btn--present"
                        (click)="bulkMark(col, 'present')"
                        title="Отметить всем «присутствовал»">+</button>
                <button type="button" class="bulk-btn bulk-btn--absent"
                        (click)="bulkMark(col, 'absent')"
                        title="Отметить всем «не был»">Н</button>
                <button type="button" class="bulk-btn bulk-btn--excused"
                        (click)="bulkMark(col, 'excused')"
                        title="Отметить всем «уважит.»">У</button>
                <button type="button" class="bulk-more"
                        [matMenuTriggerFor]="extraMenu"
                        title="Другие статусы">
                  <mat-icon>more_horiz</mat-icon>
                </button>
                <mat-menu #extraMenu="matMenu">
                  @for (extra of extraStatuses; track extra.value) {
                    <button mat-menu-item (click)="bulkMark(col, extra.value)">
                      {{ extra.label }}
                    </button>
                  }
                </mat-menu>
              </div>
            </th>
            <td cdk-cell *cdkCellDef="let row" class="status-cell">
              @let cell = getCell(row.userId, col.id);
              @if (cell) {
                @if (cell.status === 'cancelled') {
                  <span
                    class="status-cancelled"
                    aria-disabled="true"
                    tabindex="-1"
                    [attr.aria-label]="'Отменена, ' + row.displayName + ', ' + col.displayDate"
                  >{{ cell.symbol }}</span>
                } @else {
                  <button
                    class="status-btn status-chip--{{ cell.status }}"
                    [matMenuTriggerFor]="cellMenu"
                    [matMenuTriggerData]="{ cell: cell, row: row }"
                    [attr.aria-label]="'Статус: ' + cell.symbol + ', ' + row.displayName + ', ' + col.displayDate"
                  >{{ cell.symbol }}</button>
                }
              } @else {
                <button
                  class="status-empty"
                  [matMenuTriggerFor]="cellMenu"
                  [matMenuTriggerData]="{ cell: null, row: row, col: col }"
                  [attr.aria-label]="'Нет отметки, ' + row.displayName + ', ' + col.displayDate"
                >—</button>
              }
            </td>
          </ng-container>
        }

        <!-- Sticky right percentage column -->
        <ng-container cdkColumnDef="percent" stickyEnd>
          <th cdk-header-cell *cdkHeaderCellDef class="percent-header">%</th>
          <td cdk-cell *cdkCellDef="let row" class="percent-cell">
            {{ getPercent(row.userId) }}%
          </td>
        </ng-container>

        <tr cdk-header-row *cdkHeaderRowDef="displayedColumns(); sticky: true"></tr>
        <tr cdk-row *cdkRowDef="let row; columns: displayedColumns();"></tr>
      </table>

      <mat-menu #cellMenu="matMenu">
        <ng-template matMenuContent let-cell="cell" let-row="row" let-col="col">
          <button mat-menu-item (click)="setStatus(cell, row, col, 'present')">Присутствовал</button>
          <button mat-menu-item (click)="setStatus(cell, row, col, 'absent')">Не был</button>
          <button mat-menu-item (click)="setStatus(cell, row, col, 'excused')">Уважит. причина</button>
          <button mat-menu-item (click)="setStatus(cell, row, col, 'free_attendance')">Свободное посещение</button>
        </ng-template>
      </mat-menu>
    </div>
  `,
})
export class HeadmanJournalGridComponent implements OnChanges {
  @Input({ required: true }) journalData!: JournalResponse;
  @Input() loading = false;

  private readonly headmanApi = inject(HeadmanApiService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly today = new Date().toISOString().slice(0, 10);

  readonly extraStatuses = EXTRA_STATUSES;

  readonly cellMap = signal<Map<string, JournalCell>>(new Map());

  readonly columns = computed<(JournalColumn & { weekday: string })[]>(() => {
    void this.cellMap();
    if (!this.journalData) return [];
    return this.buildColumns(this.journalData);
  });

  readonly displayedColumns = computed<string[]>(() => [
    'student',
    ...this.columns().map(c => c.id),
    'percent',
  ]);

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

  private buildColumns(journal: JournalResponse): (JournalColumn & { weekday: string })[] {
    const colSet = new Map<string, JournalColumn & { weekday: string }>();
    journal.students.forEach(row =>
      row.records.forEach(cell => {
        if (cell.date > this.today) return; // hide future lessons
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

  setStatus(
    cell: JournalCell | null,
    row: JournalStudentRow,
    col: (JournalColumn & { weekday: string }) | undefined,
    next: AttendanceStatus,
  ): void {
    const resolvedCell = cell ?? (col ? this.getCell(row.userId, col.id) : undefined);
    if (!resolvedCell || !resolvedCell.lessonId) return;
    this.applyStatus(resolvedCell, row, next);
  }

  private applyStatus(cell: JournalCell, row: JournalStudentRow, next: AttendanceStatus): void {
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

  bulkMark(col: JournalColumn, status: AttendanceStatus): void {
    const targets: { cell: JournalCell; row: JournalStudentRow; prev: AttendanceStatus }[] = [];
    for (const row of this.journalData.students) {
      const cell = this.cellMap().get(`${row.userId}_${col.id}`);
      if (!cell || !cell.lessonId) continue;
      if (cell.status === 'cancelled' && status !== 'cancelled') continue;
      if (cell.status === status) continue;
      targets.push({ cell, row, prev: cell.status as AttendanceStatus });
    }
    if (targets.length === 0) return;

    targets.forEach(t => {
      t.cell.status = status;
      t.cell.symbol = STATUS_SYMBOLS[status];
    });
    this.cellMap.set(new Map(this.cellMap()));

    forkJoin(
      targets.map(t =>
        this.headmanApi.markAttendance(t.cell.lessonId!, t.row.userId, status).pipe(
          catchError(() => {
            t.cell.status = t.prev;
            t.cell.symbol = STATUS_SYMBOLS[t.prev];
            return of({ failed: true });
          }),
        ),
      ),
    ).subscribe(results => {
      const failed = results.filter((r: any) => r?.failed).length;
      if (failed > 0) {
        this.cellMap.set(new Map(this.cellMap()));
        this.snackBar.open(
          `Не удалось обновить ${failed} ${failed === 1 ? 'запись' : 'записей'}.`,
          undefined,
          { duration: 4000 },
        );
      }
    });
  }
}
