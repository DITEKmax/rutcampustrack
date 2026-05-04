import { describe, expect, it } from 'vitest';
import { StudentSubjectChartComponent } from './student-subject-chart.component';

describe('StudentSubjectChartComponent', () => {
  function makeComponent(): StudentSubjectChartComponent {
    const component = new StudentSubjectChartComponent();
    component.threshold = 75;
    component.stat = {
      subjectId: 10,
      subjectName: 'Математика',
      subjectType: 'lab',
      total: 12,
      attended: 8,
      absent: 4,
      excused: 2,
      percentage: 66.7,
    };
    return component;
  }

  it('renders full subject type label from backend subjectType', () => {
    const component = makeComponent();

    expect(component.subjectTypeLabel).toBe('Лабораторная');
  });

  it('builds a pie chart with only absence, excused, and present categories', () => {
    const component = makeComponent();

    component.ngOnChanges();

    expect(component.pieChartData.labels).toEqual([
      'Пропуск',
      'Уважительная причина',
      'Был (+)',
    ]);
    expect(component.pieChartData.datasets).toHaveLength(1);
    expect(component.pieChartData.datasets[0].data).toEqual([4, 2, 6]);
  });
});
