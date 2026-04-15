import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach } from 'vitest';

import { StudentHomeworkDayViewComponent } from './student-homework-day-view.component';
import type { HomeworkItem } from '../../shared/student-schedule.types';

/**
 * Tests for StudentHomeworkDayViewComponent (Phase 61-06 Task 2).
 *
 * Covers:
 * - Shows only items matching the selected date
 * - Empty state shown when no items for day
 * - Previous button emits dateChanged with date - 1 day
 * - Next button emits dateChanged with date + 1 day
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

function setup(date: Date, items: HomeworkItem[]): {
  fixture: ComponentFixture<StudentHomeworkDayViewComponent>;
  component: StudentHomeworkDayViewComponent;
} {
  TestBed.configureTestingModule({
    imports: [StudentHomeworkDayViewComponent],
    providers: [provideNoopAnimations()],
  });
  const fixture = TestBed.createComponent(StudentHomeworkDayViewComponent);
  fixture.componentInstance.date = date;
  fixture.componentInstance.items = items;
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('StudentHomeworkDayViewComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('shows only items whose lessonDate matches the selected date', () => {
    const date = new Date(2026, 3, 16); // April 16
    const items = [
      makeItem({ id: 1, lessonDate: '2026-04-16' }),
      makeItem({ id: 2, lessonDate: '2026-04-17' }), // different day — should not appear
      makeItem({ id: 3, lessonDate: '2026-04-16' }),
    ];
    const { component } = setup(date, items);
    expect(component.itemsForDay.length).toBe(2);
    expect(component.itemsForDay.map(i => i.id)).toEqual([1, 3]);
  });

  it('shows empty state when no items match the selected date', () => {
    const date = new Date(2026, 3, 16);
    const { fixture } = setup(date, [makeItem({ lessonDate: '2026-04-17' })]);
    const empty: HTMLElement = fixture.nativeElement.querySelector('.day-view__empty');
    expect(empty).not.toBeNull();
    expect(empty.textContent).toContain('На этот день заданий нет');
  });

  it('previous button emits dateChanged with date minus 1 day', () => {
    const date = new Date(2026, 3, 16); // April 16
    const { fixture, component } = setup(date, []);
    const emitted: Date[] = [];
    component.dateChanged.subscribe((d: Date) => emitted.push(d));

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[aria-label="Предыдущий день"]');
    btn.click();

    expect(emitted.length).toBe(1);
    expect(emitted[0].getDate()).toBe(15); // April 15
    expect(emitted[0].getMonth()).toBe(3);
  });

  it('next button emits dateChanged with date plus 1 day', () => {
    const date = new Date(2026, 3, 16); // April 16
    const { fixture, component } = setup(date, []);
    const emitted: Date[] = [];
    component.dateChanged.subscribe((d: Date) => emitted.push(d));

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[aria-label="Следующий день"]');
    btn.click();

    expect(emitted.length).toBe(1);
    expect(emitted[0].getDate()).toBe(17); // April 17
    expect(emitted[0].getMonth()).toBe(3);
  });
});
