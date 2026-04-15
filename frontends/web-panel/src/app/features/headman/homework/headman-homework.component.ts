import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { AsyncPipe, CommonModule } from '@angular/common';
import { animate, style, transition, trigger } from '@angular/animations';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { StudentApiService } from '../../student/shared/student-api.service';
import { SubjectCacheService } from '../../student/shared/subject-cache.service';
import type { LessonResponse } from '../../student/shared/student-schedule.types';
import { addDays, formatDate, getMonday } from '../../student/schedule/week-utils';
import { WeekNavigatorComponent } from '../../../shared/week-navigator/week-navigator.component';
import { HomeworkCardComponent } from '../../../shared/homework-card/homework-card.component';
import { HomeworkInlineFormComponent } from './homework-inline-form/homework-inline-form.component';
import {
  HeadmanHomeworkApiService,
  HomeworkResponse,
} from './headman-homework-api.service';

interface LessonRow {
  lesson: LessonResponse;
  key: string; // `${date}-${lessonNumber}`
  subjectName$: ReturnType<SubjectCacheService['getName']>;
}

interface DayRow {
  date: string;
  dow: number; // 1..6
  label: string;
  lessons: LessonRow[];
}

const DAY_NAMES = [
  '', // placeholder — backend dayOfWeek is 1-indexed
  'Понедельник',
  'Вторник',
  'Среда',
  'Четверг',
  'Пятница',
  'Суббота',
];

const MONTH_ABBREV = [
  'янв', 'фев', 'мар', 'апр', 'мая', 'июн',
  'июл', 'авг', 'сен', 'окт', 'ноя', 'дек',
];

/**
 * Headman homework page — `/headman/homework`.
 *
 * Phase 61 / D-08 / D-11: недельный список пар группы старосты (Пн-Сб) с
 * inline-формой создания/редактирования ДЗ под каждой парой. НЕ использует
 * MatDialog — форма разворачивается как отдельная секция (animation expand).
 *
 * Data sources:
 * - Пары недели: `StudentApiService.getWeekLessons(groupId, mon, sat)` —
 *   уже использовался в Phase 51; тот же endpoint что у студента.
 * - ДЗ: `HeadmanHomeworkApiService.list(groupId, semesterId)` + клиентская
 *   фильтрация по диапазону недели (RESEARCH: не добавляем server-side
 *   ?from&to в Phase 61 — N ≤ 100 ДЗ/семестр).
 * - Активный семестр: `HeadmanApiService.listSemesters()` → первый active.
 *
 * editingState (single-shot signal): одновременно может быть открыта только
 * одна inline-форма — новая постановка editingState автоматически закрывает
 * предыдущую (Angular @if убирает компонент из DOM).
 *
 * Ownership: edit/delete иконки рендерятся только у своих ДЗ
 * (hw.publishedBy === currentUserId). Backend guards (D-05) — финальная
 * защита.
 */
