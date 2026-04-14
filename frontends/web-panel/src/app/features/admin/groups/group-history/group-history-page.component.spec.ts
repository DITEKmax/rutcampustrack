import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { GroupHistoryPageComponent } from './group-history-page.component';
import type { GroupResponse, UserResponse, PagedResponse } from '../../shared/types';

function mockUsersResponse(users: UserResponse[]): PagedResponse<UserResponse> {
  return {
    _embedded: { userResponseList: users },
    page: { totalElements: users.length, totalPages: 1, size: 500, number: 0 },
  };
}

const ARCHIVED: GroupResponse = {
  id: 7,
  name: 'УИТ-411 (выпуск 2026)',
  active: false,
  createdAt: '2022-09-01T00:00:00Z',
  archivedAt: '2026-06-15T00:00:00Z',
};

const STUDENTS: UserResponse[] = [
  { id: 100, login: 'student00001', lastName: 'Иванов', firstName: 'Иван', middleName: null, role: 'student', status: 'active', groupId: 7, headman: false, employeeNumber: null, telegramId: null, createdAt: '2022-09-01T00:00:00Z' },
  { id: 101, login: 'student00002', lastName: 'Петров', firstName: 'Пётр', middleName: null, role: 'student', status: 'active', groupId: 99, headman: false, employeeNumber: null, telegramId: null, createdAt: '2022-09-01T00:00:00Z' },
];

function setupTestBed(idParam: string) {
  TestBed.configureTestingModule({
    imports: [GroupHistoryPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      provideNoopAnimations(),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: { get: (_: string) => idParam } } },
      },
    ],
  });
}

describe('GroupHistoryPageComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => {
    if (httpMock) httpMock.verify();
  });

  it('грузит архивную группу + студентов, показывает и фильтрует по groupId', () => {
    setupTestBed('7');
    const fixture = TestBed.createComponent(GroupHistoryPageComponent);
    const component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    component.ngOnInit();

    httpMock.expectOne('/api/academic/groups/7').flush(ARCHIVED);
    httpMock.expectOne(r => r.url === '/api/academic/users').flush(mockUsersResponse(STUDENTS));

    expect(component.group()).toEqual(ARCHIVED);
    expect(component.students().length).toBe(1);
    expect(component.students()[0].id).toBe(100);
    expect(component.loading()).toBe(false);
    expect(component.notFound()).toBe(false);
  });

  it('graduationYear/activeName правильно разбирают суффикс "(выпуск YYYY)"', () => {
    setupTestBed('7');
    const fixture = TestBed.createComponent(GroupHistoryPageComponent);
    const component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    component.ngOnInit();
    httpMock.expectOne('/api/academic/groups/7').flush(ARCHIVED);
    httpMock.expectOne(r => r.url === '/api/academic/users').flush(mockUsersResponse([]));

    expect(component.graduationYear()).toBe(2026);
    expect(component.activeName()).toBe('УИТ-411');
  });

  it('активная группа → router.navigate(/admin/groups) + snackbar', () => {
    setupTestBed('7');
    const fixture = TestBed.createComponent(GroupHistoryPageComponent);
    const component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.ngOnInit();
    httpMock
      .expectOne('/api/academic/groups/7')
      .flush({ ...ARCHIVED, active: true, archivedAt: null });
    httpMock.expectOne(r => r.url === '/api/academic/users').flush(mockUsersResponse([]));

    expect(navSpy).toHaveBeenCalledWith(['/admin/groups']);
    expect(component.group()).toBeNull();
  });

  it('404 на getGroup → notFound=true', () => {
    setupTestBed('77');
    const fixture = TestBed.createComponent(GroupHistoryPageComponent);
    const component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    component.ngOnInit();
    httpMock
      .expectOne('/api/academic/groups/77')
      .flush({ detail: 'not found' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(r => r.url === '/api/academic/users').flush(mockUsersResponse([]));

    expect(component.notFound()).toBe(true);
    expect(component.group()).toBeNull();
    expect(component.loading()).toBe(false);
  });

  it('нечисловой id → notFound=true без HTTP-запросов', () => {
    setupTestBed('abc');
    const fixture = TestBed.createComponent(GroupHistoryPageComponent);
    const component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    component.ngOnInit();

    expect(component.notFound()).toBe(true);
    expect(component.loading()).toBe(false);
    httpMock.expectNone('/api/academic/groups/abc');
  });
});
