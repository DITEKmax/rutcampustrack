import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { trigger, transition, style, animate } from '@angular/animations';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { SubjectCacheService } from '../../student/shared/subject-cache.service';
import { NotificationCenterService } from '../../../core/notifications/notification-center.service';
import {
  EXCUSE_STATUS_LABELS,
  EXCUSE_TYPE_LABELS,
  ExcuseTicket,
  ExcuseType,
  ExcuseTicketStatus,
  LessonBrief,
} from './excuse.types';

/**
 * Headman excuses approval page (Phase 59, D-23/D-24).
 *
 * Responsibilities:
 * - Load excuse tickets for the headman's group via HeadmanApiService.
 * - Render pending (submitted) tickets on top with Approve / Reject actions.
 * - Render resolved (approved/rejected/draft) tickets below.
 * - Require a comment on reject; surface 403 / 409 from the backend with
 *   friendly Russian messages in MatSnackBar.
 *
 * Upstream contract (plan 59-01 / 59-04):
 * - GET  /api/attendance/excuses/group/{groupId}
 * - PATCH /api/attendance/excuses/{id}/status
 *   - 409 is returned when headman approves own ticket (D-13) or the ticket
 *     is already decided (D-18); both are mapped to a single user-friendly
 *     message — backend decides, UI just tells the user to refresh.
 */
