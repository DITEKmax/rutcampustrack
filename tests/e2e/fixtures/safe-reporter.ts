import { mkdirSync, writeFileSync } from 'node:fs';
import { basename, isAbsolute, relative, resolve } from 'node:path';
import { isIP } from 'node:net';
import type {
  FullConfig,
  FullResult,
  Reporter,
  TestCase,
  TestError,
  TestResult,
} from '@playwright/test/reporter';

import {
  ownedContainerIdentityAnnotation,
  ownedContainerIdentityFields,
  type OwnedContainerIdentityFailure,
} from './owned-container-identity';

/** Self-contained: Playwright serializes this exact function into the page. */
export function createD002ReconnectObserver() {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'fetch');
  if (!descriptor || typeof descriptor.value !== 'function' || !descriptor.configurable)
    return { read: () => null, restore: () => {} };
  const original = descriptor.value as typeof fetch;
  type Phase = 'NOT_CALLED' | 'PENDING' | 'FULFILLED' | 'REJECTED';
  const refresh = {
    phase: 'NOT_CALLED' as Phase,
    beforeOnline: false,
    multiple: false,
  };
  const current = {
    phase: 'NOT_CALLED' as Phase,
    beforeOnline: false,
    multiple: false,
  };
  const makeState = () => ({ phase: 'NOT_CALLED' as Phase, multiple: false });
  const preOnline = { refresh: makeState(), current: makeState() };
  const postOnline = { refresh: makeState(), current: makeState() };
  let observationUnknown = false;
  let offlineSeen = false;
  let onlineSeen = false;
  const offline = () => {
    offlineSeen = true;
  };
  const online = () => {
    onlineSeen = true;
  };
  const observed: typeof fetch = function (this: unknown, ...args) {
    let first: typeof refresh | undefined;
    let epochFirst: ReturnType<typeof makeState> | undefined;
    try {
      const [input, init] = args;
      const request = input instanceof globalThis.Request ? input : null;
      const method = (init?.method ?? request?.method ?? 'GET').toUpperCase();
      const address =
        typeof input === 'string' ? input : input instanceof URL ? input.href : request?.url;
      if (typeof address === 'string') {
        const url = new URL(address, location.href);
        const state =
          url.origin !== location.origin
            ? undefined
            : method === 'POST' && url.pathname === '/api/auth/refresh'
              ? refresh
              : method === 'GET' && url.pathname === '/api/auth/session'
                ? current
                : undefined;
        if (state) {
          const epoch = onlineSeen ? postOnline : preOnline;
          const epochState = state === refresh ? epoch.refresh : epoch.current;
          if (epochState.phase === 'NOT_CALLED') {
            epochState.phase = 'PENDING';
            epochFirst = epochState;
          } else epochState.multiple = true;
          if (state.phase === 'NOT_CALLED') {
            state.phase = 'PENDING';
            state.beforeOnline = !onlineSeen;
            first = state;
          } else state.multiple = true;
        }
      }
    } catch {
      observationUnknown = true;
      // Observation never changes native arguments, promises, responses or errors.
    }
    let promise: ReturnType<typeof fetch>;
    try {
      promise = Reflect.apply(original, this, args);
    } catch (error) {
      if (first) first.phase = 'REJECTED';
      if (epochFirst) epochFirst.phase = 'REJECTED';
      throw error;
    }
    if (first || epochFirst) {
      const states = [first, epochFirst];
      try {
        void promise
          .then(
            () => {
              for (const state of states) if (state) state.phase = 'FULFILLED';
            },
            () => {
              for (const state of states) if (state) state.phase = 'REJECTED';
            },
          )
          .catch(() => {});
      } catch {
        observationUnknown = true;
        // An observation failure must not replace the original promise.
      }
    }
    return promise;
  };
  Object.defineProperty(globalThis, 'fetch', {
    ...descriptor,
    value: observed,
  });
  // Capture runs before the app's bubble listener can synchronously call fetch.
  addEventListener('offline', offline, { capture: true, passive: true });
  addEventListener('online', online, { capture: true, passive: true });
  return {
    read: () => ({
      observationUnknown,
      offlineSeen,
      onlineSeen,
      online: navigator.onLine,
      refresh: { ...refresh },
      current: { ...current },
      native: {
        preOnline: { refresh: { ...preOnline.refresh }, current: { ...preOnline.current } },
        postOnline: { refresh: { ...postOnline.refresh }, current: { ...postOnline.current } },
      },
    }),
    restore: () => {
      removeEventListener('offline', offline, true);
      removeEventListener('online', online, true);
      Object.defineProperty(globalThis, 'fetch', descriptor);
    },
  };
}

export const safeFailureAnnotation = 'rct-safe-failure-v1';
const nativePrimaryAnnotation = 'rct-native-primary-v1';
const nativePrimaryPaths = {
  SUBJECT_OTP: ['DOCKER_INSPECT', 'JSON_PARSE'],
  SEMESTER_PREVIEW: ['HTTP_GET'],
  ASYNC_REPORT: ['WAIT_DOWNLOAD'],
  BOT_ROLE_MENU: ['CHILD_EXIT'],
  SESSION_RELOAD: ['NAVIGATION', 'WAIT_RESPONSE', 'HTTP_STATUS', 'JSON_DECODE'],
  HELPER_MARK_DENIAL: [
    'BEFORE_REPORT',
    'BEFORE_ENTRY',
    'MARK_REQUEST',
    'MARK_STATUS',
    'AFTER_REPORT',
    'STATUS_EQUAL',
    'REVISION_EQUAL',
  ],
} as const;
const nativeErrorClasses = [
  'Error',
  'TypeError',
  'RangeError',
  'SyntaxError',
  'TimeoutError',
  'AbortError',
  'TargetClosedError',
  'UNKNOWN',
] as const;
const nativeErrorCodes = [
  'ECONNRESET',
  'ECONNREFUSED',
  'ETIMEDOUT',
  'EPIPE',
  'ENOTFOUND',
  'EAI_AGAIN',
  'ABORT_ERR',
  'ENOBUFS',
  'ENOENT',
  'EACCES',
  'ERR_CHILD_PROCESS_STDIO_MAXBUFFER',
  'UNKNOWN',
] as const;
const nativeJobStates = ['queued', 'preparing', 'ready', 'error', 'UNKNOWN'] as const;
const nativeJobErrors = [
  'AUTHORITY_DENIED',
  'GENERATION_FAILED',
  'EXPORT_TOO_LARGE',
  'UNKNOWN',
] as const;
const nativeTransportHints = [
  'TLS_CERTIFICATE',
  'TLS_HANDSHAKE',
  'ECONNRESET',
  'ECONNREFUSED',
  'REQUEST_CONTEXT_DISPOSED',
  'REQUEST_TIMEOUT',
  'UNKNOWN',
] as const;
const nativeReloadTransportHints = [
  'ABORTED',
  'RESET',
  'REFUSED',
  'TIMEOUT',
  'DNS',
  'TLS',
  'TARGET_CLOSED',
  'UNKNOWN',
] as const;
const nativeBotPhases = [
  'setup',
  'hosted_private_current_menu',
  'hosted_private_guard',
  'hosted_teacher_today_and_empty',
  'hosted_headman_current_roster_statistics',
  'hosted_fresh_binding',
  'hosted_revoked_headman_and_late_callback',
  'hosted_role_menu_complete',
  'UNKNOWN',
] as const;
const nativeBotReadOutcomes = [
  'RPC_DENIED',
  'RPC_UNAVAILABLE',
  'RPC_STATUS_UNAUTHENTICATED',
  'RPC_STATUS_UNAVAILABLE',
  'RPC_STATUS_DEADLINE_EXCEEDED',
  'RPC_STATUS_OTHER',
  'LOCAL_UNAVAILABLE',
  'RETURNED_NO_CURRENT',
  'RETURNED_CHOOSE',
  'RETURNED_READY_WRONG_LESSON',
  'RETURNED_READY_CURRENT_LESSON',
  'UNKNOWN',
] as const;
const nativeBotReadStages = [
  'BFF_IDENTITY',
  'BFF_AUTH',
  'BFF_AUTH_RESPONSE',
  'BFF_ACADEMIC',
  'BFF_SCOPE_RESPONSE',
  'BFF_GROUP',
  'BFF_DAY',
  'BFF_SELECTION',
  'BFF_SUMMARY',
  'BFF_ATTENDANCE',
  'BFF_ATTENDANCE_RESPONSE',
  'BFF_ROSTER',
  'BFF_RECHECK_GROUP',
  'BFF_RECHECK_RESPONSE',
  'BFF_FINAL_SELECTION',
  'ATTENDANCE_IDENTITY',
  'ATTENDANCE_AUTH',
  'ATTENDANCE_AUTH_RESPONSE',
  'ATTENDANCE_ACADEMIC',
  'ATTENDANCE_SCOPE_RESPONSE',
  'ATTENDANCE_GROUP',
  'ATTENDANCE_SCHEDULE',
  'ATTENDANCE_CURRENT',
  'ATTENDANCE_REPORT',
  'ATTENDANCE_ROWS',
  'ATTENDANCE_SUMMARY',
  'ATTENDANCE_STATISTICS',
  'ATTENDANCE_RECHECK',
  'ATTENDANCE_FINAL_CURRENT',
  'ACADEMIC_IDENTITY',
  'ACADEMIC_AUTH_BEFORE',
  'ACADEMIC_QUERY',
  'ACADEMIC_AUTH_AFTER',
  'UNKNOWN',
] as const;
const nativeBotReadCauses = [
  'STATUS_UNAVAILABLE',
  'STATUS_DEADLINE_EXCEEDED',
  'STATUS_UNAUTHENTICATED',
  'STATUS_PERMISSION_DENIED',
  'STATUS_FAILED_PRECONDITION',
  'STATUS_INVALID_ARGUMENT',
  'STATUS_NOT_FOUND',
  'STATUS_OTHER',
  'DATA_ACCESS',
  'HISTORY',
  'TIME',
  'ARGUMENT',
  'STATE',
  'NULL',
  'RUNTIME',
  'UNKNOWN',
] as const;
type NativePrimaryPath = keyof typeof nativePrimaryPaths;
interface NativePrimaryFailure {
  path: NativePrimaryPath;
  action:
    | 'DOCKER_INSPECT'
    | 'JSON_PARSE'
    | 'HTTP_GET'
    | 'WAIT_DOWNLOAD'
    | 'CHILD_EXIT'
    | 'NAVIGATION'
    | 'WAIT_RESPONSE'
    | 'HTTP_STATUS'
    | 'JSON_DECODE'
    | 'BEFORE_REPORT'
    | 'BEFORE_ENTRY'
    | 'MARK_REQUEST'
    | 'MARK_STATUS'
    | 'AFTER_REPORT'
    | 'STATUS_EQUAL'
    | 'REVISION_EQUAL';
  errorClass: (typeof nativeErrorClasses)[number];
  code: (typeof nativeErrorCodes)[number];
  exitStatus: number | 'UNKNOWN';
  state?: (typeof nativeJobStates)[number];
  errorCode?: (typeof nativeJobErrors)[number];
  transportHint?:
    | (typeof nativeTransportHints)[number]
    | (typeof nativeReloadTransportHints)[number];
  httpStatus?: number | null;
  markHttpStatus?: number | null;
  statusEqual?: boolean | null;
  revisionEqual?: boolean | null;
  excusedTransition?: boolean | null;
  phase?: (typeof nativeBotPhases)[number];
  checks?: number | 'UNKNOWN';
  headmanReadOutcome?: (typeof nativeBotReadOutcomes)[number];
  readStage?: (typeof nativeBotReadStages)[number];
  readCause?: (typeof nativeBotReadCauses)[number];
}

