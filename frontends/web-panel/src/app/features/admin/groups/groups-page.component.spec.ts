import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { GroupsPageComponent } from './groups-page.component';
import type { GroupResponse, UserResponse, PagedResponse } from '../shared/types';

function mockGroupsResponse(groups: GroupResponse[]): PagedResponse<GroupResponse> {
  return {
    _embedded: { groupResponseList: groups },
    page: { totalElements: groups.length, totalPages: 1, size: 200, number: 0 },
  };
}

function mockUsersResponse(users: UserResponse[]): PagedResponse<UserResponse> {
  return {
    _embedded: { userResponseList: users },
    page: { totalElements: users.length, totalPages: 1, size: 500, number: 0 },
  };
}

const MOCK_ACTIVE_GROUPS: GroupResponse[] = [
  { id: 1, name: 'УИТ-211', active: true, createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'УВП-221', active: true, createdAt: '2026-01-01T00:00:00Z' },
];

const MOCK_ARCHIVED_GROUPS: GroupResponse[] = [
  {
    id: 5,
    name: 'УИТ-411 (выпуск 2026)',
    active: false,
    createdAt: '2022-09-01T00:00:00Z',
    archivedAt: '2026-06-15T00:00:00Z',
  },
];

const MOCK_STUDENTS: UserResponse[] = [
  { id: 10, login: 'student00001', lastName: 'Иванов', firstName: 'Иван', middleName: null, role: 'student', status: 'active', groupId: 1, headman: true, employeeNumber: null, telegramId: null, createdAt: '2026-01-01T00:00:00Z' },
  { id: 11, login: 'student00002', lastName: 'Петров', firstName: 'Пётр', middleName: null, role: 'student', status: 'active', groupId: 1, headman: false, employeeNumber: null, telegramId: null, createdAt: '2026-01-01T00:00:00Z' },
  { id: 12, login: 'student00003', lastName: 'Сидорова', firstName: 'Мария', middleName: null, role: 'student', status: 'active', groupId: 2, headman: false, employeeNumber: null, telegramId: null, createdAt: '2026-01-01T00:00:00Z' },
];

describe('GroupsPageComponent', () => {
  let component: GroupsPageComponent;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GroupsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
      ],
    });
    const fixture = TestBed.createComponent(GroupsPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  /**
   * Симулирует один reload(): один запрос listGroupsByStatus + один listUsers.
   */
  function flushReload(
    groups: GroupResponse[],
    students: UserResponse[] = MOCK_STUDENTS,
    opts: { expectedStatus?: string; expectedSearch?: string | null } = {},
  ): void {
    const groupsReq = httpMock.expectOne(r => r.url === '/api/academic/groups');
    if (opts.expectedStatus) {
      expect(groupsReq.request.params.get('status')).toBe(opts.expectedStatus);
    }
    if (opts.expectedSearch !== undefined) {
      expect(groupsReq.request.params.get('search')).toBe(opts.expectedSearch);
    }
    groupsReq.flush(mockGroupsResponse(groups));

    const usersReq = httpMock.expectOne(r => r.url === '/api/academic/users');
    usersReq.flush(mockUsersResponse(students));
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ngOnInit загружает активные группы по умолчанию (status=ACTIVE)', () => {
    component.ngOnInit();
    flushReload(MOCK_ACTIVE_GROUPS, MOCK_STUDENTS, { expectedStatus: 'ACTIVE' });

    expect(component.status()).toBe('ACTIVE');
    expect(component.groups().length).toBe(2);
    expect(component.allStudents().length).toBe(3);
    expect(component.loading()).toBe(false);
  });

  it('onTabChange(1) переключает на ARCHIVED и грузит архивные группы', () => {
    component.ngOnInit();
    flushReload(MOCK_ACTIVE_GROUPS);

    component.onTabChange(1);

    flushReload(MOCK_ARCHIVED_GROUPS, MOCK_STUDENTS, { expectedStatus: 'ARCHIVED' });

    expect(component.status()).toBe('ARCHIVED');
    expect(component.groups().length).toBe(1);
    expect(component.groups()[0].name).toContain('выпуск 2026');
  });

  it('displayedColumns возвращает archivedColumns (без actions redact) в режиме ARCHIVED', () => {
    component.ngOnInit();
    flushReload(MOCK_ACTIVE_GROUPS);

    expect(component.displayedColumns).toEqual(['name', 'headman', 'studentCount', 'actions']);

    component.onTabChange(1);
    flushReload(MOCK_ARCHIVED_GROUPS);

    // Архивные колонки — без headman/studentCount.
    expect(component.displayedColumns).toEqual(['name', 'archivedAt', 'actions']);
  });

  it('поиск передаётся в GET /groups?search=... после debounce', async () => {
    component.ngOnInit();
    flushReload(MOCK_ACTIVE_GROUPS);

    component.searchControl.setValue('УИТ');

    // Подождать debounce(300ms) + microtasks.
    await new Promise(r => setTimeout(r, 350));

    flushReload(MOCK_ACTIVE_GROUPS, MOCK_STUDENTS, {
      expectedStatus: 'ACTIVE',
      expectedSearch: 'УИТ',
    });
  });

  it('groupHeadman возвращает старосту для группы', () => {
    component.ngOnInit();
    flushReload(MOCK_ACTIVE_GROUPS);

    const headman = component.groupHeadman(1);
    expect(headman).toBeDefined();
    expect(headman!.lastName).toBe('Иванов');

    expect(component.groupHeadman(2)).toBeUndefined();
  });

  it('groupStudentCount считает студентов', () => {
    component.ngOnInit();
    flushReload(MOCK_ACTIVE_GROUPS);

    expect(component.groupStudentCount(1)).toBe(2);
    expect(component.groupStudentCount(2)).toBe(1);
    expect(component.groupStudentCount(999)).toBe(0);
  });

  it('onTabChange сбрасывает поиск при смене таба', () => {
    component.ngOnInit();
    flushReload(MOCK_ACTIVE_GROUPS);

    component.searchControl.setValue('УИТ', { emitEvent: false });
    component.onTabChange(1);

    expect(component.searchControl.value).toBe('');

    flushReload(MOCK_ARCHIVED_GROUPS, MOCK_STUDENTS, { expectedStatus: 'ARCHIVED', expectedSearch: null });
  });
});
