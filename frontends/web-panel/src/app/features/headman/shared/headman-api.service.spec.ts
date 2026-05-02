import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { HeadmanApiService } from './headman-api.service';

describe('HeadmanApiService', () => {
  let service: HeadmanApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        HeadmanApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(HeadmanApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getGroupMembers', () => {
    it('calls GET /api/academic/groups/my/members with page and size params', () => {
      service.getGroupMembers(0, 50).subscribe();

      const req = httpMock.expectOne(
        r =>
          r.url === '/api/academic/groups/my/members' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '50',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { userResponseList: [] } });
    });
  });

  describe('listAssistants', () => {
    it('calls GET /api/academic/assistants with groupId param', () => {
      service.listAssistants(42).subscribe();

      const req = httpMock.expectOne(
        r =>
          r.url === '/api/academic/assistants' &&
          r.params.get('groupId') === '42',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { assistantResponseList: [] } });
    });
  });

  describe('assignAssistant', () => {
    it('calls POST /api/academic/assistants with body', () => {
      const body = { studentId: 7, permissions: ['MANAGE_ATTENDANCE'] };
      service.assignAssistant(body).subscribe();

      const req = httpMock.expectOne('/api/academic/assistants');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 1, studentId: 7, permissions: ['MANAGE_ATTENDANCE'] });
    });
  });

  describe('revokeAssistant', () => {
    it('calls DELETE /api/academic/assistants/{id}', () => {
      service.revokeAssistant(5).subscribe();

      const req = httpMock.expectOne('/api/academic/assistants/5');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('listSubjects', () => {
    it('calls GET /api/academic/subjects with page and size params', () => {
      service.listSubjects(0, 50).subscribe();

      const req = httpMock.expectOne(
        r =>
          r.url === '/api/academic/subjects' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '50',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { subjectResponseList: [] } });
    });
  });

  describe('createSubject', () => {
    it('calls POST /api/academic/subjects with {name, type, teacherIds[]}', () => {
      const body = { name: 'Математика', type: 'LECTURE', teacherIds: [3, 5] };
      service.createSubject(body).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 10, name: 'Математика', type: 'LECTURE', teacherIds: [3, 5] });
    });

    it('accepts empty teacherIds array', () => {
      const body = { name: 'Физика', type: 'PRACTICE', teacherIds: [] };
      service.createSubject(body).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 11, name: 'Физика', type: 'PRACTICE', teacherIds: [] });
    });
  });

  describe('updateSubject', () => {
    it('calls PUT /api/academic/subjects/{id} with {name, type, teacherIds[]}', () => {
      const body = { name: 'Физика', type: 'LAB', teacherIds: [4] };
      service.updateSubject(10, body).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects/10');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 10, name: 'Физика', type: 'LAB', teacherIds: [4] });
    });
  });

  describe('addTeacherToSubject', () => {
    it('calls POST /api/academic/subjects/{id}/teachers/{teacherId}', () => {
      service.addTeacherToSubject(10, 3).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects/10/teachers/3');
      expect(req.request.method).toBe('POST');
      req.flush(null);
    });
  });

  describe('removeTeacherFromSubject', () => {
    it('calls DELETE /api/academic/subjects/{id}/teachers/{teacherId}', () => {
      service.removeTeacherFromSubject(10, 3).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects/10/teachers/3');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('deleteSubject', () => {
    it('calls DELETE /api/academic/subjects/{id}', () => {
      service.deleteSubject(10).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects/10');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('listTeachers', () => {
    it('calls GET /api/academic/users/teachers', () => {
      service.listTeachers().subscribe();

      const req = httpMock.expectOne('/api/academic/users/teachers');
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { userResponseList: [] } });
    });
  });

  describe('getGroupScheduleItems', () => {
    it('calls GET /api/schedule/items with groupId and semesterId params', () => {
      service.getGroupScheduleItems(5, 1).subscribe();

      const req = httpMock.expectOne(
        r =>
          r.url === '/api/schedule/items' &&
          r.params.get('groupId') === '5' &&
          r.params.get('semesterId') === '1',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { scheduleItemResponseList: [] } });
    });
  });

  describe('createScheduleItem', () => {
    it('calls POST /api/schedule/items with body', () => {
      const body = {
        groupId: 5, subjectId: 10, semesterId: 1,
        dayOfWeek: 1, lessonNumber: 2,
        startTime: '09:00', endTime: '10:30',
        weekType: 'ALL', room: '301',
      };
      service.createScheduleItem(body).subscribe();
      const req = httpMock.expectOne('/api/schedule/items');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 42 });
    });
  });

  describe('updateScheduleItem', () => {
    it('calls PUT /api/schedule/items/{id}', () => {
      const body = {
        subjectId: 10, dayOfWeek: 1, lessonNumber: 2,
        startTime: '09:00', endTime: '10:30', weekType: 'ODD', room: '302',
      };
      service.updateScheduleItem(42, body).subscribe();
      const req = httpMock.expectOne('/api/schedule/items/42');
      expect(req.request.method).toBe('PUT');
      req.flush({ id: 42 });
    });
  });

  describe('deleteScheduleItem', () => {
    it('calls DELETE /api/schedule/items/{id}', () => {
      service.deleteScheduleItem(42).subscribe();
      const req = httpMock.expectOne('/api/schedule/items/42');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('createOneOffLesson', () => {
    it('calls POST /api/schedule/one-off-lessons with body', () => {
      const body = {
        groupId: 5, subjectId: 10, date: '2026-04-20',
        lessonNumber: 3, classroom: '305',
      };
      service.createOneOffLesson(body).subscribe();
      const req = httpMock.expectOne('/api/schedule/one-off-lessons');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 100 });
    });
  });

  describe('deleteOneOffLesson', () => {
    it('calls DELETE /api/schedule/one-off-lessons/{id}', () => {
      service.deleteOneOffLesson(100).subscribe();
      const req = httpMock.expectOne('/api/schedule/one-off-lessons/100');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('getOneOffLessons', () => {
    it('calls GET /api/schedule/one-off-lessons with range params', () => {
      service.getOneOffLessons(5, '2026-04-01', '2026-04-30').subscribe();
      const req = httpMock.expectOne(
        r =>
          r.url === '/api/schedule/one-off-lessons' &&
          r.params.get('groupId') === '5' &&
          r.params.get('dateFrom') === '2026-04-01' &&
          r.params.get('dateTo') === '2026-04-30',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { oneOffLessonResponseList: [] } });
    });
  });

  describe('cancelLesson', () => {
    it('calls PATCH /api/schedule/lessons/{id}/cancel with reason', () => {
      service.cancelLesson(77, 'Болезнь преподавателя').subscribe();
      const req = httpMock.expectOne('/api/schedule/lessons/77/cancel');
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ reason: 'Болезнь преподавателя' });
      req.flush(null);
    });
  });

  describe('restoreLesson', () => {
    it('calls PATCH /api/schedule/lessons/{id}/restore', () => {
      service.restoreLesson(77).subscribe();
      const req = httpMock.expectOne('/api/schedule/lessons/77/restore');
      expect(req.request.method).toBe('PATCH');
      req.flush(null);
    });
  });

  describe('getGroupLessons', () => {
    it('calls GET /api/schedule/groups/{groupId}/lessons with date range', () => {
      service.getGroupLessons(5, '2026-04-15', '2026-04-29').subscribe();
      const req = httpMock.expectOne(r =>
        r.url === '/api/schedule/groups/5/lessons'
        && r.params.get('dateFrom') === '2026-04-15'
        && r.params.get('dateTo') === '2026-04-29'
        && r.params.get('size') === '500',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { lessonResponseList: [] } });
    });

    it('passes optional status filter when provided', () => {
      service.getGroupLessons(5, '2026-04-15', '2026-04-29', 'planned').subscribe();
      const req = httpMock.expectOne(r => r.params.get('status') === 'planned');
      req.flush({ _embedded: { lessonResponseList: [] } });
    });
  });

  describe('headman weekly report export', () => {
    it('lists active semester weeks without groupId', () => {
      service.listHeadmanWeeklyReportWeeks().subscribe(resp => {
        expect(resp.weeks[0].weekStart).toBe('2026-04-27');
      });

      const req = httpMock.expectOne('/api/attendance/reports/headman-weekly/weeks');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys()).toEqual([]);
      req.flush({
        semesterId: 1,
        semesterName: 'Spring',
        semesterDateFrom: '2026-04-29',
        semesterDateTo: '2026-05-12',
        weeks: [
          {
            weekOfSemester: 1,
            isoWeek: 18,
            label: 'Н1',
            weekStart: '2026-04-27',
            weekEnd: '2026-05-03',
            current: true,
          },
        ],
      });
    });

    it('downloads one displayed week as blob with weekStart and format params only', () => {
      service.downloadHeadmanWeeklyReport('2026-04-27', 'pdf').subscribe(resp => {
        expect(resp.body).toBeInstanceOf(Blob);
      });

      const req = httpMock.expectOne(r =>
        r.url === '/api/attendance/reports/headman-weekly/current'
        && r.params.get('weekStart') === '2026-04-27'
        && r.params.get('format') === 'pdf'
        && r.params.keys().length === 2,
      );
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['pdf']), {
        headers: { 'Content-Type': 'application/pdf' },
      });
    });

    it('exports selected weeks as blob without groupId in request body', () => {
      service.exportHeadmanWeeklyReport({
        weekStarts: ['2026-04-27', '2026-05-11'],
        format: 'png',
      }).subscribe(resp => {
        expect(resp.body).toBeInstanceOf(Blob);
      });

      const req = httpMock.expectOne('/api/attendance/reports/headman-weekly/export');
      expect(req.request.method).toBe('POST');
      expect(req.request.responseType).toBe('blob');
      expect(req.request.body).toEqual({
        weekStarts: ['2026-04-27', '2026-05-11'],
        format: 'png',
      });
      expect(req.request.body.groupId).toBeUndefined();
      req.flush(new Blob(['zip']), {
        headers: { 'Content-Type': 'application/zip' },
      });
    });
  });

  describe('listSemesters', () => {
    it('calls GET /api/academic/semesters with size=200', () => {
      service.listSemesters().subscribe();
      const req = httpMock.expectOne(
        r => r.url === '/api/academic/semesters' && r.params.get('size') === '200',
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { semesterResponseList: [] } });
    });
  });

  describe('getTodayLessons', () => {
    it('calls GET /api/schedule/groups/{groupId}/lessons with today dateFrom and dateTo params', () => {
      const today = new Date().toISOString().split('T')[0];
      service.getTodayLessons(5).subscribe();

      const req = httpMock.expectOne(
        r =>
          r.url === '/api/schedule/groups/5/lessons' &&
          r.params.get('dateFrom') === today &&
          r.params.get('dateTo') === today,
      );
      expect(req.request.method).toBe('GET');
      req.flush({ _embedded: { lessonResponseList: [] } });
    });
  });
});
