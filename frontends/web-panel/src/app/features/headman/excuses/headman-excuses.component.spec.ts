import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MatSnackBar } from '@angular/material/snack-bar';

import { HeadmanExcusesComponent } from './headman-excuses.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import type { ExcuseTicket } from './excuse.types';

const MOCK_PENDING: ExcuseTicket = {
  id: 'mongo-1',
  studentId: 101,
  groupId: 42,
  studentName: 'Иванов Иван',
  lessonIds: [1001, 1002],
  excuseType: 'illness',
  comment: 'Простуда',
  status: 'submitted',
  decisionBy: null,
  decisionComment: null,
  decisionAt: null,
  createdAt: '2026-04-14T10:00:00Z',
  updatedAt: '2026-04-14T10:00:00Z',
};

const MOCK_APPROVED: ExcuseTicket = {
  ...MOCK_PENDING,
  id: 'mongo-2',
  studentId: 102,
  studentName: 'Петров Пётр',
  status: 'approved',
  decisionBy: 1,
  decisionComment: null,
  decisionAt: '2026-04-14T11:00:00Z',
};

function makeApi(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    getGroupExcuses: vi.fn(() => of([MOCK_PENDING, MOCK_APPROVED])),
    approveExcuse: vi.fn(() => of(undefined as unknown as void)),
    rejectExcuse: vi.fn(() => of(undefined as unknown as void)),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function makeAuth(groupId: number | null = 42): AuthService {
  return {
    currentUser: signal({ id: 1, role: 'STUDENT', isHeadman: true, groupId }),
  } as unknown as AuthService;
}

describe('HeadmanExcusesComponent', () => {
  let fixture: ComponentFixture<HeadmanExcusesComponent>;
  let component: HeadmanExcusesComponent;
  let api: HeadmanApiService;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  function setup(
    apiOverrides: Partial<HeadmanApiService> = {},
    groupId: number | null = 42,
  ) {
    api = makeApi(apiOverrides);
    snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({
      imports: [HeadmanExcusesComponent],
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: HeadmanApiService, useValue: api },
        { provide: AuthService, useValue: makeAuth(groupId) },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    fixture = TestBed.createComponent(HeadmanExcusesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('calls HeadmanApiService.getGroupExcuses with groupId on init (AC-10 load)', () => {
    setup();
    expect(api.getGroupExcuses).toHaveBeenCalledWith(42);
  });

  it('splits tickets into pending (submitted) and resolved sections (D-24)', () => {
    setup();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Иванов Иван');
    expect(compiled.textContent).toContain('Петров Пётр');
    // Pending section heading present
    expect(compiled.textContent).toMatch(/На рассмотрении/);
    // Russian excuse type label present
    expect(compiled.textContent).toContain('Болезнь');
    // Pending tickets expose Approve/Reject buttons; resolved do not
    const buttons = Array.from(compiled.querySelectorAll('button'));
    const approveCount = buttons.filter(b => /Одобрить/.test(b.textContent ?? '')).length;
    expect(approveCount).toBeGreaterThanOrEqual(1);
  });

  it('approve() calls approveExcuse and reloads list (AC-10 approve)', () => {
    setup();
    component.approve('mongo-1');
    expect(api.approveExcuse).toHaveBeenCalledWith('mongo-1', null);
    // loadTickets triggered: initial call + reload
    expect((api.getGroupExcuses as any).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('reject() with empty comment sets validationError and does NOT call API (D-24)', () => {
    setup();
    component.startReject('mongo-1');
    component.rejectComment.set('   ');
    component.confirmReject('mongo-1');
    expect(component.validationError()).toBe('Введите комментарий для отклонения');
    expect(api.rejectExcuse).not.toHaveBeenCalled();
  });

  it('reject() with non-empty comment calls rejectExcuse and reloads (AC-10 reject)', () => {
    setup();
    component.startReject('mongo-1');
    component.rejectComment.set('Нет подтверждающих документов');
    component.confirmReject('mongo-1');
    expect(api.rejectExcuse).toHaveBeenCalledWith('mongo-1', 'Нет подтверждающих документов');
    expect((api.getGroupExcuses as any).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('maps 409 on approve to a friendly message (own ticket / already decided)', () => {
    // Exercise the private error mapper directly — subscribing to throwError()
    // inside zone.js + vitest raises an async unhandled-rejection warning that
    // swallows the synchronous snackBar call; we validate the user-facing
    // contract (friendly message) without the observable wiring.
    setup();
    const err = new HttpErrorResponse({ status: 409, statusText: 'Conflict' });
    const msg = (component as any).errorMessage(err, 'approve') as string;
    expect(msg).toMatch(/решение уже принято|нельзя одобрить|Невозможно/i);
    const rejectMsg = (component as any).errorMessage(err, 'reject') as string;
    expect(rejectMsg).toMatch(/решение уже принято|Невозможно/i);
  });

  it('surfaces friendly load error when getGroupExcuses fails', () => {
    // HeadmanApiService already swallows 403/404 into []; this covers any other
    // unexpected status (500) bubbling to the component.
    const err = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
    setup({
      getGroupExcuses: vi.fn(() => throwError(() => err)) as any,
    });
    // Signal is set synchronously in error handler of subscribe; re-render.
    fixture.detectChanges();
    expect(component.loadError()).toMatch(/Не удалось загрузить|Доступ/i);
  });

  it('skips load and shows empty state when current user has no groupId', () => {
    setup({}, null);
    expect(api.getGroupExcuses).not.toHaveBeenCalled();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toMatch(/Нет заявок|Группа не определена|недоступно/i);
  });
});
