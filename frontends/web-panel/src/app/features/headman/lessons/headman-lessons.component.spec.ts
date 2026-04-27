import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { HeadmanLessonsComponent } from './headman-lessons.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmWithReasonDialogComponent } from '../../../shared/confirm-with-reason-dialog/confirm-with-reason-dialog.component';

const LESSONS = [
  {
    id: 100, groupId: 5, subjectId: 1, date: '2026-04-15',
    status: 'PLANNED', lessonNumber: 1, startTime: '08:30', endTime: '09:50',
    weekType: 'ALL', room: '301',
  },
  {
    id: 101, groupId: 5, subjectId: 2, date: '2026-04-15',
    status: 'CANCELLED', lessonNumber: 2, startTime: '10:05', endTime: '11:25',
    weekType: 'ALL', room: '302', cancelReason: 'Праздник',
  },
  {
    id: 102, groupId: 5, subjectId: 1, date: '2026-04-16',
    status: 'PLANNED', lessonNumber: 1, startTime: '08:30', endTime: '09:50',
    weekType: 'ALL',
  },
];
const SUBJECTS = [{ id: 1, name: 'Алгебра' }, { id: 2, name: 'Физика' }];

function makeApi(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    getGroupLessons: vi.fn(() => of({ _embedded: { lessonResponseList: LESSONS } })),
    // C6: defensive default — компонент дёргает forkJoin([getGroupLessons, getOneOffLessons])
    // в любом kind кроме 'cancelled'. Полное покрытие one-off merge — в C10.
    getOneOffLessons: vi.fn(() => of({ _embedded: { oneOffLessonResponseList: [] } })),
    // C4: defensive default — листинг семестров вызывается только при period='semester'.
    // По default'у period='month' → не вызывается, но мок защищает от регрессий.
    listSemesters: vi.fn(() => of({ _embedded: { semesterResponseList: [] } })),
    listSubjects: vi.fn(() => of({ _embedded: { subjectResponseList: SUBJECTS } })),
    cancelLesson: vi.fn(() => of(null)),
    restoreLesson: vi.fn(() => of(null)),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function setup(apiOverrides: Partial<HeadmanApiService> = {}): {
  fixture: ComponentFixture<HeadmanLessonsComponent>;
  component: HeadmanLessonsComponent;
  api: HeadmanApiService;
  snackBar: { open: ReturnType<typeof vi.fn> };
  dialog: { open: ReturnType<typeof vi.fn> };
} {
  const api = makeApi(apiOverrides);
  const auth = {
    currentUser: () => ({ id: 1, role: 'STUDENT', isHeadman: true, groupId: 5 }),
  } as unknown as AuthService;
  const snackBar = { open: vi.fn() };
  // Dialog-мок различает ConfirmWithReasonDialog (возвращает reason string)
  // и ConfirmDialog (возвращает true/false для restore-потока).
  const dialog = {
    open: vi.fn((cmp: unknown) => ({
      afterClosed: () =>
        of(cmp === ConfirmWithReasonDialogComponent ? 'Болезнь' : true),
    })),
  };
  TestBed.configureTestingModule({
    imports: [HeadmanLessonsComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: AuthService, useValue: auth },
      { provide: MatSnackBar, useValue: snackBar },
      { provide: MatDialog, useValue: dialog },
    ],
  });
  const fixture = TestBed.createComponent(HeadmanLessonsComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, snackBar, dialog };
}

