import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { JournalApiService } from './journal-api.service';

describe('JournalApiService', () => {
  let service: JournalApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        JournalApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(JournalApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getJournal() calls GET /api/attendance/reports/journal with correct params', () => {
    service
      .getJournal(1, 2, '2026-03-01', '2026-03-31')
      .subscribe();

    const req = httpMock.expectOne(r =>
      r.url === '/api/attendance/reports/journal' &&
      r.params.get('groupId') === '1' &&
      r.params.get('subjectId') === '2' &&
      r.params.get('dateFrom') === '2026-03-01' &&
      r.params.get('dateTo') === '2026-03-31',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ groupId: 1, subjectId: 2, dates: [], students: [] });
  });

  it('getMyAssignments() calls GET /api/academic/assignments/my', () => {
    service.getMyAssignments().subscribe();

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/assignments/my' &&
      r.params.get('size') === '200',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ _embedded: { assignmentResponseList: [] } });
  });

  it('getGroups() calls GET /api/academic/groups and unwraps _embedded', () => {
    const mockGroup = { id: 1, name: 'ИТ-21', code: 'IT21', active: true, createdAt: '2026-01-01' };
    let result: any[] = [];

    service.getGroups().subscribe(groups => (result = groups));

    const req = httpMock.expectOne(r => r.url === '/api/academic/groups');
    expect(req.request.method).toBe('GET');
    req.flush({ _embedded: { groupResponseList: [mockGroup] } });

    expect(result).toEqual([mockGroup]);
  });

  it('getSubjects() calls GET /api/academic/subjects and unwraps _embedded', () => {
    const mockSubject = { id: 10, name: 'Математика' };
    let result: any[] = [];

    service.getSubjects().subscribe(subjects => (result = subjects));

    const req = httpMock.expectOne(r => r.url === '/api/academic/subjects');
    expect(req.request.method).toBe('GET');
    req.flush({ _embedded: { subjectResponseList: [mockSubject] } });

    expect(result).toEqual([mockSubject]);
  });
});
