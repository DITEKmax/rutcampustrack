import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, Subject } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MatSnackBar } from '@angular/material/snack-bar';

import { getByRole } from '@testing-library/dom';
import userEvent from '@testing-library/user-event';
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
  excuseType: 'ILLNESS',
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
    // enrichLessons() fires after getGroupExcuses resolves; stub empty page
    // so the async call doesn't hit an undefined method.
    getGroupLessons: vi.fn(() => of({ content: [], totalElements: 0 })),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function makeAuth(groupId: number | null = 42): AuthService {
  return {
    currentUser: signal({ id: 1, role: 'STUDENT', isHeadman: true, groupId }),
    // NotificationCenterService reads this on a provideAnAcessToken effect;
    // make it a proper signal so .accessToken() returns a string.
    accessToken: signal('stub-token').asReadonly(),
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
    TestBed.overrideComponent(HeadmanExcusesComponent, { add: { providers: [{ provide: MatSnackBar, useValue: snackBar }] } });
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
    const cards = Array.from(fixture.nativeElement.querySelectorAll('article') as NodeListOf<HTMLElement>);
    const pending = cards.find(card => card.textContent?.includes('Иванов Иван'))!;
    const resolved = cards.find(card => card.textContent?.includes('Петров Пётр'))!;
    expect(getByRole(pending, 'button', { name: 'Одобрить' })).toBeTruthy();
    expect(getByRole(pending, 'button', { name: 'Отклонить' })).toBeTruthy();
    expect(resolved.querySelectorAll('button')).toHaveLength(0);
  });

  it('approve() calls approveExcuse and reloads list (AC-10 approve)', async () => {
    const changed = { ...MOCK_PENDING, status: 'approved' as const };
    const read = vi.fn().mockReturnValueOnce(of([MOCK_PENDING, MOCK_APPROVED])).mockReturnValue(of([changed, MOCK_APPROVED]));
    setup({ getGroupExcuses: read });
    const root = fixture.nativeElement as HTMLElement;
    const user = userEvent.setup();
    await user.click(getByRole(root, 'button', { name: 'Одобрить' }));
    expect(api.approveExcuse).toHaveBeenCalledWith('mongo-1', null);
    fixture.detectChanges();
    expect(component.pendingTickets()).toEqual([]);
    const decided = Array.from(root.querySelectorAll('article')).find(card => card.textContent?.includes('Иванов Иван'))!;
    expect(decided).toBeTruthy();
    expect(decided.querySelectorAll('button')).toHaveLength(0);
  });

  it('reject() with empty comment sets validationError and does NOT call API (D-24)', () => {
    setup();
    component.startReject('mongo-1');
    component.rejectComment.set('   ');
    component.confirmReject('mongo-1');
    expect(component.validationError()).toBe('Введите комментарий для отклонения');
    expect(api.rejectExcuse).not.toHaveBeenCalled();
  });

  it('reject() with non-empty comment calls rejectExcuse and reloads (AC-10 reject)', async () => {
    const changed = { ...MOCK_PENDING, status: 'rejected' as const };
    const read = vi.fn().mockReturnValueOnce(of([MOCK_PENDING, MOCK_APPROVED])).mockReturnValue(of([changed, MOCK_APPROVED]));
    setup({ getGroupExcuses: read });
    const root = fixture.nativeElement as HTMLElement;
    const user = userEvent.setup();
    await user.click(getByRole(root, 'button', { name: 'Отклонить' }));
    fixture.detectChanges();
    await user.type(getByRole(root, 'textbox', { name: 'Комментарий к отклонению' }), 'Нет подтверждающих документов');
    await user.click(getByRole(root, 'button', { name: 'Подтвердить отклонение' }));
    expect(api.rejectExcuse).toHaveBeenCalledWith('mongo-1', 'Нет подтверждающих документов');
    fixture.detectChanges();
    expect(component.pendingTickets()).toEqual([]);
    const decided = Array.from(root.querySelectorAll('article')).find(card => card.textContent?.includes('Иванов Иван'))!;
    expect(decided).toBeTruthy();
    expect(decided.querySelectorAll('button')).toHaveLength(0);
  });

  it.each(['approve', 'reject'] as const)('keeps pending ticket and clears busy after %s conflicts', async action => {
    const decision = new Subject<void>();
    setup({ [action === 'approve' ? 'approveExcuse' : 'rejectExcuse']: vi.fn(() => decision) });
    const root = fixture.nativeElement as HTMLElement;
    const user = userEvent.setup();
    if (action === 'approve') await user.click(getByRole(root, 'button', { name: 'Одобрить' }));
    else {
      await user.click(getByRole(root, 'button', { name: 'Отклонить' }));
      fixture.detectChanges();
      await user.type(getByRole(root, 'textbox', { name: 'Комментарий к отклонению' }), 'synthetic reason');
      await user.click(getByRole(root, 'button', { name: 'Подтвердить отклонение' }));
    }
    fixture.detectChanges();
    expect(component.busyId()).toBe('mongo-1');
    decision.error(new HttpErrorResponse({ status: 409, statusText: 'Conflict' }));
    fixture.detectChanges();
    expect(component.busyId()).toBeNull();
    expect(component.pendingTickets().map(ticket => ticket.id)).toEqual(['mongo-1']);
    expect(component.resolvedTickets().map(ticket => ticket.id)).toEqual(['mongo-2']);
    expect(snackBar.open).toHaveBeenCalledWith(expect.stringMatching(/уже|Невозможно|нельзя/i), 'OK', expect.any(Object));
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