@Component({
  selector: 'app-headman-excuses',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatSnackBarModule],
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
          <h1 class="page-title">Пропуски группы</h1>
        </div>
      </div>

      @if (noGroup()) {
        <div class="page-card">
          <div class="page-empty" role="status">
            <div class="page-empty__icon"><i class="ph ph-users"></i></div>
            <p class="page-empty__title">Группа не определена</p>
            <p class="page-empty__text">
              Ваш профиль не привязан к группе — функция недоступно. Обратитесь к администратору.
            </p>
          </div>
        </div>
      } @else if (loading()) {
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
      } @else {
        <!-- Pending section -->
        <section class="page-card">
          <div class="page-card__header">
            <h2>На рассмотрении</h2>
            <span class="page-card__badge">{{ pendingTickets().length }}</span>
          </div>

          @if (pendingTickets().length === 0) {
            <div class="page-empty">
              <p class="page-empty__title">Нет заявок на рассмотрении</p>
              <p class="page-empty__text">Новые заявки студентов появятся здесь автоматически.</p>
            </div>
          } @else {
            @for (ticket of pendingTickets(); track ticket.id) {
              <article class="excuse-card" data-testid="excuse-card">
                <header class="excuse-card__head">
                  <div>
                    <strong class="excuse-card__student">{{ ticket.studentName }}</strong>
                    <span class="excuse-card__type">{{ typeLabel(ticket.excuseType) }}</span>
                  </div>
                  <span class="excuse-card__meta">
                    {{ ticket.lessonIds.length }} урок(ов) · {{ ticket.createdAt | date:'dd.MM.yyyy HH:mm' }}
                  </span>
                </header>
                @if (ticket.lessons && ticket.lessons.length > 0) {
                  <ul class="excuse-card__lessons">
                    @for (lesson of ticket.lessons; track lesson.lessonId) {
                      <li>{{ formatLesson(lesson) }}</li>
                    }
                  </ul>
                }
                @if (ticket.comment) {
                  <p class="excuse-card__comment">{{ ticket.comment }}</p>
                }
                <div class="excuse-card__actions">
                  @if (rejectingId() !== ticket.id) {
                    <button
                      type="button"
                      class="btn-brand"
                      [disabled]="busyId() === ticket.id"
                      (click)="approve(ticket.id)"
                    >
                      <i class="ph ph-check"></i> Одобрить
                    </button>
                    <button
                      type="button"
                      class="btn-ghost"
                      [disabled]="busyId() === ticket.id"
                      (click)="startReject(ticket.id)"
                    >
                      <i class="ph ph-x"></i> Отклонить
                    </button>
                  } @else {
                    <div class="excuse-card__reject">
                      <label>
                        <span class="sr-only">Комментарий к отклонению</span>
                        <input
                          type="text"
                          class="form-input"
                          placeholder="Укажите причину отклонения"
                          [ngModel]="rejectComment()"
                          (ngModelChange)="rejectComment.set($event)"
                          [attr.aria-invalid]="validationError() ? 'true' : null"
                        />
                      </label>
                      @if (validationError()) {
                        <p class="form-error" role="alert">{{ validationError() }}</p>
                      }
                      <div class="excuse-card__reject-actions">
                        <button
                          type="button"
                          class="btn-brand"
                          [disabled]="busyId() === ticket.id"
                          (click)="confirmReject(ticket.id)"
                        >
                          Подтвердить отклонение
                        </button>
                        <button type="button" class="btn-ghost" (click)="cancelReject()">
                          Отмена
                        </button>
                      </div>
                    </div>
                  }
                </div>
              </article>
            }
          }
        </section>

        <!-- Resolved section -->
        @if (resolvedTickets().length > 0) {
          <section class="page-card">
            <div class="page-card__header">
              <h2>Решения</h2>
              <span class="page-card__badge">{{ resolvedTickets().length }}</span>
            </div>
            @for (ticket of resolvedTickets(); track ticket.id) {
              <article class="excuse-card excuse-card--resolved">
                <header class="excuse-card__head">
                  <div>
                    <strong class="excuse-card__student">{{ ticket.studentName }}</strong>
                    <span class="excuse-card__type">{{ typeLabel(ticket.excuseType) }}</span>
                  </div>
                  <span
                    class="excuse-card__status"
                    [class.excuse-card__status--approved]="ticket.status === 'approved'"
                    [class.excuse-card__status--rejected]="ticket.status === 'rejected'"
                  >
                    {{ statusLabel(ticket.status) }}
                  </span>
                </header>
                @if (ticket.decisionComment) {
                  <p class="excuse-card__comment">{{ ticket.decisionComment }}</p>
                }
              </article>
            }
          </section>
        }
      }
    </div>
  `,
  styles: [
    `
      .excuse-card {
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-lg);
        padding: var(--space-4) var(--space-5);
        margin-bottom: var(--space-3);
        background: var(--bg-secondary);
        transition: border-color var(--duration-base) var(--ease-out);
      }
      .excuse-card:hover { border-color: var(--border-default); }
      .excuse-card--resolved { opacity: 0.72; }
      .excuse-card__head {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: var(--space-3);
        flex-wrap: wrap;
      }
      .excuse-card__student {
        font-weight: 600;
        color: var(--text-primary);
        margin-right: var(--space-2);
      }
      .excuse-card__type {
        font-size: 0.875rem;
        color: var(--text-secondary);
      }
      .excuse-card__meta {
        font-family: var(--font-mono);
        font-size: 0.8125rem;
        color: var(--text-muted);
      }
      .excuse-card__comment {
        margin: var(--space-2) 0;
        color: var(--text-primary);
        font-size: 0.875rem;
        line-height: 1.5;
      }
      .excuse-card__lessons {
        list-style: none;
        padding: 0;
        margin: var(--space-2) 0;
        display: flex; flex-direction: column;
        gap: 4px;
        font-size: 0.875rem;
        color: var(--text-secondary);
      }
      .excuse-card__lessons li::before {
        content: '· ';
        color: var(--text-muted);
        margin-right: 4px;
      }
      .excuse-card__actions {
        display: flex;
        gap: var(--space-2);
        margin-top: var(--space-2);
        flex-wrap: wrap;
      }
      .excuse-card__reject { flex: 1; display: flex; flex-direction: column; gap: var(--space-2); }
      .excuse-card__reject-actions { display: flex; gap: var(--space-2); }
      /* Status chip — aligns with .pill vocabulary */
      .excuse-card__status {
        display: inline-flex; align-items: center; gap: 6px;
        padding: 4px 10px;
        border-radius: var(--radius-full);
        font: 500 0.6875rem/1 var(--font-mono);
        letter-spacing: 0.04em; text-transform: uppercase; white-space: nowrap;
        color: var(--text-secondary);
        background: color-mix(in oklab, var(--text-muted) 14%, transparent);
        border: 1px solid color-mix(in oklab, var(--text-muted) 26%, transparent);
      }
      .excuse-card__status--approved {
        color: var(--accent-primary);
        background: color-mix(in oklab, var(--accent-primary) 14%, transparent);
        border-color: color-mix(in oklab, var(--accent-primary) 30%, transparent);
      }
      .excuse-card__status--rejected {
        color: var(--accent-danger);
        background: color-mix(in oklab, var(--accent-danger) 14%, transparent);
        border-color: color-mix(in oklab, var(--accent-danger) 30%, transparent);
      }
      .form-input {
        width: 100%;
        padding: var(--space-2) var(--space-3);
        border: 1px solid var(--border-default);
        background: var(--bg-surface);
        color: var(--text-primary);
        border-radius: var(--radius-md);
        font: inherit;
        transition: border-color var(--duration-base) var(--ease-out);
      }
      .form-input:focus {
        outline: none;
        border-color: var(--accent-primary);
        box-shadow: var(--glow-primary);
      }
      .form-error { color: var(--accent-danger); font-size: 0.8125rem; margin: 0; }
      .sr-only {
        position: absolute;
        width: 1px; height: 1px;
        padding: 0; margin: -1px;
        overflow: hidden; clip: rect(0, 0, 0, 0);
        white-space: nowrap; border: 0;
      }
      .page-card__badge {
        display: inline-flex; align-items: center;
        margin-left: var(--space-2);
        padding: 2px 10px;
        border-radius: var(--radius-full);
        background: color-mix(in oklab, var(--text-muted) 16%, transparent);
        font-family: var(--font-mono);
        font-size: 0.75rem;
        color: var(--text-secondary);
      }
    `,
  ],
})
export class HeadmanExcusesComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly subjectCache = inject(SubjectCacheService);
  private readonly center = inject(NotificationCenterService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal<boolean>(false);
  readonly loadError = signal<string | null>(null);
  readonly tickets = signal<ExcuseTicket[]>([]);

  private readonly ruDays = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];

  /** id of the ticket for which the inline reject form is open. */
  readonly rejectingId = signal<string | null>(null);
  readonly rejectComment = signal<string>('');
  readonly validationError = signal<string | null>(null);
  /** id of the ticket currently hitting the network (disables its buttons). */
  readonly busyId = signal<string | null>(null);

  readonly pendingTickets = computed(() =>
    this.tickets().filter(t => t.status === 'submitted'),
  );
  readonly resolvedTickets = computed(() =>
    this.tickets().filter(t => t.status !== 'submitted'),
  );

  readonly noGroup = computed(() => this.auth.currentUser()?.groupId == null);

  ngOnInit(): void {
    this.loadTickets();

    // Новая заявка пришла через STOMP → перезагружаем список (REST — источник
    // правды с lessonIds, плюс обогащение через schedule).
    this.center.onEvent$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(envelope => {
        if (envelope.type === 'excuse.requested') {
          this.loadTickets();
          return;
        }
        // Решение принято (через TG или веб в другой вкладке) → обновляем,
        // чтобы карточка ушла из раздела «На рассмотрении».
        if (envelope.type === 'excuse.decided') {
          this.loadTickets();
        }
      });
  }

  loadTickets(): void {
    const groupId = this.auth.currentUser()?.groupId;
    if (groupId == null) {
      this.tickets.set([]);
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);
    this.headmanApi.getGroupExcuses(groupId).subscribe({
      next: list => {
        this.tickets.set(list);
        this.loading.set(false);
        this.enrichLessons(groupId, list);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(
          err.status === 403
            ? 'Доступ запрещён — только староста группы может просматривать заявки.'
            : 'Не удалось загрузить заявки. Попробуйте позже.',
        );
      },
    });
  }

  /**
   * Обогащаем тикеты деталями пар, чтобы в карточке отображался список
   * «№3 Матанализ, пн 14.04» вместо «N урок(ов)». Бэкенд-DTO несёт только
   * lessonIds, поэтому тянем расписание группы ±21 день (чтобы покрыть
   * свежие тикеты, а также просроченные заявки на прошлые пары).
   */
  private enrichLessons(groupId: number, tickets: ExcuseTicket[]): void {
    if (tickets.length === 0) return;
    const today = new Date();
    const from = new Date(today);
    from.setDate(from.getDate() - 30);
    const to = new Date(today);
    to.setDate(to.getDate() + 14);
    const dateFrom = from.toISOString().slice(0, 10);
    const dateTo = to.toISOString().slice(0, 10);

    this.headmanApi.getGroupLessons(groupId, dateFrom, dateTo).subscribe({
      next: resp => {
        const lessonsById = new Map<number, { lessonNumber: number; date: string; subjectId: number }>();
        const raw = this.extractLessons(resp);
        for (const l of raw) {
          if (l && typeof l.id === 'number') {
            lessonsById.set(l.id, {
              lessonNumber: l.lessonNumber,
              date: l.date,
              subjectId: l.subjectId,
            });
          }
        }
        if (lessonsById.size === 0) return;

        const subjectIds = new Set<number>();
        for (const d of lessonsById.values()) subjectIds.add(d.subjectId);
        const subjectNames = new Map<number, string>();
        const pendingIds = [...subjectIds];

        const applyUpdate = () => {
          this.tickets.update(curr =>
            curr.map(ticket => {
              const lessons: LessonBrief[] = ticket.lessonIds.map(id => {
                const details = lessonsById.get(id);
                if (!details) {
                  return { lessonId: id, lessonNumber: null, date: null, subjectId: null, subjectName: null };
                }
                return {
                  lessonId: id,
                  lessonNumber: details.lessonNumber,
                  date: details.date,
                  subjectId: details.subjectId,
                  subjectName: subjectNames.get(details.subjectId) ?? null,
                };
              });
              return { ...ticket, lessons };
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
        // Обогащение — опциональное улучшение UX. Без него останется «N урок(ов)».
      },
    });
  }

  private extractLessons(resp: unknown): Array<{ id: number; lessonNumber: number; date: string; subjectId: number }> {
    if (!resp || typeof resp !== 'object') return [];
    const anyResp = resp as Record<string, unknown>;
    if (Array.isArray(anyResp)) return anyResp as any;
    const embedded = anyResp['_embedded'] as Record<string, unknown> | undefined;
    if (!embedded) return [];
    const firstKey = Object.keys(embedded)[0];
    return (firstKey ? (embedded[firstKey] as any[]) : []) as any;
  }

  formatLesson(lesson: LessonBrief): string {
    const parts: string[] = [];
    if (lesson.lessonNumber != null) parts.push(`№${lesson.lessonNumber}`);
    if (lesson.subjectName) parts.push(lesson.subjectName);
    const head = parts.join(' ').trim();
    const date = lesson.date ? this.formatRuDate(lesson.date) : '';
    if (!head && !date) return `Пара #${lesson.lessonId}`;
    return date ? `${head}, ${date}` : head;
  }

  private formatRuDate(iso: string): string {
    const parsed = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
    if (Number.isNaN(parsed.getTime())) return iso;
    const dow = this.ruDays[parsed.getDay()];
    const dd = String(parsed.getDate()).padStart(2, '0');
    const mm = String(parsed.getMonth() + 1).padStart(2, '0');
    return `${dow} ${dd}.${mm}`;
  }

  approve(id: string): void {
    this.busyId.set(id);
    this.headmanApi.approveExcuse(id, null).subscribe({
      next: () => {
        this.busyId.set(null);
        this.snackBar.open('Заявка одобрена', 'OK', { duration: 3000 });
        this.loadTickets();
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.snackBar.open(this.errorMessage(err, 'approve'), 'OK', { duration: 5000 });
      },
    });
  }

  startReject(id: string): void {
    this.rejectingId.set(id);
    this.rejectComment.set('');
    this.validationError.set(null);
  }

  cancelReject(): void {
    this.rejectingId.set(null);
    this.rejectComment.set('');
    this.validationError.set(null);
  }

  confirmReject(id: string): void {
    const comment = this.rejectComment().trim();
    if (!comment) {
      this.validationError.set('Введите комментарий для отклонения');
      return;
    }
    this.validationError.set(null);
    this.busyId.set(id);
    this.headmanApi.rejectExcuse(id, comment).subscribe({
      next: () => {
        this.busyId.set(null);
        this.rejectingId.set(null);
        this.rejectComment.set('');
        this.snackBar.open('Заявка отклонена', 'OK', { duration: 3000 });
        this.loadTickets();
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.snackBar.open(this.errorMessage(err, 'reject'), 'OK', { duration: 5000 });
      },
    });
  }

  typeLabel(type: ExcuseType): string {
    return EXCUSE_TYPE_LABELS[type] ?? type;
  }

  statusLabel(status: ExcuseTicketStatus): string {
    return EXCUSE_STATUS_LABELS[status] ?? status;
  }

  private errorMessage(err: HttpErrorResponse, action: 'approve' | 'reject'): string {
    if (err.status === 409) {
      // D-13 (cannot decide own ticket) and D-18 (already decided) both map to 409.
      return action === 'approve'
        ? 'Невозможно одобрить: решение уже принято или заявка недоступна для одобрения.'
        : 'Невозможно отклонить: решение уже принято или заявка недоступна для отклонения.';
    }
    if (err.status === 403) {
      return 'Недостаточно прав — эта заявка принадлежит другой группе.';
    }
    if (err.status === 404) {
      return 'Заявка не найдена — возможно, она была удалена.';
    }
    return action === 'approve'
      ? 'Не удалось одобрить заявку. Попробуйте позже.'
      : 'Не удалось отклонить заявку. Попробуйте позже.';
  }
}
