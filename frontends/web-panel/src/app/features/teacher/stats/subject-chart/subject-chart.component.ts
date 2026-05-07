import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { StudentChartData } from '../stats-utils';

@Component({
  selector: 'app-subject-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  template: `
    <section class="chart-card teacher-subject-chart">
      <header class="chart-card__head">
        <div>
          <p class="chart-card__eyebrow">Предмет</p>
          <h2 class="chart-card__title">{{ subjectName }}</h2>
        </div>
        <span class="chart-card__badge">
          <i class="ph ph-chart-bar" aria-hidden="true"></i>
          По студентам
        </span>
      </header>

      <div class="chart-card__canvas">
        <canvas
          baseChart
          role="img"
          [attr.aria-label]="subjectName + ' — статистика посещаемости по студентам'"
          [data]="barChartData"
          [options]="barChartOptions"
          [type]="'bar'">
        </canvas>
      </div>
    </section>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
    }

    .teacher-subject-chart {
      min-height: 360px;
      border-radius: var(--radius-lg);
    }

    .chart-card__title {
      overflow-wrap: anywhere;
    }

    .chart-card__canvas {
      min-height: 260px;
    }

    @media (max-width: 640px) {
      .teacher-subject-chart {
        min-height: 320px;
        padding: var(--space-4);
      }

      .chart-card__head {
        flex-direction: column;
      }
    }
  `],
})
export class SubjectChartComponent implements OnChanges {
  @Input({ required: true }) chartData!: StudentChartData[];
  @Input({ required: true }) subjectName!: string;

  barChartData: ChartData<'bar'> = { labels: [], datasets: [] };

  barChartOptions: ChartOptions<'bar'> = this.buildChartOptions();

  ngOnChanges(): void {
    this.barChartOptions = this.buildChartOptions();
    this.barChartData = {
      labels: this.chartData.map(s => s.name),
      datasets: [
        {
          label: 'Присутствовал',
          data: this.chartData.map(s => s.present),
          backgroundColor: 'rgba(22, 163, 74, 0.85)',
        },
        {
          label: 'Уваж. причина',
          data: this.chartData.map(s => s.excused),
          backgroundColor: 'rgba(217, 119, 6, 0.85)',
        },
        {
          label: 'Отсутствовал',
          data: this.chartData.map(s => s.absent),
          backgroundColor: 'rgba(220, 38, 38, 0.85)',
        },
      ],
    };
  }

  private buildChartOptions(): ChartOptions<'bar'> {
    const textColor = this.cssVar('--text-secondary', '#8B95A8');
    const primaryTextColor = this.cssVar('--text-primary', '#F0F2F5');
    const gridColor = this.isDarkTheme()
      ? 'rgba(139, 149, 168, 0.18)'
      : 'rgba(74, 85, 104, 0.18)';

    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'top',
          labels: {
            color: primaryTextColor,
            boxWidth: 12,
            boxHeight: 12,
            useBorderRadius: true,
          },
        },
      },
      scales: {
        x: {
          stacked: true,
          ticks: { color: textColor },
          grid: { color: gridColor },
        },
        y: {
          stacked: true,
          beginAtZero: true,
          ticks: { color: textColor, precision: 0 },
          grid: { color: gridColor },
          title: { display: true, text: 'Занятий', color: textColor },
        },
      },
    };
  }

  private cssVar(name: string, fallback: string): string {
    if (typeof document === 'undefined') {
      return fallback;
    }
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
  }

  private isDarkTheme(): boolean {
    if (typeof document === 'undefined') {
      return true;
    }
    return document.documentElement.dataset['theme'] !== 'light';
  }
}
