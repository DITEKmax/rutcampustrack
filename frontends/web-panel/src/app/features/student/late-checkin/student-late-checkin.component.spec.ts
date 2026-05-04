import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { userEvent } from '@testing-library/user-event';
import { StudentLateCheckinComponent } from './student-late-checkin.component';
import { StudentApiService } from '../shared/student-api.service';
import { SubjectCacheService } from '../shared/subject-cache.service';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import type { AttendanceRecord } from '../shared/student-schedule.types';

const mockRecord = (lessonId: number, status: string): AttendanceRecord => ({
  lessonId,
  subjectId: 1,
  lessonDate: '2026-04-01',
  lessonNumber: 1,
  status,
  symbol: status === 'absent' ? 'н' : '+',
  source: 'auto',
});

const mockApiService = {
  getStudentRecords: vi.fn(),
  requestLateCheckin: vi.fn(),
};

const mockSubjectCache = {
  getName: vi.fn(() => of('Математика')),
};

describe('StudentLateCheckinComponent', () => {
  beforeEach(() => vi.clearAllMocks());

  it('показывает только absent записи (фильтрует present/excused)', async () => {
    mockApiService.getStudentRecords.mockReturnValue(
      of([
        mockRecord(1, 'absent'),
        mockRecord(2, 'present'),
        mockRecord(3, 'excused'),
      ]),
    );
    await render(StudentLateCheckinComponent, {
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: SubjectCacheService, useValue: mockSubjectCache },
      ],
    });
    // Only lesson 1 is absent — should appear as a row with "Запросить отметку"
    const buttons = screen.getAllByText('Запросить отметку');
    expect(buttons).toHaveLength(1);
  });

  it('empty state при отсутствии absent записей', async () => {
    mockApiService.getStudentRecords.mockReturnValue(
      of([mockRecord(1, 'present')]),
    );
    await render(StudentLateCheckinComponent, {
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: SubjectCacheService, useValue: mockSubjectCache },
      ],
    });
    expect(screen.getByText('Нет пропущенных занятий')).toBeTruthy();
  });

  it('клик по кнопке → success state при HTTP 404 (graceful degradation)', async () => {
    mockApiService.getStudentRecords.mockReturnValue(of([mockRecord(1, 'absent')]));
    mockApiService.requestLateCheckin.mockReturnValue(of(undefined));
    const user = userEvent.setup();
    await render(StudentLateCheckinComponent, {
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: SubjectCacheService, useValue: mockSubjectCache },
      ],
    });
    const btn = screen.getByText('Запросить отметку');
    await user.click(btn);
    expect(screen.getByText('Запрос отправлен')).toBeTruthy();
  });

  it('группирует absent записи по дням с заголовками дат', async () => {
    mockApiService.getStudentRecords.mockReturnValue(
      of([
        { ...mockRecord(1, 'absent'), lessonDate: '2026-04-01', lessonNumber: 2 },
        { ...mockRecord(2, 'absent'), lessonDate: '2026-04-01', lessonNumber: 1 },
        { ...mockRecord(3, 'absent'), lessonDate: '2026-04-03', lessonNumber: 1 },
      ]),
    );
    await render(StudentLateCheckinComponent, {
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: SubjectCacheService, useValue: mockSubjectCache },
      ],
    });
    // Two day-group headings
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(2);
    // Three request buttons total (one per absent record)
    expect(screen.getAllByText('Запросить отметку')).toHaveLength(3);
  });

  it('ошибка API → кнопка восстанавливается + inline error', async () => {
    mockApiService.getStudentRecords.mockReturnValue(of([mockRecord(1, 'absent')]));
    mockApiService.requestLateCheckin.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    const user = userEvent.setup();
    await render(StudentLateCheckinComponent, {
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StudentApiService, useValue: mockApiService },
        { provide: SubjectCacheService, useValue: mockSubjectCache },
      ],
    });
    const btn = screen.getByText('Запросить отметку');
    await user.click(btn);
    expect(screen.getByText('Ошибка. Попробуйте ещё раз.')).toBeTruthy();
    // Button should still be present (not replaced by pill)
    expect(screen.getByText('Запросить отметку')).toBeTruthy();
  });
});
