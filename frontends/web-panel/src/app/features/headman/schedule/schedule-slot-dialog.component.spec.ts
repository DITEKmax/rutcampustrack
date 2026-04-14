import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { ScheduleSlotDialogComponent, ScheduleSlotDialogData } from './schedule-slot-dialog.component';
import { HeadmanApiService } from '../shared/headman-api.service';

const SUBJECTS = [
  { id: 100, name: 'Алгебра' },
  { id: 101, name: 'Физика' },
];

function makeApi(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    listSubjects: vi.fn(() => of({ _embedded: { subjectResponseList: SUBJECTS } })),
    createScheduleItem: vi.fn(() => of({ id: 42 })),
    updateScheduleItem: vi.fn(() => of({ id: 42 })),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function setup(
  data: ScheduleSlotDialogData,
  apiOverrides: Partial<HeadmanApiService> = {},
): {
  fixture: ComponentFixture<ScheduleSlotDialogComponent>;
  component: ScheduleSlotDialogComponent;
  api: HeadmanApiService;
  dialogRef: { close: ReturnType<typeof vi.fn> };
} {
  const api = makeApi(apiOverrides);
  const dialogRef = { close: vi.fn() };
  const snackBar = { open: vi.fn() };
  TestBed.configureTestingModule({
    imports: [ScheduleSlotDialogComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatSnackBar, useValue: snackBar },
    ],
  });
  const fixture = TestBed.createComponent(ScheduleSlotDialogComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, dialogRef };
}

describe('ScheduleSlotDialogComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('creates with subjectId, room, weekType form controls', () => {
    const { component } = setup({ mode: 'create', groupId: 5, semesterId: 1, dayOfWeek: 1, lessonNumber: 2 });
    expect(component.form.get('subjectId')).toBeTruthy();
    expect(component.form.get('room')).toBeTruthy();
    expect(component.form.get('weekType')).toBeTruthy();
    expect(component.subjects.length).toBe(2);
  });

  it('submit in create mode calls createScheduleItem with derived body', () => {
    const { component, api } = setup({
      mode: 'create', groupId: 5, semesterId: 1, dayOfWeek: 1, lessonNumber: 2,
    });
    component.form.patchValue({ subjectId: 100, room: '301', weekType: 'ALL' });
    component.onSubmit();
    expect(api.createScheduleItem).toHaveBeenCalledWith(expect.objectContaining({
      groupId: 5, subjectId: 100, semesterId: 1,
      dayOfWeek: 1, lessonNumber: 2,
      weekType: 'ALL', room: '301',
    }));
  });

  it('submit in edit mode calls updateScheduleItem', () => {
    const { component, api } = setup({
      mode: 'edit', groupId: 5, semesterId: 1,
      item: {
        id: 42, subjectId: 100, dayOfWeek: 1, lessonNumber: 2,
        startTime: '10:40', endTime: '12:10', weekType: 'ALL', room: '301',
      },
    });
    component.form.patchValue({ subjectId: 101, room: '305', weekType: 'ODD' });
    component.onSubmit();
    expect(api.updateScheduleItem).toHaveBeenCalledWith(42, expect.objectContaining({
      subjectId: 101, weekType: 'ODD', room: '305',
    }));
  });

  it('missing subjectId → form invalid, submit does not call API', () => {
    const { component, api } = setup({
      mode: 'create', groupId: 5, semesterId: 1, dayOfWeek: 1, lessonNumber: 2,
    });
    component.form.patchValue({ subjectId: null, room: '', weekType: 'ALL' });
    expect(component.form.valid).toBe(false);
    component.onSubmit();
    expect(api.createScheduleItem).not.toHaveBeenCalled();
  });

  it('403 response sets "Недостаточно прав" apiError', async () => {
    const err = { status: 403 };
    const api = makeApi({ createScheduleItem: vi.fn(() => ({ subscribe: (o: any) => o.error(err) } as any)) });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ScheduleSlotDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: HeadmanApiService, useValue: api },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: MAT_DIALOG_DATA, useValue: { mode: 'create', groupId: 5, semesterId: 1, dayOfWeek: 1, lessonNumber: 2 } },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(ScheduleSlotDialogComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    c.form.patchValue({ subjectId: 100, room: '301', weekType: 'ALL' });
    c.onSubmit();
    expect(c.apiError).toContain('Недостаточно прав');
  });
});
