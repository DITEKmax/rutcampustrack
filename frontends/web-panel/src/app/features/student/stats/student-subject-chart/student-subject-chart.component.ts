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
          [data]="barChartData"
          [options]="barChartOptions"
          [type]="'bar'">
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
      height: 320px;
      display: flex;
      flex-direction: column;
    }
    .chart-card.is-redzone { border-color: var(--accent-warning); }
    .chart-card__header { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--space-2); }
    .chart-card__title { font-size: var(--text-base); font-family: var(--font-heading); font-weight: 600; }
    .chart-card__ratio { font-size: var(--text-sm); font-family: var(--font-mono); font-weight: 600; font-variant-numeric: tabular-nums; color: var(--text-muted); }
    .chart-card__redzone-badge {
      display: flex; align-items: center; gap: var(--space-1);
      font-size: var(--text-xs); color: var(--accent-warning);
      background: color-mix(in oklab, var(--accent-warning) 10%, transparent);
      border-radius: var(--radius-md);
      padding: 4px var(--space-2);
      margin-bottom: var(--space-2);
    }
    .chart-container { flex: 1; position: relative; height: 240px; }
  `],
})
export class StudentSubjectChartComponent implements OnChanges {
  @Input({ required: true }) stat!: SubjectStats;
  @Input({ required: true }) threshold!: number;

  get isRedzone(): boolean {
    return this.stat.percentage < this.threshold;
  }

  barChartData: ChartData<'bar'> = { labels: [], datasets: [] };

  barChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 400,
    },
    plugins: { legend: { position: 'top' } },
    scales: {
      x: { stacked: true },
      y: { stacked: true, title: { display: true, text: 'Занятий' } },
    },
  };

  ngOnChanges(): void {
    const freeAttendance = Math.max(
      0,
      this.stat.total - this.stat.attended - this.stat.absent - this.stat.excused,
    );
    this.barChartData = {
      labels: [this.stat.subjectName],
      datasets: [
        { label: 'Присутствовал', data: [this.stat.attended], backgroundColor: 'rgba(0, 229, 160, 0.85)' },
        { label: 'Уваж. причина', data: [this.stat.excused], backgroundColor: 'rgba(245, 158, 11, 0.85)' },
        { label: 'Своб. посещение', data: [freeAttendance], backgroundColor: 'rgba(139, 92, 246, 0.85)' },
        { label: 'Отсутствовал', data: [this.stat.absent], backgroundColor: 'rgba(239, 68, 68, 0.85)' },
      ],
    };
  }
}
