import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

import { JournalApiService } from '../journal/journal-api.service';
import type {
  AssignmentResponse,
  GroupResponse,
  SemesterResponse,
  SubjectOption,
  SubjectResponse,
  SubjectType,
} from '../journal/types';
import { deriveStudentChartData, deriveOverallStats, StudentChartData, OverallStats } from './stats-utils';
import { SubjectChartComponent } from './subject-chart/subject-chart.component';
import { OverallStatCardComponent } from './overall-stat-card/overall-stat-card.component';

const SUBJECT_TYPE_LABELS: Record<SubjectType, string> = {
  LECTURE: 'Лекция',
  PRACTICE: 'Практика',
  LAB: 'Лабораторная',
};

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function todayIso(): string {
  return formatLocalDate(new Date());
}

@Component({
  selector: 'app-stats-page',
  standalone: true,
  imports: [
    CommonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressBarModule,
    SubjectChartComponent,
    OverallStatCardComponent,
  ],
  templateUrl: './stats-page.component.html',
  styles: [`
    :host {
      display: block;
    }

    .teacher-stats__charts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 480px), 1fr));
      gap: var(--space-6);
    }
  `],
})
export class StatsPageComponent implements OnInit {
  private readonly journalApi = inject(JournalApiService);

  groups = signal<GroupResponse[]>([]);
  subjects = signal<SubjectOption[]>([]);
  assignments = signal<AssignmentResponse[]>([]);
  selectedGroupId = signal<number | null>(null);
  selectedSubjectId = signal<number | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  chartDataMap = signal<Map<string, StudentChartData[]>>(new Map());
  overallStats = signal<OverallStats>({ totalLessons: 0, attendanceRate: 0 });

  private subjectDetailsById = new Map<number, SubjectResponse>();
  private statsDateFrom = todayIso();
  private statsDateTo = todayIso();
  private statsRequestId = 0;

  chartEntries = computed(() => {
    const entries: { name: string; data: StudentChartData[] }[] = [];
    this.chartDataMap().forEach((data, name) => entries.push({ name, data }));
    return entries;
  });

  ngOnInit(): void {
    forkJoin({
      assignments: this.journalApi.getMyAssignments(),
      groups: this.journalApi.getGroups(),
      subjects: this.journalApi.getSubjects().pipe(catchError(() => of([] as SubjectResponse[]))),
      semesters: this.journalApi.getSemesters().pipe(catchError(() => of([] as SemesterResponse[]))),
    }).subscribe({
      next: ({ assignments, groups, subjects, semesters }) => {
        this.assignments.set(assignments);
        this.subjectDetailsById = new Map(subjects.map(subject => [subject.id, subject]));

        const groupIds = new Set(assignments.map(assignment => assignment.groupId));
        this.groups.set(groups.filter(group => groupIds.has(group.id)));
        this.applyStatsDateRange(this.findActiveSemester(semesters));
      },
      error: () => {
        this.error.set(
          'Не удалось загрузить группы и назначения преподавателя. Обновите страницу или попробуйте позже.',
        );
      },
    });
  }

  onGroupChange(groupId: number): void {
    this.statsRequestId++;
    this.selectedGroupId.set(groupId);
    this.selectedSubjectId.set(null);
    this.chartDataMap.set(new Map());
    this.overallStats.set({ totalLessons: 0, attendanceRate: 0 });
    this.error.set(null);
    this.loading.set(false);

    const filtered = this.getSubjectsForGroup(groupId);
    this.subjects.set(filtered);

    if (filtered.length > 0) {
      this.onSubjectChange(filtered[0].id);
    }
  }

  onSubjectChange(subjectId: number): void {
    const groupId = this.selectedGroupId();
    if (groupId === null) {
      return;
    }

    const subject = this.subjects().find(candidate => candidate.id === subjectId);
    if (!subject) {
      this.selectedSubjectId.set(null);
      this.chartDataMap.set(new Map());
      this.overallStats.set({ totalLessons: 0, attendanceRate: 0 });
      return;
    }

    this.selectedSubjectId.set(subjectId);
    this.chartDataMap.set(new Map());
    this.overallStats.set({ totalLessons: 0, attendanceRate: 0 });
    this.error.set(null);
    this.loading.set(true);
    const requestId = ++this.statsRequestId;

    this.journalApi
      .getJournal(groupId, subject.id, this.statsDateFrom, this.statsDateTo)
      .pipe(finalize(() => {
        if (this.statsRequestId === requestId) {
          this.loading.set(false);
        }
      }))
      .subscribe({
        next: response => {
          if (this.statsRequestId !== requestId) {
            return;
          }
          const newMap = new Map<string, StudentChartData[]>();
          newMap.set(subject.label ?? subject.name, deriveStudentChartData(response));
          this.chartDataMap.set(newMap);
          this.overallStats.set(deriveOverallStats(response));
        },
        error: () => {
          if (this.statsRequestId !== requestId) {
            return;
          }
          this.error.set('Не удалось загрузить статистику по предмету. Попробуйте позже.');
        },
      });
  }

  private getSubjectsForGroup(groupId: number): SubjectOption[] {
    const subjectsById = new Map<number, SubjectOption>();
    for (const assignment of this.assignments()) {
      if (assignment.groupId !== groupId) {
        continue;
      }

      const subject = this.subjectDetailsById.get(assignment.subjectId);
      const name =
        assignment.subjectName?.trim() ||
        subject?.name?.trim() ||
        `Предмет #${assignment.subjectId}`;
      const type = subject?.type ?? null;

      subjectsById.set(assignment.subjectId, {
        id: assignment.subjectId,
        name,
        type,
        label: this.subjectLabel(name, type),
      });
    }

    return Array.from(subjectsById.values()).sort((left, right) =>
      (left.label ?? left.name).localeCompare(right.label ?? right.name, 'ru'),
    );
  }

  private typeLabel(type: SubjectType | null): string {
    return type ? SUBJECT_TYPE_LABELS[type] : '';
  }

  private subjectLabel(name: string, type: SubjectType | null): string {
    const typeLabel = this.typeLabel(type);
    return typeLabel ? `${name} · ${typeLabel}` : name;
  }

  private findActiveSemester(semesters: SemesterResponse[]): SemesterResponse | null {
    const today = todayIso();
    return (
      semesters.find(semester => semester.active) ??
      semesters.find(semester => semester.dateFrom <= today && semester.dateTo >= today) ??
      null
    );
  }

  private applyStatsDateRange(semester: SemesterResponse | null): void {
    const today = todayIso();
    const fallbackStart = new Date();
    fallbackStart.setDate(1);

    const fromIso = semester?.dateFrom ?? formatLocalDate(fallbackStart);
    const boundedToIso = semester?.dateTo && semester.dateTo < today ? semester.dateTo : today;

    this.statsDateFrom = fromIso <= boundedToIso ? fromIso : boundedToIso;
    this.statsDateTo = boundedToIso;
  }
}