function nullableNativeHttpStatus(value: unknown): value is number | null {
  return (
    value === null ||
    (typeof value === 'number' && Number.isInteger(value) && value >= 100 && value <= 599)
  );
}

function nullableBoolean(value: unknown): value is boolean | null {
  return value === null || typeof value === 'boolean';
}

/** Phase constraints keep an absent response distinct from a denied PUT and later readback. */
function helperMarkPhaseMatches(
  action: unknown,
  httpStatus: unknown,
  markHttpStatus: unknown,
  statusEqual: unknown,
  revisionEqual: unknown,
  excusedTransition: unknown,
): boolean {
  const noEquality = statusEqual === null && revisionEqual === null;
  if (action === 'BEFORE_REPORT' || action === 'BEFORE_ENTRY')
    return (
      markHttpStatus === null &&
      noEquality &&
      excusedTransition === null &&
      (action === 'BEFORE_REPORT' || httpStatus === 200)
    );
  if (typeof excusedTransition !== 'boolean') return false;
  if (action === 'MARK_REQUEST')
    return httpStatus === null && markHttpStatus === null && noEquality;
  if (action === 'MARK_STATUS') return httpStatus === markHttpStatus && noEquality;
  if (markHttpStatus !== 403) return false;
  if (action === 'AFTER_REPORT') return noEquality;
  if (httpStatus !== 200) return false;
  if (action === 'STATUS_EQUAL')
    return noEquality || (statusEqual === false && typeof revisionEqual === 'boolean');
  return action === 'REVISION_EQUAL' && statusEqual === true && revisionEqual === false;
}

