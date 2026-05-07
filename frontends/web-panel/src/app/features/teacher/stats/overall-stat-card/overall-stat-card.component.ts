import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-overall-stat-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="overall-card" aria-live="polite">
      <div class="overall-card__stat">
        <span class="overall-card__label">Всего занятий</span>
        <span class="overall-card__value">{{ totalLessons }}</span>
      </div>
      <div class="overall-card__stat">
        <span class="overall-card__label">Посещаемость</span>
        <span class="overall-card__value overall-card__value--accent">{{ attendanceRate }}%</span>
      </div>
    </section>
  `,
  styles: [`
    :host {
      display: block;
    }

    .overall-card {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-6);
      align-items: center;
      padding: var(--space-5);
      border-radius: var(--radius-lg);
      background: var(--bg-secondary);
      border: 1px solid var(--border-subtle);
    }

    .overall-card__stat {
      min-width: 160px;
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }

    .overall-card__label {
      font: 600 var(--text-xs)/1.2 var(--font-mono);
      letter-spacing: var(--tracking-wide);
      text-transform: uppercase;
      color: var(--text-muted);
    }

    .overall-card__value {
      font-family: var(--font-mono);
      font-size: var(--text-2xl);
      font-weight: 700;
      color: var(--text-primary);
      font-variant-numeric: tabular-nums;
    }

    .overall-card__value--accent {
      color: var(--accent-primary);
    }
  `],
})
export class OverallStatCardComponent {
  @Input({ required: true }) totalLessons!: number;
  @Input({ required: true }) attendanceRate!: number;
}