describe('HeadmanLessonsComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('loads lessons and groups them by date', () => {
    const { component, api } = setup();
    expect(api.getGroupLessons).toHaveBeenCalled();
    expect(component.lessons().length).toBe(3);
    const groups = component.dayGroups();
    expect(groups.length).toBe(2);
    expect(groups[0].date).toBe('2026-04-15');
    expect(groups[0].lessons.length).toBe(2);
    // sorted by lessonNumber
    expect(groups[0].lessons[0].lessonNumber).toBe(1);
  });

  it('isCancelled / isClosed helpers correctly read status case-insensitively', () => {
    const { component } = setup();
    expect(component.isCancelled({ status: 'cancelled' } as any)).toBe(true);
    expect(component.isCancelled({ status: 'CANCELLED' } as any)).toBe(true);
    expect(component.isCancelled({ status: 'PLANNED' } as any)).toBe(false);
    expect(component.isClosed({ status: 'closed' } as any)).toBe(true);
  });

  it('onCancel does NOT call API when dialog returns null (cancel)', () => {
    const { component, api } = setup();
    // Override global dialog mock для этого теста — вернуть null.
    (component as unknown as { dialog: { open: ReturnType<typeof vi.fn> } }).dialog.open =
      vi.fn(() => ({ afterClosed: () => of(null) }));
    component.onCancel(LESSONS[0] as any);
    expect(api.cancelLesson).not.toHaveBeenCalled();
  });

  it('onCancel calls cancelLesson with dialog reason и обновляет local статус optimistically', () => {
    const { component, api, dialog } = setup();
    component.onCancel(LESSONS[0] as any);
    expect(dialog.open).toHaveBeenCalledWith(
      ConfirmWithReasonDialogComponent,
      expect.objectContaining({ data: expect.objectContaining({ destructive: true }) }),
    );
    expect(api.cancelLesson).toHaveBeenCalledWith(100, 'Болезнь');
    const updated = component.lessons().find(l => l.id === 100);
    expect(updated?.status).toBe('CANCELLED');
    expect(updated?.cancelReason).toBe('Болезнь');
  });

  it('onRestore confirms via dialog and calls restoreLesson, sets status PLANNED', () => {
    const { component, api, dialog } = setup();
    component.onRestore(LESSONS[1] as any);
    expect(dialog.open).toHaveBeenCalled();
    expect(api.restoreLesson).toHaveBeenCalledWith(101);
    const updated = component.lessons().find(l => l.id === 101);
    expect(updated?.status).toBe('PLANNED');
  });

  it('shows snackBar with 403 message when cancel returns 403', () => {
    const { component, snackBar } = setup({
      cancelLesson: vi.fn(() => throwError(() => ({ status: 403 }))),
    });
    component.onCancel(LESSONS[0] as any);
    const arg = snackBar.open.mock.calls[snackBar.open.mock.calls.length - 1][0];
    expect(arg).toContain('прав');
  });

  it('error from getGroupLessons sets error signal', () => {
    const { component } = setup({
      getGroupLessons: vi.fn(() => throwError(() => new Error('boom'))),
    });
    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBe(false);
  });
});

describe('HeadmanLessonsComponent — period chip (DEV-C4)', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('default period = "month"', () => {
    const { component } = setup();
    expect(component.period()).toBe('month');
    expect(component.pageTitle()).toBe('Пары на месяц');
  });

  it('switching to "today" reloads with single-day range', () => {
    const { component, api } = setup();
    (api.getGroupLessons as any).mockClear();
    component.onPeriodChange('today');
    expect(component.period()).toBe('today');
    expect(api.getGroupLessons).toHaveBeenCalledTimes(1);
    const [, dateFrom, dateTo] = (api.getGroupLessons as any).mock.calls[0];
    expect(dateFrom).toBe(dateTo); // single day
    expect(component.pageTitle()).toBe('Пары на сегодня');
  });

  it('switching to "semester" calls listSemesters, then refetch with semester range', () => {
    const SEMESTER = { id: 7, name: 'Весенний 2025/2026', startDate: '2026-02-09', endDate: '2026-06-14', active: true };
    const { component, api } = setup({
      listSemesters: vi.fn(() => of({ _embedded: { semesterResponseList: [SEMESTER] } })),
    });
    (api.getGroupLessons as any).mockClear();
    component.onPeriodChange('semester');
    expect(api.listSemesters).toHaveBeenCalled();
    expect(component.activeSemester()?.startDate).toBe('2026-02-09');
    const [, from, to] = (api.getGroupLessons as any).mock.calls[0];
    expect(from).toBe('2026-02-09');
    expect(to).toBe('2026-06-14');
    expect(component.pageTitle()).toBe('Пары на семестр (Весенний 2025/2026)');
  });

  it('switching to "semester" without active semester sets error', () => {
    const { component } = setup({
      listSemesters: vi.fn(() => of({ _embedded: { semesterResponseList: [] } })),
    });
    component.onPeriodChange('semester');
    expect(component.error()).toContain('Активный семестр');
  });

  it('onPeriodChange ignores same value', () => {
    const { component, api } = setup();
    (api.getGroupLessons as any).mockClear();
    component.onPeriodChange('month'); // same as default
    expect(api.getGroupLessons).not.toHaveBeenCalled();
  });
});

