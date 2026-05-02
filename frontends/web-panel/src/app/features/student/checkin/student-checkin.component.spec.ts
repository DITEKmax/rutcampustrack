import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { signal, computed } from '@angular/core';
import { Subject } from 'rxjs';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { StudentCheckinComponent } from './student-checkin.component';
import { AuthService, AuthUser } from '../../../core/auth/auth.service';
import { StudentStompService } from '../shared/student-stomp.service';
import type {
  AttendanceMarkedPayload,
  LessonResponse,
} from '../shared/student-schedule.types';

const STUDENT_USER: AuthUser = {
  id: 3,
  role: 'STUDENT',
  isHeadman: false,
  groupId: 5,
};

function makeAuthMock(user: AuthUser | null = STUDENT_USER) {
  const accessToken = signal<string | null>(user ? 'fake-token' : null);
  return {
    currentUser: computed(() => user),
    isAuthenticated: computed(() => user !== null),
    accessToken: accessToken.asReadonly(),
    resolveDashboardFor: vi.fn(),
    setTokens: vi.fn(),
    clearTokens: vi.fn(),
    getRefreshToken: vi.fn().mockReturnValue(null),
    logout: vi.fn().mockResolvedValue(undefined),
  };
}

function makeStompMock() {
  const markedSubject = new Subject<AttendanceMarkedPayload>();
  const mock = {
    marked$: markedSubject.asObservable(),
    connect: vi.fn(),
    disconnect: vi.fn(),
    __emit(payload: AttendanceMarkedPayload) {
      markedSubject.next(payload);
    },
  };
  return mock;
}

function lesson(over: Partial<LessonResponse>): LessonResponse {
  return {
    id: 42,
    scheduleItemId: 1,
    groupId: 5,
    subjectId: 7,
    teacherId: 11,
    date: '2026-04-09',
    status: 'PLANNED',
    dayOfWeek: 4,
    lessonNumber: 2,
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

/**
 * Stub `navigator.geolocation` so the test can deterministically drive
 * the GPS capture path. The default UI-SPEC coordinates are Moscow-ish.
 */
function stubGeo(
  success: boolean,
  position: GeolocationPosition = {
    coords: {
      latitude: 55.1,
      longitude: 37.2,
      accuracy: 10,
      altitude: null,
      altitudeAccuracy: null,
      heading: null,
      speed: null,
    } as GeolocationCoordinates,
    timestamp: Date.now(),
  } as GeolocationPosition,
) {
  const impl = success
    ? (ok: PositionCallback) => ok(position)
    : (_ok: PositionCallback, fail?: PositionErrorCallback) =>
        fail?.({
          code: 1,
          message: 'denied',
          PERMISSION_DENIED: 1,
          POSITION_UNAVAILABLE: 2,
          TIMEOUT: 3,
        } as GeolocationPositionError);
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: {
      getCurrentPosition: vi.fn().mockImplementation(impl),
    },
  });
}

