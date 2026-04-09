import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { computed, signal } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import { StudentScheduleComponent } from './student-schedule.component';
import { AuthService, type AuthUser } from '../../../core/auth/auth.service';
import type { LessonResponse } from '../shared/student-schedule.types';

const STUDENT_USER: AuthUser = {
  id: 3,
  role: 'STUDENT',
  isHeadman: false,
  groupId: 5,
};

function makeAuthMock(user: AuthUser | null) {
  const _accessToken = signal<string | null>(user ? 'fake-access-token' : null);
  return {
    currentUser: computed((): AuthUser | null => user),
    isAuthenticated: computed(() => user !== null),
    accessToken: _accessToken.asReadonly(),
    setTokens: vi.fn(),
    clearTokens: vi.fn(),
    getRefreshToken: vi.fn().mockReturnValue(null),
    resolveDashboardFor: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
  };
}

function mkLesson(over: Partial<LessonResponse>): LessonResponse {
  return {
    id: 1,
    scheduleItemId: 1,
    groupId: 5,
    subjectId: 7,
    teacherId: 11,
    date: '2026-04-09',
    status: 'PLANNED',
    dayOfWeek: 4,
    lessonNumber: 1,
    startTime: '09:00:00',
    endTime: '10:30:00',
    weekType: 'BOTH',
    room: '404',
    geoBlocked: false,
    cancelReason: null,
    createdAt: '2026-04-01T00:00:00Z',
    ...over,
  };
}

function flushSubjects(httpMock: HttpTestingController): void {
  // Subject cache may fire a lookup for every unique subjectId.
  // Stubs each as "Математика" without asserting call count.
  httpMock
    .match(r => r.url.startsWith('/api/academic/subjects/'))
    .forEach(r =>
      r.flush({ id: Number(r.request.url.split('/').pop()), name: 'Математика' }),
    );
}

