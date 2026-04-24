import { Injectable, Signal, computed, effect, inject, signal } from '@angular/core';
import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { AuthApi } from '../auth/auth.api';
import { buildWsUrl } from '../auth/ws-ticket';
import { NotificationHistoryService } from './notification-history.service';

/**
 * Единый источник правды для всех STOMP-уведомлений в веб-панели.
 *
 * До введения этого сервиса каждая страница ({@code /student/checkin},
 * {@code /student/notifications}, {@code /headman/late-checkin}) самостоятельно
 * поднимала STOMP-подключение в {@code ngOnInit}. Если пользователь находился на
 * любой другой странице, уведомления вообще не приходили — STOMP-клиент был
 * мёртв. Переход на колокольчик без новых оповещений создавал впечатление, что
 * веб-канал не работает.
 *
 * NotificationCenterService живёт в корне (providedIn: 'root') и запускается
 * при старте {@link ShellComponent}. Он держит ОДИН клиент на
 * {@code /api/ws?token=...}, подписывается на {@code /topic/group/{gid}} всегда
 * и дополнительно на {@code /topic/group/{gid}/headman} если пользователь —
 * староста. При logout / смене пользователя подключение разрывается и
 * пересоздаётся с новым JWT.
 *
 * Публичная поверхность:
 * - {@link items} — сигнал с историей уведомлений (сохраняется в sessionStorage)
 * - {@link unreadCount} — число непрочитанных (для колокольчика)
 * - {@link onEvent$} — поток сырых STOMP envelope'ов (для компонентов, которые
 *   хотят реагировать на конкретные события, например авто-закрытие карточек
 *   запросов при принятии решения из Telegram)
 * - {@link markAllRead} / {@link clear} — сбросить счётчик
 *
 * Безопасность — как у StudentStompService: JWT передаётся в query-параметре
 * и нигде не логируется; при ошибках STOMP выводится только frame.headers.message.
 */

/** События, которые попадают в историю уведомлений (bell + список). */
const STORED_TYPES: ReadonlySet<string> = new Set([
  'lesson.started',
  'lesson.cancelled',
  'homework.published',
  'homework.updated',
  'attendance.marked',
  'late_checkin.requested',
  'late_checkin.decided',
  'excuse.requested',
  'excuse.decided',
]);

/**
 * События, привязанные к конкретному пользователю: показываем только если
 * payload.user_id совпадает с текущим. Без этого фильтра все студенты группы
 * видели бы вердикты по чужим тикетам, потому что broker шлёт в общий топик.
 */
const USER_SCOPED_TYPES: ReadonlySet<string> = new Set([
  'late_checkin.decided',
  'excuse.decided',
]);

/** События, которые показываем только старосте группы. */
const HEADMAN_ONLY_TYPES: ReadonlySet<string> = new Set([
  'late_checkin.requested',
  'excuse.requested',
]);

const STORAGE_KEY = 'rct-global-notifications';
const MAX_ITEMS = 200;

export interface StompEnvelope {
  type: string;
  payload: Record<string, unknown>;
}

export interface NotificationRecord {
  id: string;
  type: string;
  payload: Record<string, unknown>;
  receivedAt: string; // ISO (сохраняется в sessionStorage как строка)
  read: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  private readonly auth = inject(AuthService);
  private readonly authApi = inject(AuthApi);
  private readonly historyService = inject(NotificationHistoryService);

  private client: Client | null = null;
  private connectedKey: string | null = null; // `${userId}:${groupId}:${isHeadman}`

  private readonly _items = signal<NotificationRecord[]>(this.loadFromStorage());
  readonly items: Signal<NotificationRecord[]> = this._items.asReadonly();

  readonly unreadCount = computed(() => this._items().filter(i => !i.read).length);

  private readonly eventSubject = new Subject<StompEnvelope>();
  /** Поток всех принятых событий (для компонентов, слушающих конкретные типы). */
  readonly onEvent$ = this.eventSubject.asObservable();

  constructor() {
    // Реагируем на смену пользователя: на login поднимаем сокет, на logout — рвём.
    effect(() => {
      const user = this.auth.currentUser();
      if (!user) {
        this.disconnect();
        return;
      }
      if (user.groupId == null) {
        // У админов/преподавателей без группы нет /topic/group/{X} — оставляем без подключения.
        this.disconnect();
        return;
      }
      this.connectFor(user.id, user.groupId, user.isHeadman);
    });
  }

