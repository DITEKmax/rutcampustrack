import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { StudentExcusesComponent } from './student-excuses.component';
import { StudentApiService } from '../shared/student-api.service';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

const mockApiService = {
  getExcuseTickets: vi.fn(),
  getStudentRecords: vi.fn(),
  submitExcuse: vi.fn(),
};

const mockDialog = { open: vi.fn() };
const mockSnackBar = { open: vi.fn() };

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
});
