import { Injectable, inject } from '@angular/core';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject } from 'rxjs';
import { AuthApi } from '../../../core/auth/auth.api';
import { buildWsUrl } from '../../../core/auth/ws-ticket';
import type { LateCheckinRequestedEvent } from '../late-checkin/late-checkin.types';

/**
 * STOMP subscription for the headman-only sub-topic
 * `/topic/group/{groupId}/headman`.
 *
 * Background: notification-service routes headman-only events
 * (`excuse.requested`, `late_checkin.requested`) to a separate sub-topic so
 * that plain group subscribers never see them — see
 * services/notification-service/.../EventConsumer.java (HEADMAN_ONLY_EVENTS).
 *
 * This service is a narrower sibling of StudentStompService. M03b Группа 7:
 * теперь использует single-use ws-ticket вместо JWT в query (замена
 * `?token=<jwt>` → `?ticket=<uuid>`).
 *
 * Lifecycle:
 * - `connect(groupId)` — idempotent per groupId. Access token автоматически
 *   отправляется через `authInterceptor` при запросе ws-ticket.
 * - `lateCheckinRequested$` — stream of envelopes from the sub-topic filtered
 *   to the `late_checkin.requested` type.
 * - `disconnect()` — release the client (call from ngOnDestroy).
 */
@Injectable({ providedIn: 'root' })
export class HeadmanStompService {
  private readonly authApi = inject(AuthApi);
  private client: Client | null = null;
  private currentGroupId: number | null = null;
  private readonly lateCheckinSubject = new Subject<LateCheckinRequestedEvent['payload']>();

  readonly lateCheckinRequested$: Observable<LateCheckinRequestedEvent['payload']> =
    this.lateCheckinSubject.asObservable();

  connect(groupId: number): void {
    if (this.currentGroupId === groupId && this.client !== null) {
      return;
    }
    if (this.client !== null) {
      this.disconnect();
    }
    this.currentGroupId = groupId;
    this.client = new Client({
      webSocketFactory: async () => new SockJS(await buildWsUrl(this.authApi)),
      reconnectDelay: 1000,
      onConnect: () => {
        this.client?.subscribe(`/topic/group/${groupId}/headman`, message => {
          try {
            const envelope = JSON.parse(message.body) as {
              type: string;
              payload: LateCheckinRequestedEvent['payload'];
            };
            if (envelope.type === 'late_checkin.requested') {
              this.lateCheckinSubject.next(envelope.payload);
            }
          } catch {
            // Malformed frame — drop silently. Never echo frame body to console.
          }
        });
      },
      onStompError: frame => {
        // eslint-disable-next-line no-console
        console.error('STOMP error:', frame.headers['message']);
      },
    });
    this.client.activate();
  }

  disconnect(): void {
    if (this.client !== null) {
      this.client.deactivate();
      this.client = null;
      this.currentGroupId = null;
    }
  }
}
