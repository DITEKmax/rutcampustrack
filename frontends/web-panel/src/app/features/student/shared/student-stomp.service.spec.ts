import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of } from 'rxjs';

// ---- Module-level mocks -----------------------------------------------------
// We capture: (a) the config passed to `new Client(...)`, (b) the function
// handed to the `subscribe` call, and (c) the URL passed to SockJS.
//
// M03b Группа 7: webSocketFactory теперь async (pre-fetch ws-ticket).

type StompCallback = (message: { body: string }) => void;

interface CapturedClient {
  config: {
    webSocketFactory: () => Promise<unknown> | unknown;
    reconnectDelay: number;
    onConnect: () => void;
    onStompError?: (frame: { headers: Record<string, string> }) => void;
  };
  activate: ReturnType<typeof vi.fn>;
  deactivate: ReturnType<typeof vi.fn>;
  subscribe: ReturnType<typeof vi.fn>;
  active: boolean;
  __lastCallback: StompCallback | null;
}

const capturedClients: CapturedClient[] = [];
const capturedSockJsUrls: string[] = [];

vi.mock('@stomp/stompjs', () => {
  return {
    Client: vi.fn().mockImplementation((config: CapturedClient['config']) => {
      const instance: CapturedClient = {
        config,
        activate: vi.fn(),
        deactivate: vi.fn(),
        subscribe: vi.fn(),
        active: false,
        __lastCallback: null,
      };
      instance.subscribe.mockImplementation((_destination: string, callback: StompCallback) => {
        instance.__lastCallback = callback;
        return { id: 'sub-1', unsubscribe: vi.fn() };
      });
      instance.activate.mockImplementation(() => {
        instance.active = true;
      });
      instance.deactivate.mockImplementation(() => {
        instance.active = false;
      });
      capturedClients.push(instance);
      return instance;
    }),
  };
});

vi.mock('sockjs-client', () => {
  return {
    default: vi.fn().mockImplementation((url: string) => {
      capturedSockJsUrls.push(url);
      return { url };
    }),
  };
});

import { StudentStompService } from './student-stomp.service';
import { AuthApi } from '../../../core/auth/auth.api';

describe('StudentStompService (M03b ws-ticket)', () => {
  let service: StudentStompService;
  let mockAuthApi: Partial<AuthApi>;

  beforeEach(() => {
    capturedClients.length = 0;
    capturedSockJsUrls.length = 0;
    mockAuthApi = {
      acquireWsTicket: vi.fn().mockReturnValue(
        of({ ticket: 'uuid-42', expiresAt: '2026-04-20T00:00:30Z' }),
      ),
    };
    TestBed.configureTestingModule({
      providers: [
        StudentStompService,
        { provide: AuthApi, useValue: mockAuthApi },
      ],
    });
    service = TestBed.inject(StudentStompService);
  });

  it('connect() constructs a Client with reconnectDelay 1000 and ws factory pre-fetches ticket', async () => {
    service.connect(5);

    expect(capturedClients).toHaveLength(1);
    const client = capturedClients[0];
    expect(client.config.reconnectDelay).toBe(1000);
    expect(client.activate).toHaveBeenCalledTimes(1);

    // Invoke the SockJS factory — async (Promise). Ticket URL instead of token.
    await client.config.webSocketFactory();
    expect(mockAuthApi.acquireWsTicket).toHaveBeenCalled();
    expect(capturedSockJsUrls).toContain('/api/ws?ticket=uuid-42');
  });

  it('on onConnect, subscribes to /topic/group/{groupId}', () => {
    service.connect(5);
    const client = capturedClients[0];
    client.config.onConnect();

    expect(client.subscribe).toHaveBeenCalledWith('/topic/group/5', expect.any(Function));
  });

  it('emits attendance.marked payloads from marked$ when the subscribe callback receives a matching envelope', () => {
    service.connect(5);
    const client = capturedClients[0];
    client.config.onConnect();

    const received: unknown[] = [];
    service.marked$.subscribe(payload => received.push(payload));

    const payload = {
      lesson_id: 12,
      user_id: 34,
      group_id: 5,
      status: 'present',
      marked_by: 'self',
    };
    client.__lastCallback?.({
      body: JSON.stringify({ type: 'attendance.marked', payload }),
    });

    expect(received).toEqual([payload]);
  });

  it('ignores envelopes with a non-attendance.marked type', () => {
    service.connect(5);
    const client = capturedClients[0];
    client.config.onConnect();

    const received: unknown[] = [];
    service.marked$.subscribe(payload => received.push(payload));

    client.__lastCallback?.({
      body: JSON.stringify({ type: 'lesson.started', payload: { lesson_id: 1 } }),
    });

    expect(received).toEqual([]);
  });

  it('ignores malformed JSON frames without throwing', () => {
    service.connect(5);
    const client = capturedClients[0];
    client.config.onConnect();

    const received: unknown[] = [];
    service.marked$.subscribe(payload => received.push(payload));

    expect(() =>
      client.__lastCallback?.({ body: 'not json ———' }),
    ).not.toThrow();
    expect(received).toEqual([]);
  });

  it('disconnect() calls deactivate() on the client', () => {
    service.connect(5);
    const client = capturedClients[0];

    service.disconnect();

    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });

  it('is idempotent — calling connect() twice for the same group does not create a second client', () => {
    service.connect(5);
    service.connect(5);

    expect(capturedClients).toHaveLength(1);
  });
});
