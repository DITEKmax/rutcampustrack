import { channel } from 'node:diagnostics_channel';
import type { ClientRequest, IncomingMessage } from 'node:http';
import type { Socket } from 'node:net';

type Phase = 'READINESS' | 'ADMIN_ADMISSION';
type Emitter = { on: (name: string, listener: (...args: any[]) => void) => unknown; removeListener: (name: string, listener: (...args: any[]) => void) => unknown };
type Request = ClientRequest & { protocol?: string };
type Bus = { subscribe: (listener: (message: unknown) => void) => unknown; unsubscribe: (listener: (message: unknown) => void) => unknown };
const codes = ['ECONNREFUSED','ECONNRESET','ETIMEDOUT','ENOTFOUND','EAI_AGAIN','EPIPE','ABORT_ERR','ERR_TLS_CERT_ALTNAME_INVALID','DEPTH_ZERO_SELF_SIGNED_CERT','SELF_SIGNED_CERT_IN_CHAIN','CERT_HAS_EXPIRED','UNABLE_TO_VERIFY_LEAF_SIGNATURE'] as const;

/** Documented Node events only. Absence is UNAVAILABLE; callbacks never affect a request. */
export function observePreparationTransport(bus: Bus = channel('http.client.request.created'), clock: () => number = () => performance.now(), errors: Bus = channel('http.client.request.error')) {
  const started = clock();
  let phase: Phase | undefined;
  let stopped = false;
  let failures = 0;
  let truncated = false;
  const rows: { phase: Phase; route: 'AUTH_SESSION' | 'AUTH_LOGIN'; method: 'GET' | 'POST'; events: Record<string, number>; httpStatus: number | null; errorCode: string | null }[] = [];
  const requests = new WeakMap<Request, typeof rows[number]>();
  const listeners: { emitter: Emitter; name: string; callback: (...args: any[]) => void }[] = [];
  const protect = (callback: (...args: any[]) => void) => (...args: any[]) => {
    try { if (!stopped) callback(...args); } catch { failures = Math.min(1000, failures + 1); }
  };
  const listen = (emitter: Emitter, name: string, callback: (...args: any[]) => void) => {
    const safe = protect(callback);
    // Remember before attaching so a partial failure can still remove our listener.
    listeners.push({ emitter, name, callback: safe });
    emitter.on(name, safe);
  };
  const elapsed = () => Math.max(0, Math.min(180000, Math.trunc(clock() - started)));
  const receive = protect((message: unknown) => {
    if (!phase || !message || typeof message !== 'object') return;
    const request = Object.getOwnPropertyDescriptor(message, 'request')?.value as Request | undefined;
    if (!request || request.protocol !== 'https:' || request.getHeader('host') !== 'localhost:8443') return;
    const route = request.path === '/api/auth/session' && request.method === 'GET' ? 'AUTH_SESSION'
      : request.path === '/api/auth/login' && request.method === 'POST' ? 'AUTH_LOGIN' : undefined;
    if (!route) return;
    if (rows.length >= 16) { truncated = true; return; }
    const row: typeof rows[number] = { phase, route, method: request.method as 'GET' | 'POST', events: { CREATED: elapsed() }, httpStatus: null, errorCode: null };
    rows.push(row);
    requests.set(request, row);
    const mark = (name: string) => { if (!(name in row.events)) row.events[name] = elapsed(); };
    listen(request, 'socket', (socket: Socket) => {
      mark('SOCKET_ASSIGNED');
      listen(socket, 'connect', () => mark('CONNECT'));
      listen(socket, 'secureConnect', () => mark('SECURE_CONNECT'));
    });
    listen(request, 'finish', () => mark('REQUEST_WRITING_FINISHED'));
    listen(request, 'timeout', () => mark('TIMEOUT_EVENT'));
    listen(request, 'response', (response: IncomingMessage) => {
      mark('RESPONSE_HEADERS_EVENT');
      row.httpStatus = typeof response.statusCode === 'number' && Number.isInteger(response.statusCode) && response.statusCode >= 100 && response.statusCode <= 599 ? response.statusCode : null;
      listen(response, 'end', () => mark('RESPONSE_END_EVENT'));
      listen(response, 'aborted', () => mark('RESPONSE_ABORTED_EVENT'));
    });
  });
  const receiveError = protect((message: unknown) => {
    if (!message || typeof message !== 'object') return;
    const request = Object.getOwnPropertyDescriptor(message, 'request')?.value as Request | undefined;
    const row = request ? requests.get(request) : undefined;
    if (!row) return;
    row.events.ERROR ??= elapsed();
    const error = Object.getOwnPropertyDescriptor(message, 'error')?.value as unknown;
    const code = error && typeof error === 'object' ? Object.getOwnPropertyDescriptor(error, 'code')?.value : undefined;
    row.errorCode ??= typeof code === 'string' && codes.some(candidate => candidate === code) ? code : 'OTHER';
  });
  try { bus.subscribe(receive); } catch { failures++; }
  try { errors.subscribe(receiveError); } catch { failures++; }
  return {
    enter(value: Phase) { try { if (!stopped) phase = value; } catch { failures++; } },
    stop() {
      if (!stopped) {
        stopped = true;
        try { bus.unsubscribe(receive); } catch { failures++; }
        try { errors.unsubscribe(receiveError); } catch { failures++; }
        for (const { emitter, name, callback } of listeners) {
          try { emitter.removeListener(name, callback); } catch { failures++; }
        }
        listeners.length = 0;
      }
      return { schema: 'RCT_PREPARATION_TRANSPORT_V1', status: rows.length ? 'OBSERVED' : 'UNAVAILABLE', rows, truncated,
        observerFailures: Math.min(1000, failures), qualification: 'EVENTS_ARE_OBSERVATIONS_NOT_SERVER_RECEIPT_OR_AUTH_COMMIT; ABSENT_EVENT_IS_UNKNOWN' };
    },
  };
}
