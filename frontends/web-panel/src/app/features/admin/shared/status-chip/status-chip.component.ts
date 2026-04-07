import { Component, computed, input } from '@angular/core';
import type { AccountStatus } from '../types';

const STATUS_LABELS: Record<string, string> = {
  active: 'Активен',
  archived: 'Архивирован',
  expelled: 'Отчислен',
  suspended: 'Приостановлен',
};

@Component({
  selector: 'app-status-chip',
  standalone: true,
  template: `<span class="status-chip-admin status-chip-admin--{{status()}}">{{ statusLabel() }}</span>`,
})
export class StatusChipComponent {
  status = input.required<AccountStatus>();
  statusLabel = computed(() => STATUS_LABELS[this.status()] ?? this.status());
}
