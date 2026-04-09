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
    it('calls POST /api/academic/subjects with body', () => {
      const body = { name: 'Математика', teacherId: 3 };
      service.createSubject(body).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 10, name: 'Математика', teacherId: 3 });
    });
  });

  describe('updateSubject', () => {
    it('calls PUT /api/academic/subjects/{id} with body', () => {
      const body = { name: 'Физика', teacherId: 4 };
      service.updateSubject(10, body).subscribe();

      const req = httpMock.expectOne('/api/academic/subjects/10');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(body);
      req.flush({ id: 10, name: 'Физика', teacherId: 4 });
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