describe('HeadmanLessonsComponent — kind chip (DEV-C5/C6)', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('default kind = "all" → status filter includes cancelled, fetches one-off', () => {
    const { api } = setup();
    expect(api.getGroupLessons).toHaveBeenCalledWith(5, expect.any(String), expect.any(String), 'planned,active,closed,cancelled');
    expect(api.getOneOffLessons).toHaveBeenCalled();
  });

  it('kind="cancelled" → status filter only cancelled, one-off NOT fetched', () => {
    const { component, api } = setup();
    (api.getGroupLessons as any).mockClear();
    (api.getOneOffLessons as any).mockClear();
    component.onKindChange('cancelled');
    expect(api.getGroupLessons).toHaveBeenCalledWith(5, expect.any(String), expect.any(String), 'cancelled');
    expect(api.getOneOffLessons).not.toHaveBeenCalled();
  });

  it('kind="oneoff" → only one-off rendered, template skipped', () => {
    const ONE_OFF = { id: 999, groupId: 5, subjectId: 1, semesterId: 7, date: '2026-04-15', lessonNumber: 3, classroom: 'A1' };
    const { component } = setup({
      getOneOffLessons: vi.fn(() => of({ _embedded: { oneOffLessonResponseList: [ONE_OFF] } })),
    });
    component.onKindChange('oneoff');
    const all = component.lessons();
    expect(all.every(l => l.source === 'oneoff')).toBe(true);
    expect(all.length).toBe(1);
    expect(all[0].id).toBe(999);
  });

  it('one-off in same slot as cancelled template = replacement=true', () => {
    // CANCELLED template на (date=2026-04-15, lessonNumber=2) уже в LESSONS[1].
    const REPLACEMENT_OO = {
      id: 555, groupId: 5, subjectId: 1, semesterId: 7,
      date: '2026-04-15', lessonNumber: 2, classroom: 'A99',
    };
    const { component } = setup({
      getOneOffLessons: vi.fn(() => of({ _embedded: { oneOffLessonResponseList: [REPLACEMENT_OO] } })),
    });
    const oo = component.lessons().find(l => l.id === 555);
    expect(oo?.source).toBe('oneoff');
    expect(oo?.replacement).toBe(true);
  });

  it('one-off in non-conflicting slot = replacement=undefined', () => {
    const STANDALONE_OO = {
      id: 777, groupId: 5, subjectId: 1, semesterId: 7,
      date: '2026-04-20', lessonNumber: 5, classroom: 'B5',
    };
    const { component } = setup({
      getOneOffLessons: vi.fn(() => of({ _embedded: { oneOffLessonResponseList: [STANDALONE_OO] } })),
    });
    const oo = component.lessons().find(l => l.id === 777);
    expect(oo?.replacement).toBeFalsy();
  });
});

describe('HeadmanLessonsComponent — paginator (DEV-C8)', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('default pageSize = 50, pageIndex = 0', () => {
    const { component } = setup();
    expect(component.pageSize()).toBe(50);
    expect(component.pageIndex()).toBe(0);
  });

  it('onPage updates pageIndex and pageSize', () => {
    const { component } = setup();
    component.onPage({ pageIndex: 2, pageSize: 100, length: 200 } as any);
    expect(component.pageIndex()).toBe(2);
    expect(component.pageSize()).toBe(100);
  });

  it('changing kind chip resets pageIndex to 0', () => {
    const { component } = setup();
    component.onPage({ pageIndex: 3, pageSize: 50, length: 200 } as any);
    expect(component.pageIndex()).toBe(3);
    component.onKindChange('cancelled');
    expect(component.pageIndex()).toBe(0);
  });

  it('pagedDayGroups slices correctly', () => {
    const { component } = setup();
    // У нас 2 day-groups (2026-04-15, 2026-04-16). С pageSize=1 на странице 0 — первая, на странице 1 — вторая.
    component.onPage({ pageIndex: 0, pageSize: 1, length: 2 } as any);
    expect(component.pagedDayGroups().length).toBe(1);
    expect(component.pagedDayGroups()[0].date).toBe('2026-04-15');
    component.onPage({ pageIndex: 1, pageSize: 1, length: 2 } as any);
    expect(component.pagedDayGroups()[0].date).toBe('2026-04-16');
  });
});

