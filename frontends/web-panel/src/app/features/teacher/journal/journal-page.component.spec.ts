import { TestBed } from '@angular/core/testing';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { of, throwError } from 'rxjs';
import { provideAnimations } from '@angular/platform-browser/animations';
import { JournalPageComponent } from './journal-page.component';
import { JournalApiService } from './journal-api.service';
import type { AssignmentResponse, GroupResponse, JournalResponse, SubjectResponse } from './types';

const mockAssignments: AssignmentResponse[] = [
  { id: 1, groupId: 10, groupName: 'ИТ-21', subjectId: 20, subjectName: 'Математика', teacherId: 1 },
];

const mockGroups: GroupResponse[] = [
  { id: 10, name: 'ИТ-21', code: 'IT21', active: true, createdAt: '2026-01-01' },
];

const mockSubjects: SubjectResponse[] = [
  { id: 20, name: 'Математика' },
];

const mockJournal: JournalResponse = {
  groupId: 10,
  subjectId: 20,
  dates: [],
  students: [],
};

function makeMockApi(overrides: Partial<JournalApiService> = {}): Partial<JournalApiService> {
  return {
    getMyAssignments: vi.fn(() => of(mockAssignments)),
    getGroups: vi.fn(() => of(mockGroups)),
    getSubjects: vi.fn(() => of(mockSubjects)),
    getJournal: vi.fn(() => of(mockJournal)),
    ...overrides,
  };
}

describe('JournalPageComponent', () => {
  let mockApi: ReturnType<typeof makeMockApi>;

  beforeEach(() => {
    mockApi = makeMockApi();
    TestBed.configureTestingModule({
      imports: [JournalPageComponent],
      providers: [
        { provide: JournalApiService, useValue: mockApi },
        provideAnimations(),
      ],
    });
  });

  it('creates the component', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('"Показать журнал" button is disabled (canApply false) when no group or subject selected', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    expect(comp.canApply()).toBe(false);
  });

  it('canApply() is true when both group and subject are selected', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    expect(comp.canApply()).toBe(true);
  });

  it('ngOnInit loads assignments and groups, sets dateFrom/dateTo', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges(); // triggers ngOnInit

    expect(mockApi.getMyAssignments).toHaveBeenCalled();
    expect(mockApi.getGroups).toHaveBeenCalled();
    expect(comp.groups()).toHaveLength(1);
    expect(comp.groups()[0].id).toBe(10);
    expect(comp.dateFrom()).toMatch(/^\d{4}-\d{2}-\d{2}$/); // ISO date string set
    expect(comp.dateTo()).toMatch(/^\d{4}-\d{2}-\d{2}$/); // today ISO date string
  });

  it('calls getJournal with correct params when loadJournal() is called', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    comp.dateFrom.set('2026-03-01');
    comp.dateTo.set('2026-03-31');
    comp.loadJournal();

    expect(mockApi.getJournal).toHaveBeenCalledWith(10, 20, '2026-03-01', '2026-03-31');
  });

  it('sets journalData on successful load', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    comp.loadJournal();

    expect(comp.journalData()).toEqual(mockJournal);
    expect(comp.loading()).toBe(false);
    expect(comp.error()).toBeNull();
  });

  it('sets error message when loadJournal() fails', () => {
    mockApi.getJournal = vi.fn(() => throwError(() => new Error('Network error')));
    TestBed.overrideProvider(JournalApiService, { useValue: mockApi });

    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    comp.loadJournal();

    expect(comp.error()).toContain('Не удалось загрузить журнал');
    expect(comp.loading()).toBe(false);
  });

  it('does not call getJournal when group or subject is null', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.loadJournal(); // no group/subject set
    expect(mockApi.getJournal).not.toHaveBeenCalled();
  });

  it('onGroupChange resets selectedSubjectId and journalData, then derives subjects from assignments', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    // Pre-set values that should be reset
    comp.selectedSubjectId.set(99);
    comp.journalData.set(mockJournal);

    comp.onGroupChange(10);

    expect(comp.selectedGroupId()).toBe(10);
    expect(comp.selectedSubjectId()).toBeNull();
    expect(comp.journalData()).toBeNull();
    expect(comp.subjects()).toEqual([{ id: 20, name: 'Математика' }]);
    expect(mockApi.getSubjects).not.toHaveBeenCalled();
  });
});
