import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { forkJoin } from 'rxjs';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { StatCardComponent } from '../../admin/dashboard/stat-card/stat-card.component';

/**
 * Headman cabinet landing page — `/headman/dashboard`.
 *
 * Delivers HEAD-WEB-02: the first screen after a headman logs in.
 * Shows group size, pending excuse tickets, today's lesson, and pending
 * late check-in requests in a 2×2 stat grid.
 *
 * Data is loaded via four parallel HttpClient calls merged with forkJoin.
 * Excuse/late-checkin endpoints may 404 (deferred backend) — catchError
 * returns null and the component displays 0 for those tiles.
 */
@Component({
  selector: 'app-headman-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterModule, StatCardComponent, DatePipe],
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
  host: { '[@routeFade]': '' },
  template: `
    @if (loading()) {
      <div class="stat-grid skeleton-grid" aria-hidden="true">
        @for (i of [1,2,3,4]; track i) {
          <div class="skeleton-card"></div>
        }
      </div>
      <span aria-live="polite" class="sr-only">Загрузка...</span>
    } @else if (error()) {
      <div class="page-error">
        <i class="ph ph-warning-circle"></i>
        {{ error() }}
      </div>
    } @else {
      <div class="page-stack">
        <!-- Page header -->
        <div class="page-header">
          <div>
            <span class="page-eyebrow">Кабинет старосты</span>
            <h1 class="page-title">{{ greeting() }}</h1>
            <p class="page-date">{{ dateLabel() }}</p>
          </div>
        </div>

        <!-- Stat grid row 1: member count + excuse tickets -->
        <div class="stat-grid">
          <app-stat-card
            [value]="memberCount()"
            label="Студентов в группе"
            icon="ph ph-users"
            accent="primary"
            routerLink="/headman/group"
            style="cursor:pointer" />
          <app-stat-card
            [value]="pendingExcuses()"
            label="Тикеты о пропуске"
            icon="ph ph-file-text"
            accent="warning" />
        </div>

        <!-- Stat grid row 2: today's lesson card + late check-ins -->
        <div class="stat-grid">
          <!-- Today's lesson card (custom, not StatCardComponent) -->
          <div class="page-card today-lesson-card"
               [class.has-lesson]="todayLesson()">
            @if (todayLesson()) {
              <i class="ph ph-chalkboard-teacher"></i>
              <p class="lesson-name">{{ todayLesson()!.subjectName }}</p>
              <p class="lesson-time">
                {{ todayLesson()!.startTime | date:'HH:mm' }} –
                {{ todayLesson()!.endTime | date:'HH:mm' }}
              </p>
              @if (todayLesson()!.room) {
                <p class="lesson-room">{{ todayLesson()!.room }}</p>
              }
              <span class="status-chip" [class.active]="todayLesson()!.status === 'ACTIVE'">
                {{ todayLesson()!.status === 'ACTIVE' ? 'Идёт' : 'Запланирована' }}
              </span>
            } @else {
              <i class="ph ph-calendar-x"></i>
              <p class="no-lesson">Нет пары сегодня</p>
            }
          </div>

          <app-stat-card
            [value]="pendingLateCheckins()"
            label="Запросы поздней отметки"
            icon="ph ph-clock-countdown"
            accent="info" />
        </div>
      </div>
    }
  `,
  styles: [`
    .stat-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: var(--space-5);
      margin-bottom: var(--space-5);
    }
    @media (max-width: 600px) {
      .stat-grid { grid-template-columns: 1fr; }
    }
    .today-lesson-card {
      min-height: 80px;
      border-left: 4px solid var(--border-subtle);
    }
    .today-lesson-card.has-lesson {
      border-left-color: var(--accent-warning);
    }
    .lesson-name {
      font-family: var(--font-heading);
      font-weight: 600;
      font-size: var(--text-base);
    }
    .lesson-time {
      font-family: var(--font-mono);
      font-size: var(--text-xs);
      color: var(--text-secondary);
      font-variant-numeric: tabular-nums;
    }
    .lesson-room {
      font-size: var(--text-xs);
      color: var(--text-muted);
    }
    .no-lesson {
      font-family: var(--font-heading);
      font-weight: 600;
      font-size: var(--text-base);
      color: var(--text-secondary);
    }
    .skeleton-card {
      height: 100px;
      background: var(--bg-elevated);
      border-radius: var(--radius-md);
      animation: dashboard-shimmer 1.5s ease-in-out infinite;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }
    .page-date {
      font-size: var(--text-sm);
      color: var(--text-secondary);
      margin-top: var(--space-1);
    }
  `],
})
export class HeadmanDashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly headmanApi = inject(HeadmanApiService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly memberCount = signal(0);
  readonly todayLesson = signal<any>(null);
  readonly pendingExcuses = signal(0);
  readonly pendingLateCheckins = signal(0);

  private readonly _now = signal(new Date());

  readonly greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 12) return 'Доброе утро';
    if (hour < 17) return 'Добрый день';
    return 'Добрый вечер';
  });

  readonly dateLabel = computed(() =>
    this._now().toLocaleDateString('ru-RU', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }),
  );

  ngOnInit(): void {
    this.loading.set(true);
    const groupId = this.auth.currentUser()?.groupId;
    if (!groupId) {
      this.error.set('Не удалось загрузить данные группы. Попробуйте обновить страницу.');
      this.loading.set(false);
      return;
    }

    forkJoin([
      this.headmanApi.getGroupMembers(0, 1),
      this.headmanApi.getTodayLessons(groupId).pipe(catchError(() => of(null))),
      this.headmanApi.getPendingExcuses().pipe(catchError(() => of(null))),
      this.headmanApi.getPendingLateCheckins().pipe(catchError(() => of(null))),
    ]).subscribe({
      next: ([members, lessons, excuses, lateCheckins]) => {
        this.memberCount.set((members as any)?.page?.totalElements ?? 0);

        // Find first active or planned lesson from today's embedded list
        const embedded = (lessons as any)?._embedded;
        const lessonList: any[] = embedded
          ? (Object.values(embedded)[0] as any[])
          : [];
        this.todayLesson.set(lessonList[0] ?? null);

        this.pendingExcuses.set((excuses as any)?.page?.totalElements ?? 0);
        this.pendingLateCheckins.set((lateCheckins as any)?.page?.totalElements ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить данные группы. Попробуйте обновить страницу.');
        this.loading.set(false);
      },
    });
  }
}
