import { TestBed } from '@angular/core/testing';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { of, throwError } from 'rxjs';
import { provideAnimations } from '@angular/platform-browser/animations';
import { JournalPageComponent } from './journal-page.component';
import { JournalApiService } from './journal-api.service';
import type {
  AssignmentResponse,
  GroupResponse,
  JournalResponse,
  SemesterResponse,
  SubjectResponse,
} from './types';

const mockAssignments: AssignmentResponse[] = [
  {
    id: 1,
    groupId: 10,
    groupName: 'ИТ-21',
    subjectId: 20,
    subjectName: 'Математика',
    teacherId: 1,
    semesterId: 7,
  },
  {
    id: 2,
    groupId: 10,
    groupName: 'ИТ-21',
    subjectId: 21,
    subjectName: 'Математика',
    teacherId: 1,
    semesterId: 7,
  },
];

const mockGroups: GroupResponse[] = [
  { id: 10, name: 'ИТ-21', active: true, createdAt: '2026-01-01' },
];

const mockSubjects: SubjectResponse[] = [
  {
    id: 20,
    name: 'Математика',
    type: 'LECTURE',
    groupId: 10,
    teacherIds: [1],
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 21,
    name: 'Математика',
    type: 'LAB',
    groupId: 10,
    teacherIds: [1],
    createdAt: '2026-01-01T00:00:00Z',
  },
];

const mockSemesters: SemesterResponse[] = [
  {
    id: 7,
    name: 'Весенний 2026',
    dateFrom: '2026-02-09',
    dateTo: '2099-06-14',
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
  },
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
    getSemesters: vi.fn(() => of(mockSemesters)),
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

  it('canApply() is false when group, subject, or dates are missing', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    expect(comp.canApply()).toBe(false);
  });

  it('canApply() is true when group, subject, and valid dates are selected', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    comp.dateFrom.set(new Date(2026, 2, 1));
    comp.dateTo.set(new Date(2026, 2, 31));
    expect(comp.canApply()).toBe(true);
  });

  it('ngOnInit loads assignments, groups, subjects, and sets default semester range', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    expect(mockApi.getMyAssignments).toHaveBeenCalled();
    expect(mockApi.getGroups).toHaveBeenCalled();
    expect(mockApi.getSubjects).toHaveBeenCalled();
    expect(mockApi.getSemesters).toHaveBeenCalled();
    expect(comp.groups()).toHaveLength(1);
    expect(comp.groups()[0].id).toBe(10);
    expect(comp.dateFromIso()).toBe('2026-02-09');
    expect(comp.dateToIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('calls getJournal with local ISO dates when loadJournal() is called', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    comp.dateFrom.set(new Date(2026, 2, 1));
    comp.dateTo.set(new Date(2026, 2, 31));
    comp.loadJournal();

    expect(mockApi.getJournal).toHaveBeenCalledWith(10, 20, '2026-03-01', '2026-03-31');
  });

  it('sets journalData on successful load', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    comp.dateFrom.set(new Date(2026, 2, 1));
    comp.dateTo.set(new Date(2026, 2, 31));
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
    comp.dateFrom.set(new Date(2026, 2, 1));
    comp.dateTo.set(new Date(2026, 2, 31));
    comp.loadJournal();

    expect(comp.error()).toContain('Не удалось загрузить журнал');
    expect(comp.loading()).toBe(false);
  });

  it('does not call getJournal when group or subject is null', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.loadJournal();
    expect(mockApi.getJournal).not.toHaveBeenCalled();
  });

  it('does not call getJournal when date range is invalid', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    comp.selectedGroupId.set(10);
    comp.selectedSubjectId.set(20);
    comp.dateFrom.set(new Date(2026, 3, 1));
    comp.dateTo.set(new Date(2026, 2, 31));

    comp.loadJournal();

    expect(mockApi.getJournal).not.toHaveBeenCalled();
    expect(comp.error()).toContain('Проверьте период');
  });

  it('onGroupChange resets selectedSubjectId and journalData, then derives typed subject labels', () => {
    const fixture = TestBed.createComponent(JournalPageComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    comp.selectedSubjectId.set(99);
    comp.journalData.set(mockJournal);

    comp.onGroupChange(10);

    expect(comp.selectedGroupId()).toBe(10);
    expect(comp.selectedSubjectId()).toBeNull();
    expect(comp.journalData()).toBeNull();
    expect(comp.subjects().map(subject => subject.label)).toEqual([
      'Математика · Лабораторная',
      'Математика · Лекция',
    ]);
  });
});
