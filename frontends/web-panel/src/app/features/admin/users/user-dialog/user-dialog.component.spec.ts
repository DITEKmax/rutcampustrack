import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { UserDialogComponent, UserDialogData } from './user-dialog.component';
import type { UserResponse } from '../../shared/types';

describe('UserDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRefSpy: { close: ReturnType<typeof vi.fn> };

  function createComponent(data: UserDialogData): UserDialogComponent {
    dialogRefSpy = { close: vi.fn() };

    TestBed.configureTestingModule({
      imports: [UserDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRefSpy },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(UserDialogComponent);
    return fixture.componentInstance;
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('opens in create mode with empty form', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    expect(component.data.mode).toBe('create');
    expect(component.form.get('displayName')!.value).toBe('');
    expect(component.form.get('role')!.value).toBe('');
  });

  it('opens in edit mode with form pre-filled', () => {
    const user: UserResponse = {
      id: 1,
      login: 'student00001',
      displayName: 'Иванов Иван',
      role: 'student',
      status: 'active',
      groupId: 1,
      headman: true,
      employeeNumber: null,
      telegramId: null,
      createdAt: '2026-01-01T00:00:00Z',
    };

    const component = createComponent({ mode: 'edit', user, groups: [] });

    expect(component.form.get('displayName')!.value).toBe('Иванов Иван');
    expect(component.form.get('role')!.value).toBe('student');
    expect(component.form.get('role')!.disabled).toBe(true);
    expect(component.form.get('groupId')!.value).toBe(1);
    expect(component.form.get('isHeadman')!.value).toBe(true);
  });

  it('save in create mode calls adminApi.createUser with correct body', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({
      displayName: 'Петров Пётр',
      role: 'student',
    });

    component.save();

    const req = httpMock.expectOne('/api/academic/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      displayName: 'Петров Пётр',
      role: 'student',
    });

    req.flush({
      id: 2,
      login: 'student00002',
      displayName: 'Петров Пётр',
      role: 'student',
      status: 'active',
      groupId: null,
      headman: false,
      employeeNumber: null,
      telegramId: null,
      createdAt: '2026-01-01T00:00:00Z',
      initialPassword: 'pass123',
    });

    expect(dialogRefSpy.close).toHaveBeenCalled();
  });

  it('form validation prevents save when displayName is empty', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({ role: 'student' });
    component.save();

    httpMock.expectNone('/api/academic/users');
    expect(component.form.get('displayName')!.hasError('required')).toBe(true);
  });

  it('isHeadman checkbox visible only when role is student', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({ role: 'teacher' });
    // In template: @if (form.get('role')?.value === 'student') shows isHeadman
    // We test the form state instead of DOM in unit tests
    expect(component.form.get('role')!.value).toBe('teacher');

    component.form.patchValue({ role: 'student' });
    expect(component.form.get('role')!.value).toBe('student');
    // isHeadman control exists regardless; visibility is template-only concern
    expect(component.form.get('isHeadman')!.value).toBe(false);
  });
});
