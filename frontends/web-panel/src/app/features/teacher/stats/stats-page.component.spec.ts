import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';

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
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: JournalApiService, useValue: mockApiService },
      ],
    }).compileComponents();
  });

  it('creates the component', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
  });

  it('renders "Статистика посещаемости" heading', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    fixture.detectChanges();
    const heading = fixture.nativeElement.querySelector('h1');
    expect(heading?.textContent?.trim()).toContain('Статистика посещаемости');
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

  it('chartEntries computed returns entries from chartDataMap', () => {
    const fixture = TestBed.createComponent(StatsPageComponent);
    const comp = fixture.componentInstance;
    const map = new Map([['Математика', [{ name: 'Иванов', present: 3, excused: 0, absent: 1 }]]]);
    comp.chartDataMap.set(map);
    fixture.detectChanges();
    expect(comp.chartEntries()).toHaveLength(1);
    expect(comp.chartEntries()[0].name).toBe('Математика');
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