/** First facts only; no raw messages, commands, output, paths or object-supplied class names. */
export function retainNativePrimaryFailure(
  annotations: TestResult['annotations'],
  path: NativePrimaryPath,
  action: NativePrimaryFailure['action'],
  error: unknown,
  jobStatus?: unknown,
): void {
  if (
    annotations.length >= 32 ||
    safeNativePrimaryFailures(annotations).some((row) => row.path === path)
  )
    return;
  let helperMarkStatus:
    | {
        httpStatus: number | null;
        markHttpStatus: number | null;
        statusEqual: boolean | null;
        revisionEqual: boolean | null;
        excusedTransition: boolean | null;
      }
    | undefined;
  if (path === 'HELPER_MARK_DENIAL') {
    // A descriptor Proxy can change on every read. Validate exactly the captured primitives.
    const httpStatus = ownData(jobStatus, 'httpStatus');
    const markHttpStatus = ownData(jobStatus, 'markHttpStatus');
    const statusEqual = ownData(jobStatus, 'statusEqual');
    const revisionEqual = ownData(jobStatus, 'revisionEqual');
    const excusedTransition = ownData(jobStatus, 'excusedTransition');
    helperMarkStatus = {
      httpStatus: nullableNativeHttpStatus(httpStatus) ? httpStatus : null,
      markHttpStatus: nullableNativeHttpStatus(markHttpStatus) ? markHttpStatus : null,
      statusEqual: typeof statusEqual === 'boolean' ? statusEqual : null,
      revisionEqual: typeof revisionEqual === 'boolean' ? revisionEqual : null,
      excusedTransition: typeof excusedTransition === 'boolean' ? excusedTransition : null,
    };
  }
  if (
    helperMarkStatus &&
    !helperMarkPhaseMatches(
      action,
      helperMarkStatus.httpStatus,
      helperMarkStatus.markHttpStatus,
      helperMarkStatus.statusEqual,
      helperMarkStatus.revisionEqual,
      helperMarkStatus.excusedTransition,
    )
  )
    return;
  let errorClass: NativePrimaryFailure['errorClass'] = 'UNKNOWN';
  const name = ownData(error, 'name');
  if (nativeErrorClasses.some((candidate) => candidate === name))
    errorClass = name as NativePrimaryFailure['errorClass'];
  else {
    try {
      if (error instanceof SyntaxError) errorClass = 'SyntaxError';
      else if (error instanceof TypeError) errorClass = 'TypeError';
      else if (error instanceof RangeError) errorClass = 'RangeError';
      else if (error instanceof Error) errorClass = 'Error';
    } catch {
      // A hostile prototype cannot supply diagnostic data.
    }
  }
  let code: NativePrimaryFailure['code'] = 'UNKNOWN';
  let current = error;
  for (let depth = 0; depth < 3; depth++) {
    const candidate = ownData(current, 'code') ?? ownData(current, 'errno');
    if (nativeErrorCodes.some((value) => value !== 'UNKNOWN' && value === candidate)) {
      code = candidate as NativePrimaryFailure['code'];
      break;
    }
    current = ownData(current, 'cause');
  }
  const status = ownData(error, 'status');
  const exitStatus =
    typeof status === 'number' && Number.isInteger(status) && status >= 0 && status <= 255
      ? status
      : 'UNKNOWN';
  const state = nativeJobStates.find((value) => value === ownData(jobStatus, 'state')) ?? 'UNKNOWN';
  const errorCode =
    nativeJobErrors.find((value) => value === ownData(jobStatus, 'errorCode')) ?? 'UNKNOWN';
  const exportStatus =
    path === 'ASYNC_REPORT' ? { state, ...(state === 'error' ? { errorCode } : {}) } : {};
  let transportHint: (typeof nativeTransportHints)[number] = 'UNKNOWN';
  const message = path === 'SEMESTER_PREVIEW' ? ownData(error, 'message') : undefined;
  if (typeof message === 'string') {
    const bounded = message.slice(0, 513);
    const newline = bounded.indexOf('\n');
    const firstLine = (newline < 0 ? bounded : bounded.slice(0, newline)).replace(/\r$/, '');
    const prefix = 'apiRequestContext.get: ';
    if (
      (newline >= 0 || message.length <= 512) &&
      Buffer.byteLength(firstLine, 'utf8') <= 512 &&
      firstLine.startsWith(prefix)
    ) {
      const detail = firstLine.slice(prefix.length);
      if (
        [
          'self-signed certificate',
          'self-signed certificate in certificate chain',
          'unable to verify the first certificate',
          'unable to get local issuer certificate',
          'certificate has expired',
          'certificate is not yet valid',
          'Unsupported TLS certificate.',
        ].includes(detail)
      )
        transportHint = 'TLS_CERTIFICATE';
      else if (
        detail === 'Client network socket disconnected before secure TLS connection was established'
      )
        transportHint = 'TLS_HANDSHAKE';
      else if (['read ECONNRESET', 'write ECONNRESET', 'socket hang up'].includes(detail))
        transportHint = 'ECONNRESET';
      else if (detail === 'Request context disposed.') transportHint = 'REQUEST_CONTEXT_DISPOSED';
      else if (/^Timeout [1-9][0-9]{0,8}ms exceeded\.$/.test(detail))
        transportHint = 'REQUEST_TIMEOUT';
      else {
        const refused = /^connect ECONNREFUSED ([0-9a-fA-F:.]+):([0-9]{1,5})$/.exec(detail);
        if (refused && isIP(refused[1]) && Number(refused[2]) > 0 && Number(refused[2]) <= 65535)
          transportHint = 'ECONNREFUSED';
      }
      const compatibleCodes =
        transportHint === 'TLS_HANDSHAKE'
          ? ['UNKNOWN', 'ECONNRESET']
          : transportHint === 'ECONNRESET' || transportHint === 'ECONNREFUSED'
            ? ['UNKNOWN', transportHint]
            : transportHint === 'REQUEST_TIMEOUT'
              ? ['UNKNOWN', 'ETIMEDOUT']
              : ['UNKNOWN'];
      if (!compatibleCodes.includes(code)) transportHint = 'UNKNOWN';
    }
  }
  const transport = path === 'SEMESTER_PREVIEW' ? { transportHint } : {};
  let reloadTransport: (typeof nativeReloadTransportHints)[number] = 'UNKNOWN';
  if (path === 'SESSION_RELOAD') {
    if (code === 'ABORT_ERR' || errorClass === 'AbortError') reloadTransport = 'ABORTED';
    else if (code === 'ECONNRESET') reloadTransport = 'RESET';
    else if (code === 'ECONNREFUSED') reloadTransport = 'REFUSED';
    else if (code === 'ETIMEDOUT' || errorClass === 'TimeoutError') reloadTransport = 'TIMEOUT';
    else if (code === 'ENOTFOUND' || code === 'EAI_AGAIN') reloadTransport = 'DNS';
    else if (errorClass === 'TargetClosedError') reloadTransport = 'TARGET_CLOSED';
    else if (code === 'UNKNOWN' && (action === 'NAVIGATION' || action === 'WAIT_RESPONSE')) {
      const raw = ownData(error, 'message');
      if (typeof raw === 'string') {
        const bounded = raw.slice(0, 513);
        const newline = bounded.indexOf('\n');
        const firstLine = (newline < 0 ? bounded : bounded.slice(0, newline)).replace(/\r$/, '');
        const prefix = action === 'NAVIGATION' ? 'page.reload: ' : 'page.waitForResponse: ';
        if (
          (newline >= 0 || raw.length <= 512) &&
          Buffer.byteLength(firstLine, 'utf8') <= 512 &&
          !/[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(firstLine) &&
          firstLine.startsWith(prefix)
        ) {
          const detail = firstLine.slice(prefix.length);
          if (detail === 'Target page, context or browser has been closed')
            reloadTransport = 'TARGET_CLOSED';
          else if (/^Timeout [1-9][0-9]{0,8}ms exceeded\.$/.test(detail))
            reloadTransport = 'TIMEOUT';
          else if (action === 'NAVIGATION') {
            const net =
              /^net::(ERR_ABORTED|ERR_CONNECTION_RESET|ERR_CONNECTION_REFUSED|ERR_CONNECTION_TIMED_OUT|ERR_TIMED_OUT|ERR_NAME_NOT_RESOLVED|ERR_CERT_AUTHORITY_INVALID|ERR_CERT_DATE_INVALID|ERR_CERT_COMMON_NAME_INVALID|ERR_SSL_PROTOCOL_ERROR)(?=$|[; ])/.exec(
                detail,
              );
            if (net?.[1] === 'ERR_ABORTED') reloadTransport = 'ABORTED';
            else if (net?.[1] === 'ERR_CONNECTION_RESET') reloadTransport = 'RESET';
            else if (net?.[1] === 'ERR_CONNECTION_REFUSED') reloadTransport = 'REFUSED';
            else if (net?.[1] === 'ERR_CONNECTION_TIMED_OUT' || net?.[1] === 'ERR_TIMED_OUT')
              reloadTransport = 'TIMEOUT';
            else if (net?.[1] === 'ERR_NAME_NOT_RESOLVED') reloadTransport = 'DNS';
            else if (net) reloadTransport = 'TLS';
          }
        }
      }
    }
  }
  const observedHttpStatus =
    path === 'SESSION_RELOAD' ? ownData(jobStatus, 'httpStatus') : undefined;
  const reloadStatus =
    path === 'SESSION_RELOAD'
      ? {
          httpStatus:
            action !== 'NAVIGATION' &&
            action !== 'WAIT_RESPONSE' &&
            typeof observedHttpStatus === 'number' &&
            Number.isInteger(observedHttpStatus) &&
            observedHttpStatus >= 100 &&
            observedHttpStatus <= 599
              ? observedHttpStatus
              : null,
          transportHint: reloadTransport,
        }
      : {};
  const checks = path === 'BOT_ROLE_MENU' ? ownData(jobStatus, 'checks') : undefined;
  const headmanReadOutcome =
    path === 'BOT_ROLE_MENU' ? ownData(jobStatus, 'headmanReadOutcome') : undefined;
  const readStage = path === 'BOT_ROLE_MENU' ? ownData(jobStatus, 'readStage') : undefined;
  const readCause = path === 'BOT_ROLE_MENU' ? ownData(jobStatus, 'readCause') : undefined;
  const botPhase =
    path === 'BOT_ROLE_MENU'
      ? {
          phase:
            nativeBotPhases.find((value) => value === ownData(jobStatus, 'phase')) ?? 'UNKNOWN',
          ...(headmanReadOutcome === undefined
            ? {}
            : {
                headmanReadOutcome:
                  nativeBotReadOutcomes.find((value) => value === headmanReadOutcome) ?? 'UNKNOWN',
              }),
          ...(readStage === undefined && readCause === undefined
            ? {}
            : {
                readStage: nativeBotReadStages.find((value) => value === readStage) ?? 'UNKNOWN',
                readCause: nativeBotReadCauses.find((value) => value === readCause) ?? 'UNKNOWN',
              }),
          checks:
            typeof checks === 'number' && Number.isInteger(checks) && checks >= 0 && checks <= 60
              ? checks
              : 'UNKNOWN',
        }
      : {};
  annotations.push({
    type: nativePrimaryAnnotation,
    description: JSON.stringify({
      path,
      action,
      errorClass,
      code,
      exitStatus,
      ...exportStatus,
      ...transport,
      ...botPhase,
      ...reloadStatus,
      ...helperMarkStatus,
    }),
  });
}

/** Reconstruct fixed failure projections from untrusted, bounded annotations. */
export function safeNativePrimaryFailures(annotations: unknown): NativePrimaryFailure[] {
  const result: NativePrimaryFailure[] = [];
  if (!Array.isArray(annotations)) return result;
  for (let index = 0; index < Math.min(annotations.length, 32); index++) {
    const annotation = ownData(annotations, String(index));
    if (ownData(annotation, 'type') !== nativePrimaryAnnotation) continue;
    const description = ownData(annotation, 'description');
    if (typeof description !== 'string' || description.length > 384) continue;
    let value: unknown;
    try {
      value = JSON.parse(description);
    } catch {
      continue;
    }
    if (!value || typeof value !== 'object' || Array.isArray(value)) continue;
    const path = ownData(value, 'path');
    const state = ownData(value, 'state');
    const errorCode = ownData(value, 'errorCode');
    const transportHint = ownData(value, 'transportHint');
    const httpStatus = ownData(value, 'httpStatus');
    const markHttpStatus = ownData(value, 'markHttpStatus');
    const statusEqual = ownData(value, 'statusEqual');
    const revisionEqual = ownData(value, 'revisionEqual');
    const excusedTransition = ownData(value, 'excusedTransition');
    const phase = ownData(value, 'phase');
    const checks = ownData(value, 'checks');
    const headmanReadOutcome = ownData(value, 'headmanReadOutcome');
    const readStage = ownData(value, 'readStage');
    const readCause = ownData(value, 'readCause');
    const boundedChecks =
      typeof checks === 'number' && Number.isInteger(checks) && checks >= 0 && checks <= 60
        ? checks
        : 'UNKNOWN';
    const fields = ['path', 'action', 'errorClass', 'code', 'exitStatus'];
    if (path === 'SEMESTER_PREVIEW') fields.push('transportHint');
    if (path === 'SESSION_RELOAD') fields.push('transportHint', 'httpStatus');
    if (path === 'HELPER_MARK_DENIAL')
      fields.push(
        'httpStatus',
        'markHttpStatus',
        'statusEqual',
        'revisionEqual',
        'excusedTransition',
      );
    if (path === 'ASYNC_REPORT') fields.push('state');
    if (path === 'ASYNC_REPORT' && state === 'error') fields.push('errorCode');
    if (path === 'BOT_ROLE_MENU') fields.push('phase');
    if (path === 'BOT_ROLE_MENU' && checks !== undefined) fields.push('checks');
    if (path === 'BOT_ROLE_MENU' && headmanReadOutcome !== undefined)
      fields.push('headmanReadOutcome');
    if (path === 'BOT_ROLE_MENU' && readStage !== undefined) fields.push('readStage');
    if (path === 'BOT_ROLE_MENU' && readCause !== undefined) fields.push('readCause');
    const keys = Object.keys(value);
    if (
      keys.length !== fields.length ||
      keys.some((key) => !fields.includes(key)) ||
      (path === 'HELPER_MARK_DENIAL' &&
        (!nullableNativeHttpStatus(httpStatus) ||
          !nullableNativeHttpStatus(markHttpStatus) ||
          !nullableBoolean(statusEqual) ||
          !nullableBoolean(revisionEqual) ||
          !nullableBoolean(excusedTransition) ||
          !helperMarkPhaseMatches(
            ownData(value, 'action'),
            httpStatus,
            markHttpStatus,
            statusEqual,
            revisionEqual,
            excusedTransition,
          ))) ||
      (path === 'SEMESTER_PREVIEW' &&
        !nativeTransportHints.some((candidate) => candidate === transportHint)) ||
      (path === 'SESSION_RELOAD' &&
        (!nativeReloadTransportHints.some((candidate) => candidate === transportHint) ||
          !(
            httpStatus === null ||
            (typeof httpStatus === 'number' &&
              Number.isInteger(httpStatus) &&
              httpStatus >= 100 &&
              httpStatus <= 599)
          ) ||
          ((ownData(value, 'action') === 'NAVIGATION' ||
            ownData(value, 'action') === 'WAIT_RESPONSE') &&
            httpStatus !== null))) ||
      (path === 'ASYNC_REPORT' && !nativeJobStates.some((candidate) => candidate === state)) ||
      (path === 'BOT_ROLE_MENU' && !nativeBotPhases.some((candidate) => candidate === phase)) ||
      (path === 'BOT_ROLE_MENU' &&
        headmanReadOutcome !== undefined &&
        !nativeBotReadOutcomes.some((candidate) => candidate === headmanReadOutcome)) ||
      (path === 'BOT_ROLE_MENU' &&
        ((readStage === undefined) !== (readCause === undefined) ||
          (readStage !== undefined &&
            !nativeBotReadStages.some((candidate) => candidate === readStage)) ||
          (readCause !== undefined &&
            !nativeBotReadCauses.some((candidate) => candidate === readCause)))) ||
      (path === 'ASYNC_REPORT' &&
        state === 'error' &&
        !nativeJobErrors.some((candidate) => candidate === errorCode))
    )
      continue;
    const action = ownData(value, 'action');
    const errorClass = ownData(value, 'errorClass');
    const code = ownData(value, 'code');
    const exitStatus = ownData(value, 'exitStatus');
    if (
      (path !== 'SUBJECT_OTP' &&
        path !== 'SEMESTER_PREVIEW' &&
        path !== 'ASYNC_REPORT' &&
        path !== 'BOT_ROLE_MENU' &&
        path !== 'SESSION_RELOAD' &&
        path !== 'HELPER_MARK_DENIAL') ||
      !nativePrimaryPaths[path].some((candidate) => candidate === action) ||
      !nativeErrorClasses.some((candidate) => candidate === errorClass) ||
      !nativeErrorCodes.some((candidate) => candidate === code) ||
      !(
        exitStatus === 'UNKNOWN' ||
        (typeof exitStatus === 'number' &&
          Number.isInteger(exitStatus) &&
          exitStatus >= 0 &&
          exitStatus <= 255)
      ) ||
      result.some((row) => row.path === path)
    )
      continue;
    if (
      path === 'SESSION_RELOAD' &&
      description !==
        JSON.stringify({ path, action, errorClass, code, exitStatus, httpStatus, transportHint })
    )
      continue;
    if (
      path === 'HELPER_MARK_DENIAL' &&
      description !==
        JSON.stringify({
          path,
          action,
          errorClass,
          code,
          exitStatus,
          httpStatus,
          markHttpStatus,
          statusEqual,
          revisionEqual,
          excusedTransition,
        })
    )
      continue;
    result.push({
      path,
      action,
      errorClass,
      code,
      exitStatus,
      ...(path === 'ASYNC_REPORT' ? { state, ...(state === 'error' ? { errorCode } : {}) } : {}),
      ...(path === 'SEMESTER_PREVIEW' ? { transportHint } : {}),
      ...(path === 'SESSION_RELOAD' ? { transportHint, httpStatus } : {}),
      ...(path === 'HELPER_MARK_DENIAL'
        ? { httpStatus, markHttpStatus, statusEqual, revisionEqual, excusedTransition }
        : {}),
      ...(path === 'BOT_ROLE_MENU'
        ? {
            phase,
            ...(checks === undefined ? {} : { checks: boundedChecks }),
            ...(headmanReadOutcome === undefined ? {} : { headmanReadOutcome }),
            ...(readStage === undefined ? {} : { readStage, readCause }),
          }
        : {}),
    } as NativePrimaryFailure);
  }
  return result;
}

/** Untrusted annotations retain only closed fixture/service names and fixed boolean fields. */
export function safeOwnedContainerIdentityFailure(
  annotations: unknown,
): OwnedContainerIdentityFailure | undefined {
  if (!Array.isArray(annotations)) return;
  for (let index = 0; index < Math.min(annotations.length, 32); index++) {
    const annotation = ownData(annotations, String(index));
    if (ownData(annotation, 'type') !== ownedContainerIdentityAnnotation) continue;
    const description = ownData(annotation, 'description');
    if (typeof description !== 'string' || description.length > 512) continue;
    let value: unknown;
    try {
      value = JSON.parse(description);
    } catch {
      continue;
    }
    if (!value || typeof value !== 'object' || Array.isArray(value)) continue;
    const keys = Object.keys(value);
    if (keys.length !== 3 || keys.some((key) => !['fixture', 'service', 'changed'].includes(key)))
      continue;
    const fixture = ownData(value, 'fixture');
    const service = ownData(value, 'service');
    const changed = ownData(value, 'changed');
    if (
      (fixture !== 'promotion' && fixture !== 'subjectdelete') ||
      (service !== 'postgres-academic' &&
        service !== 'postgres-schedule' &&
        service !== 'mongo-attendance') ||
      (fixture === 'promotion' && service !== 'postgres-academic') ||
      !changed ||
      typeof changed !== 'object' ||
      Array.isArray(changed) ||
      Object.keys(changed).length !== ownedContainerIdentityFields.length ||
      ownedContainerIdentityFields.some((field) => typeof ownData(changed, field) !== 'boolean') ||
      !ownedContainerIdentityFields.some((field) => ownData(changed, field) === true)
    )
      continue;
    return {
      fixture,
      service,
      changed: Object.fromEntries(
        ownedContainerIdentityFields.map((field) => [field, ownData(changed, field)]),
      ) as OwnedContainerIdentityFailure['changed'],
    };
  }
}

/** Closed source routes only; IDs are matched in RAM and never copied into diagnostics. */
const datasetHttpOperations = {
  DATASET_SEMESTERS_READ_HTTP: {
    method: 'GET',
    path: /^\/api\/academic\/semesters\?(?:size=100|page=[0-4]&size=200&sort=id,asc)$/,
  },
  DATASET_ADMIN_ACCESS_READ_HTTP: { method: 'GET', path: /^\/api\/academic\/admin-access\/me$/ },
  DATASET_SEMESTER_READ_HTTP: {
    method: 'GET',
    path: /^\/api\/academic\/semesters\/[1-9][0-9]{0,18}$/,
  },
  DATASET_SEMESTER_DELETE_PREVIEW_HTTP: {
    method: 'GET',
    path: /^\/api\/academic\/semesters\/[1-9][0-9]{0,18}\/delete-preview$/,
  },
  DATASET_REPORT_DOWNLOAD_HTTP: {
    method: 'GET',
    path: /^\/api\/report-download\/[A-Za-z0-9_-]{43}$/,
  },
  DATASET_EXPORT_JOB_CREATE_HTTP: { method: 'POST', path: /^\/api\/auth\/export-jobs$/ },
  DATASET_EXPORT_JOB_STATUS_HTTP: {
    method: 'GET',
    path: /^\/api\/auth\/export-jobs\/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/,
  },
  DATASET_EXPORT_JOB_TICKET_HTTP: {
    method: 'POST',
    path: /^\/api\/auth\/export-jobs\/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\/ticket$/,
  },
  DATASET_SEMESTER_ARCHIVE_HTTP: {
    method: 'POST',
    path: /^\/api\/academic\/semesters\/[1-9][0-9]{0,18}\/archive$/,
  },
  DATASET_SEMESTER_RESTORE_HTTP: {
    method: 'POST',
    path: /^\/api\/academic\/semesters\/[1-9][0-9]{0,18}\/restore$/,
  },
  DATASET_SEMESTER_ARCHIVE_STATUS_HTTP: {
    method: 'GET',
    path: /^\/api\/academic\/semesters\/[1-9][0-9]{0,18}\/archive\/status$/,
  },
  DATASET_LESSONS_READ_HTTP: {
    method: 'GET',
    path: /^\/api\/schedule\/groups\/[1-9][0-9]{0,18}\/lessons\?dateFrom=[1-9][0-9]{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])&dateTo=[1-9][0-9]{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])&size=200$/,
  },
  DATASET_ATTENDANCE_REPORT_HTTP: {
    method: 'GET',
    path: /^\/api\/attendance\/reports\/lesson\/[1-9][0-9]{0,18}$/,
  },
  DATASET_ATTENDANCE_MARK_HTTP: {
    method: 'PUT',
    path: /^\/api\/attendance\/lessons\/[1-9][0-9]{0,18}\/students\/[1-9][0-9]{0,18}$/,
  },
  DATASET_STUDENT_HISTORY_HTTP: {
    method: 'GET',
    path: /^\/api\/v1\/student\/attendance\?semesterId=[1-9][0-9]{0,18}$/,
  },
  DATASET_HOMEWORK_READ_HTTP: {
    method: 'GET',
    path: /^\/api\/academic\/homeworks\/[1-9][0-9]{0,18}$/,
  },
  DATASET_HOMEWORK_UPDATE_HTTP: {
    method: 'PUT',
    path: /^\/api\/academic\/homeworks\/[1-9][0-9]{0,18}$/,
  },
  DATASET_SEMESTER_CREATE_HTTP: { method: 'POST', path: /^\/api\/academic\/semesters$/ },
  DATASET_SEMESTER_ACTIVATE_HTTP: {
    method: 'PATCH',
    path: /^\/api\/academic\/semesters\/[1-9][0-9]{0,18}\/activate$/,
  },
  DATASET_GROUP_CREATE_HTTP: { method: 'POST', path: /^\/api\/academic\/groups\/registry$/ },
  DATASET_USER_CREATE_HTTP: { method: 'POST', path: /^\/api\/academic\/users$/ },
  DATASET_USER_ARCHIVE_HTTP: {
    method: 'POST',
    path: /^\/api\/academic\/users\/[1-9][0-9]{0,18}\/archive$/,
  },
  DATASET_ASSIGNMENT_CLOSE_HTTP: {
    method: 'DELETE',
    path: /^\/api\/academic\/assignments\/[1-9][0-9]{0,18}\?validUntilExclusive=[1-9][0-9]{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])$/,
  },
  DATASET_HEADMAN_SET_HTTP: {
    method: 'PUT',
    path: /^\/api\/academic\/groups\/[1-9][0-9]{0,18}\/headman$/,
  },
  DATASET_SUBJECT_CREATE_HTTP: { method: 'POST', path: /^\/api\/academic\/subjects$/ },
  DATASET_SUBJECT_READ_HTTP: {
    method: 'GET',
    path: /^\/api\/academic\/subjects\/[1-9][0-9]{0,18}$/,
  },
  DATASET_SUBJECT_DELETE_PREVIEW_HTTP: {
    method: 'GET',
    path: /^\/api\/academic\/subjects\/[1-9][0-9]{0,18}\/deletion-preview$/,
  },
  DATASET_LESSON_CREATE_HTTP: { method: 'POST', path: /^\/api\/schedule\/one-off-lessons$/ },
  DATASET_LESSON_CANCEL_HTTP: {
    method: 'PATCH',
    path: /^\/api\/schedule\/lessons\/[1-9][0-9]{0,18}\/cancel$/,
  },
  DATASET_LESSON_TRANSFER_HTTP: {
    method: 'POST',
    path: /^\/api\/schedule\/lessons\/[1-9][0-9]{0,18}\/transfer$/,
  },
  DATASET_HOMEWORK_CREATE_HTTP: { method: 'POST', path: /^\/api\/academic\/homeworks$/ },
  DATASET_ASSISTANT_CREATE_HTTP: { method: 'POST', path: /^\/api\/academic\/assistants$/ },
} as const;
type DatasetHttpCause = keyof typeof datasetHttpOperations;
const datasetHttpStages = Object.fromEntries(
  Object.keys(datasetHttpOperations).map((cause) => [cause, 'DATASET_HTTP']),
) as Record<DatasetHttpCause, 'DATASET_HTTP'>;