@Component({
  selector: 'app-headman-homework',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    AsyncPipe,
    MatButtonModule,
    MatProgressSpinnerModule,
    WeekNavigatorComponent,
    HomeworkCardComponent,
    HomeworkInlineFormComponent,
  ],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate(
          '200ms cubic-bezier(0.16, 1, 0.3, 1)',
          style({ opacity: 1, transform: 'translateY(0)' }),
        ),
      ]),
    ]),
  ],
  template: `
    <div class="page-stack" [@routeFade]>
      <div class="page-header">
        <div>
          <span class="page-eyebrow">Староста</span>
          <h1 class="page-title">Домашние задания</h1>
          <p class="page-subtitle">Выберите пару и добавьте задание.</p>
        </div>
      </div>

      <app-week-navigator (weekChanged)="onWeekChanged($event)" />

      @if (!semesterId() && !loading()) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank" aria-hidden="true"></i>
          <h3>Нет активного семестра</h3>
          <p>Попросите администратора активировать текущий семестр.</p>
        </div>
      } @else if (loading()) {
        <div class="page-card"><mat-spinner diameter="32" /></div>
      } @else if (error()) {
        <div class="page-error" role="alert">
          <i class="ph ph-warning-circle" aria-hidden="true"></i>
          {{ error() }}
        </div>
      } @else if (days().length === 0) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank" aria-hidden="true"></i>
          <h3>На этой неделе пар нет</h3>
          <p>Выберите другую неделю через стрелки сверху.</p>
        </div>
      } @else {
        @for (day of days(); track day.date) {
          <section class="day-card">
            <header class="day-card__header">
              <span class="day-card__dow">{{ day.label }}</span>
              <span class="day-card__date">{{ formatDayDate(day.date) }}</span>
            </header>

            <ul class="lesson-list" role="list">
              @for (row of day.lessons; track row.key) {
                <li class="lesson-block">
                  <div class="lesson-block__header">
                    <span class="lesson-block__num">Пара № {{ row.lesson.lessonNumber }}</span>
                    <span class="lesson-block__time">
                      {{ shortTime(row.lesson.startTime) }}–{{ shortTime(row.lesson.endTime) }}
                    </span>
                    <span class="lesson-block__subject">
                      {{ (row.subjectName$ | async) ?? 'Предмет' }}
                    </span>
                    @if (isLessonCancelled(row.lesson)) {
                      <span class="lesson-block__cancelled">отменена</span>
                    }
                  </div>

                  <div class="lesson-block__homeworks" role="list">
                    @for (hw of homeworksFor(row.key); track hw.id) {
                      <app-homework-card
                        [homework]="hw"
                        [subjectName]="(row.subjectName$ | async) ?? null"
                        [showEditActions]="isOwn(hw)"
                        (edit)="onEditHomework(row, $any($event))"
                        (delete)="onDeleteHomework($any($event))" />
                    }
                  </div>

                  @if (isEditing(row.key)) {
                    <app-homework-inline-form
                      [subjectId]="row.lesson.subjectId"
                      [subjectName]="(row.subjectName$ | async) ?? 'Предмет'"
                      [lessonDate]="row.lesson.date"
                      [lessonNumber]="row.lesson.lessonNumber"
                      [groupId]="groupId()!"
                      [semesterId]="semesterId()!"
                      [initialHomework]="editingHomework()"
                      (saved)="onSaved()"
                      (cancelled)="onCancelled()" />
                  } @else if (!isLessonCancelled(row.lesson)) {
                    <button
                      type="button"
                      class="lesson-block__add"
                      (click)="onAddHomework(row)"
                      [attr.aria-label]="'Добавить задание на пару «' + ((row.subjectName$ | async) ?? 'Предмет') + '»'">
                      <i class="ph ph-plus" aria-hidden="true"></i>
                      Добавить задание
                    </button>
                  }
                </li>
              }
            </ul>
          </section>
        }
      }
    </div>
  `,
  styles: [`
    .page-stack { display: flex; flex-direction: column; gap: var(--space-4, 16px); }
    .page-header__subtitle,
    .page-subtitle {
      margin: 4px 0 0;
      font-size: 0.9rem;
      color: var(--text-secondary, #555);
    }
    .day-card {
      background: var(--bg-secondary, #fafafa);
      border: 1px solid var(--border-subtle, #e0e0e0);
      border-radius: var(--radius-lg, 12px);
      padding: var(--space-4, 16px);
    }
    .day-card__header {
      display: flex;
      align-items: baseline;
      gap: 12px;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--border-subtle, #e0e0e0);
    }
    .day-card__dow {
      font-weight: 600;
      font-size: 1rem;
      color: var(--text-primary);
    }
    .day-card__date {
      font-size: 0.85rem;
      color: var(--text-secondary, #666);
    }
    .lesson-list {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .lesson-block {
      padding: 12px;
      background: var(--bg-elevated, #fff);
      border: 1px solid var(--border-subtle, #e8e8e8);
      border-radius: var(--radius-md, 8px);
    }
    .lesson-block__header {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 8px;
      font-size: 0.85rem;
    }
    .lesson-block__num {
      font-weight: 600;
      color: var(--text-primary);
    }
    .lesson-block__time {
      font-family: var(--font-mono, monospace);
      font-variant-numeric: tabular-nums;
      color: var(--text-secondary, #555);
    }
    .lesson-block__subject {
      margin-left: auto;
      font-weight: 500;
      color: var(--accent-secondary, #1976d2);
    }
    .lesson-block__cancelled {
      padding: 2px 8px;
      border-radius: 10px;
      background: #ffebee;
      color: #c62828;
      font-size: 0.72rem;
      font-weight: 500;
    }
    .lesson-block__homeworks {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .lesson-block__homeworks:empty { display: none; }
    .lesson-block__add {
      margin-top: 8px;
      width: 100%;
      padding: 8px 12px;
      border: 1px dashed var(--accent-secondary, #1976d2);
      border-radius: var(--radius-md, 8px);
      background: transparent;
      color: var(--accent-secondary, #1976d2);
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      font-size: 0.9rem;
      font-weight: 500;
      transition: background 150ms ease;
    }
    .lesson-block__add:hover {
      background: color-mix(in oklab, var(--accent-secondary, #1976d2) 8%, transparent);
    }
    .page-empty {
      padding: 32px;
      text-align: center;
      color: var(--text-secondary, #555);
    }
    .page-empty i { font-size: 2.5rem; color: var(--text-muted, #999); }
    .page-empty h3 { margin: 12px 0 4px; }
    .page-error {
      padding: 12px 16px;
      border-radius: var(--radius-md, 8px);
      background: #ffebee;
      color: #c62828;
      display: flex;
      align-items: center;
      gap: 8px;
    }
  `],
})
export class HeadmanHomeworkComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly studentApi = inject(StudentApiService);
  private readonly homeworkApi = inject(HeadmanHomeworkApiService);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly destroyRef = inject(DestroyRef);

  readonly groupId = signal<number | null>(null);
  readonly currentUserId = signal<number | null>(null);
  readonly semesterId = signal<number | null>(null);
  readonly monday = signal<Date>(getMonday(new Date()));
  readonly lessons = signal<LessonResponse[]>([]);
  readonly homeworks = signal<HomeworkResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /** Single-shot: only one inline form open at a time. */
  readonly editingState = signal<{
    lessonKey: string;
    homework: HomeworkResponse | null;
  } | null>(null);

  readonly editingHomework = computed(() => this.editingState()?.homework ?? null);

  readonly days = computed<DayRow[]>(() => {
    const monday = this.monday();
    const lessons = this.lessons();
    const byDow = new Map<number, LessonRow[]>();
    for (const l of lessons) {
      if (l.dayOfWeek < 1 || l.dayOfWeek > 6) continue;
      const row: LessonRow = {
        lesson: l,
        key: `${l.date}-${l.lessonNumber}`,
        subjectName$: this.subjectCache.getName(l.subjectId),
      };
      const arr = byDow.get(l.dayOfWeek) ?? [];
      arr.push(row);
      byDow.set(l.dayOfWeek, arr);
    }
    const result: DayRow[] = [];
    for (let dow = 1; dow <= 6; dow++) {
      const arr = byDow.get(dow);
      if (!arr || arr.length === 0) continue;
      const date = formatDate(addDays(monday, dow - 1));
      arr.sort((a, b) => a.lesson.lessonNumber - b.lesson.lessonNumber);
      result.push({
        date,
        dow,
        label: DAY_NAMES[dow],
        lessons: arr,
      });
    }
    return result;
  });

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set('Не удалось определить группу. Обратитесь к администратору.');
      this.loading.set(false);
      return;
    }
    this.groupId.set(user.groupId);
    this.currentUserId.set(user.id);
    this.loadActiveSemester();
  }

  private loadActiveSemester(): void {
    this.headmanApi
      .listSemesters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resp: any) => {
          const embedded = resp?._embedded;
          const list: any[] = embedded
            ? ((Object.values(embedded)[0] as any[]) ?? [])
            : Array.isArray(resp)
              ? resp
              : [];
          const active = list.find(s => s?.active === true);
          if (!active) {
            this.semesterId.set(null);
            this.loading.set(false);
            return;
          }
          this.semesterId.set(active.id);
          this.reload();
        },
        error: () => {
          this.error.set('Не удалось загрузить активный семестр.');
          this.loading.set(false);
        },
      });
  }

  onWeekChanged(e: { monday: Date }): void {
    this.monday.set(e.monday);
    // Сворачиваем открытую форму при смене недели — иначе контекст пары
    // может не совпадать с отображаемой неделей.
    this.editingState.set(null);
    if (this.semesterId() !== null) {
      this.reload();
    }
  }

  private reload(): void {
    const g = this.groupId();
    const s = this.semesterId();
    if (!g || !s) return;
    this.loading.set(true);
    this.error.set(null);
    const monday = this.monday();
    const saturday = addDays(monday, 5);

    forkJoin({
      lessons: this.studentApi
        .getWeekLessons(g, formatDate(monday), formatDate(saturday))
        .pipe(catchError(() => of([] as LessonResponse[]))),
      homeworks: this.homeworkApi
        .list(g, s)
        .pipe(catchError(() => of([] as HomeworkResponse[]))),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        map(({ lessons, homeworks }) => ({ lessons, homeworks })),
      )
      .subscribe({
        next: ({ lessons, homeworks }) => {
          this.lessons.set(lessons);
          this.homeworks.set(homeworks);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Не удалось загрузить расписание и задания.');
          this.loading.set(false);
        },
      });
  }

  homeworksFor(lessonKey: string): HomeworkResponse[] {
    const [date, numStr] = this.splitLessonKey(lessonKey);
    const num = Number(numStr);
    return this.homeworks().filter(
      hw => hw.lessonDate === date && hw.lessonNumber === num,
    );
  }

  private splitLessonKey(key: string): [string, string] {
    // key shape: YYYY-MM-DD-N. Split on the last dash — the date itself has
    // three dashes already, so slicing by lastIndexOf is correct.
    const i = key.lastIndexOf('-');
    return [key.slice(0, i), key.slice(i + 1)];
  }

  isEditing(lessonKey: string): boolean {
    return this.editingState()?.lessonKey === lessonKey;
  }

  isOwn(hw: HomeworkResponse): boolean {
    const uid = this.currentUserId();
    return uid !== null && hw.publishedBy === uid;
  }

  isLessonCancelled(l: LessonResponse): boolean {
    return (l.status ?? '').toString().toUpperCase() === 'CANCELLED';
  }

  shortTime(t: string | null | undefined): string {
    if (!t) return '';
    return t.length >= 5 ? t.slice(0, 5) : t;
  }

  formatDayDate(date: string): string {
    const [y, m, d] = date.split('-');
    const mi = Math.max(0, Math.min(11, parseInt(m, 10) - 1));
    return `${parseInt(d, 10)} ${MONTH_ABBREV[mi]} ${y}`;
  }

  onAddHomework(row: LessonRow): void {
    this.editingState.set({ lessonKey: row.key, homework: null });
  }

  onEditHomework(row: LessonRow, hw: HomeworkResponse): void {
    this.editingState.set({ lessonKey: row.key, homework: hw });
  }

  onDeleteHomework(hw: HomeworkResponse): void {
    if (!confirm(`Удалить задание «${hw.title}»?`)) return;
    this.homeworkApi
      .delete(hw.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(),
        error: () => {
          this.error.set('Не удалось удалить задание. Попробуйте ещё раз.');
        },
      });
  }

  onSaved(): void {
    this.editingState.set(null);
    this.reload();
  }

  onCancelled(): void {
    this.editingState.set(null);
  }
}
