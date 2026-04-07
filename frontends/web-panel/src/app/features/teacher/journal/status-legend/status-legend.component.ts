import { Component } from '@angular/core';

interface LegendEntry {
  symbol: string;
  label: string;
  status: string;
}

@Component({
  selector: 'app-status-legend',
  standalone: true,
  template: `
    <div class="flex flex-wrap gap-x-6 gap-y-2 items-center">
      @for (entry of entries; track entry.status) {
        <div class="flex items-center gap-2">
          <span class="status-chip status-chip--{{ entry.status }}">{{ entry.symbol }}</span>
          <span class="text-[12px] opacity-60">{{ entry.label }}</span>
        </div>
      }
    </div>
  `,
})
export class StatusLegendComponent {
  readonly entries: LegendEntry[] = [
    { symbol: 'б', label: 'присутствовал', status: 'present' },
    { symbol: 'н', label: 'отсутствовал', status: 'absent' },
    { symbol: 'у', label: 'уважительная причина', status: 'excused' },
    { symbol: 'сп', label: 'свободное посещение', status: 'free_attendance' },
    { symbol: '—', label: 'отменена', status: 'cancelled' },
  ];
}
