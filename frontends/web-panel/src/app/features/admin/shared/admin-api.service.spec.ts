import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { AdminApiService } from './admin-api.service';
import type { UserResponse } from './types';

describe('AdminApiService', () => {
  let service: AdminApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AdminApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AdminApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listUsers() calls GET /api/academic/users with page/size params and extracts _embedded items', () => {
    const mockUser: UserResponse = {
      id: 1, login: 'student00001', displayName: 'Иванов И.И.', role: 'student',
      status: 'active', groupId: 1, headman: false, employeeNumber: null,
      telegramId: null, createdAt: '2026-01-01',
    };
    let result: any;

    service.listUsers({ page: 0, size: 20 }).subscribe(r => (result = r));

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/users' &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      _embedded: { userResponseList: [mockUser] },
      page: { totalElements: 1, totalPages: 1, size: 20, number: 0 },
    });

    expect(result.items).toEqual([mockUser]);
    expect(result.total).toBe(1);
  });

  it('listUsers() passes role and status filters as params', () => {
    service.listUsers({ page: 0, size: 20, role: 'student', status: 'active' }).subscribe();

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/users' &&
      r.params.get('role') === 'STUDENT' &&
      r.params.get('status') === 'active',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      _embedded: { userResponseList: [] },
      page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
    });
  });

  it('createUser() calls POST /api/academic/users with CreateUserRequest body', () => {
    const body = { displayName: 'Петров П.П.', role: 'student' as const };

    service.createUser(body).subscribe();

    const req = httpMock.expectOne('/api/academic/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 2, login: 'student00002', initialPassword: 'abc123', ...body,
      status: 'active', groupId: null, headman: false, employeeNumber: null,
      telegramId: null, createdAt: '2026-01-01' });
  });

  it('patchUser() calls PATCH /api/academic/users/{id} with PatchUserRequest body', () => {
    const body = { displayName: 'Обновлено' };

    service.patchUser(5, body).subscribe();

    const req = httpMock.expectOne('/api/academic/users/5');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 5, login: 'student00005', displayName: 'Обновлено', role: 'student',
      status: 'active', groupId: null, headman: false, employeeNumber: null,
      telegramId: null, createdAt: '2026-01-01' });
  });

  it('deleteUser() calls DELETE /api/academic/users/{id}', () => {
    service.deleteUser(3).subscribe();

    const req = httpMock.expectOne('/api/academic/users/3');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('listGroups() calls GET /api/academic/groups?size=200 and extracts _embedded items', () => {
    const mockGroup = { id: 1, name: 'ИТ-21', active: true, createdAt: '2026-01-01' };
    let result: any[] = [];

    service.listGroups().subscribe(g => (result = g));

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/groups' &&
      r.params.get('size') === '200',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ _embedded: { groupResponseList: [mockGroup] } });

    expect(result).toEqual([mockGroup]);
  });

  it('createGroup() calls POST /api/academic/groups', () => {
    const body = { name: 'ИТ-22' };

    service.createGroup(body).subscribe();

    const req = httpMock.expectOne('/api/academic/groups');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 2, ...body, active: true, createdAt: '2026-01-01' });
  });

  it('updateGroup() calls PUT /api/academic/groups/{id}', () => {
    const body = { name: 'ИТ-22', active: true };

    service.updateGroup(2, body).subscribe();

    const req = httpMock.expectOne('/api/academic/groups/2');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 2, ...body, createdAt: '2026-01-01' });
  });

  it('listSemesters() calls GET /api/academic/semesters?size=200', () => {
    const mockSemester = { id: 1, name: 'Весна 2026', dateFrom: '2026-02-01',
      dateTo: '2026-06-30', active: true, createdAt: '2026-01-01' };
    let result: any[] = [];

    service.listSemesters().subscribe(s => (result = s));

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/semesters' &&
      r.params.get('size') === '200',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ _embedded: { semesterResponseList: [mockSemester] } });

    expect(result).toEqual([mockSemester]);
  });

  it('deleteSemester() uses http.request DELETE with confirmation body', () => {
    service.deleteSemester(1, 'Весна 2026').subscribe();

    const req = httpMock.expectOne('/api/academic/semesters/1');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ confirmation: 'Весна 2026' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('activateSemester() calls PATCH /api/academic/semesters/{id}/activate', () => {
    service.activateSemester(1).subscribe();

    const req = httpMock.expectOne('/api/academic/semesters/1/activate');
    expect(req.request.method).toBe('PATCH');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('getDashboardStats() calls GET /api/academic/dashboard/stats', () => {
    const mockStats = { totalStudents: 100, totalTeachers: 10,
      totalGroups: 5, activeGroups: 3, activeSemesterName: 'Весна 2026' };
    let result: any;

    service.getDashboardStats().subscribe(s => (result = s));

    const req = httpMock.expectOne('/api/academic/dashboard/stats');
    expect(req.request.method).toBe('GET');
    req.flush(mockStats);

    expect(result).toEqual(mockStats);
  });

  // BUG-006-6 / plan 58-08 tests

  it('listGroupsByStatus() calls GET /api/academic/groups with status=ACTIVE filter', () => {
    const mockGroup = { id: 1, name: 'УИТ-311', active: true, createdAt: '2026-01-01T00:00:00Z' };
    let result: any;

    service.listGroupsByStatus({ status: 'ACTIVE' }).subscribe(r => (result = r));

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/groups' &&
      r.params.get('status') === 'ACTIVE',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      _embedded: { groupResponseList: [mockGroup] },
      page: { totalElements: 1, totalPages: 1, size: 200, number: 0 },
    });

    expect(result.items).toEqual([mockGroup]);
    expect(result.total).toBe(1);
  });

  it('listGroupsByStatus() passes status=ARCHIVED and search params', () => {
    service
      .listGroupsByStatus({ status: 'ARCHIVED', search: 'УИТ' })
      .subscribe();

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/groups' &&
      r.params.get('status') === 'ARCHIVED' &&
      r.params.get('search') === 'УИТ',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      _embedded: { groupResponseList: [] },
      page: { totalElements: 0, totalPages: 0, size: 200, number: 0 },
    });
  });

  it('promotePreview() calls POST /api/academic/groups/promote/preview with empty body', () => {
    const summary = { toPromote: [], toArchive: [], conflicts: [], dryRun: true, executed: false };
    let result: any;

    service.promotePreview().subscribe(r => (result = r));

    const req = httpMock.expectOne('/api/academic/groups/promote/preview');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(summary);

    expect(result).toEqual(summary);
  });

  it('promote() calls POST /api/academic/groups/promote with empty body', () => {
    const summary = {
      toPromote: [{ id: 1, from: 'УИТ-111', to: 'УИТ-211', action: 'PROMOTE' }],
      toArchive: [],
      conflicts: [],
      dryRun: false,
      executed: true,
    };
    let result: any;

    service.promote().subscribe(r => (result = r));

    const req = httpMock.expectOne('/api/academic/groups/promote');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(summary);

    expect(result).toEqual(summary);
  });

  it('getGroup() calls GET /api/academic/groups/{id}', () => {
    const group = {
      id: 7, name: 'УИТ-411 (выпуск 2026)', active: false,
      createdAt: '2022-09-01T00:00:00Z', archivedAt: '2026-06-15T00:00:00Z',
    };
    let result: any;

    service.getGroup(7).subscribe(g => (result = g));

    const req = httpMock.expectOne('/api/academic/groups/7');
    expect(req.request.method).toBe('GET');
    req.flush(group);

    expect(result).toEqual(group);
  });

  it('listStudentsByGroup() calls GET /api/academic/users with role=STUDENT param and filters by groupId', () => {
    const student1 = {
      id: 1, login: 'student00001', displayName: 'Иванов', role: 'student',
      status: 'active', groupId: 1, headman: false, employeeNumber: null,
      telegramId: null, createdAt: '2026-01-01',
    };
    const student2 = {
      id: 2, login: 'student00002', displayName: 'Петров', role: 'student',
      status: 'active', groupId: 2, headman: false, employeeNumber: null,
      telegramId: null, createdAt: '2026-01-01',
    };
    let result: any[] = [];

    service.listStudentsByGroup(1).subscribe(s => (result = s));

    const req = httpMock.expectOne(r =>
      r.url === '/api/academic/users' &&
      r.params.get('role') === 'STUDENT' &&
      r.params.get('size') === '500',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      _embedded: { userResponseList: [student1, student2] },
      page: { totalElements: 2, totalPages: 1, size: 500, number: 0 },
    });

    expect(result).toEqual([student1]);
  });
});
