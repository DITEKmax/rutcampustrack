import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HeadmanJournalGridComponent } from './headman-journal-grid.component';
import { HeadmanApiService } from '../../shared/headman-api.service';

describe('HeadmanJournalGridComponent', () => {
  let fixture: ComponentFixture<HeadmanJournalGridComponent>;
  let component: HeadmanJournalGridComponent;
  let snackBarMock: { open: ReturnType<typeof vi.fn> };
  let headmanApiMock: Partial<HeadmanApiService>;

  const mockJournalData = {
    groupId: 5,
    subjectId: 1,
    dates: ['2026-04-01'],
    students: [
      {
        userId: 10,
        displayName: 'Иванов Иван',
        records: [
          { lessonId: 42, date: '2026-04-01', lessonNumber: 1, status: 'absent' as const, symbol: 'н' },
        ],
      },
      {
        userId: 11,
        displayName: 'Петров Пётр',
        records: [
          { lessonId: 43, date: '2026-04-01', lessonNumber: 1, status: 'cancelled' as const, symbol: '--' },
        ],
      },
    ],
  };

  beforeEach(() => {
    headmanApiMock = {
      markAttendance: vi.fn().mockReturnValue(of({})),
    };
    snackBarMock = {
      open: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [HeadmanJournalGridComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
        { provide: HeadmanApiService, useValue: headmanApiMock },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });

    fixture = TestBed.createComponent(HeadmanJournalGridComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('journalData', mockJournalData);
    fixture.detectChanges();
  });

  it('renders a row for each student in journalData', () => {
    // The dataSource should contain 2 students
    expect(component.dataSource().length).toBe(2);
  });

  it('clicking a non-cancelled cell calls markAttendance on the service', () => {
    const cell = component.getCell(10, '2026-04-01_lesson1');
    expect(cell).toBeTruthy();
    expect(cell!.status).toBe('absent');

    const row = mockJournalData.students[0];
    component.onCellClick(cell!, row);

    expect(headmanApiMock.markAttendance).toHaveBeenCalledWith(42, 10, 'present');
  });

  it('on markAttendance error, status reverts and snackbar is opened', () => {
    (headmanApiMock.markAttendance as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => new Error('HTTP error'))
    );

    const cell = component.getCell(10, '2026-04-01_lesson1');
    const prevStatus = cell!.status;
    const row = mockJournalData.students[0];

    component.onCellClick(cell!, row);

    expect(cell!.status).toBe(prevStatus);
    expect(snackBarMock.open).toHaveBeenCalledWith(
      'Не удалось изменить статус. Попробуйте снова.',
      undefined,
      { duration: 4000 },
    );
  });

  it('cancelled cells do not call markAttendance when onCellClick is called', () => {
    const cell = component.getCell(11, '2026-04-01_lesson1');
    expect(cell).toBeTruthy();
    expect(cell!.status).toBe('cancelled');

    const row = mockJournalData.students[1];
    component.onCellClick(cell!, row);

    expect(headmanApiMock.markAttendance).not.toHaveBeenCalled();
  });
});