describe('StudentCheckinComponent', () => {
  let httpMock: HttpTestingController;
  let stompMock: ReturnType<typeof makeStompMock>;

  beforeEach(() => {
    // Freeze wall-clock to a deterministic point inside lesson[0].
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 3, 9, 12, 0, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
    httpMock?.verify();
  });

  async function setup(user: AuthUser | null = STUDENT_USER) {
    stompMock = makeStompMock();
    const result = await render(StudentCheckinComponent, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: makeAuthMock(user) },
        { provide: StudentStompService, useValue: stompMock },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    return result;
  }

  function flushLessons(lessons: LessonResponse[]) {
    httpMock
      .expectOne(r => r.url === '/api/schedule/groups/5/lessons')
      .flush({ _embedded: { lessonResponseList: lessons } });
    // fetchToday also pulls the student's attendance records to seed the
    // "already marked present" confirmed state. Flush an empty list by default.
    httpMock
      .match(r => r.url === '/api/attendance/reports/student/records')
      .forEach(r => r.flush({ _embedded: { attendanceRecordEntryList: [] } }));
  }

  function flushSubjectCache() {
    httpMock
      .match(r => r.url === '/api/academic/subjects/7')
      .forEach(r => r.flush({ id: 7, name: 'Математика' }));
  }

  // ── 1 ───────────────────────────────────────────────────────────
  it('renders "Нет активной пары" + "На сегодня пар больше нет" when no lessons exist', async () => {
    const { fixture } = await setup();
    flushLessons([]);
    fixture.detectChanges();

    expect(screen.getByText('Нет активной пары')).toBeTruthy();
    expect(screen.getByText('На сегодня пар больше нет')).toBeTruthy();
  });

  // ── 2 ───────────────────────────────────────────────────────────
  it('renders "Следующая пара" hint when only a PLANNED lesson exists', async () => {
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'PLANNED' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    expect(screen.getByText('Нет активной пары')).toBeTruthy();
    expect(screen.getByText('Следующая пара')).toBeTruthy();
  });

  // ── 3 ───────────────────────────────────────────────────────────
  it('renders active hero with "Отметиться" CTA when an ACTIVE lesson exists', async () => {
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'ACTIVE' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    expect(screen.getByText('Идёт сейчас')).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Отметиться на текущей паре' }),
    ).toBeTruthy();
    expect(screen.getByText(/09:00/)).toBeTruthy();
    expect(screen.getByText(/Ауд\.\s*404/)).toBeTruthy();
  });

  // ── 4 ───────────────────────────────────────────────────────────
  it('POSTs to /api/attendance/checkin with GPS coords on button click', async () => {
    stubGeo(true);
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'ACTIVE' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    const btn = screen.getByRole('button', {
      name: 'Отметиться на текущей паре',
    });
    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime })
      .click(btn);
    fixture.detectChanges();

    expect(vi.mocked(navigator.geolocation.getCurrentPosition)).toHaveBeenCalledWith(
      expect.any(Function),
      expect.any(Function),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 15000 },
    );

    const postReq = httpMock.expectOne(
      r => r.url === '/api/attendance/checkin' && r.method === 'POST',
    );
    expect(postReq.request.body).toEqual({ lat: 55.1, lng: 37.2 });
    postReq.flush({
      status: 'present',
      lessonId: 42,
      timestamp: '2026-04-09T09:05:00Z',
    });
    fixture.detectChanges();

    expect(screen.getByText('Вы отметились')).toBeTruthy();
  });

  // ── 5 ───────────────────────────────────────────────────────────
  it('on 2xx success the CTA swaps to confirmation badge', async () => {
    stubGeo(true);
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'ACTIVE' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime })
      .click(
        screen.getByRole('button', { name: 'Отметиться на текущей паре' }),
      );
    fixture.detectChanges();

    httpMock.expectOne('/api/attendance/checkin').flush({
      status: 'present',
      lessonId: 42,
      timestamp: '2026-04-09T09:05:00Z',
    });
    fixture.detectChanges();

    expect(screen.getByText('Вы отметились')).toBeTruthy();
  });

  // ── 6 ───────────────────────────────────────────────────────────
  it('shows "Вы уже отмечены на этом занятии." on HTTP 409', async () => {
    stubGeo(true);
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'ACTIVE' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime })
      .click(
        screen.getByRole('button', { name: 'Отметиться на текущей паре' }),
      );
    fixture.detectChanges();

    httpMock
      .expectOne('/api/attendance/checkin')
      .flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(
      screen.getByText('Вы уже отмечены на этом занятии.'),
    ).toBeTruthy();
  });

  // ── 7 ───────────────────────────────────────────────────────────
  it('shows GPS denied copy when permission is rejected', async () => {
    stubGeo(false);
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'ACTIVE' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime })
      .click(
        screen.getByRole('button', { name: 'Отметиться на текущей паре' }),
      );
    fixture.detectChanges();

    expect(screen.getByText(/Нет доступа к геолокации/)).toBeTruthy();
  });

  // ── 8 ───────────────────────────────────────────────────────────
  it('STOMP attendance.marked for current user auto-confirms WITHOUT extra HTTP call', async () => {
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'ACTIVE' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    stompMock.__emit({
      lesson_id: 42,
      user_id: 3,
      group_id: 5,
      status: 'present',
      marked_by: 'self',
    });
    fixture.detectChanges();

    expect(screen.getByText('Вы отметились')).toBeTruthy();
    httpMock.expectNone(r => r.url === '/api/attendance/checkin');
  });

  // ── 9 ───────────────────────────────────────────────────────────
  it('STOMP attendance.marked for another user increments the counter but does NOT confirm', async () => {
    const { fixture } = await setup();
    flushLessons([lesson({ status: 'ACTIVE' })]);
    fixture.detectChanges();
    flushSubjectCache();
    fixture.detectChanges();

    stompMock.__emit({
      lesson_id: 42,
      user_id: 99,
      group_id: 5,
      status: 'present',
      marked_by: 'self',
    });
    fixture.detectChanges();

    expect(screen.getByText(/1 отметилось/)).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Отметиться на текущей паре' }),
    ).toBeTruthy();
  });

  // ── 10 ──────────────────────────────────────────────────────────
  it('ngOnInit calls StudentStompService.connect with the user groupId', async () => {
    const { fixture } = await setup();
    flushLessons([]);
    fixture.detectChanges();

    expect(stompMock.connect).toHaveBeenCalled();
    expect(stompMock.connect.mock.calls[0][0]).toBe(5);
  });

  // ── 11 ──────────────────────────────────────────────────────────
  it('ngOnDestroy calls StudentStompService.disconnect', async () => {
    const { fixture } = await setup();
    flushLessons([]);
    fixture.detectChanges();

    fixture.destroy();
    expect(stompMock.disconnect).toHaveBeenCalled();
  });
});
