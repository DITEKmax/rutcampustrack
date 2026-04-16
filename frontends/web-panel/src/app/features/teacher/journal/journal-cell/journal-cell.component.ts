import { Component, Input } from '@angular/core';
import type { JournalCell } from '../types';
import { symbolFor } from '../../../../shared/attendance-symbols';

@Component({
  selector: 'app-journal-cell',
  standalone: true,
  imports: [],
  template: `
    @if (cell) {
      <span
        class="status-chip status-chip--{{ cell.status }}"
        [attr.title]="reasonTitle()"
      >{{ displaySymbol() }}</span>
      @if ((cell.status === 'excused' || cell.status === 'free_attendance') && cell.excuseReason) {
        <span class="excuse-reason" [attr.title]="cell.excuseReason">{{ cell.excuseReason }}</span>
      }
    } @else {
      <span class="text-[var(--color-pending)]">·</span>
    }
  `,
  styles: [`
    .excuse-reason {
      display: block;
      margin-top: 2px;
      font-size: 10px;
      line-height: 1.2;
      color: var(--status-excused, var(--accent-warning));
      max-width: 72px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `],
})
export class JournalCellComponent {
  @Input() cell: JournalCell | null | undefined;

  displaySymbol(): string {
    return this.cell ? symbolFor(this.cell.status) : '';
  }

  reasonTitle(): string | null {
    if (!this.cell) return null;
    if (this.cell.status !== 'excused' && this.cell.status !== 'free_attendance') return null;
    return this.cell.excuseReason ?? null;
  }
}
