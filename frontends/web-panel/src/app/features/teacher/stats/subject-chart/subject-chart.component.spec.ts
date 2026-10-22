import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { SubjectChartComponent } from './subject-chart.component';
import { StudentChartData } from '../stats-utils';

const sampleData: StudentChartData[] = [
  { name: 'Иванов', present: 5, excused: 1, absent: 2 },
  { name: 'Петров', present: 7, excused: 1, absent: 0 },
];

describe('SubjectChartComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubjectChartComponent],
    }).compileComponents();
  });


  it('renders the subject name in the chart heading', () => {
    const fixture = TestBed.createComponent(SubjectChartComponent);
    const comp = fixture.componentInstance;
    comp.chartData = sampleData;
    comp.subjectName = 'Физика';
    fixture.detectChanges();
    const title: HTMLElement = fixture.nativeElement.querySelector('.chart-card__title');
    expect(title?.textContent?.trim()).toBe('Физика');
  });

  it('contains a canvas element for the chart', () => {
    const fixture = TestBed.createComponent(SubjectChartComponent);
    const comp = fixture.componentInstance;
    comp.chartData = sampleData;
    comp.subjectName = 'Химия';
    fixture.detectChanges();
    const canvas = fixture.nativeElement.querySelector('canvas');
    expect(canvas).not.toBeNull();
  });

  it('builds barChartData with 3 datasets on ngOnChanges', () => {
    const fixture = TestBed.createComponent(SubjectChartComponent);
    const comp = fixture.componentInstance;
    comp.chartData = sampleData;
    comp.subjectName = 'История';
    comp.ngOnChanges();
    expect(comp.barChartData.datasets).toHaveLength(3);
    expect(comp.barChartData.labels).toEqual(['Иванов', 'Петров']);
    expect(comp.barChartData.datasets.map(({ label, data }) => ({ label, data }))).toEqual([
      { label: 'Присутствовал', data: [5, 7] },
      { label: 'Уваж. причина', data: [1, 1] },
      { label: 'Отсутствовал', data: [2, 0] },
    ]);
  });
});
