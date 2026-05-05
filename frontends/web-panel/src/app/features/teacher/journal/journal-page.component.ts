import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatInputModule } from '@angular/material/input';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { JournalApiService } from './journal-api.service';
import { JournalGridComponent } from './journal-grid/journal-grid.component';
import { StatusLegendComponent } from './status-legend/status-legend.component';
import type {
  AssignmentResponse,
  GroupResponse,
  JournalResponse,
  SemesterResponse,
  SubjectOption,
  SubjectResponse,
  SubjectType,
} from './types';

const SUBJECT_TYPE_LABELS: Record<SubjectType, string> = {
  LECTURE: 'Лекция',
  PRACTICE: 'Практика',
  LAB: 'Лабораторная',
};

function formatLocalDate(date: Date | null): string {
  if (!date) return '';
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseLocalDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
}

function todayIso(): string {
  return formatLocalDate(new Date());
}

@Component({
  selector: 'app-journal-page',
  standalone: true,
  imports: [
    MatSelectModule,
    MatFormFieldModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatProgressBarModule,
    MatInputModule,
    JournalGridComponent,
    StatusLegendComponent,
  ],
  templateUrl: './journal-page.component.html',
})
export class JournalPageComponent implements OnInit {
  private readonly journalApi = inject(JournalApiService);

  readonly groups = signal<GroupResponse[]>([]);
  readonly subjects = signal<SubjectOption[]>([]);
  readonly journalData = signal<JournalResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectedGroupId = signal<number | null>(null);
  readonly selectedSubjectId = signal<number | null>(null);
  readonly dateFrom = signal<Date | null>(null);
  readonly dateTo = signal<Date | null>(null);

  readonly dateFromIso = computed(() => formatLocalDate(this.dateFrom()));
  readonly dateToIso = computed(() => formatLocalDate(this.dateTo()));
  readonly dateRangeInvalid = computed(() => {
    const from = this.dateFromIso();
    const to = this.dateToIso();
    return Boolean(from && to && from > to);
  });

  readonly canApply = computed(
    () =>
      this.selectedGroupId() !== null &&
      this.selectedSubjectId() !== null &&
      Boolean(this.dateFromIso()) &&
      Boolean(this.dateToIso()) &&
      !this.dateRangeInvalid(),
  );

  /** Cached assignments for group-subject filtering. */
  private assignments: AssignmentResponse[] = [];
  private subjectDetailsById = new Map<number, SubjectResponse>();

  ngOnInit(): void {
    this.applyDefaultDateRange(null);

    forkJoin({
      assignments: this.journalApi.getMyAssignments(),
      groups: this.journalApi.getGroups(),
      subjects: this.journalApi.getSubjects().pipe(catchError(() => of([] as SubjectResponse[]))),
      semesters: this.journalApi.getSemesters().pipe(catchError(() => of([] as SemesterResponse[]))),
    }).subscribe({
      next: ({ assignments, groups, subjects, semesters }) => {
        this.assignments = assignments;
        this.subjectDetailsById = new Map(subjects.map(subject => [subject.id, subject]));
        const groupIds = new Set(assignments.map(a => a.groupId));
        this.groups.set(groups.filter(g => groupIds.has(g.id)));
        this.applyDefaultDateRange(this.findActiveSemester(semesters));
      },
      error: () => {
        this.error.set(
          'Не удалось загрузить группы и назначения преподавателя. Обновите страницу или попробуйте позже.',
        );
      },
    });
  }

  onGroupChange(groupId: number): void {
    this.selectedGroupId.set(groupId);
    this.selectedSubjectId.set(null);
    this.journalData.set(null);
    this.subjects.set(this.getSubjectsForGroup(groupId));
  }

  onSubjectChange(subjectId: number): void {
    this.selectedSubjectId.set(subjectId);
    this.journalData.set(null);
  }

  private getSubjectsForGroup(groupId: number): SubjectOption[] {
    const subjectsById = new Map<number, SubjectOption>();
    for (const assignment of this.assignments) {
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

  typeLabel(type: SubjectType | null): string {
    return type ? SUBJECT_TYPE_LABELS[type] : '';
  }

  subjectLabel(name: string, type: SubjectType | null): string {
    const typeLabel = this.typeLabel(type);
    return typeLabel ? `${name} · ${typeLabel}` : name;
  }

  onDateFromChange(value: Date | null): void {
    this.dateFrom.set(value);
  }

  onDateToChange(value: Date | null): void {
    this.dateTo.set(value);
  }

  loadJournal(): void {
    const groupId = this.selectedGroupId();
    const subjectId = this.selectedSubjectId();
    if (groupId === null || subjectId === null) return;
    if (!this.dateFromIso() || !this.dateToIso() || this.dateRangeInvalid()) {
      this.error.set('Проверьте период: дата начала должна быть не позже даты окончания.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.journalApi
      .getJournal(groupId, subjectId, this.dateFromIso(), this.dateToIso())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: response => {
          this.journalData.set(response);
        },
        error: () => {
          this.error.set(
            'Не удалось загрузить журнал. Проверьте соединение и попробуйте снова.',
          );
        },
      });
  }

  private findActiveSemester(semesters: SemesterResponse[]): SemesterResponse | null {
    const today = todayIso();
    return (
      semesters.find(semester => semester.active) ??
      semesters.find(semester => semester.dateFrom <= today && semester.dateTo >= today) ??
      null
    );
  }

  private applyDefaultDateRange(semester: SemesterResponse | null): void {
    const today = todayIso();
    const fallbackStart = new Date();
    fallbackStart.setDate(1);

    const fromIso = semester?.dateFrom ?? formatLocalDate(fallbackStart);
    const boundedToIso = semester?.dateTo && semester.dateTo < today ? semester.dateTo : today;
    const safeFromIso = fromIso <= boundedToIso ? fromIso : boundedToIso;

    this.dateFrom.set(parseLocalDate(safeFromIso));
    this.dateTo.set(parseLocalDate(boundedToIso));
  }
}
