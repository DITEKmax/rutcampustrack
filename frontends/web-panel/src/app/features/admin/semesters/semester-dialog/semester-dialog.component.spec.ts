import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideNativeDateAdapter } from '@angular/material/core';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
// Note: two afterEach blocks are used — one for HttpTestingController.verify()
// and one for resetting fake timers. Vitest supports multiple afterEach hooks.
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {
  SemesterDialogComponent,
  SemesterDialogData,
} from './semester-dialog.component';
import type { SemesterResponse } from '../../shared/types';

/**
 * BUG-006-7 / plan 58-05: min=today на dateFrom, async validator на overlap,
 * edit завершённого семестра — form.disabled.
 */
describe('SemesterDialogComponent (BUG-006-7)', () => {
  let httpMock: HttpTestingController;
  let dialogRefSpy: { close: ReturnType<typeof vi.fn> };

  function createComponent(data: SemesterDialogData): SemesterDialogComponent {
    dialogRefSpy = { close: vi.fn() };

    TestBed.configureTestingModule({
      imports: [SemesterDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
        provideNativeDateAdapter(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRefSpy },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SemesterDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  afterEach(() => {
    httpMock.verify();
  });

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('Test 1: minDate signal set to today (used by [min] on dateFrom picker)', () => {
    const component = createComponent({ mode: 'create' });
    const min = component.minDate();
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    expect(min.getFullYear()).toBe(today.getFullYear());
    expect(min.getMonth()).toBe(today.getMonth());
    expect(min.getDate()).toBe(today.getDate());
  });

  it('Test 2: changing dateFrom/dateTo triggers async overlap check after debounce', async () => {
    const component = createComponent({ mode: 'create' });

    const from = new Date(2030, 0, 15);
    const to = new Date(2030, 5, 30);
    component.form.patchValue({ name: 'X', dateFrom: from, dateTo: to });

    // Advance past the 300ms debounce
    await vi.advanceTimersByTimeAsync(350);

    const req = httpMock.expectOne(
      r =>
        r.url === '/api/academic/semesters/overlap' &&
        r.params.get('from') === '2030-01-15' &&
        r.params.get('to') === '2030-06-30',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('excludeId')).toBe(false);
    req.flush({ overlaps: false });
  });

  it('Test 3: overlaps=true sets form.errors.overlap with conflictingName', async () => {
    const component = createComponent({ mode: 'create' });

    component.form.patchValue({
      name: 'Collide',
      dateFrom: new Date(2030, 1, 1),
      dateTo: new Date(2030, 5, 30),
    });

    await vi.advanceTimersByTimeAsync(350);

    const req = httpMock.expectOne(r => r.url === '/api/academic/semesters/overlap');
    req.flush({ overlaps: true, conflictingName: 'Осень 2030' });

    // Flush async pipeline
    await vi.advanceTimersByTimeAsync(0);

    expect(component.form.hasError('overlap')).toBe(true);
    expect(component.form.errors?.['overlap']).toBe('Осень 2030');
  });

  it('Test 4: edit mode sends excludeId=current semester id', async () => {
    const semester: SemesterResponse = {
      id: 77,
      name: 'Spring 2030',
      dateFrom: '2030-02-01',
      dateTo: '2030-06-30',
      active: true,
      createdAt:'2030-01-01T00:00:00Z',
    } as SemesterResponse;

    const component = createComponent({ mode: 'edit', semester });

    // Trigger a change so asyncValidator fires again
    component.form.patchValue({ dateTo: new Date(2030, 6, 15) });

    await vi.advanceTimersByTimeAsync(350);

    const req = httpMock.expectOne(r => r.url === '/api/academic/semesters/overlap');
    expect(req.request.params.get('excludeId')).toBe('77');
    req.flush({ overlaps: false });
  });

  it('Test 5: edit of completed semester disables form + sets readOnlyWarning', () => {
    const past = new Date();
    past.setFullYear(past.getFullYear() - 2);
    const pastStr = `${past.getFullYear()}-01-01`;
    const pastEndStr = `${past.getFullYear()}-06-30`;

    const semester: SemesterResponse = {
      id: 99,
      name: 'Fall 2020',
      dateFrom: pastStr,
      dateTo: pastEndStr,
      active: false,
      createdAt:`${past.getFullYear()}-01-01T00:00:00Z`,
    } as SemesterResponse;

    const component = createComponent({ mode: 'edit', semester });

    expect(component.readOnlyWarning()).toContain('Завершённые');
    expect(component.form.disabled).toBe(true);
  });

  it('Test 6: debounce 300ms — no request fires before 300ms elapse', async () => {
    const component = createComponent({ mode: 'create' });

    component.form.patchValue({
      name: 'X',
      dateFrom: new Date(2030, 0, 1),
      dateTo: new Date(2030, 5, 30),
    });

    await vi.advanceTimersByTimeAsync(100);
    httpMock.expectNone('/api/academic/semesters/overlap');

    await vi.advanceTimersByTimeAsync(250); // now past 300ms total
    const req = httpMock.expectOne('/api/academic/semesters/overlap?from=2030-01-01&to=2030-06-30');
    req.flush({ overlaps: false });
  });
});