const subjectCreateProblemStages = {
  SUBJECT_CREATE_PROBLEM_ASSIGNMENT_ADOPTION_PENDING: 'SUBJECT_CREATE_PROBLEM',
  SUBJECT_CREATE_PROBLEM_IDENTITY_PUBLICATION_PENDING: 'SUBJECT_CREATE_PROBLEM',
  SUBJECT_CREATE_PROBLEM_GATEWAY_DEPENDENCY_UNAVAILABLE: 'SUBJECT_CREATE_PROBLEM',
  SUBJECT_CREATE_PROBLEM_OTHER: 'SUBJECT_CREATE_PROBLEM',
  SUBJECT_CREATE_PROBLEM_UNKNOWN: 'SUBJECT_CREATE_PROBLEM',
} as const;

const subjectDeletePreviewProblemStages = {
  SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED: 'SUBJECT_DELETE_PREVIEW_PROBLEM',
  SUBJECT_DELETE_PREVIEW_PROBLEM_IDENTITY_PUBLICATION_PENDING: 'SUBJECT_DELETE_PREVIEW_PROBLEM',
  SUBJECT_DELETE_PREVIEW_PROBLEM_GATEWAY_DEPENDENCY_UNAVAILABLE: 'SUBJECT_DELETE_PREVIEW_PROBLEM',
  SUBJECT_DELETE_PREVIEW_PROBLEM_ABOUT_BLANK: 'SUBJECT_DELETE_PREVIEW_PROBLEM',
  SUBJECT_DELETE_PREVIEW_PROBLEM_OTHER: 'SUBJECT_DELETE_PREVIEW_PROBLEM',
  SUBJECT_DELETE_PREVIEW_PROBLEM_UNKNOWN: 'SUBJECT_DELETE_PREVIEW_PROBLEM',
} as const;

const subjectDeletePreviewPhases = [
  'LOCAL_TARGET',
  'LOCAL_SNAPSHOT',
  'SCHEDULE_PREVIEW',
  'ATTENDANCE_PREVIEW',
] as const;
const subjectDeletePreviewRpcFailures = [
  'CANCELLED',
  'UNKNOWN',
  'INVALID_ARGUMENT',
  'DEADLINE_EXCEEDED',
  'NOT_FOUND',
  'ALREADY_EXISTS',
  'PERMISSION_DENIED',
  'RESOURCE_EXHAUSTED',
  'OUT_OF_RANGE',
  'UNIMPLEMENTED',
  'INTERNAL',
  'UNAVAILABLE',
  'DATA_LOSS',
  'UNAUTHENTICATED',
] as const;
interface SubjectDeletePreviewMetadata {
  phase: (typeof subjectDeletePreviewPhases)[number];
  reason:
    | 'IDENTITY_READ_FENCE'
    | 'LOCAL_STORAGE'
    | 'CREDENTIAL_CONFIGURATION'
    | 'AUTHORITY_CONFIGURATION'
    | 'RPC_FAILURE'
    | 'RECEIPT_MISMATCH'
    | 'UNEXPECTED';
  rpcStatus: 'NOT_APPLICABLE' | (typeof subjectDeletePreviewRpcFailures)[number];
}
const subjectDeletePreviewMetadataPrefix = 'SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED:503:';

const headmanCleanupStages = {
  HEADMAN_CLEANUP_SHORTCUT_SUBJECT_READ: 'HEADMAN_CLEANUP',
  HEADMAN_CLEANUP_SHORTCUT_ASSIGNMENT_IDENTITY: 'HEADMAN_CLEANUP',
  HEADMAN_CLEANUP_SHORTCUT_ASSIGNMENT_CLOSE: 'HEADMAN_CLEANUP',
  HEADMAN_CLEANUP_SHORTCUT_CATALOG_READ: 'HEADMAN_CLEANUP',
  HEADMAN_CLEANUP_SHORTCUT_CATALOG_SCOPE: 'HEADMAN_CLEANUP',
  HEADMAN_CLEANUP_PENDING_REQUEST_CANCEL: 'HEADMAN_CLEANUP',
  HEADMAN_CLEANUP_FUTURE_LESSON_RESTORE: 'HEADMAN_CLEANUP',
  HEADMAN_CLEANUP_CONTRAST_LESSON_CANCEL: 'HEADMAN_CLEANUP',
} as const;
export type HeadmanCleanupCause = keyof typeof headmanCleanupStages;
const cleanupHttpStatuses = [
  0, 200, 204, 400, 401, 403, 404, 409, 410, 422, 423, 429, 500, 502, 503, 504,
] as const;
type CleanupHttpStatus = (typeof cleanupHttpStatuses)[number] | 'OTHER';

