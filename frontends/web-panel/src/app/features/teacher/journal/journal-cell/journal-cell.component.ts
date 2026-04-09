import { Component, Input } from '@angular/core';
import type { JournalCell } from '../types';

@Component({
  selector: 'app-journal-cell',
  standalone: true,
  imports: [],
  template: `
    @if (cell) {
      <span class="status-chip status-chip--{{ cell.status }}">{{ cell.symbol }}</span>
    } @else {
      <span class="text-[var(--color-pending)]">·</span>
    }
  `,
})
export class JournalCellComponent {
  @Input() cell: JournalCell | null | undefined;
}
