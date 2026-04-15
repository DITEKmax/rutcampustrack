import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { HeadmanJournalPageComponent } from './headman-journal-page.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';

describe('HeadmanJournalPageComponent', () => {
  let fixture: ComponentFixture<HeadmanJournalPageComponent>;
  let component: HeadmanJournalPageComponent;

  const mockSubjectsResponse = {
    _embedded: {
      subjectResponseList: [
        { id: 1, name: 'Математика' },
        { id: 2, name: 'Физика' },
      ],
    },
    page: { totalElements: 2, totalPages: 1, size: 100, number: 0 },
  };

  const mockJournalResponse = {
    groupId: 5,
    subjectId: 1,
    dates: ['2026-04-01'],
    students: [
      {
        userId: 10,
        displayName: 'Иванов Иван',
        records: [
          { lessonId: 42, date: '2026-04-01', lessonNumber: 1, status: 'present', symbol: '+' },
        ],
      },
    ],
  };

  let headmanApiMock: Partial<HeadmanApiService>;
  let authServiceMock: { currentUser: ReturnType<typeof signal> };

  beforeEach(() => {
    headmanApiMock = {
      listSubjects: vi.fn().mockReturnValue(of(mockSubjectsResponse)),
      getJournal: vi.fn().mockReturnValue(of(mockJournalResponse)),
    };
    authServiceMock = {
      currentUser: signal({ id: 1, role: 'STUDENT', isHeadman: true, groupId: 5 }),
    };

    TestBed.configureTestingModule({
      imports: [HeadmanJournalPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: HeadmanApiService, useValue: headmanApiMock },
        { provide: AuthService, useValue: authServiceMock },
      ],
    });

    fixture = TestBed.createComponent(HeadmanJournalPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders subject MatSelect on init', () => {
    const select = fixture.nativeElement.querySelector('mat-select');
    expect(select).toBeTruthy();
  });

  it('does not render date range inputs', () => {
    const dateInputs = fixture.nativeElement.querySelectorAll('input[type="date"]');
    expect(dateInputs.length).toBe(0);
  });

  it('shows page-empty state when no journalData is loaded', () => {
    const empty = fixture.nativeElement.querySelector('.page-empty');
    expect(empty).toBeTruthy();
  });

  it('auto-loads journal when a subject is selected (no Apply button)', () => {
    component.selectedSubjectId.set(1);
    fixture.detectChanges();

    expect(headmanApiMock.getJournal).toHaveBeenCalledWith(
      5,
      1,
      expect.any(String),
      expect.any(String),
    );
  });

  it('shows page-error when getJournal fails', () => {
    (headmanApiMock.getJournal as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => new Error('Network error')),
    );
    component.selectedSubjectId.set(1);
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector('.page-error');
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent).toContain('Не удалось загрузить данные');
  });
});