  /** Пометить всё как прочитанное (вызывает колокольчик / страница уведомлений). */
  markAllRead(): void {
    this._items.update(list => list.map(i => (i.read ? i : { ...i, read: true })));
    this.persist();
    // M10 G7: best-effort backend sync — локальный badge уже 0, server
    // tolerant к offline/401 (history-service не rethrow'ит).
    this.historyService.markAllRead();
  }

  /** Очистить историю целиком (не используется в UI пока, но пригодится). */
  clear(): void {
    this._items.set([]);
    this.persist();
  }

  private connectFor(userId: number, groupId: number, isHeadman: boolean): void {
    const key = `${userId}:${groupId}:${isHeadman}`;
    if (this.connectedKey === key && this.client !== null) return;

    this.disconnect();
    this.connectedKey = key;

    // M03b Группа 7: ticket-based handshake. Каждый reconnect свежий ticket.
    // M07 G5: exponential backoff (1s → 30s). Fixed 2s raffle'ил broker при
    // массовых отключениях (reverse-proxy рестарт); exponential равномерно
    // размазывает reconnect'ы во времени.
    this.client = new Client({
      webSocketFactory: async () => new SockJS(await buildWsUrl(this.authApi)),
      reconnectDelay: 1000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      onConnect: () => {
        this.client?.subscribe(`/topic/group/${groupId}`, message => this.handleFrame(message.body, userId));
        if (isHeadman) {
          this.client?.subscribe(`/topic/group/${groupId}/headman`, message =>
            this.handleFrame(message.body, userId),
          );
        }
      },
      onStompError: frame => {
        // eslint-disable-next-line no-console
        console.error('[notification-center] STOMP error:', frame.headers['message']);
      },
    });
    this.client.activate();
  }

  private disconnect(): void {
    if (this.client !== null) {
      this.client.deactivate();
      this.client = null;
    }
    this.connectedKey = null;
  }

  private handleFrame(body: string, currentUserId: number): void {
    let envelope: StompEnvelope;
    try {
      envelope = JSON.parse(body) as StompEnvelope;
    } catch {
      return; // битый frame — тихо пропускаем, тело не логируем (security-поза)
    }
    if (!envelope.type) return;

    // Фильтр user-scoped: decided с чужим user_id не показываем.
    if (USER_SCOPED_TYPES.has(envelope.type)) {
      const payloadUserId = envelope.payload?.['user_id'];
      if (typeof payloadUserId !== 'number' || payloadUserId !== currentUserId) {
        // Для HEADMAN_ONLY_TYPES передаём дальше всё равно — вердикты по тикетам
        // других студентов уже отфильтрованы выше. Пробрасываем в поток только
        // если старосте это может быть полезно — но сейчас decided адресовано
        // именно студенту-владельцу, так что просто выходим.
        return;
      }
    }

    // Пробрасываем raw envelope подписчикам (авто-закрытие карточек и т.п.).
    this.eventSubject.next(envelope);

    if (!STORED_TYPES.has(envelope.type)) return;

    const record: NotificationRecord = {
      id: crypto.randomUUID(),
      type: envelope.type,
      payload: envelope.payload ?? {},
      receivedAt: new Date().toISOString(),
      read: false,
    };
    this._items.update(list => {
      const next = [record, ...list];
      return next.length > MAX_ITEMS ? next.slice(0, MAX_ITEMS) : next;
    });
    this.persist();
    // M10 G7: backend consumer после persist делает evict Caffeine
    // unread-count; refresh чтобы badge из server-side источника не
    // отставал на 30s TTL.
    this.historyService.refreshUnreadCount();
  }

  private loadFromStorage(): NotificationRecord[] {
    try {
      if (typeof sessionStorage === 'undefined') return [];
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw) as NotificationRecord[];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private persist(): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this._items()));
    } catch {
      // sessionStorage переполнен — игнорируем (история не критична)
    }
  }
}

/** Утилита: подтипы запросов используют этот helper, чтобы не плодить if'ы. */
export function isHeadmanOnlyType(type: string): boolean {
  return HEADMAN_ONLY_TYPES.has(type);
}
