import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of } from 'rxjs';
import { signal } from '@angular/core';

// ---- Module-level mocks -----------------------------------------------------
// Мокаем @stomp/stompjs и sockjs-client — нам интересна только конфигурация,
// которую сервис передаёт в Client (exponential backoff M07 G5).

type StompCallback = (message: { body: string }) => void;

interface CapturedClient {
  config: {
    webSocketFactory: () => Promise<unknown> | unknown;
    reconnectDelay?: number;
    maxReconnectDelay?: number;
    reconnectTimeMode?: number;
    onConnect: () => void;
    onStompError?: (frame: { headers: Record<string, string> }) => void;
  };
  activate: ReturnType<typeof vi.fn>;
  deactivate: ReturnType<typeof vi.fn>;
  subscribe: ReturnType<typeof vi.fn>;
  __subscriptions: Array<{ destination: string; callback: StompCallback }>;
}

const capturedClients: CapturedClient[] = [];

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation((config: CapturedClient['config']) => {
    const instance: CapturedClient = {
      config,
      activate: vi.fn(),
      deactivate: vi.fn(),
      subscribe: vi.fn(),
      __subscriptions: [],
    };
    instance.subscribe.mockImplementation((destination: string, callback: StompCallback) => {
      instance.__subscriptions.push({ destination, callback });
      return { id: 'sub-' + instance.__subscriptions.length, unsubscribe: vi.fn() };
    });
    capturedClients.push(instance);
    return instance;
  }),
  // Копируем enum 1:1 с @stomp/stompjs types.d.ts
  ReconnectionTimeMode: { LINEAR: 0, EXPONENTIAL: 1 },
}));

vi.mock('sockjs-client', () => ({
  default: vi.fn().mockImplementation((url: string) => ({ url })),
}));

import { NotificationCenterService } from './notification-center.service';
import { AuthService } from '../auth/auth.service';
import { AuthApi } from '../auth/auth.api';

describe('NotificationCenterService — M07 G5 exponential backoff', () => {
  let authUserSignal: ReturnType<typeof signal<{ id: number; role: string; groupId: number | null; isHeadman: boolean } | null>>;

  beforeEach(() => {
    capturedClients.length = 0;
    authUserSignal = signal<{ id: number; role: string; groupId: number | null; isHeadman: boolean } | null>(null);

    const authMock: Partial<AuthService> = {
      currentUser: authUserSignal.asReadonly(),
    };
    const authApiMock: Partial<AuthApi> = {
      acquireWsTicket: vi.fn().mockReturnValue(of({ ticket: 'uuid-42', expiresAt: '2026-04-22T00:00:30Z' })),
    };

    TestBed.configureTestingModule({
      providers: [
        NotificationCenterService,
        { provide: AuthService, useValue: authMock },
        { provide: AuthApi, useValue: authApiMock },
      ],
    });
  });

  it('при логине создаёт STOMP client с exponential backoff config', () => {
    const service = TestBed.inject(NotificationCenterService);
    expect(service).toBeDefined();

    authUserSignal.set({ id: 1, role: 'STUDENT', groupId: 5, isHeadman: false });
    TestBed.flushEffects();

    expect(capturedClients).toHaveLength(1);
    const client = capturedClients[0];
    // M07 G5: initial delay 1s, ceiling 30s, exponential backoff.
    expect(client.config.reconnectDelay).toBe(1000);
    expect(client.config.maxReconnectDelay).toBe(30_000);
    expect(client.config.reconnectTimeMode).toBe(1); // ReconnectionTimeMode.EXPONENTIAL
    expect(client.activate).toHaveBeenCalledTimes(1);
  });

  it('староста получает подписку на /topic/group/{gid}/headman в дополнение', () => {
    TestBed.inject(NotificationCenterService);
    authUserSignal.set({ id: 7, role: 'STUDENT', groupId: 10, isHeadman: true });
    TestBed.flushEffects();

    const client = capturedClients[0];
    client.config.onConnect();

    const destinations = client.__subscriptions.map((s) => s.destination);
    expect(destinations).toContain('/topic/group/10');
    expect(destinations).toContain('/topic/group/10/headman');
  });

  it('обычный студент не подписывается на /headman sub-topic', () => {
    TestBed.inject(NotificationCenterService);
    authUserSignal.set({ id: 7, role: 'STUDENT', groupId: 10, isHeadman: false });
    TestBed.flushEffects();

    const client = capturedClients[0];
    client.config.onConnect();

    const destinations = client.__subscriptions.map((s) => s.destination);
    expect(destinations).toEqual(['/topic/group/10']);
  });

  it('logout вызывает deactivate()', () => {
    TestBed.inject(NotificationCenterService);
    authUserSignal.set({ id: 1, role: 'STUDENT', groupId: 5, isHeadman: false });
    TestBed.flushEffects();
    const client = capturedClients[0];

    authUserSignal.set(null);
    TestBed.flushEffects();

    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });

  it('attendance.marked envelope прокидывается через onEvent$', () => {
    const service = TestBed.inject(NotificationCenterService);
    authUserSignal.set({ id: 1, role: 'STUDENT', groupId: 5, isHeadman: false });
    TestBed.flushEffects();
    const client = capturedClients[0];
    client.config.onConnect();

    const received: unknown[] = [];
    service.onEvent$.subscribe((env) => received.push(env));

    const sub = client.__subscriptions.find((s) => s.destination === '/topic/group/5');
    sub?.callback({
      body: JSON.stringify({
        type: 'attendance.marked',
        payload: { lesson_id: 1, user_id: 1, group_id: 5, status: 'present', marked_by: 'self' },
      }),
    });

    expect(received).toHaveLength(1);
    expect((received[0] as { type: string }).type).toBe('attendance.marked');
  });

  it('невалидный JSON frame не крэшит subscriber', () => {
    const service = TestBed.inject(NotificationCenterService);
    authUserSignal.set({ id: 1, role: 'STUDENT', groupId: 5, isHeadman: false });
    TestBed.flushEffects();
    const client = capturedClients[0];
    client.config.onConnect();

    const received: unknown[] = [];
    service.onEvent$.subscribe((env) => received.push(env));
    const sub = client.__subscriptions.find((s) => s.destination === '/topic/group/5');

    expect(() => sub?.callback({ body: 'not-json ——' })).not.toThrow();
    expect(received).toEqual([]);
  });
});
