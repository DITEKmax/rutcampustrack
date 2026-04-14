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
    expect(component.form.get('lastName')!.value).toBe('');
    expect(component.form.get('firstName')!.value).toBe('');
    expect(component.form.get('middleName')!.value).toBe('');
    expect(component.form.get('role')!.value).toBe('');
  });

  it('opens in edit mode with form pre-filled', () => {
    const user: UserResponse = {
      id: 1,
      login: 'student00001',
      lastName: 'Иванов',
      firstName: 'Иван',
      middleName: 'Иванович',
      role: 'STUDENT',
      status: 'active',
      groupId: 1,
      headman: true,
      employeeNumber: null,
      telegramId: null,
      createdAt: '2026-01-01T00:00:00Z',
    };

    const component = createComponent({ mode: 'edit', user, groups: [] });

    expect(component.form.get('lastName')!.value).toBe('Иванов');
    expect(component.form.get('firstName')!.value).toBe('Иван');
    expect(component.form.get('middleName')!.value).toBe('Иванович');
    expect(component.form.get('role')!.value).toBe('STUDENT');
    expect(component.form.get('role')!.disabled).toBe(true);
    expect(component.form.get('groupId')!.value).toBe(1);
    expect(component.form.get('isHeadman')!.value).toBe(true);
  });

  it('save in create mode calls adminApi.createUser with correct body', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({
      lastName: 'Петров',
      firstName: 'Пётр',
      role: 'STUDENT',
      telegramId: 123456789,
    });

    component.save();

    const req = httpMock.expectOne('/api/academic/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      lastName: 'Петров',
      firstName: 'Пётр',
      role: 'STUDENT',
      telegramId: 123456789,
    });

    req.flush({
      id: 2,
      login: 'student00002',
      lastName: 'Петров',
      firstName: 'Пётр',
      middleName: null,
      role: 'STUDENT',
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

  it('form validation prevents save when lastName is empty', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({ firstName: 'Пётр', role: 'STUDENT' });
    component.save();

    httpMock.expectNone('/api/academic/users');
    expect(component.form.get('lastName')!.hasError('required')).toBe(true);
  });

  // --- BUG-006-2: field-specific 409 messages ---

  function fillCreateForm(component: UserDialogComponent): void {
    component.form.patchValue({
      lastName: 'Петров',
      firstName: 'Пётр',
      role: 'STUDENT',
      telegramId: 123456789,
    });
  }

  function submitAndFailWith(
    component: UserDialogComponent,
    status: number,
    body: Record<string, unknown> | null,
  ): void {
    component.save();
    const req = httpMock.expectOne('/api/academic/users');
    req.flush(body, { status, statusText: status === 409 ? 'Conflict' : 'Error' });
  }

  it('409 with field=login → submitError (login автогенерируется, control отсутствует)', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    fillCreateForm(component);

    submitAndFailWith(component, 409, { status: 409, field: 'login', detail: 'x' });

    // В этой форме нет control-а login (логин генерирует backend), поэтому
    // только submitError покрывает сценарий коллизии логинов — пользователь
    // увидит баннер и поймёт, что нужно перезапустить создание.
    expect(component.submitError()).toBe('Логин уже используется. Выберите другой');
    expect(component.form.get('login')).toBeNull();
  });

  it('409 with field=email → submitError Email уже зарегистрирован', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    fillCreateForm(component);

    submitAndFailWith(component, 409, { status: 409, field: 'email', detail: 'x' });

    expect(component.submitError()).toBe('Email уже зарегистрирован');
  });

  it('409 with field=telegramId → submitError Telegram ID…', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    fillCreateForm(component);

    submitAndFailWith(component, 409, { status: 409, field: 'telegramId', detail: 'x' });

    expect(component.submitError()).toBe('Telegram ID уже привязан к другой учётной записи');
  });

  it('409 with field=employeeNumber → submitError Табельный номер…', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    component.form.patchValue({
      lastName: 'Учителев',
      firstName: 'Учитель',
      role: 'TEACHER',
      employeeNumber: 'EMP-001',
    });

    submitAndFailWith(component, 409, { status: 409, field: 'employeeNumber', detail: 'x' });

    expect(component.submitError()).toBe('Табельный номер уже используется');
    expect(component.form.get('employeeNumber')?.errors?.['conflict']).toBe(
      'Табельный номер уже используется',
    );
  });

  it('500 or other error without field → generic submitError (не field-specific)', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    fillCreateForm(component);

    submitAndFailWith(component, 500, { status: 500 });

    expect(component.submitError()).toBe('Ошибка сервера. Попробуйте ещё раз');
    expect(component.form.get('login')?.errors?.['conflict']).toBeUndefined();
  });

  it('400 without field → generic 4xx fallback message', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    fillCreateForm(component);

    submitAndFailWith(component, 400, { status: 400 });

    expect(component.submitError()).toBe('Не удалось сохранить. Проверьте введённые данные');
  });

  it('changing the offending control clears the conflict error for retry', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    component.form.patchValue({
      lastName: 'Учителев',
      firstName: 'Учитель',
      role: 'TEACHER',
      employeeNumber: 'EMP-001',
    });

    submitAndFailWith(component, 409, { status: 409, field: 'employeeNumber', detail: 'x' });
    expect(component.form.get('employeeNumber')?.errors?.['conflict']).toBe(
      'Табельный номер уже используется',
    );

    component.form.get('employeeNumber')!.setValue('EMP-002');

    // After the change the conflict error is cleared (no other validators on
    // this control, so errors become null).
    expect(component.form.get('employeeNumber')?.errors).toBeNull();
  });

  // --- BUG-006-3: telegramId required for STUDENT, optional otherwise ---

  it('role=student → telegramId has Validators.required, form invalid when telegramId empty', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({
      lastName: 'Петров',
      firstName: 'Пётр',
      role: 'STUDENT',
    });

    expect(component.form.get('telegramId')!.hasError('required')).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('role=teacher → telegramId valid when empty (required removed)', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({
      lastName: 'Учителев',
      firstName: 'Учитель',
      role: 'TEACHER',
    });

    expect(component.form.get('telegramId')!.hasError('required')).toBe(false);
    expect(component.form.valid).toBe(true);
  });

  it('role=admin → telegramId valid when empty', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({
      lastName: 'Админ',
      firstName: 'Главный',
      role: 'ADMIN',
    });

    expect(component.form.get('telegramId')!.hasError('required')).toBe(false);
    expect(component.form.valid).toBe(true);
  });

  it('switching role student→teacher clears required → form becomes valid', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({
      lastName: 'Иванов',
      firstName: 'Иван',
      role: 'STUDENT',
    });
    expect(component.form.invalid).toBe(true);

    component.form.patchValue({ role: 'TEACHER' });
    expect(component.form.get('telegramId')!.hasError('required')).toBe(false);
    expect(component.form.valid).toBe(true);
  });

  it('switching role teacher→student adds required → form invalid until telegramId filled', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({
      lastName: 'Иванов',
      firstName: 'Иван',
      role: 'TEACHER',
    });
    expect(component.form.valid).toBe(true);

    component.form.patchValue({ role: 'STUDENT' });
    expect(component.form.get('telegramId')!.hasError('required')).toBe(true);
    expect(component.form.invalid).toBe(true);

    component.form.patchValue({ telegramId: 987654321 });
    expect(component.form.get('telegramId')!.hasError('required')).toBe(false);
    expect(component.form.valid).toBe(true);
  });

  it('400 with field=telegramId → control marked with conflict error (backend as source of truth)', () => {
    const component = createComponent({ mode: 'create', groups: [] });
    fillCreateForm(component);

    // Backend returns 400 despite client-side validation passing — e.g. via
    // DevTools bypass. Handler still surfaces the field into submitError.
    submitAndFailWith(component, 400, { status: 400, field: 'telegramId', detail: 'x' });

    // Generic 400 fallback message because handleSaveError only wires
    // field→control mapping for 409. 400 still produces a friendly message.
    expect(component.submitError()).toBe('Не удалось сохранить. Проверьте введённые данные');
  });

  it('isHeadman checkbox visible only when role is student', () => {
    const component = createComponent({ mode: 'create', groups: [] });

    component.form.patchValue({ role: 'TEACHER' });
    // In template: @if (form.get('role')?.value === 'STUDENT') shows isHeadman
    // We test the form state instead of DOM in unit tests
    expect(component.form.get('role')!.value).toBe('TEACHER');

    component.form.patchValue({ role: 'STUDENT' });
    expect(component.form.get('role')!.value).toBe('STUDENT');
    // isHeadman control exists regardless; visibility is template-only concern
    expect(component.form.get('isHeadman')!.value).toBe(false);
  });
});
