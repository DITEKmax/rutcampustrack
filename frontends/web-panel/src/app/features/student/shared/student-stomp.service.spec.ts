import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';

// ---- Module-level mocks -----------------------------------------------------
// We capture: (a) the config passed to `new Client(...)`, (b) the function
// handed to the `subscribe` call so the test can drive incoming STOMP frames,
// and (c) the URL passed to the SockJS constructor.
//
// The service under test imports these as ES-module defaults / named exports,
// so we mock them via vi.mock hoisted to the top of the module graph.
//
// @stomp/stompjs exports `Client` as a named export.
// sockjs-client exports the constructor as the default export.

type StompCallback = (message: { body: string }) => void;

interface CapturedClient {
  config: {
    webSocketFactory: () => unknown;
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

// Import the service AFTER the mocks are declared so the module resolves to
// the mocked versions.
import { StudentStompService } from './student-stomp.service';

describe('StudentStompService', () => {
  let service: StudentStompService;

  beforeEach(() => {
    capturedClients.length = 0;
    capturedSockJsUrls.length = 0;
    TestBed.configureTestingModule({ providers: [StudentStompService] });
    service = TestBed.inject(StudentStompService);
  });

  it('connect() constructs a Client with reconnectDelay 1000 and a SockJS factory pointing at /api/ws?token=...', () => {
    service.connect(5, () => 'fake-token');

    expect(capturedClients).toHaveLength(1);
    const client = capturedClients[0];
    expect(client.config.reconnectDelay).toBe(1000);
    expect(client.activate).toHaveBeenCalledTimes(1);

    // Invoke the SockJS factory to capture the URL it constructs.
    client.config.webSocketFactory();
    expect(capturedSockJsUrls).toContain('/api/ws?token=fake-token');
  });

  it('on onConnect, subscribes to /topic/group/{groupId}', () => {
    service.connect(5, () => 'fake-token');
    const client = capturedClients[0];

    // Drive the onConnect callback as the underlying stompjs Client would.
    client.config.onConnect();

    expect(client.subscribe).toHaveBeenCalledWith('/topic/group/5', expect.any(Function));
  });

  it('emits attendance.marked payloads from marked$ when the subscribe callback receives a matching envelope', () => {
    service.connect(5, () => 'fake-token');
    const client = capturedClients[0];
    client.config.onConnect();

    const received: unknown[] = [];
    service.marked$.subscribe(payload => received.push(payload));

    // Feed an envelope to the subscribe callback.
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
    service.connect(5, () => 'fake-token');
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
    service.connect(5, () => 'fake-token');
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
    service.connect(5, () => 'fake-token');
    const client = capturedClients[0];

    service.disconnect();

    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });

  it('is idempotent — calling connect() twice for the same group does not create a second client', () => {
    service.connect(5, () => 'fake-token');
    // Simulate a successful activation (the mock sets active=true on activate()).
    service.connect(5, () => 'fake-token');

    expect(capturedClients).toHaveLength(1);
  });
});
