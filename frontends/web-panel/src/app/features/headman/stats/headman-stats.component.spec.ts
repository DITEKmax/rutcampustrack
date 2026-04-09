import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HeadmanStatsComponent } from './headman-stats.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';

const MOCK_SUBJECTS_PAGE = {
  _embedded: {
    subjectResponseList: [
      { id: 1, name: 'Математика' },
      { id: 2, name: 'Физика' },
    ],
  },
};

const MOCK_JOURNAL = {
  students: [
    {
      records: [
        { status: 'present' },
        { status: 'absent' },
        { status: 'present' },
        { status: 'cancelled' },
      ],
    },
  ],
};

// present=2, absent=1, cancelled excluded → 2/3 = 67%
const MOCK_THRESHOLD = { minPercentage: 75 };

function makeApiService(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    listSubjects: () => of(MOCK_SUBJECTS_PAGE),
    getJournal: () => of(MOCK_JOURNAL),
    resolveThreshold: () => of(MOCK_THRESHOLD),
    setSubjectThreshold: () => of({}),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function makeAuthService(groupId: number | null = 42): AuthService {
  return {
    currentUser: signal({ id: 1, role: 'STUDENT', isHeadman: true, groupId }),
  } as unknown as AuthService;
}

describe('HeadmanStatsComponent', () => {
  let fixture: ComponentFixture<HeadmanStatsComponent>;
  let component: HeadmanStatsComponent;
  let snackBarMock: { open: ReturnType<typeof vi.fn> };

  function setupTestBed(apiOverrides: Partial<HeadmanApiService> = {}, groupId: number | null = 42) {
    snackBarMock = { open: vi.fn() };
    TestBed.configureTestingModule({
      imports: [HeadmanStatsComponent],
      providers: [
        provideNoopAnimations(),
        { provide: HeadmanApiService, useValue: makeApiService(apiOverrides) },
        { provide: AuthService, useValue: makeAuthService(groupId) },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    });
    fixture = TestBed.createComponent(HeadmanStatsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('renders stats table with subject rows when service returns mock data', () => {
    setupTestBed();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Математика');
    expect(compiled.textContent).toContain('Физика');
  });

  it('calls setSubjectThreshold when valid new threshold is entered and blurred', () => {
    const setSubjectThreshold = vi.fn(() => of({}));
    setupTestBed({ setSubjectThreshold } as any);

    // Populate statsRows directly to simulate loaded state
    const row = { subjectId: 1, subjectName: 'Математика', groupAveragePercent: 67, threshold: 75, isRedZone: true };
    component.statsRows.set([row]);
    fixture.detectChanges();

    const input = document.createElement('input');
    input.value = '60';
    const event = { target: input } as unknown as Event;

    component.onThresholdBlur(row, event);

    expect(setSubjectThreshold).toHaveBeenCalledWith(1, 60);
  });

  it('reverts threshold to previous value when setSubjectThreshold errors', () => {
    const setSubjectThreshold = vi.fn(() => throwError(() => new Error('fail')));
    setupTestBed({ setSubjectThreshold } as any);

    const row = { subjectId: 1, subjectName: 'Математика', groupAveragePercent: 67, threshold: 75, isRedZone: true };
    component.statsRows.set([row]);
    fixture.detectChanges();

    const input = document.createElement('input');
    input.value = '60';
    const event = { target: input } as unknown as Event;

    component.onThresholdBlur(row, event);

    // Threshold must revert to previous value on API error
    expect(row.threshold).toBe(75);
    expect(input.value).toBe('75');
    // setSubjectThreshold was still called (optimistic attempt)
    expect(setSubjectThreshold).toHaveBeenCalledWith(1, 60);
  });

  it('reverts invalid threshold input without API call', () => {
    const setSubjectThreshold = vi.fn(() => of({}));
    setupTestBed({ setSubjectThreshold } as any);

    const row = { subjectId: 1, subjectName: 'Математика', groupAveragePercent: 67, threshold: 75, isRedZone: true };
    component.statsRows.set([row]);
    fixture.detectChanges();

    const input = document.createElement('input');
    input.value = '999';
    const event = { target: input } as unknown as Event;

    component.onThresholdBlur(row, event);

    expect(setSubjectThreshold).not.toHaveBeenCalled();
    expect(input.value).toBe('75'); // reverted
  });

  it('isRedZone is true when groupAveragePercent < threshold', () => {
    setupTestBed();
    // After ngOnInit, statsRows should be populated: 2/3 non-cancelled = 67% < 75%
    const redZoneRows = component.statsRows().filter(r => r.isRedZone);
    expect(redZoneRows.length).toBeGreaterThan(0);
    expect(redZoneRows[0].groupAveragePercent).toBe(67);
    expect(redZoneRows[0].threshold).toBe(75);
  });
});
