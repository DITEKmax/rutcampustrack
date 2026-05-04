import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { computed, signal } from '@angular/core';
import { StudentDashboardComponent } from './student-dashboard.component';
import { AuthService, type AuthUser } from '../../../core/auth/auth.service';
import type {
  LessonResponse,
  StudentStatsResponse,
  ResolvedThresholdResponse,
} from '../shared/student-schedule.types';

const STUDENT_USER: AuthUser = {
  id: 3,
  role: 'STUDENT',
  isHeadman: false,
  groupId: 5,
};

function makeAuthMock() {
  return {
    currentUser: computed(() => STUDENT_USER),
    isAuthenticated: computed(() => true),
    accessToken: signal<string | null>('fake-token').asReadonly(),
    resolveDashboardFor: vi.fn(),
  };
}

function lesson(over: Partial<LessonResponse> = {}): LessonResponse {
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

describe('StudentDashboardComponent', () => {
  let fixture: ComponentFixture<StudentDashboardComponent>;
  let component: StudentDashboardComponent;
  let httpMock: HttpTestingController;
  let dateSpy: ReturnType<typeof vi.spyOn> | null = null;

  /**
   * Stub `new Date()` so the component's greeting / dateLabel / timeLabel
   * behave deterministically without fake timers (fake timers interact badly
   * with zone.js microtasks inside HttpClient observers). Keeps real Date.now
   * and Date.parse working.
   */
  function stubNow(iso: string): void {
    const fixed = new Date(iso).valueOf();
    const OriginalDate = globalThis.Date;
    dateSpy = vi.spyOn(globalThis, 'Date').mockImplementation(function (
      this: unknown,
      ...args: unknown[]
    ) {
      if (args.length === 0) return new OriginalDate(fixed);
      return new (OriginalDate as any)(...(args as []));
    } as unknown as DateConstructor) as ReturnType<typeof vi.spyOn>;
    // Preserve static methods
    (globalThis.Date as unknown as { now: () => number }).now = () => fixed;
    (globalThis.Date as unknown as { parse: typeof OriginalDate.parse }).parse =
      OriginalDate.parse.bind(OriginalDate);
    (globalThis.Date as unknown as { UTC: typeof OriginalDate.UTC }).UTC =
      OriginalDate.UTC.bind(OriginalDate);
  }

  beforeEach(() => {
    // Default: 2026-04-09 14:00 local — "Добрый день"
    stubNow('2026-04-09T14:00:00');
    TestBed.configureTestingModule({
      imports: [StudentDashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: makeAuthMock() },
      ],
    });
    fixture = TestBed.createComponent(StudentDashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      httpMock.verify();
    } finally {
      dateSpy?.mockRestore();
      dateSpy = null;
    }
  });

  function initAndFlush(
    opts: {
      lessons?: LessonResponse[];
      stats?: StudentStatsResponse;
      threshold?: ResolvedThresholdResponse;
    } = {},
  ): void {
    fixture.detectChanges(); // triggers ngOnInit + first render
    const lessons = opts.lessons ?? [];
    const stats =
      opts.stats ?? {
        subjects: [],
        overall: { total: 0, attended: 0, absent: 0, excused: 0, percentage: 100 },
      };
    const threshold =
      opts.threshold ?? {
        groupId: null,
        subjectId: null,
        percentage: 70,
        level: 'global',
      };

    httpMock
      .match((r) => r.url === '/api/schedule/groups/5/lessons')
      .forEach((r) => r.flush({ _embedded: { lessonResponseList: lessons } }));
    httpMock
      .match('/api/attendance/reports/student/stats')
      .forEach((r) => r.flush(stats));
    httpMock
      .match('/api/academic/thresholds/resolve')
      .forEach((r) => r.flush(threshold));

    fixture.detectChanges();

    // Subject-name lookups fire lazily via the async pipe after the first
    // detectChanges pass renders the next-lesson card and chip row.
    httpMock
      .match((r) => r.url.startsWith('/api/academic/subjects/'))
      .forEach((r) => r.flush({ id: 7, name: 'Математика' }));

    fixture.detectChanges();
  }

  it('issues three parallel API requests on init', () => {
    fixture.detectChanges();
    const lessonsReqs = httpMock.match((r) => r.url === '/api/schedule/groups/5/lessons');
    const statsReqs = httpMock.match('/api/attendance/reports/student/stats');
    const thresholdReqs = httpMock.match('/api/academic/thresholds/resolve');
    expect(lessonsReqs.length).toBeGreaterThanOrEqual(1);
    expect(statsReqs.length).toBeGreaterThanOrEqual(1);
    expect(thresholdReqs.length).toBeGreaterThanOrEqual(1);
    // Drain to keep httpMock.verify() happy
    lessonsReqs.forEach((r) => r.flush({ _embedded: { lessonResponseList: [] } }));
    statsReqs.forEach((r) =>
      r.flush({
        subjects: [],
        overall: { total: 0, attended: 0, absent: 0, excused: 0, percentage: 0 },
      }),
    );
    thresholdReqs.forEach((r) =>
      r.flush({ groupId: null, subjectId: null, percentage: 70, level: 'global' }),
    );
  });

  it('renders "Добрый день" at 14:00', () => {
    initAndFlush();
    expect(component.greeting()).toBe('Добрый день');
    const title = fixture.nativeElement.querySelector('.page-hero__title');
    expect(title?.textContent).toContain('Добрый день');
  });

  it('renders "Доброе утро" at 08:00', () => {
    dateSpy?.mockRestore();
    dateSpy = null;
    stubNow('2026-04-09T08:00:00');
    fixture = TestBed.createComponent(StudentDashboardComponent);
    component = fixture.componentInstance;
    initAndFlush();
    expect(component.greeting()).toBe('Доброе утро');
    const title = fixture.nativeElement.querySelector('.page-hero__title');
    expect(title?.textContent).toContain('Доброе утро');
  });

  it('renders "Добрый вечер" at 21:00', () => {
    dateSpy?.mockRestore();
    dateSpy = null;
    stubNow('2026-04-09T21:00:00');
    fixture = TestBed.createComponent(StudentDashboardComponent);
    component = fixture.componentInstance;
    initAndFlush();
    expect(component.greeting()).toBe('Добрый вечер');
  });

  it('renders "Сегодня пар нет" when today lesson list is empty', () => {
    initAndFlush();
    const emptyEl = fixture.nativeElement.querySelector('.dashboard-today__empty');
    expect(emptyEl?.textContent).toContain('Сегодня пар нет');
  });

  it('renders one today-chip per lesson', () => {
    initAndFlush({
      lessons: [
        lesson({ id: 1, startTime: '09:00:00' }),
        lesson({ id: 2, startTime: '10:45:00' }),
      ],
    });
    const chips = fixture.nativeElement.querySelectorAll('.dashboard-today-chip');
    expect(chips.length).toBe(2);
  });

  it('wires NextLessonCard with the ACTIVE lesson when one exists', () => {
    initAndFlush({
      lessons: [
        lesson({ id: 1, status: 'PLANNED', startTime: '08:00:00' }),
        lesson({ id: 2, status: 'ACTIVE', startTime: '14:00:00' }),
      ],
    });
    expect(component.nextLesson()?.status).toBe('ACTIVE');
    const eyebrow = fixture.nativeElement.querySelector('.next-lesson-card__eyebrow');
    expect(eyebrow?.textContent).toContain('Текущая пара');
  });

  it('wires NextLessonCard with earliest PLANNED lesson when no ACTIVE', () => {
    initAndFlush({
      lessons: [
        lesson({ id: 1, status: 'PLANNED', startTime: '12:00:00' }),
        lesson({ id: 2, status: 'PLANNED', startTime: '10:00:00' }),
      ],
    });
    expect(component.nextLesson()?.startTime).toBe('10:00:00');
    const eyebrow = fixture.nativeElement.querySelector('.next-lesson-card__eyebrow');
    expect(eyebrow?.textContent).toContain('Следующая пара');
  });

  it('renders RedzoneWarning for subjects below threshold (70%)', () => {
    initAndFlush({
      stats: {
        subjects: [
          {
            subjectId: 1,
            subjectName: 'Мат',
            subjectType: 'lab',
            total: 10,
            attended: 4,
            absent: 6,
            excused: 0,
            percentage: 42,
          },
          {
            subjectId: 2,
            subjectName: 'Физ',
            total: 10,
            attended: 8,
            absent: 2,
            excused: 0,
            percentage: 80,
          },
        ],
        overall: { total: 20, attended: 12, absent: 8, excused: 0, percentage: 60 },
      },
      threshold: { groupId: null, subjectId: null, percentage: 70, level: 'global' },
    });
    expect(component.redZoneSubjects().map((s) => s.subjectName)).toEqual(['Мат']);
    const banners = fixture.nativeElement.querySelectorAll('app-redzone-warning');
    expect(banners.length).toBe(1);
    const banner = banners[0] as HTMLElement;
    expect(banner.textContent).toContain('Мат');
    expect(banner.textContent).toContain('ЛЗ');
    expect(banner.textContent).toContain('42%');
  });

  it('renders NO red-zone warnings when all subjects are above threshold', () => {
    initAndFlush({
      stats: {
        subjects: [
          {
            subjectId: 1,
            subjectName: 'Мат',
            total: 10,
            attended: 9,
            absent: 1,
            excused: 0,
            percentage: 90,
          },
        ],
        overall: { total: 10, attended: 9, absent: 1, excused: 0, percentage: 90 },
      },
      threshold: { groupId: null, subjectId: null, percentage: 70, level: 'global' },
    });
    const banners = fixture.nativeElement.querySelectorAll('app-redzone-warning');
    expect(banners.length).toBe(0);
  });

  it('renders error copy when any of the three requests fails', () => {
    fixture.detectChanges();
    // Fail the lessons request — forkJoin will propagate the error to the
    // subscriber. The other two requests may still be live or already
    // cancelled in HttpClient's testing backend depending on timing; drain
    // them defensively so httpMock.verify() reports a clean slate.
    const drainSafely = (matcher: Parameters<HttpTestingController['match']>[0]) => {
      for (const req of httpMock.match(matcher)) {
        if (req.cancelled) continue;
        try {
          req.flush({});
        } catch {
          // ignore any race between forkJoin cancellation and flush
        }
      }
    };

    httpMock
      .match((r) => r.url === '/api/schedule/groups/5/lessons')
      .forEach((r) => r.flush(null, { status: 500, statusText: 'Server Error' }));

    drainSafely('/api/attendance/reports/student/stats');
    drainSafely('/api/academic/thresholds/resolve');

    fixture.detectChanges();

    expect(component.error()).toBe(
      'Не удалось загрузить данные. Сервер временно недоступен (500).',
    );
    const errorEl = fixture.nativeElement.querySelector('.dashboard-error');
    expect(errorEl?.textContent).toContain(
      'Не удалось загрузить данные. Сервер временно недоступен (500).',
    );
  });
});
