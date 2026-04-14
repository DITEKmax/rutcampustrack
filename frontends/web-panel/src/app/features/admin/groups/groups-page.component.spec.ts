import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
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

const MOCK_GROUPS: GroupResponse[] = [
  { id: 1, name: 'ИБ-211', active: true, createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'ПМ-221', active: true, createdAt: '2026-01-01T00:00:00Z' },
];

const MOCK_STUDENTS: UserResponse[] = [
  { id: 10, login: 'student00001', displayName: 'Иванов Иван', role: 'student', status: 'active', groupId: 1, headman: true, employeeNumber: null, telegramId: null, createdAt: '2026-01-01T00:00:00Z' },
  { id: 11, login: 'student00002', displayName: 'Петров Пётр', role: 'student', status: 'active', groupId: 1, headman: false, employeeNumber: null, telegramId: null, createdAt: '2026-01-01T00:00:00Z' },
  { id: 12, login: 'student00003', displayName: 'Сидорова Мария', role: 'student', status: 'active', groupId: 2, headman: false, employeeNumber: null, telegramId: null, createdAt: '2026-01-01T00:00:00Z' },
];

describe('GroupsPageComponent', () => {
  let component: GroupsPageComponent;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GroupsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(GroupsPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitRequests(): void {
    httpMock.expectOne(req => req.url === '/api/academic/groups').flush(mockGroupsResponse(MOCK_GROUPS));
    httpMock.expectOne(req => req.url === '/api/academic/users').flush(mockUsersResponse(MOCK_STUDENTS));
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ngOnInit loads groups and students', () => {
    component.ngOnInit();
    flushInitRequests();

    expect(component.groups().length).toBe(2);
    expect(component.allStudents().length).toBe(3);
    expect(component.loading()).toBe(false);
  });

  it('groupHeadman returns correct user for a group', () => {
    component.ngOnInit();
    flushInitRequests();

    const headman = component.groupHeadman(1);
    expect(headman).toBeDefined();
    expect(headman!.displayName).toBe('Иванов Иван');

    const noHeadman = component.groupHeadman(2);
    expect(noHeadman).toBeUndefined();
  });

  it('groupStudentCount returns correct count', () => {
    component.ngOnInit();
    flushInitRequests();

    expect(component.groupStudentCount(1)).toBe(2);
    expect(component.groupStudentCount(2)).toBe(1);
    expect(component.groupStudentCount(999)).toBe(0);
  });
});
