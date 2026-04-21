import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subject, Subscription, filter, map } from 'rxjs';
import type {
  AttendanceMarkedPayload,
  StompEnvelope,
} from './student-schedule.types';
import { StudentNotificationBadgeService } from './student-notification-badge.service';
import { NotificationCenterService } from '../../../core/notifications/notification-center.service';

/** События, которые студент хочет видеть в badge-счётчике. */
const BADGE_TYPES: ReadonlySet<string> = new Set([
  'lesson.started',
  'lesson.cancelled',
  'homework.published',
  'homework.updated',
  'attendance.marked',
  'late_checkin.decided',
  'excuse.decided',
]);

/**
 * Adapter поверх {@link NotificationCenterService} (M07 G5).
 *
 * До G5 был самостоятельным STOMP-клиентом с собственным `Client.activate()` —
 * это открывало ещё одно WebSocket-соединение параллельно с NotificationCenter'ом,
 * клиентам приходили дубли событий, и при reverse-proxy-рестарте оба клиента
 * ломились на reconnect в один и тот же момент.
 *
 * Теперь `connect(groupId)` идемпотентно начинает подписку на `onEvent$`
 * NotificationCenter'а. Реальный STOMP-коннект держится только там, reconnect
 * и security (T-51-01: токен не логируется) — тоже ответственность center'а.
 *
 * Контракт остался тем же:
 * - `marked$` — `attendance.marked` payload;
 * - `onAnyEvent$` — сырой envelope;
 * - `connect(groupId)` / `disconnect()` — lifecycle hooks для компонента.
 *
 * `groupId` сейчас используется только для sanity (если пользователь сменил
 * группу между connect'ами — сбрасываем подписку). Фильтрация по groupId
 * уже сделана NotificationCenter'ом (он подписывается на `/topic/group/{gid}`).
 */
@Injectable({ providedIn: 'root' })
export class StudentStompService implements OnDestroy {
  private readonly center = inject(NotificationCenterService);
  private readonly badgeService = inject(StudentNotificationBadgeService);

  private currentGroupId: number | null = null;
  private subscription: Subscription | null = null;
  private readonly markedSubject = new Subject<AttendanceMarkedPayload>();
  private readonly onAnySubject = new Subject<StompEnvelope<Record<string, unknown>>>();

  /** Reactive stream of attendance.marked payloads. */
  readonly marked$: Observable<AttendanceMarkedPayload> = this.markedSubject.asObservable();

  /** Reactive stream of ALL STOMP envelopes from the group topic. */
  readonly onAnyEvent$: Observable<StompEnvelope<Record<string, unknown>>> = this.onAnySubject.asObservable();

  /**
   * Idempotent per groupId. Switching groups resubscribes.
   *
   * Не открывает WebSocket сам — только слушает `NotificationCenterService.onEvent$`.
   * Сам STOMP-клиент (M03b ws-ticket handshake + M07 G5 exponential backoff)
   * держится center'ом.
   */
  connect(groupId: number): void {
    if (this.currentGroupId === groupId && this.subscription !== null) {
      return;
    }
    this.disconnect();
    this.currentGroupId = groupId;

    this.subscription = this.center.onEvent$
      .pipe(
        // Center может в будущем шлёт события других групп (админы, super-users);
        // сейчас гарантирована привязка к user.groupId, но оставляем type-guard.
        map((envelope) => envelope as StompEnvelope<Record<string, unknown>>),
        filter((envelope) => typeof envelope.type === 'string'),
      )
      .subscribe((envelope) => {
        this.onAnySubject.next(envelope);
        if (BADGE_TYPES.has(envelope.type)) this.badgeService.increment();
        if (envelope.type === 'attendance.marked') {
          this.markedSubject.next(envelope.payload as unknown as AttendanceMarkedPayload);
        }
      });
  }

  /** Отписаться. Center продолжает держать сокет — disconnect здесь лёгкий. */
  disconnect(): void {
    if (this.subscription !== null) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }
    this.currentGroupId = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
