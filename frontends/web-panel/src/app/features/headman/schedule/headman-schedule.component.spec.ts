import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { HeadmanScheduleComponent } from './headman-schedule.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ScheduleSlotDialogComponent } from './schedule-slot-dialog.component';
import { OneOffDialogComponent } from './one-off-dialog.component';

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

  it('creates without errors (smoke test)', () => {
    const { component } = setup();
    expect(component).toBeTruthy();
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
