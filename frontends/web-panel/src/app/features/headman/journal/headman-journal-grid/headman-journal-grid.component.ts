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
import { ScrollingModule } from '@angular/cdk/scrolling';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, of } from 'rxjs';
import { HeadmanApiService } from '../../shared/headman-api.service';
import type { JournalCell, JournalColumn, JournalResponse, JournalStudentRow } from '../../../teacher/journal/types';

type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled';

const NEXT_STATUS: Record<AttendanceStatus, AttendanceStatus> = {
  absent: 'present',
  present: 'excused',
  excused: 'free_attendance',
  free_attendance: 'absent',
  cancelled: 'absent', // fallback — cancelled cells are not clickable in UI
};

const STATUS_SYMBOLS: Record<AttendanceStatus, string> = {
  present: 'б',
  absent: 'н',
  excused: 'у',
  free_attendance: 'сп',
  cancelled: '--',
};

@Component({
  selector: 'app-headman-journal-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkTableModule, ScrollingModule],
  styles: [`
    .journal-grid-wrapper { overflow-x: auto; min-width: 800px; }
    .journal-viewport { height: calc(100vh - 280px); }
    :host ::ng-deep cdk-row, :host ::ng-deep cdk-header-row {
      display: flex; align-items: center; height: 40px;
    }
    .student-header, .student-cell {
      position: sticky; left: 0; z-index: 2;
      width: 200px; min-width: 200px; max-width: 200px;
      padding: 0 8px;
      background: var(--mat-sys-surface);
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      font-size: 14px;
    }
    .date-header {
      width: 48px; min-width: 48px; text-align: center;
      height: 48px; display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      font-size: 12px; font-weight: 600;
      background: var(--mat-sys-surface-container);
    }
    .today-column { border-bottom: 2px solid var(--mat-sys-primary); }
    .date-label { line-height: 1.2; }
    .lesson-label { line-height: 1.2; color: var(--mat-sys-on-surface); opacity: 0.6; }
    .status-cell {
      width: 48px; min-width: 48px; text-align: center; padding: 8px 0;
      display: flex; align-items: center; justify-content: center;
    }
    .status-btn {
      width: 36px; height: 36px;
      border: none; border-radius: 4px;
      cursor: pointer; font-size: 12px; font-weight: 600;
      display: flex; align-items: center; justify-content: center;
      transition: opacity 0.15s;
    }
    .status-btn:hover { opacity: 0.8; }
    .status-chip--present { background: #e8f5e9; color: #2e7d32; }
    .status-chip--absent { background: #ffebee; color: #c62828; }
    .status-chip--excused { background: #fff8e1; color: #f57f17; }
    .status-chip--free_attendance { background: #e3f2fd; color: #1565c0; }
    .status-cancelled {
      width: 36px; height: 36px;
      font-size: 12px; color: var(--mat-sys-on-surface);
      opacity: 0.4; display: flex; align-items: center; justify-content: center;
      cursor: default;
    }
    .even-row { background: var(--mat-sys-surface-container); }
    :host ::ng-deep cdk-header-row {
      position: sticky; top: 0; z-index: 3;
      background: var(--mat-sys-surface-container);
    }
  `],
  template: `
    <div class="journal-grid-wrapper"
         [attr.aria-busy]="loading"
         [style.pointer-events]="loading ? 'none' : ''">
      <cdk-virtual-scroll-viewport [itemSize]="40" [minBufferPx]="400" [maxBufferPx]="800"
        class="journal-viewport">
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
                <div class="date-label">{{ col.displayDate }}</div>
                <div class="lesson-label">Пара {{ col.lessonNumber }}</div>
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
                      class="status-btn status-chip status-chip--{{ cell.status }}"
                      (click)="onCellClick(cell, row)"
                      [attr.aria-label]="'Статус: ' + cell.symbol + ', ' + row.displayName + ', ' + col.displayDate"
                      [style.opacity]="loading ? '0.6' : ''"
                      [style.pointer-events]="loading ? 'none' : ''"
                    >{{ cell.symbol }}</button>
                  }
                }
              </td>
            </ng-container>
          }

          <tr cdk-header-row *cdkHeaderRowDef="displayedColumns(); sticky: true"></tr>
          <tr cdk-row *cdkRowDef="let row; columns: displayedColumns();"
              [class.even-row]="isEvenRow(row)"></tr>
        </table>
      </cdk-virtual-scroll-viewport>
    </div>
  `,
})
export class HeadmanJournalGridComponent implements OnChanges {
  @Input({ required: true }) journalData!: JournalResponse;
  @Input() loading = false;

  private readonly headmanApi = inject(HeadmanApiService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly today = new Date().toISOString().slice(0, 10);

  /** Flat cell map for optimistic updates: key = "{userId}_{colId}" */
  readonly cellMap = signal<Map<string, JournalCell>>(new Map());

  readonly columns = computed<JournalColumn[]>(() => {
    void this.cellMap(); // depend on cellMap to trigger recompute after updates
    if (!this.journalData) return [];
    return this.buildColumns(this.journalData);
  });

  readonly displayedColumns = computed<string[]>(() => [
    'student',
    ...this.columns().map(c => c.id),
  ]);

  readonly dataSource = computed<JournalStudentRow[]>(() => {
    if (!this.journalData) return [];
    return this.journalData.students;
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['journalData'] && this.journalData) {
      this.cellMap.set(this.buildCellMap(this.journalData));
    }
  }

  private buildColumns(journal: JournalResponse): JournalColumn[] {
    const colSet = new Map<string, JournalColumn>();
    journal.students.forEach(row =>
      row.records.forEach(cell => {
        const id = `${cell.date}_lesson${cell.lessonNumber}`;
        if (!colSet.has(id)) {
          const parts = cell.date.split('-');
          colSet.set(id, {
            id,
            date: cell.date,
            lessonNumber: cell.lessonNumber,
            displayDate: `${parts[2]}.${parts[1]}`,
            isToday: cell.date === this.today,
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
        map.set(`${row.userId}_${colId}`, { ...cell }); // shallow copy for optimistic mutability
      }),
    );
    return map;
  }

  getCell(userId: number, colId: string): JournalCell | undefined {
    return this.cellMap().get(`${userId}_${colId}`);
  }

  isEvenRow(row: JournalStudentRow): boolean {
    if (!this.journalData) return false;
    return this.journalData.students.indexOf(row) % 2 === 0;
  }

  onCellClick(cell: JournalCell, row: JournalStudentRow): void {
    if (cell.status === 'cancelled' || !cell.lessonId) return;

    const prevStatus = cell.status as AttendanceStatus;
    const nextStatus = NEXT_STATUS[prevStatus];

    // Optimistic update — mutate cell in map and signal new Map reference
    cell.status = nextStatus;
    cell.symbol = STATUS_SYMBOLS[nextStatus];
    this.cellMap.set(new Map(this.cellMap()));

    this.headmanApi
      .markAttendance(cell.lessonId, row.userId, nextStatus)
      .pipe(
        catchError(() => {
          // Revert on error
          cell.status = prevStatus;
          cell.symbol = STATUS_SYMBOLS[prevStatus];
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
