import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';

import { HeadmanApiService } from '../shared/headman-api.service';
import { HeadmanStompService } from '../shared/headman-stomp.service';
import { AuthService } from '../../../core/auth/auth.service';
import { SubjectCacheService } from '../../student/shared/subject-cache.service';
import { NotificationCenterService } from '../../../core/notifications/notification-center.service';
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
 * - STOMP `/topic/group/{groupId}/headman` — real-time additions (новые заявки).
 * - REST `GET /api/schedule/groups/{gid}/lessons?dateFrom..dateTo` — обогащение
 *   карточек названием предмета, датой и номером пары (бэкенд-DTO
 *   LateCheckinRequestResponse несёт только lessonId).
 * - NotificationCenterService — слушаем {@code late_checkin.decided}, чтобы
 *   убирать карточку, если староста одобрил/отклонил запрос через Telegram.
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
              Заявки появятся здесь автоматически — когда студент запросит подтверждение.
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
                <div class="lcr-card__info">
                  <strong class="lcr-card__student">{{ req.studentName }}</strong>
                  <span class="lcr-card__lesson">{{ formatLesson(req) }}</span>
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
      .lcr-card__info { display: flex; flex-direction: column; gap: 2px; }
      .lcr-card__student {
        font-weight: 600;
        color: var(--text-primary);
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
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly center = inject(NotificationCenterService);
  private readonly destroyRef = inject(DestroyRef);

  readonly requests = signal<LateCheckinRequestView[]>([]);
  readonly loading = signal<boolean>(true);
  readonly loadError = signal<string | null>(null);
  readonly busyId = signal<string | null>(null);

  readonly noGroup = computed(() => this.auth.currentUser()?.groupId == null);

  private realtimeSub: Subscription | null = null;

  private readonly ruDays = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];

  ngOnInit(): void {
    const user = this.auth.currentUser();
    const groupId = user?.groupId ?? null;

    this.fetch(groupId);

    if (groupId != null) {
      this.stomp.connect(groupId, () => this.auth.accessToken());
      this.realtimeSub = this.stomp.lateCheckinRequested$.subscribe(payload => {
        // Append if not already present (duplicates possible on reconnect replays).
        const existing = this.requests();
        if (existing.some(r => r.id === payload.request_id)) return;
        const enriched: LateCheckinRequestView = {
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
          lessonNumber: payload.lesson_number ?? null,
          lessonDate: payload.lesson_date ?? null,
          subjectId: payload.subject_id ?? null,
          subjectName: payload.subject_name ?? null,
        };
        this.requests.set([...existing, enriched]);
      });
    }

    // Авто-закрытие карточек, когда староста принял решение через Telegram
    // (см. NotificationCenterService — слушает оба топика группы).
    this.center.onEvent$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(envelope => {
        if (envelope.type !== 'late_checkin.decided') return;
        const requestId = envelope.payload?.['request_id'];
        if (typeof requestId !== 'string') return;
        this.requests.set(this.requests().filter(r => r.id !== requestId));
      });
  }

  ngOnDestroy(): void {
    this.realtimeSub?.unsubscribe();
    this.stomp.disconnect();
  }

  private fetch(groupId: number | null): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.getPendingLateCheckins().subscribe({
      next: list => {
        const pending = list.filter(r => r.status === 'pending');
        this.requests.set(pending);
        this.loading.set(false);
        if (groupId != null && pending.length > 0) {
          this.enrichFromSchedule(groupId, pending);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loadError.set(this.describeLoadError(err));
        this.loading.set(false);
      },
    });
  }

  /**
   * Подтягиваем расписание группы за ±7 дней от сегодня и обогащаем карточки
   * деталями пар (subjectId, lessonNumber, lessonDate). Для subjectName
   * используем существующий {@link SubjectCacheService}, чтобы переиспользовать
   * кэш со страницами студента.
   *
   * Если какой-то lessonId не найден в окне дат — не страшно: в карточке
   * останется «Пара #{lessonId}» вместо обогащённой строки, и ошибки в UI нет.
   */
  private enrichFromSchedule(groupId: number, list: LateCheckinRequestView[]): void {
    const today = new Date();
    const from = new Date(today);
    from.setDate(from.getDate() - 7);
    const to = new Date(today);
    to.setDate(to.getDate() + 7);
    const dateFrom = from.toISOString().slice(0, 10);
    const dateTo = to.toISOString().slice(0, 10);

    this.api.getGroupLessons(groupId, dateFrom, dateTo).subscribe({
      next: resp => {
        const lessons = this.extractLessons(resp);
        const byId = new Map<number, { lessonNumber: number; date: string; subjectId: number }>();
        for (const l of lessons) {
          if (l && typeof l.id === 'number') {
            byId.set(l.id, {
              lessonNumber: l.lessonNumber,
              date: l.date,
              subjectId: l.subjectId,
            });
          }
        }
        if (byId.size === 0) return;

        // Резолвим subjectName через общий кэш (он уже shareReplay'ит запросы).
        const subjectIds = new Set<number>();
        for (const details of byId.values()) subjectIds.add(details.subjectId);
        const subjectNames = new Map<number, string>();
        const pendingIds = [...subjectIds];

        const applyUpdate = () => {
          this.requests.update(curr =>
            curr.map(req => {
              const details = byId.get(req.lessonId);
              if (!details) return req;
              return {
                ...req,
                lessonNumber: details.lessonNumber,
                lessonDate: details.date,
                subjectId: details.subjectId,
                subjectName: subjectNames.get(details.subjectId) ?? req.subjectName ?? null,
              };
            }),
          );
        };

        if (pendingIds.length === 0) {
          applyUpdate();
          return;
        }

        let resolved = 0;
        for (const sid of pendingIds) {
          this.subjectCache.getName(sid).subscribe(name => {
            subjectNames.set(sid, name);
            resolved += 1;
            if (resolved === pendingIds.length) applyUpdate();
          });
        }
      },
      error: () => {
        // Обогащение опционально — на ошибке просто оставляем «Пара #id».
      },
    });
  }

  private extractLessons(resp: unknown): Array<{ id: number; lessonNumber: number; date: string; subjectId: number }> {
    // Backend отдаёт PagedModel: { _embedded: { lessonResponseList: [...] }, page: ... }
    // или напрямую массив. Поддерживаем оба варианта — defensive shape matching.
    if (!resp || typeof resp !== 'object') return [];
    const anyResp = resp as Record<string, unknown>;
    if (Array.isArray(anyResp)) return anyResp as any;
    const embedded = anyResp['_embedded'] as Record<string, unknown> | undefined;
    if (!embedded) return [];
    const firstKey = Object.keys(embedded)[0];
    return (firstKey ? (embedded[firstKey] as any[]) : []) as any;
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

  /**
   * «№3 Матанализ, пн 14.04» — если есть хотя бы subjectName или дата.
   * Иначе fallback «Пара #123».
   */
  formatLesson(req: LateCheckinRequestView): string {
    const hasDetails = req.lessonNumber != null || req.subjectName || req.lessonDate;
    if (!hasDetails) return `Пара #${req.lessonId}`;
    const parts: string[] = [];
    if (req.lessonNumber != null) parts.push(`№${req.lessonNumber}`);
    if (req.subjectName) parts.push(req.subjectName);
    const head = parts.join(' ').trim();
    const date = req.lessonDate ? this.formatRuDate(req.lessonDate) : '';
    return date ? `${head}, ${date}` : head || `Пара #${req.lessonId}`;
  }

  private formatRuDate(iso: string): string {
    // YYYY-MM-DD — показываем «пн 14.04».
    const parsed = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
    if (Number.isNaN(parsed.getTime())) return iso;
    const dow = this.ruDays[parsed.getDay()];
    const dd = String(parsed.getDate()).padStart(2, '0');
    const mm = String(parsed.getMonth() + 1).padStart(2, '0');
    return `${dow} ${dd}.${mm}`;
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
