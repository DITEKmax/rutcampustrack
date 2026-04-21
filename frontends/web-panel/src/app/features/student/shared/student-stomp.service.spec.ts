import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Subject } from 'rxjs';

import { StudentStompService } from './student-stomp.service';
import { StudentNotificationBadgeService } from './student-notification-badge.service';
import {
  NotificationCenterService,
  type StompEnvelope,
} from '../../../core/notifications/notification-center.service';

/**
 * M07 G5: StudentStompService теперь — thin adapter поверх
 * NotificationCenterService.onEvent$. Спецификация проверяет только
 * адаптер-логику (фильтры, badge increment, idempotency). Сам STOMP-клиент
 * тестируется в notification-center.service.spec (exponential backoff,
 * ws-ticket handshake, reconnect).
 */
describe('StudentStompService (adapter over NotificationCenter)', () => {
  let service: StudentStompService;
  let eventsSubject: Subject<StompEnvelope>;
  let badgeMock: { increment: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    eventsSubject = new Subject<StompEnvelope>();
    badgeMock = { increment: vi.fn() };

    const centerMock: Partial<NotificationCenterService> = {
      onEvent$: eventsSubject.asObservable(),
    };

    TestBed.configureTestingModule({
      providers: [
        StudentStompService,
        { provide: NotificationCenterService, useValue: centerMock },
        { provide: StudentNotificationBadgeService, useValue: badgeMock },
      ],
    });
    service = TestBed.inject(StudentStompService);
  });

  it('connect() подписывается на NotificationCenter.onEvent$', () => {
    service.connect(5);
    expect(eventsSubject.observed).toBe(true);
  });

  it('эмитит attendance.marked payload через marked$', () => {
    service.connect(5);
    const received: unknown[] = [];
    service.marked$.subscribe((payload) => received.push(payload));

    const payload = {
      lesson_id: 12,
      user_id: 34,
      group_id: 5,
      status: 'present',
      marked_by: 'self',
    };
    eventsSubject.next({ type: 'attendance.marked', payload });

    expect(received).toEqual([payload]);
  });

  it('событие !== attendance.marked не попадает в marked$, но попадает в onAnyEvent$', () => {
    service.connect(5);
    const marked: unknown[] = [];
    const any: unknown[] = [];
    service.marked$.subscribe((p) => marked.push(p));
    service.onAnyEvent$.subscribe((env) => any.push(env));

    eventsSubject.next({ type: 'lesson.started', payload: { lesson_id: 1 } });

    expect(marked).toEqual([]);
    expect(any).toHaveLength(1);
    expect((any[0] as StompEnvelope).type).toBe('lesson.started');
  });

  it('increments badge на каждом BADGE_TYPES событии', () => {
    service.connect(5);
    eventsSubject.next({ type: 'homework.published', payload: {} });
    eventsSubject.next({ type: 'attendance.marked', payload: {} });
    expect(badgeMock.increment).toHaveBeenCalledTimes(2);
  });

  it('не инкрементирует badge для событий вне BADGE_TYPES', () => {
    service.connect(5);
    eventsSubject.next({ type: 'late_checkin.requested', payload: {} });
    expect(badgeMock.increment).not.toHaveBeenCalled();
  });

  it('disconnect() освобождает подписку', () => {
    service.connect(5);
    expect(eventsSubject.observed).toBe(true);
    service.disconnect();
    expect(eventsSubject.observed).toBe(false);
  });

  it('connect() идемпотентен для одного и того же groupId', () => {
    service.connect(5);
    service.connect(5);
    // Только одна подписка на Subject — observed остаётся true, но второй
    // connect ничего не создаёт дополнительно. Проверяется через счётчик
    // событий: 1 envelope → 1 marked.
    const received: unknown[] = [];
    service.marked$.subscribe((p) => received.push(p));
    eventsSubject.next({ type: 'attendance.marked', payload: { lesson_id: 7 } });
    expect(received).toEqual([{ lesson_id: 7 }]);
  });

  it('connect() с другим groupId переустанавливает подписку', () => {
    service.connect(5);
    service.connect(6);
    // Первая подписка отписана, вторая активна — Subject видит одного подписчика.
    expect(eventsSubject.observed).toBe(true);
  });
});
