import { Component, Input, computed, signal, OnChanges, SimpleChanges } from '@angular/core';
import { CdkTableModule } from '@angular/cdk/table';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { JournalCellComponent } from '../journal-cell/journal-cell.component';
import type { JournalCell, JournalColumn, JournalResponse, JournalStudentRow } from '../types';

@Component({
  selector: 'app-journal-grid',
  standalone: true,
  imports: [CdkTableModule, ScrollingModule, JournalCellComponent],
  templateUrl: './journal-grid.component.html',
  styles: [`
    .journal-grid-wrapper { overflow-x: auto; min-width: 800px; }
    .journal-viewport { height: calc(100vh - 220px); }
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
    .status-cell { width: 48px; min-width: 48px; text-align: center; padding: 8px 0; }
    .even-row { background: var(--mat-sys-surface-container); }
    :host ::ng-deep cdk-header-row {
      position: sticky; top: 0; z-index: 3;
      background: var(--mat-sys-surface-container);
    }
  `],
})
export class JournalGridComponent implements OnChanges {
  @Input({ required: true }) journalData!: JournalResponse;

  private readonly today = new Date().toISOString().slice(0, 10);

  /** Internal signal holding current journalData for computed signals */
  private readonly _journalData = signal<JournalResponse | null>(null);

  /** Pre-computed lookup: userId -> (columnId -> JournalCell) */
  private cellMap = new Map<number, Map<string, JournalCell>>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['journalData'] && this.journalData) {
      this._journalData.set(this.journalData);
      this.buildCellMap();
    }
  }

  private buildCellMap(): void {
    this.cellMap = new Map();
    for (const student of this.journalData.students) {
      const colMap = new Map<string, JournalCell>();
      for (const record of student.records) {
        const key = `${record.date}_lesson${record.lessonNumber}`;
        colMap.set(key, record);
      }
      this.cellMap.set(student.userId, colMap);
    }
  }

  readonly columns = computed<JournalColumn[]>(() => {
    const data = this._journalData();
    if (!data) return [];

    // Collect unique (date, lessonNumber) pairs
    const seen = new Set<string>();
    const pairs: { date: string; lessonNumber: number }[] = [];

    for (const student of data.students) {
      for (const record of student.records) {
        const key = `${record.date}_lesson${record.lessonNumber}`;
        if (!seen.has(key)) {
          seen.add(key);
          pairs.push({ date: record.date, lessonNumber: record.lessonNumber });
        }
      }
    }

    // Sort by date then lessonNumber
    pairs.sort((a, b) => {
      const dateCmp = a.date.localeCompare(b.date);
      return dateCmp !== 0 ? dateCmp : a.lessonNumber - b.lessonNumber;
    });

    return pairs.map(({ date, lessonNumber }) => {
      // "дд.мм" format
      const [year, month, day] = date.split('-');
      const displayDate = `${day}.${month}`;
      return {
        id: `${date}_lesson${lessonNumber}`,
        date,
        lessonNumber,
        displayDate,
        isToday: date === this.today,
      };
    });
  });

  readonly displayedColumns = computed<string[]>(() => [
    'student',
    ...this.columns().map(c => c.id),
  ]);

  readonly dataSource = computed<JournalStudentRow[]>(() => {
    const data = this._journalData();
    return data ? data.students : [];
  });

  getCellFor(row: JournalStudentRow, date: string, lessonNumber: number): JournalCell | null {
    const colId = `${date}_lesson${lessonNumber}`;
    return this.cellMap.get(row.userId)?.get(colId) ?? null;
  }

  isEvenRow(row: JournalStudentRow): boolean {
    const data = this._journalData();
    if (!data) return false;
    const index = data.students.indexOf(row);
    return index % 2 === 0;
  }
}
