import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { getByRole } from '@testing-library/dom';
import userEvent from '@testing-library/user-event';
import { SubjectChartComponent } from './subject-chart/subject-chart.component';
import { OverallStatCardComponent } from './overall-stat-card/overall-stat-card.component';

import { StatsPageComponent } from './stats-page.component';
import { JournalApiService } from '../journal/journal-api.service';

function makeJournalApiMock() {
  return {
    getMyAssignments: vi.fn().mockReturnValue(of([])),
    getGroups: vi.fn().mockReturnValue(of([])),
    getSubjects: vi.fn().mockReturnValue(of([])),
    getSemesters: vi.fn().mockReturnValue(of([])),
    getJournal: vi.fn().mockReturnValue(of({ groupId: 1, subjectId: 10, dates: [], students: [] })),
  };
}

describe('StatsPageComponent', () => {
  let mockApiService: ReturnType<typeof makeJournalApiMock>;

  beforeEach(async () => {
    mockApiService = makeJournalApiMock();

    await TestBed.configureTestingModule({
      imports: [StatsPageComponent],
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: JournalApiService, useValue: mockApiService },
      ],
    }).compileComponents();
  });



  it('shows mat-progress-bar when loading signal is true', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    const comp = fixture.componentInstance;
    comp.loading.set(true);
    fixture.detectChanges();
    const progressBar = fixture.nativeElement.querySelector('mat-progress-bar');
    expect(progressBar).not.toBeNull();
  });

  it('hides mat-progress-bar when loading is false', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    const comp = fixture.componentInstance;
    comp.loading.set(false);
    fixture.detectChanges();
    const progressBar = fixture.nativeElement.querySelector('mat-progress-bar');
    expect(progressBar).toBeNull();
  });

  it('loads assignments, groups, subjects and semesters on init', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    fixture.detectChanges();

    expect(mockApiService.getMyAssignments).toHaveBeenCalled();
    expect(mockApiService.getGroups).toHaveBeenCalled();
    expect(mockApiService.getSubjects).toHaveBeenCalled();
    expect(mockApiService.getSemesters).toHaveBeenCalled();
  });

  it('shows error message when error signal is set', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    const comp = fixture.componentInstance;
    comp.error.set('Не удалось загрузить статистику по предмету. Попробуйте позже.');
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Не удалось загрузить статистику');
  });

  it('chartEntries computed returns empty array when chartDataMap is empty', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp.chartEntries()).toEqual([]);
  });

  it('renders selected group journal as summary and per-student chart inputs', async () => {
    mockApiService.getMyAssignments.mockReturnValue(of([{ id: 1, groupId: 10, subjectId: 20, subjectName: 'Математика', teacherId: 1 }]));
    mockApiService.getGroups.mockReturnValue(of([{ id: 10, name: 'Тестовая группа' }]));
    mockApiService.getJournal.mockReturnValue(of({ groupId: 10, subjectId: 20, dates: ['2026-03-01', '2026-03-02'], students: [
      { userId: 1, displayName: 'Иванов', records: [
        { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: '+' },
        { date: '2026-03-02', lessonNumber: 1, status: 'absent', symbol: 'н' },
      ] },
      { userId: 2, displayName: 'Петров', records: [
        { date: '2026-03-01', lessonNumber: 1, status: 'present', symbol: '+' },
        { date: '2026-03-02', lessonNumber: 1, status: 'excused', symbol: 'у' },
      ] },
    ] }));
    const fixture = TestBed.createComponent(StatsPageComponent);
    fixture.detectChanges();
    const user = userEvent.setup();
    await user.click(getByRole(fixture.nativeElement, 'combobox', { name: 'Группа' }));
    fixture.detectChanges();
    await user.click(getByRole(document.body, 'option', { name: 'Тестовая группа' }));
    fixture.detectChanges();
    expect(mockApiService.getJournal).toHaveBeenCalledWith(10, 20, expect.any(String), expect.any(String));
    const summary = fixture.debugElement.query(By.directive(OverallStatCardComponent)).componentInstance as OverallStatCardComponent;
    expect(summary.totalLessons).toBe(2);
    expect(summary.attendanceRate).toBe(75);
    const chart = fixture.debugElement.query(By.directive(SubjectChartComponent)).componentInstance as SubjectChartComponent;
    expect(chart.subjectName).toBe('Математика');
    expect(chart.chartData).toEqual([
      { name: 'Иванов', present: 1, absent: 1, excused: 0 },
      { name: 'Петров', present: 1, absent: 0, excused: 1 },
    ]);
  });

  it('derives subjects from teacher assignments and loads the first subject when group changes', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    comp.assignments.set([
      { id: 1, groupId: 10, subjectId: 20, subjectName: 'Математика', teacherId: 1 },
    ]);

    comp.onGroupChange(10);

    expect(comp.subjects()).toEqual([{ id: 20, name: 'Математика', type: null, label: 'Математика' }]);
    expect(comp.selectedSubjectId()).toBe(20);
    expect(mockApiService.getJournal).toHaveBeenCalledWith(10, 20, expect.any(String), expect.any(String));
  });
});