const lifecycleCleanupStages = {
  CLEANUP_TRANSFER_CANCEL: 'LIFECYCLE_CLEANUP',
  CLEANUP_TRANSFER_CONTEXT: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_RESTART: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_RECOVER: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_PUBLISH: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_APPLY: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_ARCHIVE: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_RESTORE: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_ACTIVATE: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_VERIFY: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_DLQ: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_SETTLE: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_CLOCK: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_DISPOSE: 'LIFECYCLE_CLEANUP',
  CLEANUP_SEMESTER_WRITE: 'LIFECYCLE_CLEANUP',
} as const;
export type LifecycleCleanupCause = keyof typeof lifecycleCleanupStages;
const transportCodes = [
  'TIMEOUT',
  'ECONNRESET',
  'ECONNREFUSED',
  'ETIMEDOUT',
  'EPIPE',
  'ENOTFOUND',
  'EAI_AGAIN',
  'ABORTED',
  'TARGET_CLOSED',
  'UNKNOWN',
] as const;
type TransportCode = (typeof transportCodes)[number];
type LifecycleTransportCause =
  | `SEMESTER_PREVIEW_${TransportCode}`
  | `CLEANUP_TRANSPORT_${TransportCode}`;
const lifecycleTransportStages = Object.fromEntries(
  transportCodes.flatMap((code) => [
    [`SEMESTER_PREVIEW_${code}`, 'SEMESTER_PREVIEW_TRANSPORT'],
    [`CLEANUP_TRANSPORT_${code}`, 'LIFECYCLE_CLEANUP_TRANSPORT'],
  ]),
) as Record<LifecycleTransportCause, 'SEMESTER_PREVIEW_TRANSPORT' | 'LIFECYCLE_CLEANUP_TRANSPORT'>;

const failureStages = {
  UNKNOWN: 'UNKNOWN',
  ARTIFACT_REPORTER_GUARD: 'ARTIFACT_PRIVACY',
  ARTIFACT_CAPTURE_GUARD: 'ARTIFACT_PRIVACY',
  DATASET_TARGET_GUARD: 'DATASET_TARGET',
  DATASET_CREDENTIALS_MISSING: 'DATASET_USER',
  ACTOR_LOGIN_HTTP: 'ACTOR_LOGIN',
  ACTOR_LOGIN_GATEWAY_LOCAL: 'ACTOR_LOGIN',
  ACTOR_LOGIN_AUTH_SERVICE: 'ACTOR_LOGIN',
  ACTOR_SESSION_HTTP: 'ACTOR_SESSION',
  ACTOR_ROLE_HTTP: 'ACTOR_ROLE',
  BROWSER_ROLE_DENIED: 'BROWSER_ROLE',
  CLOCK_TARGET_GUARD: 'TEACHING_CLOCK',
  CLOCK_DISPOSABLE_GUARD: 'TEACHING_CLOCK',
  CLOCK_FORMAT_GUARD: 'TEACHING_CLOCK',
  CLOCK_INSTANT_GUARD: 'TEACHING_CLOCK',
  CLOCK_WINDOW_GUARD: 'TEACHING_CLOCK',
  INVALID_TIME_VALUE: 'UNKNOWN',
  ...datasetHttpStages,
  ...subjectCreateProblemStages,
  ...subjectDeletePreviewProblemStages,
  ...headmanCleanupStages,
  ...lifecycleCleanupStages,
  ...lifecycleTransportStages,
} as const;
type FailureCause = keyof typeof failureStages;
const reportedClassHints = {
  Error: 'REPORTED_CLASS_ERROR',
  TypeError: 'REPORTED_CLASS_TYPE_ERROR',
  RangeError: 'REPORTED_CLASS_RANGE_ERROR',
  ReferenceError: 'REPORTED_CLASS_REFERENCE_ERROR',
  SyntaxError: 'REPORTED_CLASS_SYNTAX_ERROR',
  TimeoutError: 'REPORTED_CLASS_TIMEOUT_ERROR',
} as const;
const reportedFrameHints = {
  'Actor.call': 'REPORTED_FRAME_DATASET_CALL',
  moscowDate: 'REPORTED_FRAME_DATASET_DATE',
  'MobileDataset.initialize': 'REPORTED_FRAME_DATASET_INITIALIZE',
} as const;
type ReportedFailureHint =
  | (typeof reportedClassHints)[keyof typeof reportedClassHints]
  | (typeof reportedFrameHints)[keyof typeof reportedFrameHints];
const failureHttpStatuses = [
  0, 200, 201, 202, 400, 401, 403, 404, 409, 410, 422, 423, 429, 500, 502, 503, 504,
] as const;
type FailureHttpStatus = (typeof failureHttpStatuses)[number];

export const transferObservationAnnotation = 'rct-own-transfer-v1';
const transferStates = ['UNOBSERVED', 'PENDING', 'COMPLETED', 'ERROR', 'OTHER'] as const;
type TransferState = (typeof transferStates)[number];
type TransferHttpStatus = FailureHttpStatus | 'OTHER';

export function closedTransferState(value: unknown): TransferState {
  return value === 'PENDING' || value === 'COMPLETED' || value === 'ERROR' ? value : 'OTHER';
}

export function closedTransferHttpStatus(value: number): TransferHttpStatus {
  return failureHttpStatuses.some((status) => status === value)
    ? (value as FailureHttpStatus)
    : 'OTHER';
}

/** Observed fixture facts, never an authenticated backend failure cause. */
export interface TransferObservation {
  postHttpStatus: TransferHttpStatus;
  postState: TransferState;
  getResponseCount: number;
  getObservationOverflow: boolean;
  lastGetHttpStatus: TransferHttpStatus;
  lastGetBody: 'UNOBSERVED' | 'READING' | 'PARSED' | 'INVALID' | 'NOT_READ';
  lastParsedState: TransferState;
  operationMatched: boolean;
  sourceMatched: boolean;
  occurrenceMatched: boolean;
  observationOutcome: 'RETURNED' | 'TIMEOUT' | 'ERROR';
}

/** Treat annotations as untrusted; reconstruct only a fixed, bounded projection. */
export function safeTransferObservation(annotations: unknown): TransferObservation | undefined {
  if (!Array.isArray(annotations)) return;
  for (let index = 0; index < Math.min(annotations.length, 32); index++) {
    const annotation = ownData(annotations, String(index));
    if (ownData(annotation, 'type') !== transferObservationAnnotation) continue;
    const description = ownData(annotation, 'description');
    if (typeof description !== 'string' || description.length > 768) continue;
    let value: TransferObservation;
    try {
      value = JSON.parse(description);
    } catch {
      continue;
    }
    const keys = [
      'postHttpStatus',
      'postState',
      'getResponseCount',
      'getObservationOverflow',
      'lastGetHttpStatus',
      'lastGetBody',
      'lastParsedState',
      'operationMatched',
      'sourceMatched',
      'occurrenceMatched',
      'observationOutcome',
    ] as const;
    if (
      !value ||
      typeof value !== 'object' ||
      Array.isArray(value) ||
      Object.keys(value).length !== keys.length ||
      keys.some((key) => !Object.prototype.hasOwnProperty.call(value, key))
    )
      continue;
    const validStatus = (status: unknown) =>
      status === 'OTHER' || failureHttpStatuses.some((candidate) => candidate === status);
    if (
      !validStatus(value.postHttpStatus) ||
      !validStatus(value.lastGetHttpStatus) ||
      !transferStates.includes(value.postState) ||
      !transferStates.includes(value.lastParsedState) ||
      !Number.isSafeInteger(value.getResponseCount) ||
      value.getResponseCount < 0 ||
      value.getResponseCount > 128 ||
      !['UNOBSERVED', 'READING', 'PARSED', 'INVALID', 'NOT_READ'].includes(value.lastGetBody) ||
      !['RETURNED', 'TIMEOUT', 'ERROR'].includes(value.observationOutcome) ||
      [
        value.getObservationOverflow,
        value.operationMatched,
        value.sourceMatched,
        value.occurrenceMatched,
      ].some((flag) => typeof flag !== 'boolean')
    )
      continue;
    return {
      postHttpStatus: value.postHttpStatus,
      postState: value.postState,
      getResponseCount: value.getResponseCount,
      getObservationOverflow: value.getObservationOverflow,
      lastGetHttpStatus: value.lastGetHttpStatus,
      lastGetBody: value.lastGetBody,
      lastParsedState: value.lastParsedState,
      operationMatched: value.operationMatched,
      sourceMatched: value.sourceMatched,
      occurrenceMatched: value.occurrenceMatched,
      observationOutcome: value.observationOutcome,
    };
  }
}

