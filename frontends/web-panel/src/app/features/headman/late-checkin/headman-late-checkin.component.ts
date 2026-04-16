import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';

import { HeadmanApiService } from '../shared/headman-api.service';
import { HeadmanStompService } from '../shared/headman-stomp.service';
import { AuthService } from '../../../core/auth/auth.service';
import type { LateCheckinRequestView } from './late-checkin.types';

/**
 * Headman cabinet — late-checkin approval page (`/headman/late-checkin`).
 *
 * Parallel web channel for the Telegram bot: the headman can approve or reject
 * student late-checkin requests without a Telegram account. Telegram-based
 * inline-button flow (notification-bot) continues to work in parallel —
 * `LateCheckinService.applyDecision` is idempotent so whichever channel wins
 * the race, the other becomes a harmless no-op.
 *
 * Data sources:
 * - REST `GET /api/attendance/late-checkin/pending` — initial list.
 * - STOMP `/topic/group/{groupId}/headman` — real-time additions.
 * - REST `POST /api/attendance/late-checkin/{id}/decision` — approve/reject.
 *
 * Graceful degradation: 403/404 on the list endpoint surfaces the empty state
 * (mock in component spec already relies on this behaviour).
 */
@Component({
  selector: 'app-headman-late-checkin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatSnackBarModule],
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
    <div class="page-stack">
      <div class="page-header">
        <div>
          <span class="page-eyebrow">Старостат</span>
          <h1 class="page-title">Запросы на отметку</h1>
        </div>
      </div>

      @if (loading()) {
        <div class="page-card">
          <div class="skeleton-row" aria-hidden="true"></div>
          <div class="skeleton-row" aria-hidden="true"></div>
          <span aria-live="polite" class="sr-only">Загрузка...</span>
        </div>
      } @else if (loadError()) {
        <div class="page-card">
          <div class="page-error" role="alert">
            <i class="ph ph-warning-circle"></i>
            {{ loadError() }}
          </div>
        </div>
      } @else if (requests().length === 0) {
        <div class="page-card">
          <div class="page-empty" role="status" aria-live="polite">
            <div class="page-empty__icon"><i class="ph ph-clock-countdown"></i></div>
            <p class="page-empty__title">Нет активных запросов</p>
            <p class="page-empty__text">
              Функция находится в разработке. Заявки появятся здесь автоматически.
            </p>
          </div>
        </div>
      } @else {
        <section class="page-card">
          <div class="page-card__header">
            <h2>На рассмотрении</h2>
            <span class="page-card__badge">{{ requests().length }}</span>
          </div>

          @for (req of requests(); track req.id) {
            <article class="lcr-card">
              <header class="lcr-card__head">
                <div>
                  <strong class="lcr-card__student">{{ req.studentName }}</strong>
                  <span class="lcr-card__lesson">Пара #{{ req.lessonId }}</span>
                </div>
                <span class="lcr-card__meta">
                  {{ req.createdAt | date: 'dd.MM.yyyy HH:mm' }}
                </span>
              </header>
              <div class="lcr-card__actions">
                <button
                  type="button"
                  class="btn-brand"
                  [disabled]="busyId() === req.id"
                  (click)="decide(req.id, true)"
                >
                  <i class="ph ph-check"></i> Подтвердить
                </button>
                <button
                  type="button"
                  class="btn-ghost"
                  [disabled]="busyId() === req.id"
                  (click)="decide(req.id, false)"
                >
                  <i class="ph ph-x"></i> Отклонить
                </button>
              </div>
            </article>
          }
        </section>
      }
    </div>
  `,
  styles: [
    `
      .lcr-card {
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-lg);
        padding: var(--space-4) var(--space-5);
        margin-bottom: var(--space-3);
        background: var(--bg-secondary);
        transition: border-color var(--duration-base) var(--ease-out);
      }
      .lcr-card:hover { border-color: var(--border-default); }
      .lcr-card__head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: var(--space-3);
        flex-wrap: wrap;
      }
      .lcr-card__student {
        font-weight: 600;
        color: var(--text-primary);
        margin-right: var(--space-2);
      }
      .lcr-card__lesson {
        font-size: 0.875rem;
        color: var(--text-secondary);
      }
      .lcr-card__meta {
        font-family: var(--font-mono);
        font-size: 0.8125rem;
        color: var(--text-muted);
      }
      .lcr-card__actions {
        display: flex;
        gap: var(--space-2);
        margin-top: var(--space-3);
      }
    `,
  ],
})
export class HeadmanLateCheckinComponent implements OnInit, OnDestroy {
  private readonly api = inject(HeadmanApiService);
  private readonly stomp = inject(HeadmanStompService);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);

  readonly requests = signal<LateCheckinRequestView[]>([]);
  readonly loading = signal<boolean>(true);
  readonly loadError = signal<string | null>(null);
  readonly busyId = signal<string | null>(null);

  readonly noGroup = computed(() => this.auth.currentUser()?.groupId == null);

  private realtimeSub: Subscription | null = null;

  ngOnInit(): void {
    const user = this.auth.currentUser();
    const groupId = user?.groupId ?? null;

    this.fetch();

    if (groupId != null) {
      this.stomp.connect(groupId, () => this.auth.accessToken());
      this.realtimeSub = this.stomp.lateCheckinRequested$.subscribe(payload => {
        // Append if not already present (duplicates possible on reconnect replays).
        const existing = this.requests();
        if (existing.some(r => r.id === payload.request_id)) return;
        const optimistic: LateCheckinRequestView = {
          id: payload.request_id,
          studentId: payload.user_id,
          groupId: payload.group_id,
          lessonId: payload.lesson_id,
          studentName: payload.student_name,
          status: 'pending',
          decisionBy: null,
          decisionAt: null,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };
        this.requests.set([...existing, optimistic]);
      });
    }
  }

  ngOnDestroy(): void {
    this.realtimeSub?.unsubscribe();
    this.stomp.disconnect();
  }

  private fetch(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.getPendingLateCheckins().subscribe({
      next: list => {
        this.requests.set(list.filter(r => r.status === 'pending'));
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loadError.set(this.describeLoadError(err));
        this.loading.set(false);
      },
    });
  }

  decide(requestId: string, approved: boolean): void {
    if (this.busyId() === requestId) return;
    this.busyId.set(requestId);
    this.api.decideLateCheckin(requestId, approved).subscribe({
      next: () => {
        this.requests.set(this.requests().filter(r => r.id !== requestId));
        this.busyId.set(null);
        this.snack.open(
          approved ? 'Запрос подтверждён' : 'Запрос отклонён',
          'OK',
          { duration: 2500 },
        );
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.snack.open(this.describeDecisionError(err), 'OK', { duration: 4000 });
      },
    });
  }

  private describeLoadError(err: HttpErrorResponse): string {
    if (err.status === 403) return 'Нет прав просмотра списка запросов.';
    if (err.status === 0) return 'Сервер недоступен. Повторите позже.';
    return 'Не удалось загрузить список запросов.';
  }

  private describeDecisionError(err: HttpErrorResponse): string {
    if (err.status === 403) return 'Нет прав на решение этого запроса.';
    if (err.status === 404) return 'Запрос не найден. Обновите страницу.';
    if (err.status === 400) return 'Некорректное тело запроса.';
    return 'Не удалось отправить решение. Попробуйте ещё раз.';
  }
}
