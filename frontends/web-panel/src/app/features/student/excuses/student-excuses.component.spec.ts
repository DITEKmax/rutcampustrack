import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/angular';
import { StudentExcusesComponent } from './student-excuses.component';
import { ExcuseFormDialogComponent } from './excuse-form-dialog/excuse-form-dialog.component';
import { StudentApiService } from '../shared/student-api.service';
import { MatDialog, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import type { ExcuseTicket } from '../shared/student-schedule.types';

const mockApiService = {
  getExcuseTickets: vi.fn(),
  getStudentRecords: vi.fn(),
  submitExcuse: vi.fn(),
};

const mockDialog = { open: vi.fn() };
const mockSnackBar = { open: vi.fn() };
const mockDialogRef = { close: vi.fn() };

const sampleTicket: ExcuseTicket = {
  id: '650000000000000000000001',
  studentId: 1,
  groupId: 1,
  studentName: 'Иванов Иван',
  lessonIds: [101, 102],
  excuseType: 'illness',
  comment: 'Болел ОРВИ',
  status: 'submitted',
  decisionBy: null,
  decisionComment: null,
  decisionAt: null,
  createdAt: '2026-04-10T08:00:00Z',
  updatedAt: '2026-04-10T08:00:00Z',
};

describe('StudentExcusesComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApiService.getStudentRecords.mockReturnValue(of([]));
  });

  it('показывает empty state при 404 от getExcuseTickets (graceful degradation)', async () => {
    mockApiService.getExcuseTickets.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 }))
    );
    await render(StudentExcusesComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });
    expect(screen.getByText('Нет тикетов о пропуске')).toBeTruthy();
  });

  it('показывает empty state при пустом списке тикетов', async () => {
    mockApiService.getExcuseTickets.mockReturnValue(of([]));
    await render(StudentExcusesComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });
    expect(screen.getByText('Нет тикетов о пропуске')).toBeTruthy();
    // AC-9: вызывается реальный endpoint GET /excuses/me
    expect(mockApiService.getExcuseTickets).toHaveBeenCalled();
  });

  it('кнопка "Подать тикет" присутствует в DOM', async () => {
    mockApiService.getExcuseTickets.mockReturnValue(of([]));
    await render(StudentExcusesComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });
    expect(screen.getByText('Подать тикет')).toBeTruthy();
  });

  it('отображает тикет с русской подписью excuseType и русским статусом (AC-9 list)', async () => {
    mockApiService.getExcuseTickets.mockReturnValue(of([sampleTicket]));
    await render(StudentExcusesComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });
    // excuseType=illness → «Болезнь»
    expect(screen.getByText('Болезнь')).toBeTruthy();
    // status=submitted → «На рассмотрении»
    expect(screen.getByText('На рассмотрении')).toBeTruthy();
  });

  it('statusLabels маппит submitted → "На рассмотрении" (D-21 enum fix)', async () => {
    mockApiService.getExcuseTickets.mockReturnValue(of([]));
    const { fixture } = await render(StudentExcusesComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });
    const cmp = fixture.componentInstance;
    expect(cmp.statusLabels.submitted).toBe('На рассмотрении');
    expect(cmp.statusLabels.approved).toBe('Одобрено');
    expect(cmp.statusLabels.rejected).toBe('Отклонено');
    expect(cmp.statusLabels.draft).toBe('Черновик');
  });
});

describe('ExcuseFormDialogComponent', () => {
  const lessons = [
    {
      lessonId: 101,
      subjectId: 1,
      lessonDate: new Date().toISOString().slice(0, 10),
      lessonNumber: 1,
      status: 'absent',
      symbol: 'н',
      source: 'auto',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function renderDialog() {
    return render(ExcuseFormDialogComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { lessons } },
      ],
    });
  }

  it('рендерит dropdown причины пропуска с 6 ExcuseType значениями (AC-9)', async () => {
    const { fixture } = await renderDialog();
    const cmp = fixture.componentInstance;
    expect(cmp.excuseTypes).toEqual([
      'illness',
      'summons',
      'university_order',
      'exemption',
      'free_attendance',
      'other',
    ]);
    expect(cmp.excuseTypeLabels.illness).toBe('Болезнь');
    expect(cmp.excuseTypeLabels.free_attendance).toBe('Свободное посещение');
    // mat-select trigger should be rendered
    const select = fixture.nativeElement.querySelector('mat-select');
    expect(select).toBeTruthy();
  });

  it('submit без выбранного excuseType → validationError', async () => {
    const { fixture } = await renderDialog();
    const cmp = fixture.componentInstance;
    // Выбираем урок, но не причину
    cmp.toggleLesson(101);
    cmp.submit();
    expect(cmp.validationError()).toBe('Выберите причину пропуска');
    expect(mockApiService.submitExcuse).not.toHaveBeenCalled();
  });

  it('submit без выбранных занятий → validationError', async () => {
    const { fixture } = await renderDialog();
    const cmp = fixture.componentInstance;
    cmp.excuseTypeControl.setValue('illness');
    cmp.submit();
    expect(cmp.validationError()).toBe('Выберите хотя бы одно занятие');
    expect(mockApiService.submitExcuse).not.toHaveBeenCalled();
  });

  it('submit с корректной формой → submitExcuse(lessonIds, excuseType, comment) (AC-9)', async () => {
    mockApiService.submitExcuse.mockReturnValue(of(undefined));
    const { fixture } = await renderDialog();
    const cmp = fixture.componentInstance;
    cmp.toggleLesson(101);
    cmp.excuseTypeControl.setValue('illness');
    cmp.commentControl.setValue('Болел ОРВИ');
    cmp.submit();
    expect(mockApiService.submitExcuse).toHaveBeenCalledWith(
      [101],
      'illness',
      'Болел ОРВИ',
    );
    expect(mockDialogRef.close).toHaveBeenCalledWith(true);
  });

  it('submit с пустым комментарием передаёт null', async () => {
    mockApiService.submitExcuse.mockReturnValue(of(undefined));
    const { fixture } = await renderDialog();
    const cmp = fixture.componentInstance;
    cmp.toggleLesson(101);
    cmp.excuseTypeControl.setValue('free_attendance');
    cmp.commentControl.setValue('   ');
    cmp.submit();
    expect(mockApiService.submitExcuse).toHaveBeenCalledWith(
      [101],
      'free_attendance',
      null,
    );
  });
});
