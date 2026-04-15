import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach } from 'vitest';

import { StudentHomeworkWeekViewComponent } from './student-homework-week-view.component';
import type { HomeworkItem } from '../../shared/student-schedule.types';

/**
 * Tests for StudentHomeworkWeekViewComponent (Phase 61-06 Task 2).
 *
 * Covers:
 * - Groups items correctly per day
 * - Day headings are rendered for days with items
 * - Previous/Next week buttons emit mondayChanged
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
    lessonDate: '2026-04-14',
    lessonNumber: 1,
    ...overrides,
  };
}

// Monday April 14, 2026
const MONDAY = new Date(2026, 3, 13); // April 13 is Monday

function setup(monday: Date, items: HomeworkItem[]): {
  fixture: ComponentFixture<StudentHomeworkWeekViewComponent>;
  component: StudentHomeworkWeekViewComponent;
} {
  TestBed.configureTestingModule({
    imports: [StudentHomeworkWeekViewComponent],
    providers: [provideNoopAnimations()],
  });
  const fixture = TestBed.createComponent(StudentHomeworkWeekViewComponent);
  fixture.componentInstance.monday = monday;
  fixture.componentInstance.items = items;
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('StudentHomeworkWeekViewComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('groups items by day correctly', () => {
    const monday = new Date(2026, 3, 13); // April 13 (Mon)
    const items = [
      makeItem({ id: 1, lessonDate: '2026-04-13' }), // Mon
      makeItem({ id: 2, lessonDate: '2026-04-14' }), // Tue
      makeItem({ id: 3, lessonDate: '2026-04-13' }), // Mon again
    ];
    const { component } = setup(monday, items);

    const days = component.days;
    const monDay = days.find(d => d.dateKey === '2026-04-13');
    const tueDay = days.find(d => d.dateKey === '2026-04-14');
    expect(monDay?.items.length).toBe(2);
    expect(tueDay?.items.length).toBe(1);
  });

  it('renders day headings for days with items', () => {
    const monday = new Date(2026, 3, 13);
    const items = [makeItem({ id: 1, lessonDate: '2026-04-13' })];
    const { fixture } = setup(monday, items);

    const headings: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.week-view__day-heading');
    expect(headings.length).toBeGreaterThanOrEqual(1);
  });

  it('previous week button emits mondayChanged with monday - 7 days', () => {
    const monday = new Date(2026, 3, 13);
    const { fixture, component } = setup(monday, []);
    const emitted: Date[] = [];
    component.mondayChanged.subscribe((d: Date) => emitted.push(d));

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[aria-label="Предыдущая неделя"]');
    btn.click();

    expect(emitted.length).toBe(1);
    // April 13 - 7 = April 6
    expect(emitted[0].getDate()).toBe(6);
    expect(emitted[0].getMonth()).toBe(3);
  });

  it('next week button emits mondayChanged with monday + 7 days', () => {
    const monday = new Date(2026, 3, 13);
    const { fixture, component } = setup(monday, []);
    const emitted: Date[] = [];
    component.mondayChanged.subscribe((d: Date) => emitted.push(d));

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[aria-label="Следующая неделя"]');
    btn.click();

    expect(emitted.length).toBe(1);
    // April 13 + 7 = April 20
    expect(emitted[0].getDate()).toBe(20);
    expect(emitted[0].getMonth()).toBe(3);
  });
});
