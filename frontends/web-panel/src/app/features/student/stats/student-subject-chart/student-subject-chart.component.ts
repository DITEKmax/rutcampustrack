import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import type { SubjectStats } from '../../shared/student-schedule.types';

@Component({
  selector: 'app-student-subject-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, BaseChartDirective],
  template: `
    <div class="chart-card" [class.is-redzone]="isRedzone">
      <div class="chart-card__header">
        <h3 class="chart-card__title">{{ stat.subjectName }}</h3>
        @if (subjectTypeLabel) {
          <span class="chart-card__type">{{ subjectTypeLabel }}</span>
        }
        <span class="chart-card__ratio">{{ stat.attended }}/{{ stat.total }}</span>
      </div>
      @if (isRedzone) {
        <div class="chart-card__redzone-badge">
          <i class="ph-warning ph-fill"></i>
          <span>Посещаемость ниже порога ({{ threshold }}%)</span>
        </div>
      }
      <div class="chart-container">
        <canvas
          baseChart
          role="img"
          [attr.aria-label]="stat.subjectName + ' — статистика посещаемости'"
          [data]="pieChartData"
          [options]="pieChartOptions"
          [type]="'pie'">
        </canvas>
      </div>
    </div>
  `,
  styles: [`
    .chart-card {
      background: var(--bg-elevated);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-xl);
      padding: var(--space-4);
      min-height: 340px;
      display: flex;
      flex-direction: column;
    }
    .chart-card.is-redzone { border-color: var(--accent-warning); }
    .chart-card__header {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto auto;
      align-items: center;
      gap: var(--space-2);
      margin-bottom: var(--space-2);
    }
    .chart-card__title {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: var(--text-base);
      font-family: var(--font-heading);
      font-weight: 600;
    }
    .chart-card__type {
      display: inline-flex;
      align-items: center;
      min-height: 22px;
      padding: 0 var(--space-2);
      border-radius: var(--radius-full);
      background: color-mix(in oklab, var(--accent-primary) 12%, transparent);
      border: 1px solid color-mix(in oklab, var(--accent-primary) 28%, transparent);
      color: var(--accent-primary);
      font-size: var(--text-xs);
      font-weight: 700;
      white-space: nowrap;
    }
    .chart-card__ratio {
      font-size: var(--text-sm);
      font-family: var(--font-mono);
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      color: var(--text-muted);
      white-space: nowrap;
    }
    .chart-card__redzone-badge {
      display: flex; align-items: center; gap: var(--space-1);
      font-size: var(--text-xs); color: var(--accent-warning);
      background: color-mix(in oklab, var(--accent-warning) 10%, transparent);
      border-radius: var(--radius-md);
      padding: 4px var(--space-2);
      margin-bottom: var(--space-2);
    }
    .chart-container { flex: 1; position: relative; min-height: 250px; }
  `],
})
export class StudentSubjectChartComponent implements OnChanges {
  @Input({ required: true }) stat!: SubjectStats;
  @Input({ required: true }) threshold!: number;

  get isRedzone(): boolean {
    return this.stat.percentage < this.threshold;
  }

  get subjectTypeLabel(): string {
    switch ((this.stat?.subjectType ?? '').trim().toLowerCase()) {
      case 'lecture':
        return 'Лекция';
      case 'practice':
        return 'Практика';
      case 'lab':
        return 'Лабораторная';
      default:
        return '';
    }
  }

  pieChartData: ChartData<'pie'> = { labels: [], datasets: [] };

  pieChartOptions: ChartOptions<'pie'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: this.prefersReducedMotion() ? 0 : 400,
    },
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          boxWidth: 12,
          boxHeight: 12,
          useBorderRadius: true,
          padding: 14,
        },
      },
    },
  };

  ngOnChanges(): void {
    const present = Math.max(0, this.stat.attended - this.stat.excused);
    this.pieChartData = {
      labels: ['Пропуск', 'Уважительная причина', 'Был (+)'],
      datasets: [
        {
          label: 'Занятий',
          data: [this.stat.absent, this.stat.excused, present],
          backgroundColor: [
            'rgba(239, 68, 68, 0.88)',
            'rgba(245, 158, 11, 0.88)',
            'rgba(0, 181, 126, 0.88)',
          ],
          borderWidth: 0,
          hoverOffset: 4,
        },
      ],
    };
  }

  private prefersReducedMotion(): boolean {
    return typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
