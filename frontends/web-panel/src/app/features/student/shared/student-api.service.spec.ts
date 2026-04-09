import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { StudentApiService } from './student-api.service';
import type {
  LessonResponse,
  StudentStatsResponse,
  ResolvedThresholdResponse,
} from './student-schedule.types';

describe('StudentApiService', () => {
  let service: StudentApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        StudentApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(StudentApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getWeekLessons', () => {
    it('calls GET /api/schedule/groups/{id}/lessons with dateFrom/dateTo/size=100 and unwraps _embedded.lessonResponseList', () => {
      const mockLesson: LessonResponse = {
        id: 11,
        scheduleItemId: 21,
        groupId: 5,
        subjectId: 42,
        teacherId: 7,
        date: '2026-04-06',
        status: 'PLANNED',
        dayOfWeek: 1,
        lessonNumber: 1,
        startTime: '09:00:00',
        endTime: '10:30:00',
        weekType: 'BOTH',
        room: '101',
        geoBlocked: false,
        cancelReason: null,
        createdAt: '2026-04-01T00:00:00Z',
      };
      let result: LessonResponse[] = [];

      service
        .getWeekLessons(5, '2026-04-06', '2026-04-11')
        .subscribe(lessons => (result = lessons));

      const req = httpMock.expectOne(
        r =>
          r.url === '/api/schedule/groups/5/lessons' &&
          r.params.get('dateFrom') === '2026-04-06' &&
          r.params.get('dateTo') === '2026-04-11' &&
          r.params.get('size') === '100',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { lessonResponseList: [mockLesson] } });

      expect(result).toEqual([mockLesson]);
    });

    it('returns an empty array when the response body has no _embedded key', () => {
      let result: LessonResponse[] = [];

      service
        .getWeekLessons(5, '2026-04-06', '2026-04-11')
        .subscribe(lessons => (result = lessons));

      const req = httpMock.expectOne(r => r.url === '/api/schedule/groups/5/lessons');
      req.flush({ page: { totalElements: 0, totalPages: 0, size: 100, number: 0 } });

      expect(result).toEqual([]);
    });
  });

  describe('getStudentStats', () => {
    it('calls GET /api/attendance/reports/student/stats and returns the parsed body', () => {
      const mockStats: StudentStatsResponse = {
        subjects: [
          {
            subjectId: 10,
            subjectName: 'Математика',
            total: 20,
            attended: 15,
            absent: 5,
            excused: 0,
            percentage: 75,
          },
        ],
        overall: { total: 20, attended: 15, absent: 5, excused: 0, percentage: 75 },
      };
      let result: StudentStatsResponse | null = null;

      service.getStudentStats().subscribe(stats => (result = stats));

      const req = httpMock.expectOne('/api/attendance/reports/student/stats');
      expect(req.request.method).toBe('GET');
      req.flush(mockStats);

      expect(result).toEqual(mockStats);
    });
  });

  describe('resolveGlobalThreshold', () => {
    it('calls GET /api/academic/thresholds/resolve with NO query params', () => {
      const mockThreshold: ResolvedThresholdResponse = {
        groupId: null,
        subjectId: null,
        percentage: 70,
        level: 'global',
      };
      let result: ResolvedThresholdResponse | null = null;

      service.resolveGlobalThreshold().subscribe(t => (result = t));

      const req = httpMock.expectOne(
        r => r.url === '/api/academic/thresholds/resolve' && r.params.keys().length === 0,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockThreshold);

      expect(result).toEqual(mockThreshold);
    });
  });

  describe('resolveGroupThreshold', () => {
    it('calls GET /api/academic/thresholds/resolve with groupId query param', () => {
      const mockThreshold: ResolvedThresholdResponse = {
        groupId: 5,
        subjectId: null,
        percentage: 65,
        level: 'group',
      };

      service.resolveGroupThreshold(5).subscribe();

      const req = httpMock.expectOne(
        r =>
          r.url === '/api/academic/thresholds/resolve' && r.params.get('groupId') === '5',
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockThreshold);
    });
  });

  describe('checkin', () => {
    it('calls POST /api/attendance/checkin with {lat, lng} body', () => {
      const mockResponse = {
        status: 'present' as const,
        lessonId: 99,
        timestamp: '2026-04-09T10:00:00Z',
      };

      service.checkin({ lat: 55.1, lng: 37.2 }).subscribe();

      const req = httpMock.expectOne('/api/attendance/checkin');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ lat: 55.1, lng: 37.2 });
      req.flush(mockResponse);
    });
  });
});