describe('StudentScheduleComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    // Freeze time to Thursday Apr 9 2026 12:00 local.
    vi.setSystemTime(new Date(2026, 3, 9, 12, 0, 0));
  });

  afterEach(() => {
    if (httpMock) {
      httpMock.verify();
    }
    vi.useRealTimers();
  });

  async function setup(user: AuthUser | null = STUDENT_USER) {
    const result = await render(StudentScheduleComponent, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: makeAuthMock(user) },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    return result;
  }

  it('fetches lessons for the current Monday-Saturday range on init', async () => {
    await setup();
    const req = httpMock.expectOne(
      r => r.url === '/api/schedule/groups/5/lessons',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('dateFrom')).toBe('2026-04-06');
    expect(req.request.params.get('dateTo')).toBe('2026-04-11');
    expect(req.request.params.get('size')).toBe('100');
    req.flush({ _embedded: { lessonResponseList: [] } });
  });

  it('renders 4 skeleton rows while the request is pending', async () => {
    const { container } = await setup();
    expect(container.querySelectorAll('.schedule-skeleton').length).toBe(4);
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [] } });
  });

  it('renders "Занятий нет" empty state when no lessons are returned', async () => {
    const { fixture } = await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [] } });
    fixture.detectChanges();

    expect(screen.getByText('Занятий нет')).toBeTruthy();
    expect(
      screen.getByText('В этот день пар не запланировано.'),
    ).toBeTruthy();
  });

  it('renders an error message on a 500 response', async () => {
    const { fixture } = await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(
      screen.getByText('Не удалось загрузить расписание. Попробуйте позже.'),
    ).toBeTruthy();
  });

  it('next week button shifts the date range by +7 and issues a second request', async () => {
    const { fixture } = await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [] } });
    fixture.detectChanges();

    const nextBtn = fixture.nativeElement.querySelector(
      'button[aria-label="Следующая неделя"]',
    ) as HTMLButtonElement;
    nextBtn.click();
    fixture.detectChanges();

    const second = httpMock.expectOne(
      r => r.url === '/api/schedule/groups/5/lessons',
    );
    expect(second.request.params.get('dateFrom')).toBe('2026-04-13');
    expect(second.request.params.get('dateTo')).toBe('2026-04-18');
    second.flush({ _embedded: { lessonResponseList: [] } });
  });

  it('previous week button shifts the date range by -7', async () => {
    const { fixture } = await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [] } });
    fixture.detectChanges();

    const prevBtn = fixture.nativeElement.querySelector(
      'button[aria-label="Предыдущая неделя"]',
    ) as HTMLButtonElement;
    prevBtn.click();
    fixture.detectChanges();

    const second = httpMock.expectOne(
      r => r.url === '/api/schedule/groups/5/lessons',
    );
    expect(second.request.params.get('dateFrom')).toBe('2026-03-30');
    expect(second.request.params.get('dateTo')).toBe('2026-04-04');
    second.flush({ _embedded: { lessonResponseList: [] } });
  });

  it('"Сегодня" pill appears after navigation and jumps back to current week on click', async () => {
    const { fixture } = await setup();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [] } });
    fixture.detectChanges();

    // Initially on the current week — pill should NOT be visible.
    expect(screen.queryByText('Сегодня')).toBeNull();

    // Move to next week.
    const nextBtn = fixture.nativeElement.querySelector(
      'button[aria-label="Следующая неделя"]',
    ) as HTMLButtonElement;
    nextBtn.click();
    fixture.detectChanges();
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: [] } });
    fixture.detectChanges();

    // Now the pill should be rendered.
    const pill = screen.getByText('Сегодня');
    expect(pill).toBeTruthy();

    // Click it — should jump back to current week (dateFrom = 2026-04-06).
    (pill.closest('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const third = httpMock.expectOne(
      r => r.url === '/api/schedule/groups/5/lessons',
    );
    expect(third.request.params.get('dateFrom')).toBe('2026-04-06');
    third.flush({ _embedded: { lessonResponseList: [] } });
    fixture.detectChanges();

    // Pill hides again.
    expect(screen.queryByText('Сегодня')).toBeNull();
  });

  it('filters lessons by the selected day tab', async () => {
    const { fixture } = await setup();
    const req = httpMock.expectOne(
      r => r.url === '/api/schedule/groups/5/lessons',
    );
    req.flush({
      _embedded: {
        lessonResponseList: [
          mkLesson({
            id: 1,
            dayOfWeek: 1,
            subjectId: 7,
            lessonNumber: 1,
            room: 'A-101',
          }),
          mkLesson({
            id: 2,
            dayOfWeek: 4,
            subjectId: 7,
            lessonNumber: 2,
            room: 'B-202',
          }),
        ],
      },
    });
    fixture.detectChanges();
    flushSubjects(httpMock);
    fixture.detectChanges();

    // Default selected day is Thursday (index 3) — lesson #2 (B-202) shows.
    expect(screen.getByText(/Ауд\.\s*B-202/)).toBeTruthy();
    expect(screen.queryByText(/Ауд\.\s*A-101/)).toBeNull();

    // Switch to Monday (index 0).
    const monTab = screen.getByRole('tab', { name: 'Пн' });
    (monTab as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(screen.getByText(/Ауд\.\s*A-101/)).toBeTruthy();
    expect(screen.queryByText(/Ауд\.\s*B-202/)).toBeNull();
  });

  it('toggles lesson expansion on row click (open then close)', async () => {
    const { fixture } = await setup();
    httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons').flush({
      _embedded: {
        lessonResponseList: [
          mkLesson({
            id: 42,
            dayOfWeek: 4,
            subjectId: 7,
            lessonNumber: 3,
            room: 'C-303',
          }),
        ],
      },
    });
    fixture.detectChanges();
    flushSubjects(httpMock);
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector(
      'button.lesson-row',
    ) as HTMLButtonElement;
    expect(row).toBeTruthy();

    // Initially collapsed.
    expect(row.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText('Номер пары')).toBeNull();

    row.click();
    fixture.detectChanges();

    // Expanded — detail panel renders, aria-expanded true.
    expect(screen.getByText('Номер пары')).toBeTruthy();

    // Clicking again collapses.
    const rowAfter = fixture.nativeElement.querySelector(
      'button.lesson-row',
    ) as HTMLButtonElement;
    rowAfter.click();
    fixture.detectChanges();

    expect(screen.queryByText('Номер пары')).toBeNull();
  });

  it('refuses to fetch and shows an error when the user has no groupId', async () => {
    const { fixture } = await setup({
      id: 9,
      role: 'STUDENT',
      isHeadman: false,
      groupId: null,
    });
    fixture.detectChanges();

    httpMock.expectNone(r => r.url.startsWith('/api/schedule/groups/'));
    expect(
      screen.getByText('Не удалось определить группу пользователя.'),
    ).toBeTruthy();
  });
});
