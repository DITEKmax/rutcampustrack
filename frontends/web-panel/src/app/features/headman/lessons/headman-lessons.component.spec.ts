import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { HeadmanLessonsComponent } from './headman-lessons.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';

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
} {
  const api = makeApi(apiOverrides);
  const auth = {
    currentUser: () => ({ id: 1, role: 'STUDENT', isHeadman: true, groupId: 5 }),
  } as unknown as AuthService;
  const snackBar = { open: vi.fn() };
  TestBed.configureTestingModule({
    imports: [HeadmanLessonsComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: AuthService, useValue: auth },
      { provide: MatSnackBar, useValue: snackBar },
    ],
  });
  const fixture = TestBed.createComponent(HeadmanLessonsComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, snackBar };
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

  it('onCancel does NOT call API when user dismisses prompt', () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue(null);
    const { component, api } = setup();
    component.onCancel(LESSONS[0] as any);
    expect(api.cancelLesson).not.toHaveBeenCalled();
    promptSpy.mockRestore();
  });

  it('onCancel rejects empty/whitespace reason and shows snackBar', () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('   ');
    const { component, api, snackBar } = setup();
    component.onCancel(LESSONS[0] as any);
    expect(api.cancelLesson).not.toHaveBeenCalled();
    expect(snackBar.open).toHaveBeenCalled();
    promptSpy.mockRestore();
  });

  it('onCancel calls cancelLesson and updates local lesson status optimistically', () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('Болезнь');
    const { component, api } = setup();
    component.onCancel(LESSONS[0] as any);
    expect(api.cancelLesson).toHaveBeenCalledWith(100, 'Болезнь');
    const updated = component.lessons().find(l => l.id === 100);
    expect(updated?.status).toBe('CANCELLED');
    expect(updated?.cancelReason).toBe('Болезнь');
    promptSpy.mockRestore();
  });

  it('onRestore confirms and calls restoreLesson, sets status PLANNED', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { component, api } = setup();
    component.onRestore(LESSONS[1] as any);
    expect(api.restoreLesson).toHaveBeenCalledWith(101);
    const updated = component.lessons().find(l => l.id === 101);
    expect(updated?.status).toBe('PLANNED');
    confirmSpy.mockRestore();
  });

  it('shows snackBar with 403 message when cancel returns 403', () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('reason');
    const { component, snackBar } = setup({
      cancelLesson: vi.fn(() => throwError(() => ({ status: 403 }))),
    });
    component.onCancel(LESSONS[0] as any);
    const arg = snackBar.open.mock.calls[snackBar.open.mock.calls.length - 1][0];
    expect(arg).toContain('прав');
    promptSpy.mockRestore();
  });

  it('error from getGroupLessons sets error signal', () => {
    const { component } = setup({
      getGroupLessons: vi.fn(() => throwError(() => new Error('boom'))),
    });
    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBe(false);
  });
});
