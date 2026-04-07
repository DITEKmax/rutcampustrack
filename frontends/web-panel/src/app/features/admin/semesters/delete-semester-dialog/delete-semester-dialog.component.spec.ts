import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { DeleteSemesterDialogComponent } from './delete-semester-dialog.component';
import type { SemesterResponse } from '../../shared/types';

const MOCK_SEMESTER: SemesterResponse = {
  id: 1,
  name: 'Весна 2026',
  dateFrom: '2026-02-01',
  dateTo: '2026-06-30',
  active: false,
  createdAt: '2026-01-01T00:00:00Z',
};

describe('DeleteSemesterDialogComponent', () => {
  let component: DeleteSemesterDialogComponent;
  let dialogRefSpy: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRefSpy = { close: vi.fn() };

    TestBed.configureTestingModule({
      imports: [DeleteSemesterDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { semester: MOCK_SEMESTER } },
        { provide: MatDialogRef, useValue: dialogRefSpy },
      ],
    });
    const fixture = TestBed.createComponent(DeleteSemesterDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('confirm button disabled when input is empty', () => {
    component.confirmInput = '';
    expect(component.confirmInput.trim() !== MOCK_SEMESTER.name).toBe(true);
  });

  it('confirm button disabled when input does not match semester name', () => {
    component.confirmInput = 'Wrong Name';
    expect(component.confirmInput.trim() !== MOCK_SEMESTER.name).toBe(true);
  });

  it('confirm button enabled when input exactly matches semester name', () => {
    component.confirmInput = 'Весна 2026';
    expect(component.confirmInput.trim() !== MOCK_SEMESTER.name).toBe(false);
  });

  it('confirm() closes dialog with the confirmation string', () => {
    component.confirmInput = '  Весна 2026  ';
    component.confirm();
    expect(dialogRefSpy.close).toHaveBeenCalledWith('Весна 2026');
  });
});
