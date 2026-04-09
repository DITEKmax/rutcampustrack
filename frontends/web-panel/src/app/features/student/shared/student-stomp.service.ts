import { Injectable, inject } from '@angular/core';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject } from 'rxjs';
import type {
  AttendanceMarkedPayload,
  StompEnvelope,
} from './student-schedule.types';
import { StudentNotificationBadgeService } from './student-notification-badge.service';

const STORED_TYPES = ['lesson.started', 'lesson.cancelled', 'homework.published', 'homework.updated', 'attendance.marked'];

/**
 * STOMP subscription service for the student cabinet.
 *
 * Mirrors the PWA implementation (frontends/pwa/src/features/checkin/
 * useStompCheckin.ts) so both clients interoperate with the same
 * notification-web broker topology shipped in Phase 20.
 *
 * Connection lifecycle:
 * 1. Caller invokes `connect(groupId, getAccessToken)`; the service builds a
 *    @stomp/stompjs Client with a SockJS factory pointing at
 *    `/api/ws?token=<access-token>` — this is the only channel the
 *    notification-web JwtHandshakeInterceptor accepts (see
 *    services/notification-service/.../WebSocketConfig.java).
 * 2. On successful CONNECT the client subscribes to
 *    `/topic/group/{groupId}` and parses each envelope as
 *    `{ type: 'attendance.marked', payload: AttendanceMarkedPayload }`.
 * 3. Matching envelopes are pushed onto `marked$` for downstream reactive
 *    consumers (Plan 03 wires the CheckinComponent to this observable).
 * 4. Caller invokes `disconnect()` in ngOnDestroy to deactivate the client.
 *
 * Idempotency: calling `connect()` twice for the same groupId while a
 * client is already live is a no-op. Calling with a different groupId
 * tears down the existing client and creates a new one.
 *
 * ## Security (T-51-01 mitigation)
 *
 * The access token arrives as a `?token=` query param on the WebSocket
 * upgrade URL. This module:
 *   - NEVER logs the full URL.
 *   - NEVER logs `getAccessToken()` output.
 *   - NEVER echoes the token into an error message.
 *   - NEVER persists the token to localStorage.
 * `onStompError` only surfaces `frame.headers['message']` — an
 * intentionally narrow channel that cannot leak connection context.
 * Token lifecycle is owned by the notification-web interceptor, which
 * validates the JWT at handshake and discards it thereafter.
 */
@Injectable({ providedIn: 'root' })
export class StudentStompService {
  private client: Client | null = null;
  private currentGroupId: number | null = null;
  private readonly markedSubject = new Subject<AttendanceMarkedPayload>();
  private readonly onAnySubject = new Subject<StompEnvelope<Record<string, unknown>>>();
  private readonly badgeService = inject(StudentNotificationBadgeService);

  /** Reactive stream of attendance.marked payloads. */
  readonly marked$: Observable<AttendanceMarkedPayload> = this.markedSubject.asObservable();

  /** Reactive stream of ALL STOMP envelopes from the group topic. */
  readonly onAnyEvent$: Observable<StompEnvelope<Record<string, unknown>>> = this.onAnySubject.asObservable();

  /**
   * Connect to /api/ws for a given group. Idempotent per (groupId).
   * Switching groups tears down the prior client.
   */
  connect(groupId: number, getAccessToken: () => string | null): void {
    if (this.currentGroupId === groupId && this.client !== null) {
      // Already connected or connecting for this group.
      return;
    }

    if (this.client !== null) {
      this.disconnect();
    }

    this.currentGroupId = groupId;
    this.client = new Client({
      webSocketFactory: () => new SockJS(`/api/ws?token=${getAccessToken() ?? ''}`),
      reconnectDelay: 1000,
      onConnect: () => {
        this.client?.subscribe(`/topic/group/${groupId}`, message => {
          try {
            const envelope = JSON.parse(message.body) as StompEnvelope<Record<string, unknown>>;
            this.onAnySubject.next(envelope);
            if (STORED_TYPES.includes(envelope.type)) { this.badgeService.increment(); }
            if (envelope.type === 'attendance.marked') {
              this.markedSubject.next(envelope.payload as unknown as AttendanceMarkedPayload);
            }
          } catch {
            // Malformed frame — drop silently. Never echo frame body to console.
          }
        });
      },
      onStompError: frame => {
        // T-51-01: log ONLY the broker-provided message header, never the URL
        // or any headers that could carry the token.
        // eslint-disable-next-line no-console
        console.error('STOMP error:', frame.headers['message']);
      },
    });
    this.client.activate();
  }

  /** Disconnect and release the current client, if any. */
  disconnect(): void {
    if (this.client !== null) {
      this.client.deactivate();
      this.client = null;
      this.currentGroupId = null;
    }
  }
}
