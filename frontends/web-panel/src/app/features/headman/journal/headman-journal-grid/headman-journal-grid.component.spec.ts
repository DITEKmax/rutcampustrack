import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HeadmanJournalGridComponent } from './headman-journal-grid.component';
import { HeadmanApiService } from '../../shared/headman-api.service';

function pastDate(offsetDays = 3): string {
  const d = new Date();
  d.setDate(d.getDate() - offsetDays);
  return d.toISOString().slice(0, 10);
}

describe('HeadmanJournalGridComponent', () => {
  let fixture: ComponentFixture<HeadmanJournalGridComponent>;
  let component: HeadmanJournalGridComponent;
  let snackBarMock: { open: ReturnType<typeof vi.fn> };
  let headmanApiMock: Partial<HeadmanApiService>;

  const date = pastDate(3);

  const mockJournalData = {
    groupId: 5,
    subjectId: 1,
    dates: [date],
    students: [
      {
        userId: 10,
        displayName: 'Иванов Иван',
        records: [
          { lessonId: 42, date, lessonNumber: 1, status: 'absent' as const, symbol: 'н' },
        ],
      },
      {
        userId: 11,
        displayName: 'Петров Пётр',
        records: [
          { lessonId: 43, date, lessonNumber: 1, status: 'cancelled' as const, symbol: '--' },
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
    fixture.componentRef.setInput('journalData', { ...mockJournalData, students: [...mockJournalData.students].reverse() });
    fixture.detectChanges();
  });

  it('sorts students by surname (Russian locale)', () => {
    expect(component.dataSource().length).toBe(2);
    expect(component.dataSource()[0].displayName).toBe('Иванов Иван');
    expect(component.dataSource()[1].displayName).toBe('Петров Пётр');
    expect(component.getCell(10, `${date}_lesson1`)?.lessonId).toBe(42);
    expect(component.getCell(11, `${date}_lesson1`)?.lessonId).toBe(43);
  });

  it('setStatus on a non-cancelled cell calls markAttendance', () => {
    const cell = component.getCell(10, `${date}_lesson1`);
    expect(cell).toBeTruthy();
    expect(cell!.status).toBe('absent');

    const row = mockJournalData.students[0];
    component.setStatus(cell!, row, 'present');

    expect(headmanApiMock.markAttendance).toHaveBeenCalledWith(42, 10, 'present');
  });

  it('reverts status and opens snackbar on markAttendance error', () => {
    (headmanApiMock.markAttendance as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => new Error('HTTP error')),
    );

    const cell = component.getCell(10, `${date}_lesson1`);
    const prevStatus = cell!.status;
    const row = mockJournalData.students[0];

    component.setStatus(cell!, row, 'present');

    expect(cell!.status).toBe(prevStatus);
    expect(snackBarMock.open).toHaveBeenCalledWith(
      'Не удалось изменить статус. Попробуйте снова.',
      undefined,
      { duration: 4000 },
    );
  });

  it('cancelled cells expose no status action while active cells submit the selected status', () => {
    const rows = Array.from(fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLElement>);
    const cancelled = rows.find(row => row.textContent?.includes('Петров Пётр'))!;
    expect(cancelled.querySelectorAll('button')).toHaveLength(0);
    cancelled.querySelector<HTMLElement>('[aria-label^="Отменена"]')!.click();
    expect(headmanApiMock.markAttendance).not.toHaveBeenCalled();
    const active = rows.find(row => row.textContent?.includes('Иванов Иван'))!;
    active.querySelector<HTMLButtonElement>('button[aria-label^="Присутствовал"]')!.click();
    expect(headmanApiMock.markAttendance).toHaveBeenCalledWith(42, 10, 'present');
  });

  it('getPercent returns 0 when the only lesson is absent', () => {
    expect(component.getPercent(10)).toBe(0);
  });

  it('renders a sticky percent column', () => {
    const percentHeader = fixture.nativeElement.querySelector('.col-percent--head');
    expect(percentHeader).toBeTruthy();
    expect(percentHeader.textContent.trim()).toBe('%');
  });

  it('renders three quick-status buttons per active cell', () => {
    const dots = fixture.nativeElement.querySelectorAll('.dot');
    // student 10 has one active cell (3 dots); student 11 is cancelled (0 dots).
    expect(dots.length).toBe(3);
  });
});
