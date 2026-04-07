import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-overall-stat-card',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <mat-card class="p-6">
      <div class="flex gap-8">
        <div>
          <p class="text-sm text-[var(--mat-sys-on-surface)]/60">Всего занятий</p>
          <p class="text-2xl font-semibold text-[var(--mat-sys-on-surface)]">{{ totalLessons }}</p>
        </div>
        <div>
          <p class="text-sm text-[var(--mat-sys-on-surface)]/60">Посещаемость</p>
          <p class="text-2xl font-semibold text-[var(--mat-sys-on-surface)]">{{ attendanceRate }}%</p>
        </div>
      </div>
    </mat-card>
  `,
})
export class OverallStatCardComponent {
  @Input({ required: true }) totalLessons!: number;
  @Input({ required: true }) attendanceRate!: number;
}
