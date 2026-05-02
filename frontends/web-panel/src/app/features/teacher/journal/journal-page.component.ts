import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatInputModule } from '@angular/material/input';
import { JournalApiService } from './journal-api.service';
import { JournalGridComponent } from './journal-grid/journal-grid.component';
import { StatusLegendComponent } from './status-legend/status-legend.component';
import type {
  AssignmentResponse,
  GroupResponse,
  JournalResponse,
  SubjectOption,
} from './types';

@Component({
  selector: 'app-journal-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatSelectModule,
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
  readonly dateFrom = signal<string>('');
  readonly dateTo = signal<string>('');

  readonly canApply = computed(
    () => this.selectedGroupId() !== null && this.selectedSubjectId() !== null,
  );

  /** Cached assignments for group-subject filtering */
  private assignments: AssignmentResponse[] = [];

  ngOnInit(): void {
    // Set default date range: first day of current month to today
    const now = new Date();
    this.dateFrom.set(
      new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10),
    );
    this.dateTo.set(now.toISOString().slice(0, 10));

    // Load teacher's assignments, then filter groups
    this.journalApi.getMyAssignments().subscribe({
      next: assignments => {
        this.assignments = assignments;
        const groupIds = new Set(assignments.map(a => a.groupId));
        this.journalApi.getGroups().subscribe({
          next: groups => {
            this.groups.set(groups.filter(g => groupIds.has(g.id)));
          },
        });
      },
    });
  }

  onGroupChange(groupId: number): void {
    this.selectedGroupId.set(groupId);
    this.selectedSubjectId.set(null);
    this.journalData.set(null);
    this.subjects.set(this.getSubjectsForGroup(groupId));
  }

  private getSubjectsForGroup(groupId: number): SubjectOption[] {
    const subjectsById = new Map<number, SubjectOption>();
    for (const assignment of this.assignments) {
      if (assignment.groupId !== groupId) {
        continue;
      }
      subjectsById.set(assignment.subjectId, {
        id: assignment.subjectId,
        name: assignment.subjectName?.trim() || `Предмет #${assignment.subjectId}`,
      });
    }
    return Array.from(subjectsById.values())
      .sort((left, right) => left.name.localeCompare(right.name, 'ru'));
  }

  loadJournal(): void {
    const groupId = this.selectedGroupId();
    const subjectId = this.selectedSubjectId();
    if (groupId === null || subjectId === null) return;

    this.loading.set(true);
    this.error.set(null);

    this.journalApi
      .getJournal(groupId, subjectId, this.dateFrom(), this.dateTo())
      .subscribe({
        next: response => {
          this.journalData.set(response);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(
            'Не удалось загрузить журнал. Проверьте соединение и попробуйте снова.',
          );
          this.loading.set(false);
        },
      });
  }
}
