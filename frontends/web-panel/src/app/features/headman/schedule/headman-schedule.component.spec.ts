import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, Subject, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { HeadmanScheduleComponent } from './headman-schedule.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ScheduleSlotDialogComponent } from './schedule-slot-dialog.component';
import { OneOffDialogComponent } from './one-off-dialog.component';
import type { AcademicWeekPolicyResponse } from '../shared/academic-week-policy.types';

const WEEK_POLICY: AcademicWeekPolicyResponse = {
  weekStart: '2026-10-05', weekEnd: '2026-10-11', isoWeek: 41,
  weekType: 'EVEN', parityNumber: 2, label: '2',
  weekTypes: [
    { weekType: 'ALL', parityNumber: null, label: 'Каждую неделю' },
    { weekType: 'ODD', parityNumber: 1, label: '1' },
    { weekType: 'EVEN', parityNumber: 2, label: '2' },
  ],
};

const ACTIVE_SEMESTER = { id: 1, name: 'Spring 2026', active: true };
const INACTIVE_SEMESTER = { id: 2, name: 'Fall 2025', active: false };
const SCHEDULE_ITEM = {
  id: 10,
  groupId: 5,
  subjectId: 100,
  semesterId: 1,
  dayOfWeek: 1,
  lessonNumber: 2,
  startTime: '10:40',
  endTime: '12:10',
  weekType: 'ALL',
  room: '301',
  active: true,
};
const SUBJECT = { id: 100, name: 'Алгебра', type: 'LECTURE' };

function makeApi(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    getAcademicWeekPolicy: vi.fn(() => of(WEEK_POLICY)),
    listSemesters: vi.fn(() => of({ _embedded: { semesterResponseList: [ACTIVE_SEMESTER, INACTIVE_SEMESTER] } })),
    getGroupScheduleItems: vi.fn(() => of({ _embedded: { scheduleItemResponseList: [SCHEDULE_ITEM] } })),
    listSubjects: vi.fn(() => of({ _embedded: { subjectResponseList: [SUBJECT] } })),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function makeAuth(): AuthService {
  return {
    currentUser: () => ({ id: 1, role: 'STUDENT', isHeadman: true, groupId: 5 }),
  } as unknown as AuthService;
}

function setup(apiOverrides: Partial<HeadmanApiService> = {}): {
  fixture: ComponentFixture<HeadmanScheduleComponent>;
  component: HeadmanScheduleComponent;
  api: HeadmanApiService;
  dialog: { open: ReturnType<typeof vi.fn> };
} {
  const api = makeApi(apiOverrides);
  const auth = makeAuth();
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(false) } as unknown as MatDialogRef<unknown>)) };
  const snackBar = { open: vi.fn() };
  TestBed.configureTestingModule({
    imports: [HeadmanScheduleComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: AuthService, useValue: auth },
      { provide: MatDialog, useValue: dialog },
      { provide: MatSnackBar, useValue: snackBar },
    ],
  });
  const fixture = TestBed.createComponent(HeadmanScheduleComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, dialog };
}

