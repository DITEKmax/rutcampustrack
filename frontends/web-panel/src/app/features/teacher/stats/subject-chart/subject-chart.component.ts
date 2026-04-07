import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { StudentChartData } from '../stats-utils';

@Component({
  selector: 'app-subject-chart',
  standalone: true,
  imports: [CommonModule, MatCardModule, BaseChartDirective],
  template: `
    <mat-card class="chart-card">
      <mat-card-header>
        <mat-card-title class="text-[20px] font-semibold">{{ subjectName }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <div class="chart-container">
          <canvas baseChart
            [data]="barChartData"
            [options]="barChartOptions"
            [type]="'bar'">
          </canvas>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .chart-card { height: 320px; }
    .chart-container { height: 240px; position: relative; }
  `],
})
export class SubjectChartComponent implements OnChanges {
  @Input({ required: true }) chartData!: StudentChartData[];
  @Input({ required: true }) subjectName!: string;

  barChartData: ChartData<'bar'> = { labels: [], datasets: [] };

  barChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'top' },
    },
    scales: {
      x: { stacked: true },
      y: { stacked: true, title: { display: true, text: 'Занятий' } },
    },
  };

  ngOnChanges(): void {
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
          label: 'Своб. посещение',
          data: this.chartData.map(s => s.freeAttendance),
          backgroundColor: 'rgba(147, 51, 234, 0.85)',
        },
        {
          label: 'Отсутствовал',
          data: this.chartData.map(s => s.absent),
          backgroundColor: 'rgba(220, 38, 38, 0.85)',
        },
      ],
    };
  }
}
