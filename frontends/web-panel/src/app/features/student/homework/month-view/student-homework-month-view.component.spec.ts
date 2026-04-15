import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach } from 'vitest';

import { StudentHomeworkMonthViewComponent } from './student-homework-month-view.component';
import type { HomeworkItem } from '../../shared/student-schedule.types';

/**
 * Tests for StudentHomeworkMonthViewComponent (Phase 61-06 Task 2).
 *
 * Covers:
 * - Matrix has exactly 42 cells (6 weeks × 7 days)
 * - Click on a cell emits dateSelected with the correct date
 * - Count badge shows correct homework count for a day
 */

function makeItem(overrides: Partial<HomeworkItem> = {}): HomeworkItem {
  return {
    id: 1,
    title: 'Задача',
    description: null,
    link: null,
    subjectId: 7,
    groupId: 5,
    semesterId: 1,
    publishedBy: 100,
    completed: false,
    createdAt: '2026-04-15T10:00:00Z',
    lessonDate: '2026-04-16',
    lessonNumber: 1,
    ...overrides,
  };
}

function setup(month: Date, items: HomeworkItem[]): {
  fixture: ComponentFixture<StudentHomeworkMonthViewComponent>;
  component: StudentHomeworkMonthViewComponent;
} {
  TestBed.configureTestingModule({
    imports: [StudentHomeworkMonthViewComponent],
    providers: [provideNoopAnimations()],
  });
  const fixture = TestBed.createComponent(StudentHomeworkMonthViewComponent);
  fixture.componentInstance.month = month;
  fixture.componentInstance.items = items;
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('StudentHomeworkMonthViewComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('matrix has exactly 42 cells (6 weeks × 7 days)', () => {
    const month = new Date(2026, 3, 1); // April 2026
    const { component } = setup(month, []);
    const allCells = component.cells;
    expect(allCells.length).toBe(42);
  });

  it('click on a cell emits dateSelected with the cell date', () => {
    const month = new Date(2026, 3, 1); // April 2026
    const { fixture, component } = setup(month, []);
    const emitted: Date[] = [];
    component.dateSelected.subscribe((d: Date) => emitted.push(d));

    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button[role="gridcell"]');
    expect(buttons.length).toBe(42);
    buttons[0].click();

    expect(emitted.length).toBe(1);
  });

  it('cell count matches number of homework items for that date', () => {
    const month = new Date(2026, 3, 1); // April 2026
    const items = [
      makeItem({ id: 1, lessonDate: '2026-04-16' }),
      makeItem({ id: 2, lessonDate: '2026-04-16' }),
      makeItem({ id: 3, lessonDate: '2026-04-17' }),
    ];
    const { component } = setup(month, items);

    const cell16 = component.cells.find(c => c.dateKey === '2026-04-16');
    const cell17 = component.cells.find(c => c.dateKey === '2026-04-17');
    expect(cell16?.count).toBe(2);
    expect(cell17?.count).toBe(1);
  });

  it('hasUncompleted flag is true only when there are uncompleted items on that day', () => {
    const month = new Date(2026, 3, 1);
    const items = [
      makeItem({ id: 1, lessonDate: '2026-04-16', completed: false }),
      makeItem({ id: 2, lessonDate: '2026-04-17', completed: true }),
    ];
    const { component } = setup(month, items);

    const cell16 = component.cells.find(c => c.dateKey === '2026-04-16');
    const cell17 = component.cells.find(c => c.dateKey === '2026-04-17');
    expect(cell16?.hasUncompleted).toBe(true);
    expect(cell17?.hasUncompleted).toBe(false);
  });
});