/** This marker reports source context; serialized/forged text authenticates no operation or cause. */
export function datasetHttpFailure(method: unknown, path: unknown, httpStatus: unknown): Error {
  if (
    typeof method !== 'string' ||
    typeof path !== 'string' ||
    path.length > 128 ||
    /[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(path) ||
    typeof httpStatus !== 'number' ||
    !failureHttpStatuses.some((status) => status !== 0 && status === httpStatus)
  )
    return new Error('Synthetic dataset request failed');
  for (const [cause, operation] of Object.entries(datasetHttpOperations)) {
    if (method === operation.method && operation.path.test(path))
      return new Error(`Reported synthetic dataset ${cause}: HTTP ${httpStatus}`);
  }
  return new Error('Synthetic dataset request failed');
}

export interface SafeFailureDiagnostic {
  stage: (typeof failureStages)[FailureCause];
  cause: FailureCause | ReportedFailureHint;
  httpStatus: FailureHttpStatus | CleanupHttpStatus;
  subjectPreview?: SubjectDeletePreviewMetadata;
}
const knownFailureMessages: Record<string, FailureCause> = {
  'Real-mobile requires the dedicated safe reporter, no debug/extra reporter and snapshot capture disabled':
    'ARTIFACT_REPORTER_GUARD',
  'Real-mobile credentials require trace/video/screenshots off': 'ARTIFACT_CAPTURE_GUARD',
  'Synthetic dataset requires RCT_E2E_SYNTHETIC_SEED=true and disposable HTTPS loopback:8443; private/local stands are forbidden':
    'DATASET_TARGET_GUARD',
  'Synthetic user creation omitted credentials': 'DATASET_CREDENTIALS_MISSING',
  'Browser role admission denied': 'BROWSER_ROLE_DENIED',
  'Teaching-clock lane requires explicit synthetic seed and absolute fixture directory':
    'CLOCK_TARGET_GUARD',
  'Teaching-clock lane requires a bounded disposable fixture': 'CLOCK_DISPOSABLE_GUARD',
  'Teaching-clock fixture must contain an ISO UTC instant': 'CLOCK_FORMAT_GUARD',
  'Teaching-clock instant is invalid': 'CLOCK_INSTANT_GUARD',
  'Positive-geo lane requires an ordinary weekday at 09:00 Europe/Moscow': 'CLOCK_WINDOW_GUARD',
  // An intrinsic public error hint; it does not identify a clock or a failing operation.
  'Invalid time value': 'INVALID_TIME_VALUE',
};
function failureDiagnostic(
  cause: FailureCause = 'UNKNOWN',
  httpStatus: FailureHttpStatus | CleanupHttpStatus = 0,
  subjectPreview?: SubjectDeletePreviewMetadata,
): SafeFailureDiagnostic {
  const diagnostic: SafeFailureDiagnostic = { stage: failureStages[cause], cause, httpStatus };
  if (cause === 'SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED' && httpStatus === 503 && subjectPreview)
    diagnostic.subjectPreview = {
      phase: subjectPreview.phase,
      reason: subjectPreview.reason,
      rpcStatus: subjectPreview.rpcStatus,
    };
  return diagnostic;
}
function ownData(value: unknown, key: string): unknown {
  if (typeof value !== 'object' || value === null) return undefined;
  try {
    return Object.getOwnPropertyDescriptor(value, key)?.value;
  } catch {
    return undefined;
  }
}

function isReportedHint(cause: string): cause is ReportedFailureHint {
  return (
    Object.values(reportedClassHints).some((value) => value === cause) ||
    Object.values(reportedFrameHints).some((value) => value === cause)
  );
}

/** Free an existing reported-only slot for a core fact; never remove a core annotation. */
export function discardReportedFailureHint(annotations: TestResult['annotations']): boolean {
  for (let index = Math.min(annotations.length, 32) - 1; index >= 0; index--) {
    const annotation = ownData(annotations, String(index));
    if (ownData(annotation, 'type') !== safeFailureAnnotation) continue;
    const description = ownData(annotation, 'description');
    const match =
      typeof description === 'string' && description.length <= 96
        ? /^(REPORTED_[A-Z_]+):0$/.exec(description)
        : null;
    if (!match || !isReportedHint(match[1])) continue;
    annotations.splice(index, 1);
    return true;
  }
  return false;
}

/** UNTRUSTED reported framing only: even an exact fabricated match proves no class, source, stage or HTTP fact. */
export function reportedFailureHints(error: unknown): SafeFailureDiagnostic[] {
  const message = ownData(error, 'message');
  if (
    typeof message !== 'string' ||
    message.length > 512 ||
    /[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(message)
  )
    return [];
  const classMatch =
    /^(Error|TypeError|RangeError|ReferenceError|SyntaxError|TimeoutError): .+$/.exec(message);
  if (!classMatch) return [];
  const cause = reportedClassHints[classMatch[1] as keyof typeof reportedClassHints];
  const hints: SafeFailureDiagnostic[] = [{ stage: 'UNKNOWN', cause, httpStatus: 0 }];
  const stack = ownData(error, 'stack');
  if (
    typeof stack !== 'string' ||
    stack.length > 8192 ||
    /[\x00-\x09\x0b-\x1f\x7f-\x9f\u2028\u2029]/.test(stack)
  )
    return hints;
  const lines = stack.split('\n');
  if (
    lines.length > 16 ||
    lines[0] !== message ||
    lines.slice(1).some((line) => line.length > 1024 || !line.startsWith('    at '))
  )
    return hints;
  // Exact repo symbols/path only; the pinned source map is recorded in the checking packet.
  const datasetFile = resolve(__dirname, 'mobile-dataset.ts');
  for (const line of lines.slice(1)) {
    const frame =
      /^    at (?:async )?(Actor\.call|moscowDate|MobileDataset\.initialize) \(([^\r\n]+):([1-9][0-9]{0,5}):([1-9][0-9]{0,5})\)$/.exec(
        line,
      );
    if (!frame || frame[2] !== datasetFile) continue;
    hints.push({
      stage: 'UNKNOWN',
      cause: reportedFrameHints[frame[1] as keyof typeof reportedFrameHints],
      httpStatus: 0,
    });
    break;
  }
  return hints;
}

/** Only bounded own message data is read in RAM; no name/stack/getter/toString or dynamic path is inspected. */
export function classifySafeFailure(error: unknown): SafeFailureDiagnostic {
  const rawMessage = ownData(error, 'message');
  if (typeof rawMessage !== 'string' || rawMessage.length > 512 || /[\r\n]/.test(rawMessage))
    return failureDiagnostic();
  // Pinned Playwright serializeError adds this exact frame; no dynamic error name is read.
  if (rawMessage === 'RangeError: Invalid time value')
    return failureDiagnostic('INVALID_TIME_VALUE');
  const message = rawMessage.startsWith('Error: ')
    ? rawMessage.slice('Error: '.length)
    : rawMessage;
  if (Object.prototype.hasOwnProperty.call(knownFailureMessages, message))
    return failureDiagnostic(knownFailureMessages[message]);
  // Exact closed reported marker only; it cannot authenticate source execution or admit an actor.
  const datasetStatus =
    /^Reported synthetic dataset ([A-Z_]+): HTTP (200|201|202|400|401|403|404|409|410|422|423|429|500|502|503|504)$/.exec(
      message,
    );
  if (
    datasetStatus &&
    datasetStatus[0] === message &&
    Object.prototype.hasOwnProperty.call(datasetHttpOperations, datasetStatus[1])
  )
    return failureDiagnostic(
      datasetStatus[1] as DatasetHttpCause,
      Number(datasetStatus[2]) as FailureHttpStatus,
    );
  const status =
    /^Synthetic actor (login|session|role (?:STUDENT|HEADMAN|TEACHER|ADMIN)): HTTP (400|401|403|404|409|410|422|423|429|500|502|503|504)$/.exec(
      message,
    );
  if (!status) return failureDiagnostic();
  const cause =
    status[1] === 'login'
      ? 'ACTOR_LOGIN_HTTP'
      : status[1] === 'session'
        ? 'ACTOR_SESSION_HTTP'
        : 'ACTOR_ROLE_HTTP';
  return failureDiagnostic(cause, Number(status[2]) as FailureHttpStatus);
}

/** Validate the worker's public annotation channel; arbitrary annotation descriptions never enter artifacts. */
export function safeFailureDiagnostics(
  annotations: unknown,
  hasErrors = false,
): SafeFailureDiagnostic[] {
  const result: SafeFailureDiagnostic[] = [];
  const reportedHints: SafeFailureDiagnostic[] = [];
  if (Array.isArray(annotations)) {
    for (let index = 0; index < Math.min(annotations.length, 32) && result.length < 8; index++) {
      const annotation = ownData(annotations, String(index));
      if (ownData(annotation, 'type') !== safeFailureAnnotation) continue;
      const description = ownData(annotation, 'description');
      if (
        typeof description === 'string' &&
        description.startsWith('SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED:')
      ) {
        if (
          description !== 'SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED:503' &&
          !description.startsWith(subjectDeletePreviewMetadataPrefix)
        )
          continue;
        let metadata: SubjectDeletePreviewMetadata | undefined;
        if (
          description.length <= 320 &&
          description.startsWith(subjectDeletePreviewMetadataPrefix)
        ) {
          try {
            metadata = closedSubjectDeletePreviewMetadata(
              JSON.parse(description.slice(subjectDeletePreviewMetadataPrefix.length)),
            );
            if (
              metadata &&
              description !==
                subjectDeletePreviewMetadataPrefix +
                  JSON.stringify({
                    code: 'SUBJECT_DELETION_UNAVAILABLE',
                    ...metadata,
                  })
            )
              metadata = undefined;
          } catch {
            metadata = undefined;
          }
        }
        // The source emits the original exact HTTP marker before this optional reported tuple.
        if (
          !result.some(
            (item) =>
              item.cause === 'DATASET_SUBJECT_DELETE_PREVIEW_HTTP' && item.httpStatus === 503,
          )
        )
          metadata = undefined;
        if (!result.some((item) => item.stage === 'SUBJECT_DELETE_PREVIEW_PROBLEM'))
          result.push(
            failureDiagnostic(
              metadata
                ? 'SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED'
                : 'SUBJECT_DELETE_PREVIEW_PROBLEM_UNKNOWN',
              503,
              metadata,
            ),
          );
        continue;
      }
      const match =
        typeof description === 'string' && description.length <= 96
          ? /^([A-Z_]+):(0|200|201|202|204|400|401|403|404|409|410|422|423|429|500|502|503|504|OTHER)$/.exec(
              description,
            )
          : null;
      if (match?.[1] === 'ACTOR_LOGIN_GATEWAY_LOCAL' || match?.[1] === 'ACTOR_LOGIN_AUTH_SERVICE') {
        if (description !== `${match[1]}:429`) continue;
        if (
          !result.some(
            (item) =>
              item.cause === 'ACTOR_LOGIN_GATEWAY_LOCAL' ||
              item.cause === 'ACTOR_LOGIN_AUTH_SERVICE',
          )
        )
          result.push(failureDiagnostic(match[1], 429));
        continue;
      }
      const valid = match && Object.prototype.hasOwnProperty.call(failureStages, match[1]);
      const reported = match && isReportedHint(match[1]);
      const diagnostic: SafeFailureDiagnostic = valid
        ? failureDiagnostic(
            match[1] as FailureCause,
            match[2] === 'OTHER' ? 'OTHER' : (Number(match[2]) as FailureHttpStatus | 204),
          )
        : reported && match[2] === '0'
          ? { stage: 'UNKNOWN', cause: match[1] as ReportedFailureHint, httpStatus: 0 }
          : failureDiagnostic();
      if (
        diagnostic.stage === 'SUBJECT_CREATE_PROBLEM' ||
        diagnostic.stage === 'SUBJECT_DELETE_PREVIEW_PROBLEM'
      ) {
        if (match?.[0] !== description || diagnostic.httpStatus !== 503) continue;
        if (!result.some((item) => item.stage === diagnostic.stage)) result.push(diagnostic);
        continue;
      }
      const lifecycleCleanup = Object.prototype.hasOwnProperty.call(
        lifecycleCleanupStages,
        diagnostic.cause,
      );
      const lifecycleTransport = Object.prototype.hasOwnProperty.call(
        lifecycleTransportStages,
        diagnostic.cause,
      );
      if (lifecycleCleanup || lifecycleTransport) {
        if (match?.[0] !== description) continue;
        if (lifecycleTransport && diagnostic.httpStatus !== 0) continue;
        if (
          lifecycleCleanup &&
          !cleanupHttpStatuses.some((status) => status === diagnostic.httpStatus) &&
          diagnostic.httpStatus !== 'OTHER'
        )
          continue;
        // The first failure in each channel wins, including across later cleanup errors.
        if (!result.some((item) => item.stage === diagnostic.stage)) result.push(diagnostic);
        continue;
      }
      const cleanupCause = Object.prototype.hasOwnProperty.call(
        headmanCleanupStages,
        diagnostic.cause,
      );
      if (cleanupCause) {
        if (match?.[0] !== description) continue;
        if (
          !cleanupHttpStatuses.some((status) => status === diagnostic.httpStatus) &&
          diagnostic.httpStatus !== 'OTHER'
        )
          continue;
        // One rejected operation, even if untrusted annotations claim several phases.
        const operation = diagnostic.cause.split('_')[2];
        if (
          !result.some(
            (item) => item.stage === 'HEADMAN_CLEANUP' && item.cause.split('_')[2] === operation,
          )
        )
          result.push(diagnostic);
        continue;
      }
      if (diagnostic.httpStatus === 204 || diagnostic.httpStatus === 'OTHER') {
        if (!result.some((item) => item.cause === 'UNKNOWN')) result.push(failureDiagnostic());
        continue;
      }
      // Only the existing actor and closed dataset HTTP markers may carry a public status.
      const httpCause =
        Object.prototype.hasOwnProperty.call(datasetHttpOperations, diagnostic.cause) ||
        ['ACTOR_LOGIN_HTTP', 'ACTOR_SESSION_HTTP', 'ACTOR_ROLE_HTTP'].includes(diagnostic.cause);
      const value =
        (httpCause && diagnostic.httpStatus === 0) || (!httpCause && diagnostic.httpStatus !== 0)
          ? failureDiagnostic()
          : diagnostic;
      // Retain at most one class and one frame hint across repeated/late errors.
      if (isReportedHint(value.cause)) {
        const classHint = value.cause.startsWith('REPORTED_CLASS_');
        if (
          reportedHints.some(
            (item) =>
              isReportedHint(item.cause) && item.cause.startsWith('REPORTED_CLASS_') === classHint,
          )
        )
          continue;
        reportedHints.push(value);
        continue;
      }
      if (
        !result.some((item) => item.cause === value.cause && item.httpStatus === value.httpStatus)
      )
        result.push(value);
    }
  }
  const diagnostics = [...result, ...reportedHints].slice(0, 8);
  return diagnostics.length || !hasErrors ? diagnostics : [failureDiagnostic()];
}

/** Read only bounded own data: protocol serialization may omit the native code entirely. */
export function closedLifecycleTransportCode(error: unknown): TransportCode {
  let current = error;
  for (let depth = 0; depth < 3; depth++) {
    const code = ownData(current, 'code');
    if (transportCodes.some((value) => value !== 'UNKNOWN' && value === code))
      return code as TransportCode;
    if (code === 'ABORT_ERR') return 'ABORTED';
    const name = ownData(current, 'name');
    if (name === 'TimeoutError') return 'TIMEOUT';
    if (name === 'AbortError') return 'ABORTED';
    if (name === 'TargetClosedError') return 'TARGET_CLOSED';
    current = ownData(current, 'cause');
  }
  return 'UNKNOWN';
}
function retainLifecycleDiagnostic(
  annotations: TestResult['annotations'],
  cause: FailureCause,
  status: SafeFailureDiagnostic['httpStatus'] = 0,
  subjectPreview?: SubjectDeletePreviewMetadata,
): void {
  const previous = safeFailureDiagnostics(annotations);
  if (previous.some((item) => item.stage === failureStages[cause])) return;
  if (previous.filter((item) => !item.cause.startsWith('REPORTED_')).length >= 8) return;
  if (previous.length >= 8 && !discardReportedFailureHint(annotations)) return;
  if (annotations.length >= 32) return;
  const description =
    cause === 'SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED' && status === 503 && subjectPreview
      ? subjectDeletePreviewMetadataPrefix +
        JSON.stringify({
          code: 'SUBJECT_DELETION_UNAVAILABLE',
          ...subjectPreview,
        })
      : `${cause}:${status}`;
  annotations.push({ type: safeFailureAnnotation, description });
}

/** Reconstruct only source-emitted enum tuples; never retain response objects or free text. */
export function closedSubjectDeletePreviewMetadata(
  value: unknown,
): SubjectDeletePreviewMetadata | undefined {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return;
  let keys: PropertyKey[];
  try {
    keys = Reflect.ownKeys(value);
  } catch {
    return;
  }
  if (
    keys.length !== 4 ||
    !['code', 'phase', 'reason', 'rpcStatus'].every((key) => keys.includes(key)) ||
    ownData(value, 'code') !== 'SUBJECT_DELETION_UNAVAILABLE'
  )
    return;
  const phase = ownData(value, 'phase'),
    reason = ownData(value, 'reason'),
    rpcStatus = ownData(value, 'rpcStatus');
  if (!subjectDeletePreviewPhases.some((candidate) => candidate === phase)) return;
  const local = phase === 'LOCAL_TARGET' || phase === 'LOCAL_SNAPSHOT';
  const valid = local
    ? (reason === 'IDENTITY_READ_FENCE' && rpcStatus === 'UNAVAILABLE') ||
      (['LOCAL_STORAGE', 'UNEXPECTED'].some((candidate) => candidate === reason) &&
        rpcStatus === 'NOT_APPLICABLE')
    : ([
        'CREDENTIAL_CONFIGURATION',
        'AUTHORITY_CONFIGURATION',
        'RECEIPT_MISMATCH',
        'UNEXPECTED',
      ].some((candidate) => candidate === reason) &&
        rpcStatus === 'NOT_APPLICABLE') ||
      (reason === 'RPC_FAILURE' &&
        subjectDeletePreviewRpcFailures.some((candidate) => candidate === rpcStatus));
  if (!valid) return;
  return {
    phase: phase as SubjectDeletePreviewMetadata['phase'],
    reason: reason as SubjectDeletePreviewMetadata['reason'],
    rpcStatus: rpcStatus as SubjectDeletePreviewMetadata['rpcStatus'],
  };
}

/** Untrusted response metadata only: no body, identifiers or free text leave this projection. */
export function closedSubjectCreateProblem(
  value: unknown,
): keyof typeof subjectCreateProblemStages {
  if (ownData(value, 'status') !== 503) return 'SUBJECT_CREATE_PROBLEM_UNKNOWN';
  const type = ownData(value, 'type');
  const code = ownData(value, 'code');
  const extraCode = ownData(ownData(value, 'extras'), 'code');
  if (type === 'https://api.rutcampustrack.ru/problems/assignment-adoption-pending')
    return 'SUBJECT_CREATE_PROBLEM_ASSIGNMENT_ADOPTION_PENDING';
  if (
    type === 'https://api.rutcampustrack.ru/problems/identity-state-unavailable' &&
    extraCode === 'IDENTITY_PUBLICATION_PENDING'
  )
    return 'SUBJECT_CREATE_PROBLEM_IDENTITY_PUBLICATION_PENDING';
  if (type === undefined && code === 'DEPENDENCY_UNAVAILABLE')
    return 'SUBJECT_CREATE_PROBLEM_GATEWAY_DEPENDENCY_UNAVAILABLE';
  return typeof type === 'string' || typeof code === 'string' || typeof extraCode === 'string'
    ? 'SUBJECT_CREATE_PROBLEM_OTHER'
    : 'SUBJECT_CREATE_PROBLEM_UNKNOWN';
}

/** First failed POST subject response only; the original HTTP failure remains separate. */
export function retainDatasetSubjectCreateProblem(
  annotations: TestResult['annotations'],
  value: unknown,
): void {
  retainLifecycleDiagnostic(annotations, closedSubjectCreateProblem(value), 503);
}

/** An about:blank body identifies no failing service, phase or exception. */
export function closedSubjectDeletePreviewProblem(
  value: unknown,
): keyof typeof subjectDeletePreviewProblemStages {
  if (ownData(value, 'status') !== 503) return 'SUBJECT_DELETE_PREVIEW_PROBLEM_UNKNOWN';
  const extras = ownData(value, 'extras');
  if (
    ownData(extras, 'code') === 'SUBJECT_DELETION_UNAVAILABLE' ||
    ['phase', 'reason', 'rpcStatus'].some((key) => ownData(extras, key) !== undefined)
  )
    return closedSubjectDeletePreviewMetadata(extras)
      ? 'SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED'
      : 'SUBJECT_DELETE_PREVIEW_PROBLEM_UNKNOWN';
  const common = closedSubjectCreateProblem(value);
  if (common === 'SUBJECT_CREATE_PROBLEM_IDENTITY_PUBLICATION_PENDING')
    return 'SUBJECT_DELETE_PREVIEW_PROBLEM_IDENTITY_PUBLICATION_PENDING';
  if (common === 'SUBJECT_CREATE_PROBLEM_GATEWAY_DEPENDENCY_UNAVAILABLE')
    return 'SUBJECT_DELETE_PREVIEW_PROBLEM_GATEWAY_DEPENDENCY_UNAVAILABLE';
  if (ownData(value, 'type') === 'about:blank') return 'SUBJECT_DELETE_PREVIEW_PROBLEM_ABOUT_BLANK';
  return common === 'SUBJECT_CREATE_PROBLEM_UNKNOWN'
    ? 'SUBJECT_DELETE_PREVIEW_PROBLEM_UNKNOWN'
    : 'SUBJECT_DELETE_PREVIEW_PROBLEM_OTHER';
}

/** First rejected exact preview GET only; preserve the separate original HTTP failure. */
export function retainDatasetSubjectDeletePreviewProblem(
  annotations: TestResult['annotations'],
  value: unknown,
): void {
  const cause = closedSubjectDeletePreviewProblem(value);
  retainLifecycleDiagnostic(
    annotations,
    cause,
    503,
    cause === 'SUBJECT_DELETE_PREVIEW_PROBLEM_TYPED'
      ? closedSubjectDeletePreviewMetadata(ownData(value, 'extras'))
      : undefined,
  );
}
/** Retain the original preview transport, before the caller rethrows the identical error. */
export function retainSemesterPreviewTransport(
  annotations: TestResult['annotations'],
  error: unknown,
): void {
  retainNativePrimaryFailure(annotations, 'SEMESTER_PREVIEW', 'HTTP_GET', error);
  retainLifecycleDiagnostic(annotations, `SEMESTER_PREVIEW_${closedLifecycleTransportCode(error)}`);
}
/** First rejected cleanup substage/status, separate from the primary request failure. */
export function retainLifecycleCleanupFailure(
  annotations: TestResult['annotations'],
  cause: LifecycleCleanupCause,
  error: unknown,
  observedStatus?: unknown,
): void {
  if (!Object.prototype.hasOwnProperty.call(lifecycleCleanupStages, cause)) return;
  if (safeFailureDiagnostics(annotations).some((item) => item.stage === 'LIFECYCLE_CLEANUP'))
    return;
  const status =
    observedStatus === undefined ? classifySafeFailure(error).httpStatus : observedStatus;
  const closedStatus = cleanupHttpStatuses.some((value) => value === status)
    ? (status as CleanupHttpStatus)
    : 'OTHER';
  retainLifecycleDiagnostic(annotations, cause, closedStatus);
  retainLifecycleDiagnostic(
    annotations,
    `CLEANUP_TRANSPORT_${closedLifecycleTransportCode(error)}`,
  );
}

/** Preserve the first propagated body HTTP marker before a later cleanup error can replace it. */
export function retainFirstDatasetHttpFailure(
  annotations: TestResult['annotations'],
  error: unknown,
): void {
  const diagnostic = classifySafeFailure(error);
  if (diagnostic.stage !== 'DATASET_HTTP' || annotations.length > 32) return;
  const previous = safeFailureDiagnostics(annotations);
  if (previous.some((item) => item.stage === 'DATASET_HTTP')) return;
  const coreCount = previous.filter((item) => !item.cause.startsWith('REPORTED_')).length;
  if (coreCount >= 8) return;
  if (previous.length >= 8 && !discardReportedFailureHint(annotations)) return;
  if (annotations.length >= 32) return;
  annotations.push({
    type: safeFailureAnnotation,
    description: `${diagnostic.cause}:${diagnostic.httpStatus}`,
  });
}

/** A rejected fixture cleanup observation, not an authenticated backend failure cause. */
export function retainHeadmanCleanupFailure(
  annotations: TestResult['annotations'],
  cause: HeadmanCleanupCause,
  httpStatus: unknown = 0,
): void {
  if (!Object.prototype.hasOwnProperty.call(headmanCleanupStages, cause)) return;
  const previous = safeFailureDiagnostics(annotations);
  const operation = cause.split('_')[2];
  if (
    previous.some(
      (item) => item.stage === 'HEADMAN_CLEANUP' && item.cause.split('_')[2] === operation,
    )
  )
    return;
  if (previous.filter((item) => !item.cause.startsWith('REPORTED_')).length >= 8) return;
  if (previous.length >= 8 && !discardReportedFailureHint(annotations)) return;
  if (annotations.length >= 32) return;
  const status = cleanupHttpStatuses.some((value) => value === httpStatus) ? httpStatus : 'OTHER';
  annotations.push({ type: safeFailureAnnotation, description: `${cause}:${status}` });
}

export const d002ReconnectObservationAnnotation = 'rct-d002-reconnect-v1';
const d002ReconnectTitle =
  'D002 reconnect rechecks changed grants and a late old feed cannot replace the new account';
type D002NativeState = {
  phase: 'NOT_CALLED' | 'PENDING' | 'FULFILLED' | 'REJECTED';
  multiple: boolean;
};
type D002Endpoints<T> = { refresh: T; current: T };
export interface D002ReconnectObservation {
  offlineSeen: boolean;
  onlineSeen: boolean;
  online: boolean;
  observationUnknown: boolean;
  native: {
    preOnline: D002Endpoints<D002NativeState>;
    postOnline: D002Endpoints<D002NativeState>;
  };
  pageHttp: D002Endpoints<{ requested: boolean; responded: boolean; failed: boolean }>;
  priorPending: D002Endpoints<boolean>;
  refreshHttpStatus: 'UNOBSERVED' | 'OTHER' | 200 | 401 | 403 | 429 | 503;
}

/** Project own data only; no caller object, unknown key, payload or getter is serialized. */
function closedD002ReconnectObservation(value: unknown): D002ReconnectObservation | undefined {
  const exact = (record: unknown, keys: readonly string[]) => {
    if (!record || typeof record !== 'object' || Array.isArray(record)) return false;
    try {
      const actual = Object.getOwnPropertyNames(record);
      return actual.length === keys.length && keys.every((key) => actual.includes(key));
    } catch {
      return false;
    }
  };
  const flags = (record: unknown, keys: readonly string[]) =>
    exact(record, keys) && keys.every((key) => typeof ownData(record, key) === 'boolean');
  const endpoints = ['refresh', 'current'] as const;
  const epochs = ['preOnline', 'postOnline'] as const;
  const topFlags = ['offlineSeen', 'onlineSeen', 'online', 'observationUnknown'] as const;
  if (!exact(value, [...topFlags, 'native', 'pageHttp', 'priorPending', 'refreshHttpStatus']))
    return;
  if (topFlags.some((key) => typeof ownData(value, key) !== 'boolean')) return;
  const native = ownData(value, 'native');
  const pageHttp = ownData(value, 'pageHttp');
  const priorPending = ownData(value, 'priorPending');
  const refreshHttpStatus = ownData(value, 'refreshHttpStatus');
  if (!exact(native, epochs) || !exact(pageHttp, endpoints) || !flags(priorPending, endpoints))
    return;
  for (const epoch of epochs) {
    const states = ownData(native, epoch);
    if (!exact(states, endpoints)) return;
    for (const endpoint of endpoints) {
      const state = ownData(states, endpoint);
      if (
        !exact(state, ['phase', 'multiple']) ||
        !['NOT_CALLED', 'PENDING', 'FULFILLED', 'REJECTED'].includes(
          ownData(state, 'phase') as string,
        ) ||
        typeof ownData(state, 'multiple') !== 'boolean'
      )
        return;
    }
  }
  for (const endpoint of endpoints)
    if (!flags(ownData(pageHttp, endpoint), ['requested', 'responded', 'failed'])) return;
  if (!['UNOBSERVED', 'OTHER', 200, 401, 403, 429, 503].includes(refreshHttpStatus as never))
    return;
  const state = (epoch: (typeof epochs)[number], endpoint: (typeof endpoints)[number]) => {
    const item = ownData(ownData(native, epoch), endpoint);
    return {
      phase: ownData(item, 'phase') as D002NativeState['phase'],
      multiple: ownData(item, 'multiple') as boolean,
    };
  };
  const http = (endpoint: (typeof endpoints)[number]) => {
    const item = ownData(pageHttp, endpoint);
    return {
      requested: ownData(item, 'requested') as boolean,
      responded: ownData(item, 'responded') as boolean,
      failed: ownData(item, 'failed') as boolean,
    };
  };
  return {
    offlineSeen: ownData(value, 'offlineSeen') as boolean,
    onlineSeen: ownData(value, 'onlineSeen') as boolean,
    online: ownData(value, 'online') as boolean,
    observationUnknown: ownData(value, 'observationUnknown') as boolean,
    native: {
      preOnline: { refresh: state('preOnline', 'refresh'), current: state('preOnline', 'current') },
      postOnline: {
        refresh: state('postOnline', 'refresh'),
        current: state('postOnline', 'current'),
      },
    },
    pageHttp: { refresh: http('refresh'), current: http('current') },
    priorPending: {
      refresh: ownData(priorPending, 'refresh') as boolean,
      current: ownData(priorPending, 'current') as boolean,
    },
    refreshHttpStatus: refreshHttpStatus as D002ReconnectObservation['refreshHttpStatus'],
  };
}

/** A bounded observation, not proof of endpoint ownership, HTTP status or product cause. */
export function safeD002ReconnectObservation(
  annotations: unknown,
): D002ReconnectObservation | undefined {
  if (!Array.isArray(annotations)) return;
  for (let index = 0; index < Math.min(annotations.length, 32); index++) {
    const annotation = ownData(annotations, String(index));
    if (ownData(annotation, 'type') !== d002ReconnectObservationAnnotation) continue;
    const description = ownData(annotation, 'description');
    if (typeof description !== 'string' || description.length > 2048) continue;
    try {
      const closed = closedD002ReconnectObservation(JSON.parse(description));
      if (closed) return closed;
    } catch {
      // Malformed untrusted annotations cannot enter the safe result.
    }
  }
}

export function retainD002ReconnectObservation(
  annotations: TestResult['annotations'],
  value: unknown,
): void {
  if (annotations.length >= 32 || safeD002ReconnectObservation(annotations)) return;
  const closed = closedD002ReconnectObservation(value);
  if (!closed) return;
  const description = JSON.stringify(closed);
  if (description.length > 2048) return;
  annotations.push({ type: d002ReconnectObservationAnnotation, description });
}

interface SafeResult {
  file: string;
  line: number;
  project: string;
  status: TestResult['status'];
  expectedStatus: TestCase['expectedStatus'];
  durationMs: number;
  errorCount: number;
  diagnostics: SafeFailureDiagnostic[];
  errorLocations: { file: string; line: number }[];
  transferObservation?: TransferObservation;
  ownedContainerIdentityFailure?: OwnedContainerIdentityFailure;
  nativePrimaryFailures?: NativePrimaryFailure[];
  d002ReconnectObservation?: D002ReconnectObservation;
}

/** Allowlist output only. Raw step titles, errors, attachments and worker output are never serialized. */
export default class SafeReporter implements Reporter {
  private outputDir = '';
  private results: SafeResult[] = [];
  private runnerErrors = 0;
  private suppressedStdoutBytes = 0;
  private suppressedStderrBytes = 0;
  private unsafeArtifact = false;

  printsToStdio() {
    return true;
  }
  onBegin(config: FullConfig) {
    this.outputDir = config.projects[0].outputDir;
    mkdirSync(this.outputDir, { recursive: true });
  }
  onStdOut(chunk: string | Buffer) {
    this.suppressedStdoutBytes += Buffer.byteLength(chunk);
  }
  onStdErr(chunk: string | Buffer) {
    this.suppressedStderrBytes += Buffer.byteLength(chunk);
  }
  onError(_error: TestError) {
    this.runnerErrors++;
  }
  onTestEnd(test: TestCase, result: TestResult) {
    const row: SafeResult = {
      file: basename(test.location.file),
      line: test.location.line,
      project: test.parent.project()?.name ?? 'unknown',
      status: result.status,
      expectedStatus: test.expectedStatus,
      durationMs: result.duration,
      errorCount: result.errors.length,
      diagnostics: safeFailureDiagnostics(result.annotations, result.errors.length > 0),
      errorLocations: result.errors.flatMap((error) =>
        error.location ? [{ file: basename(error.location.file), line: error.location.line }] : [],
      ),
    };
    const transferObservation = safeTransferObservation(result.annotations);
    if (transferObservation) row.transferObservation = transferObservation;
    const identityFailure = safeOwnedContainerIdentityFailure(result.annotations);
    if (identityFailure) row.ownedContainerIdentityFailure = identityFailure;
    const nativePrimaryFailures = safeNativePrimaryFailures(result.annotations);
    if (nativePrimaryFailures.length) row.nativePrimaryFailures = nativePrimaryFailures;
    if (
      test.location.file === resolve(__dirname, '../specs/mobile-real-server.spec.ts') &&
      test.title === d002ReconnectTitle &&
      test.expectedStatus === 'passed' &&
      result.status === 'failed' &&
      result.errors.length > 0
    ) {
      const reconnect = safeD002ReconnectObservation(result.annotations);
      if (reconnect) row.d002ReconnectObservation = reconnect;
    }
    this.results.push(row);
    // Automatic error contexts are created during worker teardown, after afterEach.
    // Replace attached artifact contents with safe metadata as a second boundary.
    for (const attachment of result.attachments) {
      if (!attachment.path) continue;
      const path = resolve(attachment.path);
      const child = relative(this.outputDir, path);
      if (
        isAbsolute(child) ||
        child === '..' ||
        child.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`)
      ) {
        this.unsafeArtifact = true;
        continue;
      }
      writeFileSync(path, JSON.stringify(row, null, 2));
    }
    process.stdout.write(`${row.project} ${row.file}:${row.line} ${row.status}\n`);
  }
  async onEnd(result: FullResult) {
    if (!this.outputDir) return { status: 'failed' as const };
    const status = this.unsafeArtifact ? 'failed' : result.status;
    writeFileSync(
      resolve(this.outputDir, 'safe-results.json'),
      JSON.stringify(
        {
          status,
          runnerErrors: this.runnerErrors,
          unsafeArtifact: this.unsafeArtifact,
          suppressedStdoutBytes: this.suppressedStdoutBytes,
          suppressedStderrBytes: this.suppressedStderrBytes,
          results: this.results,
        },
        null,
        2,
      ),
    );
    process.stdout.write(
      `real-mobile ${status}; ${this.results.length} results; ${this.runnerErrors} runner errors\n`,
    );
    return { status };
  }
}
