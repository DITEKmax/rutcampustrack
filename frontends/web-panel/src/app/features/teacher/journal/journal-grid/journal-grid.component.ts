import { Component, Input, OnChanges, SimpleChanges, computed, signal } from '@angular/core';
import { JournalCellComponent } from '../journal-cell/journal-cell.component';
import type { JournalCell, JournalColumn, JournalResponse, JournalStudentRow } from '../types';

const WEEKDAY_LABELS = ['ВС', 'ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ'] as const;

interface RowStats {
  total: number;
  attended: number;
  absent: number;
  excused: number;
  percent: number;
}

type DisplayColumn = JournalColumn & { weekday: string };

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-journal-grid',
  standalone: true,
  imports: [JournalCellComponent],
  templateUrl: './journal-grid.component.html',
  styles: [`
    :host { display: block; }

    .journal-grid-wrapper {
      overflow: auto;
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-xl);
      background: var(--bg-secondary);
      max-height: calc(100vh - 260px);
    }

    .journal-table {
      border-collapse: separate;
      border-spacing: 0;
      width: max-content;
      min-width: 100%;
      font-size: 13px;
    }

    .student-column {
      position: sticky;
      left: 0;
      z-index: 3;
      width: 240px;
      min-width: 240px;
      max-width: 240px;
      padding: var(--space-3) var(--space-4);
      background: var(--bg-secondary);
      border-right: 1px solid var(--border-subtle);
      text-align: left;
      vertical-align: middle;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .student-column--head {
      top: 0;
      z-index: 6;
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-subtle);
      color: var(--text-muted);
      font: 500 var(--text-xs)/1 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
    }

    .date-column {
      position: sticky;
      top: 0;
      z-index: 4;
      width: 76px;
      min-width: 76px;
      padding: var(--space-3) 4px;
      background: var(--bg-surface);
      border-right: 1px solid var(--border-subtle);
      border-bottom: 1px solid var(--border-subtle);
      color: var(--text-muted);
      text-align: center;
      vertical-align: middle;
      white-space: nowrap;
      font: 500 var(--text-xs)/1.2 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
    }

    .date-column.today-column {
      color: var(--accent-primary);
      background: color-mix(in oklab, var(--accent-primary) 8%, var(--bg-surface));
    }

    .date-column__weekday {
      display: block;
      margin-bottom: 2px;
      opacity: 0.8;
    }

    .date-column__date {
      display: block;
      color: var(--text-secondary);
    }

    .date-column__lesson {
      display: block;
      margin-top: 2px;
      color: var(--text-muted);
      font-size: 10px;
      letter-spacing: 0;
      text-transform: none;
    }

    .status-cell {
      width: 76px;
      min-width: 76px;
      padding: 6px 4px;
      border-right: 1px solid var(--border-subtle);
      border-bottom: 1px solid var(--border-subtle);
      text-align: center;
      vertical-align: middle;
    }

    .stat-cell {
      position: sticky;
      right: 0;
      z-index: 3;
      width: 112px;
      min-width: 112px;
      max-width: 112px;
      padding: var(--space-3);
      background: var(--bg-secondary);
      border-left: 1px solid var(--border-subtle);
      border-bottom: 1px solid var(--border-subtle);
      text-align: center;
      vertical-align: middle;
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
    }

    .stat-cell--head {
      top: 0;
      z-index: 6;
      background: var(--bg-surface);
      color: var(--text-muted);
      font: 500 var(--text-xs)/1 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
    }

    .stat-cell--good { color: var(--accent-primary); }
    .stat-cell--warn { color: var(--accent-warning); }
    .stat-cell--bad { color: var(--accent-danger); }

    .stat-cell__percent {
      display: block;
      font-weight: 700;
    }

    .stat-cell__detail {
      display: block;
      margin-top: 2px;
      color: var(--text-muted);
      font-size: 11px;
    }

    .student-row:hover {
      background: var(--bg-elevated);
    }

    .student-row:hover .student-column,
    .student-row:hover .stat-cell {
      background: var(--bg-elevated);
    }
  `],
})
export class JournalGridComponent implements OnChanges {
  @Input({ required: true }) journalData!: JournalResponse;

  private readonly today = formatLocalDate(new Date());
  private readonly _journalData = signal<JournalResponse | null>(null);

  private cellMap = new Map<number, Map<string, JournalCell>>();

  readonly columns = computed<DisplayColumn[]>(() => {
    const data = this._journalData();
    if (!data) return [];

    const seen = new Set<string>();
    const pairs: { date: string; lessonNumber: number }[] = [];

    for (const student of data.students) {
      for (const record of student.records) {
        if (record.date > this.today) {
          continue;
        }
        const key = this.columnId(record.date, record.lessonNumber);
        if (!seen.has(key)) {
          seen.add(key);
          pairs.push({ date: record.date, lessonNumber: record.lessonNumber });
        }
      }
    }

    pairs.sort((a, b) => {
      const dateCmp = a.date.localeCompare(b.date);
      return dateCmp !== 0 ? dateCmp : a.lessonNumber - b.lessonNumber;
    });

    return pairs.map(({ date, lessonNumber }) => {
      const [year, month, day] = date.split('-');
      const dayIdx = new Date(Number(year), Number(month) - 1, Number(day)).getDay();
      return {
        id: this.columnId(date, lessonNumber),
        date,
        lessonNumber,
        displayDate: `${day}.${month}`,
        isToday: date === this.today,
        weekday: WEEKDAY_LABELS[dayIdx],
      };
    });
  });

  readonly dataSource = computed<JournalStudentRow[]>(() => {
    const data = this._journalData();
    return data ? data.students : [];
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['journalData'] && this.journalData) {
      this._journalData.set(this.journalData);
      this.buildCellMap();
    }
  }

  getCellFor(row: JournalStudentRow, columnId: string): JournalCell | null {
    return this.cellMap.get(row.userId)?.get(columnId) ?? null;
  }

  getStats(userId: number): RowStats {
    let total = 0;
    let attended = 0;
    let absent = 0;
    let excused = 0;

    for (const column of this.columns()) {
      const cell = this.cellMap.get(userId)?.get(column.id);
      if (!cell || cell.status === 'cancelled') {
        continue;
      }
      total++;
      if (cell.status === 'present' || cell.status === 'excused' || cell.status === 'free_attendance') {
        attended++;
      }
      if (cell.status === 'absent') {
        absent++;
      }
      if (cell.status === 'excused' || cell.status === 'free_attendance') {
        excused++;
      }
    }

    return {
      total,
      attended,
      absent,
      excused,
      percent: total === 0 ? 0 : Math.round((attended / total) * 100),
    };
  }

  private buildCellMap(): void {
    this.cellMap = new Map();
    for (const student of this.journalData.students) {
      const colMap = new Map<string, JournalCell>();
      for (const record of student.records) {
        colMap.set(this.columnId(record.date, record.lessonNumber), record);
      }
      this.cellMap.set(student.userId, colMap);
    }
  }

  private columnId(date: string, lessonNumber: number): string {
    return `${date}_lesson${lessonNumber}`;
  }
}