describe('HeadmanScheduleComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());
  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });

  it('policy failure leaves parity unavailable without a local calculation', () => {
    const { component, dialog } = setup({ getAcademicWeekPolicy: vi.fn(() => throwError(() => new Error('unavailable'))) });
    expect(component.currentAcademicWeek()).toBeNull();
    expect(component.weekPolicy()).toBeNull();
    expect(component.weekChip('ODD')).toBe('Тип недели недоступен');
    component.onCellClick(0, 2);
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('refreshes at Moscow Sunday midnight and removes date watchers on destroy', () => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] });
    vi.setSystemTime(new Date('2026-10-11T20:59:59Z'));
    const monday = { ...WEEK_POLICY, weekStart: '2026-10-12', weekEnd: '2026-10-18', isoWeek: 42,
      weekType: 'ODD' as const, parityNumber: 1 as const, label: '1' };
    const getPolicy = vi.fn().mockReturnValueOnce(of(WEEK_POLICY)).mockReturnValueOnce(of(monday));
    const { component, fixture } = setup({ getAcademicWeekPolicy: getPolicy });
    expect(getPolicy).toHaveBeenCalledWith('2026-10-11');
    expect(component.currentAcademicWeek()).toBe(2);
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(getPolicy).toHaveBeenLastCalledWith('2026-10-12');
    expect(component.currentAcademicWeek()).toBe(1);
    expect(fixture.nativeElement.querySelector('.current-week-banner').textContent).toContain('1-я');
    window.dispatchEvent(new Event('focus'));
    document.dispatchEvent(new Event('visibilitychange'));
    expect(getPolicy).toHaveBeenCalledTimes(2);
    fixture.destroy();
    vi.advanceTimersByTime(24 * 60 * 60 * 1000);
    window.dispatchEvent(new Event('focus'));
    document.dispatchEvent(new Event('visibilitychange'));
    expect(getPolicy).toHaveBeenCalledTimes(2);
  });

  it('wake-up replaces a pending prior-day request and never accepts its late response', () => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] });
    vi.setSystemTime(new Date('2026-10-11T20:59:00Z'));
    const sunday = new Subject<AcademicWeekPolicyResponse>();
    const monday = new Subject<AcademicWeekPolicyResponse>();
    const getPolicy = vi.fn().mockReturnValueOnce(sunday).mockReturnValueOnce(monday);
    const { component } = setup({ getAcademicWeekPolicy: getPolicy });
    vi.setSystemTime(new Date('2026-10-11T21:05:00Z'));
    window.dispatchEvent(new Event('focus'));
    expect(getPolicy).toHaveBeenLastCalledWith('2026-10-12');
    expect(sunday.observed).toBe(false);
    monday.next({ ...WEEK_POLICY, weekType: 'ODD', parityNumber: 1, label: '1' });
    sunday.next(WEEK_POLICY);
    expect(component.currentAcademicWeek()).toBe(1);
  });

  it('opens the form only after delayed metadata arrives with the actual catalog', () => {
    const response = new Subject<AcademicWeekPolicyResponse>();
    const { component, dialog } = setup({ getAcademicWeekPolicy: vi.fn(() => response) });
    component.onCellClick(0, 2);
    expect(dialog.open).not.toHaveBeenCalled();
    response.next(WEEK_POLICY);
    component.onCellClick(0, 2);
    expect(dialog.open).toHaveBeenCalledWith(ScheduleSlotDialogComponent,
      expect.objectContaining({ data: expect.objectContaining({ weekTypes: WEEK_POLICY.weekTypes }) }));
  });


  it('loadSchedule calls getGroupScheduleItems with (groupId, activeSemesterId) and populates matrix', () => {
    const { component, api } = setup();
    expect(api.listSemesters).toHaveBeenCalled();
    expect(api.getGroupScheduleItems).toHaveBeenCalledWith(5, 1);
    expect(component.items().length).toBe(1);
    // Ячейка Пн (dayIdx=0) / 2-й слот содержит SCHEDULE_ITEM
    expect(component.cellAt(0, 2)?.id).toBe(10);
    expect(component.cellAt(0, 1)).toBeNull();
  });

  it('clicking an occupied cell opens ScheduleSlotDialogComponent in edit mode', () => {
    const { component, dialog } = setup();
    component.onCellClick(0, 2);
    expect(dialog.open).toHaveBeenCalledWith(
      ScheduleSlotDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({ mode: 'edit', groupId: 5, semesterId: 1 }),
      }),
    );
  });

  it('clicking an empty cell opens ScheduleSlotDialogComponent in create mode with dayOfWeek/lessonNumber', () => {
    const { component, dialog } = setup();
    component.onCellClick(2, 5); // Ср, 5-й слот
    expect(dialog.open).toHaveBeenCalledWith(
      ScheduleSlotDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          mode: 'create', groupId: 5, semesterId: 1, dayOfWeek: 3, lessonNumber: 5,
        }),
      }),
    );
  });

  it('openOneOffDialog opens OneOffDialogComponent with groupId data', () => {
    const { component, dialog } = setup();
    component.openOneOffDialog();
    expect(dialog.open).toHaveBeenCalledWith(
      OneOffDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({ groupId: 5 }),
      }),
    );
  });

  it('shows empty state when no active semester', () => {
    const { component } = setup({
      listSemesters: vi.fn(() => of({ _embedded: { semesterResponseList: [INACTIVE_SEMESTER] } })),
    });
    expect(component.semesterId()).toBeNull();
  });
});
