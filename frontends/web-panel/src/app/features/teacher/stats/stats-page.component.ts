import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { JournalApiService } from '../journal/journal-api.service';
import type { AssignmentResponse, GroupResponse, SubjectResponse } from '../journal/types';
import { deriveStudentChartData, deriveOverallStats, StudentChartData, OverallStats } from './stats-utils';
import { SubjectChartComponent } from './subject-chart/subject-chart.component';
import { OverallStatCardComponent } from './overall-stat-card/overall-stat-card.component';

@Component({
  selector: 'app-stats-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressBarModule,
    SubjectChartComponent,
    OverallStatCardComponent,
  ],
  templateUrl: './stats-page.component.html',
})
export class StatsPageComponent implements OnInit {
  private readonly journalApi = inject(JournalApiService);

  groups = signal<GroupResponse[]>([]);
  subjects = signal<SubjectResponse[]>([]);
  assignments = signal<AssignmentResponse[]>([]);
  selectedGroupId = signal<number | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  chartDataMap = signal<Map<string, StudentChartData[]>>(new Map());
  overallStats = signal<OverallStats>({ totalLessons: 0, attendanceRate: 0 });

  chartEntries = computed(() => {
    const entries: { name: string; data: StudentChartData[] }[] = [];
    this.chartDataMap().forEach((data, name) => entries.push({ name, data }));
    return entries;
  });

  ngOnInit(): void {
    this.journalApi.getMyAssignments().subscribe({
      next: assignments => {
        this.assignments.set(assignments);
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
    this.chartDataMap.set(new Map());
    this.overallStats.set({ totalLessons: 0, attendanceRate: 0 });
    this.error.set(null);

    const subjectIds = new Set(
      this.assignments()
        .filter(a => a.groupId === groupId)
        .map(a => a.subjectId),
    );

    this.journalApi.getSubjects().subscribe({
      next: subjects => {
        const filtered = subjects.filter(s => subjectIds.has(s.id));
        this.subjects.set(filtered);

        if (filtered.length === 0) {
          return;
        }

        this.loading.set(true);

        const today = new Date().toISOString().slice(0, 10);
        const semesterStart = `${new Date().getFullYear()}-01-01`;

        const requests = filtered.map(subject =>
          this.journalApi.getJournal(groupId, subject.id, semesterStart, today).pipe(
            catchError(() => of(null)),
          ),
        );

        forkJoin(requests).subscribe({
          next: responses => {
            const newMap = new Map<string, StudentChartData[]>();
            responses.forEach((response, index) => {
              if (response) {
                const subjectName = filtered[index].name;
                newMap.set(subjectName, deriveStudentChartData(response));
              }
            });
            this.chartDataMap.set(newMap);

            // Derive overall stats from all journal data combined
            const allRecords: StudentChartData[] = [];
            newMap.forEach(data => allRecords.push(...data));

            // Compute aggregated overall stats from all responses
            const validResponses = responses.filter(r => r !== null);
            if (validResponses.length > 0) {
              let totalLessons = 0;
              let totalPresent = 0;
              let totalRecords = 0;
              validResponses.forEach(r => {
                const stats = deriveOverallStats(r!);
                totalLessons += stats.totalLessons;
                // Re-count actual records for accurate rate
                r!.students.forEach(s => {
                  s.records.forEach(cell => {
                    if (cell.status !== 'cancelled') {
                      totalRecords++;
                      if (cell.status === 'present') totalPresent++;
                    }
                  });
                });
              });
              this.overallStats.set({
                totalLessons,
                attendanceRate: totalRecords > 0 ? Math.round((totalPresent / totalRecords) * 100) : 0,
              });
            }

            this.loading.set(false);
          },
          error: () => {
            this.error.set('Не удалось загрузить статистику. Попробуйте позже.');
            this.loading.set(false);
          },
        });
      },
    });
  }
}
