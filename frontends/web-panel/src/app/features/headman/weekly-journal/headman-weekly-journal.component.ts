import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { addDays, formatDate, getMonday, isSameWeek } from '../../student/schedule/week-utils';

function isoWeekNumber(d: Date): number {
  const target = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const dayNum = (target.getUTCDay() + 6) % 7;
  target.setUTCDate(target.getUTCDate() - dayNum + 3);
  const firstThursday = new Date(Date.UTC(target.getUTCFullYear(), 0, 4));
  const firstThursdayDayNum = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstThursdayDayNum + 3);
  return 1 + Math.round((target.getTime() - firstThursday.getTime()) / (7 * 24 * 3600 * 1000));
}

type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled';

interface Lesson {
  id: number;
  subjectId: number;
  date: string;
  status: string;
  lessonNumber: number;
  startTime: string;
  endTime: string;
  weekType?: string;
}

interface Student {
  userId: number;
  displayName: string;
}

interface LessonColumn {
  lessonId: number;
  subjectId: number;
  subjectName: string;
  lessonNumber: number;
  startTime: string;
  date: string;
  header: string;
}

interface DayGroup {
  date: string;
  weekday: string;
  displayDate: string;
  columns: LessonColumn[];
}

const WEEKDAY_LABELS = ['ВС', 'ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ'] as const;

