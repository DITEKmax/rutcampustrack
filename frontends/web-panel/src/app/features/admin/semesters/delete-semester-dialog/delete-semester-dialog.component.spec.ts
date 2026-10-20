import { ComponentFixture, TestBed } from '@angular/core/testing';
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
  let fixture: ComponentFixture<DeleteSemesterDialogComponent>;
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
    fixture = TestBed.createComponent(DeleteSemesterDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('confirm button disabled when input is empty', async () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = "";
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(button => button.textContent?.includes('Удалить навсегда'))!;
    expect(button.disabled).toBe(true);
    button.click();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });

  it('confirm button disabled when input does not match semester name', async () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = "Wrong Name";
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(button => button.textContent?.includes('Удалить навсегда'))!;
    expect(button.disabled).toBe(true);
    button.click();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });

  it('confirm button enabled when input exactly matches semester name', async () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = "Весна 2026";
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(button => button.textContent?.includes('Удалить навсегда'))!;
    expect(button.disabled).toBe(false);
    button.click();
    expect(dialogRefSpy.close).toHaveBeenCalledWith('Весна 2026');
  });

  it('confirm() closes dialog with the confirmation string', async () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = "  Весна 2026  ";
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(button => button.textContent?.includes('Удалить навсегда'))!;
    expect(button.disabled).toBe(false);
    button.click();
    expect(dialogRefSpy.close).toHaveBeenCalledWith('Весна 2026');
  });
});
