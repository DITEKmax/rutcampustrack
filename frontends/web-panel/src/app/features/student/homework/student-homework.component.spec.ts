import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { StudentHomeworkComponent } from './student-homework.component';
import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import type { HomeworkItem } from '../shared/student-schedule.types';

/**
 * Tests for StudentHomeworkComponent (Phase 61-06 Task 2).
 *
 * Covers:
 * - Segmented control mode change switches the active view
 * - Filter toggle filters out completed items
 * - onCompleteToggled calls markHomeworkComplete / unmarkHomeworkComplete
 * - onDateSelected switches mode to 'day' and updates selectedDate
 */

function makeItem(overrides: Partial<HomeworkItem> = {}): HomeworkItem {
  return {
    id: 1,
    title: 'Задача 1',
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

function makeApiService(items: HomeworkItem[] = []): StudentApiService {
  return {
    getActiveSemesterId: vi.fn(() => of(1)),
    getHomeworks: vi.fn(() => of(items)),
    markHomeworkComplete: vi.fn(() => of(undefined)),
    unmarkHomeworkComplete: vi.fn(() => of(undefined)),
  } as unknown as StudentApiService;
}

function makeAuth(): AuthService {
  return {
    currentUser: () => ({ id: 100, role: 'STUDENT', isHeadman: false, groupId: 5 }),
  } as unknown as AuthService;
}

function setup(items: HomeworkItem[] = []): {
  fixture: ComponentFixture<StudentHomeworkComponent>;
  component: StudentHomeworkComponent;
  api: StudentApiService;
} {
  const api = makeApiService(items);
  const auth = makeAuth();

  TestBed.configureTestingModule({
    imports: [StudentHomeworkComponent],
    providers: [
      provideNoopAnimations(),
      provideHttpClient(),
      { provide: AuthService, useValue: auth },
      { provide: StudentApiService, useValue: api },
    ],
  });
  const fixture = TestBed.createComponent(StudentHomeworkComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api };
}

describe('StudentHomeworkComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('default mode is day and selectedDate is tomorrow', () => {
    const { component } = setup();
    expect(component.mode()).toBe('day');
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    // Compare just the date portion (YYYY-MM-DD)
    const expected = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth()+1).padStart(2,'0')}-${String(tomorrow.getDate()).padStart(2,'0')}`;
    const actual = `${component.selectedDate().getFullYear()}-${String(component.selectedDate().getMonth()+1).padStart(2,'0')}-${String(component.selectedDate().getDate()).padStart(2,'0')}`;
    expect(actual).toBe(expected);
  });

  it('onModeChange switches mode signal', () => {
    const { component } = setup();
    expect(component.mode()).toBe('day');
    component.onModeChange('week');
    expect(component.mode()).toBe('week');
    component.onModeChange('month');
    expect(component.mode()).toBe('month');
  });

  it('filterUncompleted toggle filters out completed items', () => {
    const items = [
      makeItem({ id: 1, completed: false }),
      makeItem({ id: 2, completed: true }),
      makeItem({ id: 3, completed: false }),
    ];
    const { component } = setup(items);
    expect(component.filteredHomeworks().length).toBe(3);

    component.onFilterChange(true);
    expect(component.filteredHomeworks().length).toBe(2);
    expect(component.filteredHomeworks().every(h => !h.completed)).toBe(true);

    component.onFilterChange(false);
    expect(component.filteredHomeworks().length).toBe(3);
  });

  it('onCompleteToggled calls markHomeworkComplete when completed=true', () => {
    const { component, api } = setup([makeItem({ id: 5, completed: false })]);
    component.onCompleteToggled({ homework: makeItem({ id: 5 }), completed: true });
    expect(api.markHomeworkComplete).toHaveBeenCalledWith(5);
    expect(api.unmarkHomeworkComplete).not.toHaveBeenCalled();
  });

  it('onDateSelected sets selectedDate and switches mode to day', () => {
    const { component } = setup();
    component.onModeChange('month');
    expect(component.mode()).toBe('month');

    const target = new Date(2026, 3, 20); // April 20
    component.onDateSelected(target);

    expect(component.mode()).toBe('day');
    expect(component.selectedDate()).toBe(target);
  });
});
