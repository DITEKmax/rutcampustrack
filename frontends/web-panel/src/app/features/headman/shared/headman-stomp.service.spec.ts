import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { Subject } from 'rxjs';

import { HeadmanStompService } from './headman-stomp.service';
import {
  NotificationCenterService,
  type StompEnvelope,
} from '../../../core/notifications/notification-center.service';

/**
 * M07 G5: HeadmanStompService — thin adapter над NotificationCenter,
 * фильтрует только `late_checkin.requested` события. Сам STOMP-клиент
 * и sub-topic `/topic/group/{gid}/headman` — ответственность center'а.
 */
describe('HeadmanStompService (adapter over NotificationCenter)', () => {
  let service: HeadmanStompService;
  let eventsSubject: Subject<StompEnvelope>;

  beforeEach(() => {
    eventsSubject = new Subject<StompEnvelope>();
    const centerMock: Partial<NotificationCenterService> = {
      onEvent$: eventsSubject.asObservable(),
    };

    TestBed.configureTestingModule({
      providers: [
        HeadmanStompService,
        { provide: NotificationCenterService, useValue: centerMock },
      ],
    });
    service = TestBed.inject(HeadmanStompService);
  });

  it('connect() подписывается на NotificationCenter.onEvent$', () => {
    service.connect(5);
    expect(eventsSubject.observed).toBe(true);
  });

  it('пропускает late_checkin.requested payload через lateCheckinRequested$', () => {
    service.connect(5);
    const received: unknown[] = [];
    service.lateCheckinRequested$.subscribe((p) => received.push(p));

    const payload = {
      request_id: 'req-1',
      user_id: 10,
      group_id: 5,
      lesson_id: 42,
      student_name: 'Сидоров С.С.',
      lesson_date: '2026-04-22',
      lesson_number: 3,
      subject_id: 7,
      subject_name: 'Алгебра',
    };
    eventsSubject.next({ type: 'late_checkin.requested', payload });

    expect(received).toEqual([payload]);
  });

  it('фильтрует другие типы событий', () => {
    service.connect(5);
    const received: unknown[] = [];
    service.lateCheckinRequested$.subscribe((p) => received.push(p));

    eventsSubject.next({ type: 'lesson.started', payload: {} });
    eventsSubject.next({ type: 'late_checkin.decided', payload: {} });

    expect(received).toEqual([]);
  });

  it('disconnect() отписывается от шины', () => {
    service.connect(5);
    expect(eventsSubject.observed).toBe(true);
    service.disconnect();
    expect(eventsSubject.observed).toBe(false);
  });

  it('connect() идемпотентен для одного groupId', () => {
    service.connect(5);
    service.connect(5);
    const received: unknown[] = [];
    service.lateCheckinRequested$.subscribe((p) => received.push(p));
    eventsSubject.next({ type: 'late_checkin.requested', payload: { request_id: 'x' } });
    expect(received).toHaveLength(1);
  });
});
