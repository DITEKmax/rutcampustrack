import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import type { OverallStats } from '../../shared/student-schedule.types';

@Component({
  selector: 'app-student-overall-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <div class="overall-card" aria-live="polite">
      <div class="overall-card__stat">
        <span class="overall-card__label">Всего занятий</span>
        <span class="overall-card__value overall-card__value--mono">{{ stats.total }}</span>
      </div>
      <div class="overall-card__stat">
        <span class="overall-card__label">Посещаемость</span>
        <span
          class="overall-card__percentage"
          [class.is-good]="stats.percentage >= threshold"
          [class.is-warning]="stats.percentage < threshold">
          {{ stats.percentage }}%
        </span>
      </div>
    </div>
  `,
  styles: [`
    .overall-card {
      background: var(--bg-secondary);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-xl);
      padding: var(--space-5);
      display: flex;
      gap: var(--space-6);
      align-items: center;
      margin-bottom: var(--space-5);
    }
    .overall-card__stat { display: flex; flex-direction: column; gap: var(--space-1); }
    .overall-card__label { font-size: var(--text-xs); color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; font-weight: 600; }
    .overall-card__value--mono { font-size: var(--text-2xl); font-family: var(--font-mono); font-weight: 600; font-variant-numeric: tabular-nums; }
    .overall-card__percentage { font-size: var(--text-2xl); font-family: var(--font-mono); font-weight: 600; font-variant-numeric: tabular-nums; }
    .overall-card__percentage.is-good { color: var(--accent-primary); }
    .overall-card__percentage.is-warning { color: var(--accent-warning); }
  `],
})
export class StudentOverallCardComponent {
  @Input({ required: true }) stats!: OverallStats;
  @Input({ required: true }) threshold!: number;
}