describe('HeadmanLessonsComponent — cancellation audit (DEV-C9)', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('batch-резолвит имена авторов отмен после загрузки', () => {
    const CANCELLED_LESSONS = [
      { ...LESSONS[1], cancelledBy: 42, cancelledAt: '2026-04-23T11:32:00Z' },
      { ...LESSONS[0], id: 200, status: 'CANCELLED', cancelReason: 'X', cancelledBy: 42, cancelledAt: '2026-04-23T12:00:00Z' },
      { ...LESSONS[2], id: 201, status: 'CANCELLED', cancelReason: 'Y', cancelledBy: 99, cancelledAt: '2026-04-22T08:00:00Z' },
    ];
    const getUsersByIds = vi.fn(() => of({ _embedded: { userSummaryResponseList: [
      { id: 42, fullName: 'Иванов Иван Иванович' },
      { id: 99, fullName: 'Петров Пётр' },
    ] } }));
    const { component } = setup({
      getGroupLessons: vi.fn(() => of({ _embedded: { lessonResponseList: CANCELLED_LESSONS } })),
      getUsersByIds: getUsersByIds as any,
    });
    // Должен быть вызван ровно один раз, с уникальными IDs (42, 99) — без дубля 42.
    expect(getUsersByIds).toHaveBeenCalledTimes(1);
    const calledIds = getUsersByIds.mock.calls[0][0] as number[];
    expect(calledIds.sort()).toEqual([42, 99]);
    expect(component.userNames().get(42)).toBe('Иванов Иван Иванович');
  });

  it('cancellerDisplay форматирует «Отменена ... — Имя» с резолвом', () => {
    const lesson = {
      ...LESSONS[1],
      cancelledBy: 42,
      cancelledAt: '2026-04-23T11:32:00Z',
    } as any;
    const { component } = setup({
      getGroupLessons: vi.fn(() => of({ _embedded: { lessonResponseList: [lesson] } })),
      getUsersByIds: vi.fn(() => of({ _embedded: { userSummaryResponseList: [{ id: 42, fullName: 'Сидоров С.С.' }] } })) as any,
    });
    const display = component.cancellerDisplay(lesson);
    expect(display).toContain('Отменена');
    expect(display).toContain('Сидоров С.С.');
    expect(display).toContain('апреля');
  });

  it('cancellerDisplay показывает «вами» для cancelledBy === currentUser.id', () => {
    const lesson = {
      ...LESSONS[1],
      cancelledBy: 1, // совпадает с currentUser.id из setup()
      cancelledAt: '2026-04-23T11:32:00Z',
    } as any;
    const { component } = setup({
      getGroupLessons: vi.fn(() => of({ _embedded: { lessonResponseList: [lesson] } })),
    });
    expect(component.cancellerDisplay(lesson)).toContain('вами');
  });

  it('cancellerDisplay returns null если нет cancelledAt', () => {
    const { component } = setup();
    expect(component.cancellerDisplay({ status: 'CANCELLED', cancelledAt: null } as any)).toBeNull();
  });

  it('тихо degrades если getUsersByIds падает', () => {
    const lesson = { ...LESSONS[1], cancelledBy: 42, cancelledAt: '2026-04-23T11:32:00Z' };
    const { component } = setup({
      getGroupLessons: vi.fn(() => of({ _embedded: { lessonResponseList: [lesson] } })),
      getUsersByIds: vi.fn(() => throwError(() => new Error('500'))) as any,
    });
    // Никаких error / snackbar — данные lessons всё равно загрузились.
    expect(component.error()).toBeNull();
    expect(component.lessons().length).toBe(1);
  });
});