@Component({
  selector: 'app-headman-weekly-journal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatProgressSpinnerModule],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './headman-weekly-journal.component.html',
  styleUrls: ['./headman-weekly-journal.component.css'],
})
export class HeadmanWeeklyJournalComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly monday = signal<Date>(getMonday(new Date()));
  readonly students = signal<Student[]>([]);
  readonly lessons = signal<Lesson[]>([]);
  readonly subjects = signal<Map<number, string>>(new Map());
  /** key = `${lessonId}_${userId}` -> status */
  readonly statusMap = signal<Map<string, AttendanceStatus>>(new Map());
  /** cells currently saving (visual feedback) */
  readonly savingCells = signal<Set<string>>(new Set());
  /** cells that just saved successfully — flash indicator */
  readonly flashCells = signal<Set<string>>(new Set());

  readonly weekRange = computed(() => {
    const mon = this.monday();
    const sun = addDays(mon, 6);
    const fmt = (d: Date) => `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}`;
    return `${fmt(mon)} — ${fmt(sun)}`;
  });

  readonly isCurrentWeek = computed(() => isSameWeek(this.monday(), new Date()));

  readonly dayGroups = computed<DayGroup[]>(() => {
    const monday = this.monday();
    const subjects = this.subjects();
    const weekParity: 'ODD' | 'EVEN' = isoWeekNumber(monday) % 2 === 1 ? 'ODD' : 'EVEN';
    const byDate = new Map<string, Lesson[]>();
    for (const l of this.lessons()) {
      const wt = (l.weekType ?? 'ALL').toUpperCase();
      if (wt !== 'ALL' && wt !== weekParity) continue;
      const arr = byDate.get(l.date) ?? [];
      arr.push(l);
      byDate.set(l.date, arr);
    }

    const groups: DayGroup[] = [];
    for (let i = 0; i < 7; i++) {
      const d = addDays(monday, i);
      const iso = formatDate(d);
      const dayLessons = byDate.get(iso);
      if (!dayLessons || dayLessons.length === 0) continue;

      const sorted = dayLessons.slice().sort((a, b) => {
        if (a.lessonNumber !== b.lessonNumber) return a.lessonNumber - b.lessonNumber;
        return a.startTime.localeCompare(b.startTime);
      });

      const columns: LessonColumn[] = sorted.map(l => ({
        lessonId: l.id,
        subjectId: l.subjectId,
        subjectName: subjects.get(l.subjectId) ?? `Предмет #${l.subjectId}`,
        lessonNumber: l.lessonNumber,
        startTime: (l.startTime ?? '').slice(0, 5),
        date: l.date,
        header: `${(subjects.get(l.subjectId) ?? `#${l.subjectId}`).toUpperCase()} § ${l.lessonNumber} ${(l.startTime ?? '').slice(0, 5)}`,
      }));

      groups.push({
        date: iso,
        weekday: WEEKDAY_LABELS[d.getDay()],
        displayDate: `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}`,
        columns,
      });
    }
    return groups;
  });

  readonly sortedStudents = computed<Student[]>(() => {
    return this.students().slice().sort((a, b) =>
      a.displayName.localeCompare(b.displayName, 'ru'),
    );
  });

  readonly hasLessons = computed(() => this.dayGroups().length > 0);

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set('Не удалось определить группу. Обратитесь к администратору.');
      this.loading.set(false);
      return;
    }
    this.loadSubjects();
    this.loadWeek();
  }

  private loadSubjects(): void {
    this.headmanApi.listSubjects(0, 200).pipe(catchError(() => of(null))).subscribe(resp => {
      if (!resp?._embedded) return;
      const list: any[] = (Object.values(resp._embedded)[0] as any[]) ?? [];
      const map = new Map<number, string>();
      for (const s of list) map.set(s.id, s.name);
      this.subjects.set(map);
    });
  }

  loadWeek(): void {
    const user = this.auth.currentUser();
    if (!user?.groupId) return;
    const groupId = user.groupId;

    this.loading.set(true);
    this.error.set(null);

    const mon = this.monday();
    const sun = addDays(mon, 6);
    const dateFrom = formatDate(mon);
    const dateTo = formatDate(sun);

    forkJoin({
      members: this.headmanApi.getGroupMembers(0, 200).pipe(catchError(() => of(null))),
      lessons: this.headmanApi.getGroupLessons(groupId, dateFrom, dateTo).pipe(catchError(() => of(null))),
    }).subscribe(({ members, lessons }) => {
      if (members?._embedded) {
        const list: any[] = (Object.values(members._embedded)[0] as any[]) ?? [];
        this.students.set(
          list.map(m => ({
            userId: m.userId ?? m.id,
            displayName: m.fullName ?? m.displayName ?? `#${m.userId ?? m.id}`,
          })),
        );
      } else {
        this.students.set([]);
      }

      const embedded = lessons?._embedded;
      const rawLessons: any[] = embedded ? ((Object.values(embedded)[0] as any[]) ?? []) : [];
      const normalised: Lesson[] = rawLessons
        .map((entry: any) => entry?.content ?? entry)
        .filter(Boolean);
      this.lessons.set(normalised);

      // Fetch attendance for each lesson
      if (normalised.length === 0) {
        this.statusMap.set(new Map());
        this.loading.set(false);
        return;
      }

      forkJoin(
        normalised.map(l =>
          this.headmanApi
            .getLessonAttendance(l.id)
            .pipe(catchError(() => of(null))),
        ),
      ).subscribe(results => {
        const map = new Map<string, AttendanceStatus>();
        for (let i = 0; i < normalised.length; i++) {
          const lesson = normalised[i];
          const resp: any = results[i];
          const payload = resp?.content ?? resp;
          const entries: any[] = payload?.entries ?? [];
          for (const e of entries) {
            const status = (e.status ?? '').toLowerCase() as AttendanceStatus;
            if (!status) continue;
            map.set(`${lesson.id}_${e.userId}`, status);
          }
        }
        this.statusMap.set(map);
        this.loading.set(false);
      });
    });
  }

  prevWeek(): void {
    this.monday.set(addDays(this.monday(), -7));
    this.loadWeek();
  }

  nextWeek(): void {
    if (this.isCurrentWeek()) return;
    const next = addDays(this.monday(), 7);
    // Clamp to current week if jump lands in future
    const currentMonday = getMonday(new Date());
    if (next.getTime() > currentMonday.getTime()) {
      this.monday.set(currentMonday);
    } else {
      this.monday.set(next);
    }
    this.loadWeek();
  }

  getStatus(lessonId: number, userId: number): AttendanceStatus | undefined {
    return this.statusMap().get(`${lessonId}_${userId}`);
  }

  isSaving(lessonId: number, userId: number): boolean {
    return this.savingCells().has(`${lessonId}_${userId}`);
  }

  isFlashing(lessonId: number, userId: number): boolean {
    return this.flashCells().has(`${lessonId}_${userId}`);
  }

  onStatusClick(lessonId: number, userId: number, next: AttendanceStatus): void {
    const key = `${lessonId}_${userId}`;
    const current = this.statusMap().get(key);
    // Re-click active button — backend has no delete; treat as no-op.
    if (current === next) return;

    const prev = current;
    const map = new Map(this.statusMap());
    map.set(key, next);
    this.statusMap.set(map);

    const saving = new Set(this.savingCells());
    saving.add(key);
    this.savingCells.set(saving);

    this.headmanApi.markAttendance(lessonId, userId, next).pipe(
      catchError(() => {
        const rollback = new Map(this.statusMap());
        if (prev) rollback.set(key, prev); else rollback.delete(key);
        this.statusMap.set(rollback);
        this.snackBar.open('Не удалось сохранить. Повторите.', undefined, { duration: 4000 });
        return of(null);
      }),
    ).subscribe(result => {
      const s = new Set(this.savingCells());
      s.delete(key);
      this.savingCells.set(s);
      if (result !== null) {
        const f = new Set(this.flashCells());
        f.add(key);
        this.flashCells.set(f);
        setTimeout(() => {
          const f2 = new Set(this.flashCells());
          f2.delete(key);
          this.flashCells.set(f2);
        }, 600);
      }
    });
  }

}
