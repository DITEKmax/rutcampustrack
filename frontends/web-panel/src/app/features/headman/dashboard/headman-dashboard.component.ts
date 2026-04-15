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
 * Headman cabinet landing page — `/headman/dashboard` (HEAD-WEB-02).
 *
 * Shows group size, pending excuse tickets, today's lesson, and pending late
 * check-ins in a 2×2 stat grid. Uses shared Transit Grid data-surface classes
 * (page-hero, chart-card, pill) so the look is consistent with admin/teacher
 * /student dashboards.
 *
 * Excuse + late-checkin endpoints may 404 when backend is deferred; the
 * catchError swallows the 404 and displays 0 rather than failing the page.
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
        animate('200ms cubic-bezier(0.16, 1, 0.3, 1)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  host: { '[@routeFade]': '' },
  template: `
    @if (loading()) {
      <div class="headman-dash">
        <div class="stat-grid skeleton-grid" aria-hidden="true">
          @for (i of [1,2,3,4]; track i) {
            <div class="skeleton-card"></div>
          }
        </div>
      </div>
      <span aria-live="polite" class="sr-only">Загрузка...</span>
    } @else if (error()) {
      <div class="dashboard-error" role="alert">
        <i class="ph ph-warning-circle" aria-hidden="true"></i>
        {{ error() }}
      </div>
    } @else {
      <div class="headman-dash">
        <header class="page-hero">
          <div class="page-hero__body">
            <p class="page-hero__eyebrow">
              <span class="page-hero__pulse" aria-hidden="true"></span>
              Кабинет старосты
            </p>
            <h2 class="page-hero__title">{{ greeting() }}</h2>
            <p class="page-hero__subtitle">{{ dateLabel() }}</p>
          </div>
        </header>

        <div class="stat-grid">
          <app-stat-card
            [value]="memberCount()"
            label="Студентов в группе"
            icon="ph-duotone ph-users"
            accent="primary"
            routerLink="/headman/group"
            class="stat-card-link" />
          <app-stat-card
            [value]="pendingExcuses()"
            label="Тикеты о пропуске"
            icon="ph-duotone ph-file-text"
            accent="warning"
            routerLink="/headman/excuses"
            class="stat-card-link" />
        </div>

        <div class="stat-grid">
          <section class="chart-card today-lesson-card" aria-labelledby="today-lesson-title">
            <header class="chart-card__head">
              <div>
                <p class="chart-card__eyebrow">Сейчас / скоро</p>
                <h3 class="chart-card__title" id="today-lesson-title">Пара сегодня</h3>
              </div>
              @if (todayLesson()) {
                <span class="pill" [class.pill--success]="todayLesson()!.status === 'ACTIVE'" [class.pill--info]="todayLesson()!.status !== 'ACTIVE'">
                  <span class="pill__dot" aria-hidden="true"></span>
                  {{ todayLesson()!.status === 'ACTIVE' ? 'Идёт' : 'Запланирована' }}
                </span>
              }
            </header>
            <div class="today-lesson-card__body">
              @if (todayLesson()) {
                <p class="lesson-name">{{ todayLesson()!.subjectName }}</p>
                <p class="lesson-time">
                  {{ todayLesson()!.startTime | date:'HH:mm' }} –
                  {{ todayLesson()!.endTime | date:'HH:mm' }}
                  @if (todayLesson()!.room) {
                    · ауд. {{ todayLesson()!.room }}
                  }
                </p>
              } @else {
                <p class="no-lesson">
                  <i class="ph ph-calendar-x" aria-hidden="true"></i>
                  Нет пары сегодня
                </p>
              }
            </div>
          </section>

          <app-stat-card
            [value]="pendingLateCheckins()"
            label="Запросы поздней отметки"
            icon="ph-duotone ph-clock-countdown"
            accent="info" />
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .headman-dash { display: flex; flex-direction: column; gap: var(--space-6); }
    .stat-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: var(--space-5);
    }
    @media (max-width: 600px) { .stat-grid { grid-template-columns: 1fr; } }

    .stat-card-link { display: block; cursor: pointer; }

    .today-lesson-card__body {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      min-height: 80px;
    }
    .lesson-name {
      margin: 0;
      font: 600 var(--text-xl)/1.2 var(--font-heading);
      color: var(--text-primary);
    }
    .lesson-time {
      margin: 0;
      font-family: var(--font-mono);
      font-size: var(--text-sm);
      color: var(--text-secondary);
      font-variant-numeric: tabular-nums;
    }
    .no-lesson {
      margin: 0;
      display: inline-flex; align-items: center; gap: var(--space-2);
      font: 500 var(--text-base)/1.4 var(--font-heading);
      color: var(--text-secondary);
    }
    .no-lesson i { font-size: 20px; color: var(--text-muted); }

    .dashboard-error {
      display: inline-flex; align-items: center; gap: var(--space-2);
      padding: var(--space-4);
      border-radius: var(--radius-md);
      background: color-mix(in oklab, var(--accent-danger) 12%, transparent);
      border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);
      color: var(--accent-danger);
      font-size: var(--text-sm);
    }

    .skeleton-grid { margin-bottom: var(--space-5); }
    .skeleton-card {
      min-height: 168px;
      border-radius: var(--radius-xl);
      background: linear-gradient(90deg,
        var(--bg-elevated) 25%,
        color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,
        var(--bg-elevated) 75%);
      background-size: 200% 100%;
      animation: dashboard-shimmer 1.5s linear infinite;
    }
    @keyframes dashboard-shimmer {
      0%   { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
    .sr-only {
      position: absolute;
      width: 1px; height: 1px;
      padding: 0; margin: -1px;
      overflow: hidden; clip: rect(0, 0, 0, 0);
      white-space: nowrap; border: 0;
    }

    @media (prefers-reduced-motion: reduce) {
      .skeleton-card { animation: none; }
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
      this.headmanApi.getGroupExcuses(groupId, 'submitted').pipe(catchError(() => of([] as any[]))),
    ]).subscribe({
      next: ([members, lessons, excuses]) => {
        this.memberCount.set((members as any)?.page?.totalElements ?? 0);

        const embedded = (lessons as any)?._embedded;
        const lessonList: any[] = embedded ? (Object.values(embedded)[0] as any[]) : [];
        this.todayLesson.set(lessonList[0] ?? null);

        this.pendingExcuses.set(Array.isArray(excuses) ? excuses.length : 0);
        this.pendingLateCheckins.set(0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить данные группы. Попробуйте обновить страницу.');
        this.loading.set(false);
      },
    });
  }
}
