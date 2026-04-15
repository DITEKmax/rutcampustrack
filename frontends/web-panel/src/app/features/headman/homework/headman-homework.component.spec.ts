import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { HeadmanHomeworkComponent } from './headman-homework.component';
import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { StudentApiService } from '../../student/shared/student-api.service';
import { SubjectCacheService } from '../../student/shared/subject-cache.service';
import {
  HeadmanHomeworkApiService,
  HomeworkResponse,
} from './headman-homework-api.service';
import type { LessonResponse } from '../../student/shared/student-schedule.types';

/**
 * Tests for HeadmanHomeworkComponent (Phase 61-05).
 *
 * Covers:
 * - Renders week-navigator (smoke) and loads active semester on init
 * - Click «+ Добавить» sets editingState → inline form appears
 * - onSaved collapses form and triggers reload
 */

const LESSON_MON: LessonResponse = {
  id: 1,
  scheduleItemId: 10,
  groupId: 5,
  subjectId: 7,
  teacherId: 0,
  date: '2026-04-13',
  status: 'PLANNED',
  dayOfWeek: 1,
  lessonNumber: 2,
  startTime: '10:05:00',
  endTime: '11:25:00',
  weekType: 'BOTH',
  room: '401',
  geoBlocked: false,
  cancelReason: null,
  createdAt: '2026-04-01T00:00:00Z',
};

const HOMEWORK_OWN: HomeworkResponse = {
  id: 99,
  title: 'Домашка',
  description: null,
  link: null,
  subjectId: 7,
  groupId: 5,
  semesterId: 1,
  publishedBy: 100, // === currentUser.id
  completed: false,
  createdAt: '2026-04-10T10:00:00Z',
  lessonDate: '2026-04-13',
  lessonNumber: 2,
};

function makeHeadmanApi(): HeadmanApiService {
  return {
    listSemesters: vi.fn(() =>
      of({ _embedded: { semesterResponseList: [{ id: 1, active: true }] } }),
    ),
  } as unknown as HeadmanApiService;
}

function makeStudentApi(lessons: LessonResponse[] = [LESSON_MON]): StudentApiService {
  return {
    getWeekLessons: vi.fn(() => of(lessons)),
  } as unknown as StudentApiService;
}

function makeHomeworkApi(homeworks: HomeworkResponse[] = []): HeadmanHomeworkApiService {
  return {
    list: vi.fn(() => of(homeworks)),
    create: vi.fn(() => of(HOMEWORK_OWN)),
    update: vi.fn(() => of(HOMEWORK_OWN)),
    delete: vi.fn(() => of(void 0)),
  } as unknown as HeadmanHomeworkApiService;
}

function makeAuth(): AuthService {
  return {
    currentUser: () => ({ id: 100, role: 'STUDENT', isHeadman: true, groupId: 5 }),
  } as unknown as AuthService;
}

function makeSubjectCache(): SubjectCacheService {
  return {
    getName: vi.fn(() => of('Математика')),
  } as unknown as SubjectCacheService;
}

function setup(options: {
  lessons?: LessonResponse[];
  homeworks?: HomeworkResponse[];
} = {}): {
  fixture: ComponentFixture<HeadmanHomeworkComponent>;
  component: HeadmanHomeworkComponent;
  homeworkApi: HeadmanHomeworkApiService;
  studentApi: StudentApiService;
} {
  const headmanApi = makeHeadmanApi();
  const studentApi = makeStudentApi(options.lessons);
  const homeworkApi = makeHomeworkApi(options.homeworks);
  const auth = makeAuth();
  const subjectCache = makeSubjectCache();

  TestBed.configureTestingModule({
    imports: [HeadmanHomeworkComponent],
    providers: [
      provideNoopAnimations(),
      provideHttpClient(),
      { provide: AuthService, useValue: auth },
      { provide: HeadmanApiService, useValue: headmanApi },
      { provide: StudentApiService, useValue: studentApi },
      { provide: HeadmanHomeworkApiService, useValue: homeworkApi },
      { provide: SubjectCacheService, useValue: subjectCache },
    ],
  });
  const fixture = TestBed.createComponent(HeadmanHomeworkComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, homeworkApi, studentApi };
}

describe('HeadmanHomeworkComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('renders without errors and loads active semester + lessons on init', () => {
    const { component, studentApi } = setup();
    expect(component).toBeTruthy();
    expect(component.semesterId()).toBe(1);
    expect(studentApi.getWeekLessons).toHaveBeenCalled();
    expect(component.lessons().length).toBe(1);
    // weekChanged fires on init → reload triggered, loading flipped back.
    expect(component.loading()).toBe(false);
  });

  it('onAddHomework sets editingState and isEditing returns true for that lessonKey', () => {
    const { component } = setup();
    const day = component.days()[0];
    const row = day.lessons[0];
    component.onAddHomework(row);
    expect(component.editingState()).not.toBeNull();
    expect(component.editingState()?.lessonKey).toBe(row.key);
    expect(component.editingState()?.homework).toBeNull();
    expect(component.isEditing(row.key)).toBe(true);
    expect(component.isEditing('other-key')).toBe(false);
  });

  it('onSaved clears editingState and reloads (homework list refetched)', () => {
    const { component, homeworkApi } = setup();
    const day = component.days()[0];
    const row = day.lessons[0];
    component.onAddHomework(row);
    expect(component.editingState()).not.toBeNull();

    // Baseline — list was called once during ngOnInit reload.
    const callsBefore = (homeworkApi.list as unknown as { mock: { calls: unknown[] } }).mock.calls.length;

    component.onSaved();

    expect(component.editingState()).toBeNull();
    const callsAfter = (homeworkApi.list as unknown as { mock: { calls: unknown[] } }).mock.calls.length;
    expect(callsAfter).toBeGreaterThan(callsBefore);
  });

  it('homeworksFor returns only homeworks matching lessonKey (date + number)', () => {
    const { component } = setup({ homeworks: [HOMEWORK_OWN] });
    const row = component.days()[0].lessons[0];
    const matching = component.homeworksFor(row.key);
    expect(matching.length).toBe(1);
    expect(matching[0].id).toBe(99);

    const otherKey = '2026-04-14-3';
    expect(component.homeworksFor(otherKey).length).toBe(0);
  });

  it('isOwn returns true when publishedBy === currentUserId, false otherwise', () => {
    const { component } = setup({ homeworks: [HOMEWORK_OWN] });
    expect(component.isOwn(HOMEWORK_OWN)).toBe(true);
    const notMine = { ...HOMEWORK_OWN, publishedBy: 999 };
    expect(component.isOwn(notMine)).toBe(false);
  });
});
