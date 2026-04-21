import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subject, Subscription, filter } from 'rxjs';
import { NotificationCenterService } from '../../../core/notifications/notification-center.service';
import type { LateCheckinRequestedEvent } from '../late-checkin/late-checkin.types';

/**
 * Adapter поверх {@link NotificationCenterService} (M07 G5) для
 * headman-only sub-topic `/topic/group/{groupId}/headman`.
 *
 * Notification center уже подписывается на sub-topic если `user.isHeadman`
 * и прокидывает raw envelope'ы через `onEvent$`. Здесь фильтруем только
 * `late_checkin.requested` и отдаём payload downstream компоненту.
 *
 * До G5 держал отдельный STOMP-клиент — это дублировало сокет и reconnect
 * logic параллельно с NotificationCenter'ом. Теперь реальный STOMP-connect
 * только в center'е (M03b ws-ticket + M07 G5 exponential backoff).
 */
@Injectable({ providedIn: 'root' })
export class HeadmanStompService implements OnDestroy {
  private readonly center = inject(NotificationCenterService);

  private currentGroupId: number | null = null;
  private subscription: Subscription | null = null;
  private readonly lateCheckinSubject = new Subject<LateCheckinRequestedEvent['payload']>();

  readonly lateCheckinRequested$: Observable<LateCheckinRequestedEvent['payload']> =
    this.lateCheckinSubject.asObservable();

  connect(groupId: number): void {
    if (this.currentGroupId === groupId && this.subscription !== null) {
      return;
    }
    this.disconnect();
    this.currentGroupId = groupId;

    this.subscription = this.center.onEvent$
      .pipe(filter((envelope) => envelope.type === 'late_checkin.requested'))
      .subscribe((envelope) => {
        this.lateCheckinSubject.next(
          envelope.payload as unknown as LateCheckinRequestedEvent['payload'],
        );
      });
  }

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
