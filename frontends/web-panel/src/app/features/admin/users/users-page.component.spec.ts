import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { UsersPageComponent } from './users-page.component';

describe('UsersPageComponent', () => {
  let component: UsersPageComponent;
  let httpMock: HttpTestingController;
  let dialog: MatDialog;

  const mockUsersResponse = {
    _embedded: {
      users: [
        {
          id: 1,
          login: 'student00001',
          displayName: 'Иванов Иван',
          role: 'student',
          status: 'active',
          groupId: 1,
          headman: false,
          employeeNumber: null,
          telegramId: null,
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
    },
    page: { totalElements: 1, totalPages: 1, size: 20, number: 0 },
  };

  const mockGroupsResponse = {
    _embedded: {
      groups: [
        { id: 1, name: 'ИВТ-101', code: 'IVT101', active: true, createdAt: '2026-01-01T00:00:00Z' },
      ],
    },
    page: { totalElements: 1, totalPages: 1, size: 200, number: 0 },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [UsersPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
      ],
    });
    const fixture = TestBed.createComponent(UsersPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    dialog = TestBed.inject(MatDialog);
    fixture.detectChanges(); // triggers ngOnInit
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialRequests(): void {
    httpMock.expectOne(req => req.url === '/api/academic/groups').flush(mockGroupsResponse);
    httpMock.expectOne(req => req.url === '/api/academic/users').flush(mockUsersResponse);
  }

  it('should create', () => {
    expect(component).toBeTruthy();
    flushInitialRequests();
  });

  it('ngOnInit triggers loadUsers and listGroups calls', () => {
    const groupsReq = httpMock.expectOne(req => req.url === '/api/academic/groups');
    expect(groupsReq.request.method).toBe('GET');
    groupsReq.flush(mockGroupsResponse);

    const usersReq = httpMock.expectOne(req => req.url === '/api/academic/users');
    expect(usersReq.request.method).toBe('GET');
    usersReq.flush(mockUsersResponse);

    expect(component.users().length).toBe(1);
    expect(component.groups().length).toBe(1);
    expect(component.totalElements()).toBe(1);
  });

  it('openCreateDialog opens MatDialog with mode create', () => {
    flushInitialRequests();

    const componentDialog = (component as any).dialog as MatDialog;
    const openSpy = vi.spyOn(componentDialog, 'open').mockReturnValue({ afterClosed: () => of(null) } as any);

    component.openCreateDialog();

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        data: expect.objectContaining({ mode: 'create' }),
      }),
    );
  });

  it('restoreUser calls patchUser with status active', () => {
    flushInitialRequests();

    const user = {
      id: 1,
      login: 'student00001',
      displayName: 'Иванов',
      role: 'student' as const,
      status: 'archived' as const,
      groupId: null,
      headman: false,
      employeeNumber: null,
      telegramId: null,
      createdAt: '2026-01-01T00:00:00Z',
    };

    component.restoreUser(user);

    const patchReq = httpMock.expectOne(`/api/academic/users/1`);
    expect(patchReq.request.method).toBe('PATCH');
    expect(patchReq.request.body).toEqual({ status: 'active' });

    patchReq.flush({ ...user, status: 'active' });

    // After patch success, loadUsers is called again
    httpMock.expectOne(req => req.url === '/api/academic/users').flush(mockUsersResponse);
  });
});
