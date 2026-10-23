import { constants, closeSync, fsyncSync, lstatSync, openSync, realpathSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

export type RecoveryHealthRead = {
  channelState: 'READY' | 'IDLE' | 'CONNECTING' | 'TRANSIENT_FAILURE' | 'SHUTDOWN' | 'UNKNOWN';
  fresh: boolean | null;
};
export const HEALTH_REASONS = [
  'ROWS_TYPE', 'ROWS_EMPTY', 'ROWS_LIMIT', 'ROW_NULL', 'ROW_TYPE', 'ROW_ARRAY',
  'ROW_PROTOTYPE', 'ROW_KEYS', 'EXIT_TYPE', 'EXIT_NOT_ZERO', 'START_TYPE', 'END_TYPE',
  'OUTPUT_TYPE', 'OUTPUT_SIZE', 'SINCE_NONFINITE', 'OBSERVED_NONFINITE',
  'START_NON_Z', 'START_FORMAT', 'END_NON_Z', 'END_FORMAT', 'START_DATE', 'END_DATE',
  'START_AFTER_END', 'END_AFTER_OBSERVED', 'START_NONCANONICAL', 'END_NONCANONICAL',
  'SUBMILLI_ORDER', 'BODY_JSON', 'BODY_STATUS', 'GRPC_STATUS', 'DETAILS_NULL',
  'DETAILS_TYPE', 'DETAILS_ARRAY', 'SCHEDULE_MISSING', 'STATE_NOT_ALLOWED',
  'STALE_SNAPSHOT', 'OBSERVED_READY', 'OBSERVED_IDLE', 'OBSERVED_CONNECTING',
  'OBSERVED_TRANSIENT_FAILURE', 'OBSERVED_SHUTDOWN', 'UNEXPECTED', 'RESULT_MISMATCH',
] as const;
type Reason = (typeof HEALTH_REASONS)[number];
type Side = 'ATTENDANCE' | 'ACADEMIC';
type Phase = 'MAIN' | 'CLEANUP';
type Observation = RecoveryHealthRead & { reason: Reason };
type Summary = {
  observations: number;
  truncated: boolean;
  firstUnknown: Observation | null;
  last: Observation | null;
  counts: Partial<Record<Reason, number>>;
};
const states = ['READY', 'IDLE', 'CONNECTING', 'TRANSIENT_FAILURE', 'SHUTDOWN'] as const;
const unknown = (reason: Reason): Observation => ({ reason, channelState: 'UNKNOWN', fresh: null });

/** Normalize only strict numeric RFC3339 offsets; the existing UTC parser still validates Z input. */
export function normalizeDockerHealthTimestamp(value: string): string | null {
  if (value.endsWith('Z')) return value;
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(\.\d{1,9})?([+-])(\d{2}):(\d{2})$/.exec(value);
  if (!match || match[0] !== value) return null;
  const [, local, fraction = '', sign, hours, minutes] = match;
  if (Number(hours) > 23 || Number(minutes) > 59) return null;
  const localMs = Date.parse(`${local}Z`);
  // Date.parse alone can normalize an impossible calendar date or 24:00 into another day.
  if (!Number.isFinite(localMs) || new Date(localMs).toISOString().slice(0, 19) !== local) return null;
  const offsetMs = (Number(hours) * 60 + Number(minutes)) * 60000 * (sign === '+' ? 1 : -1);
  const utc = new Date(localMs - offsetMs).toISOString();
  // Keep the original four-digit-year domain and preserve all fractional digits verbatim.
  if (!/^\d{4}-/.test(utc)) return null;
  return `${utc.slice(0, 19)}${fraction}Z`;
}


export const GATEWAY_ADMISSION_CHECKS = [
  'EXPECT_USER', 'EXPECT_SESSION', 'EXPECT_SESSION_VERSION', 'EXPECT_ROLES_VERSION', 'EXPECT_ACCESS_EXPIRY',
  'RESPONSE_STRUCTURE', 'RESPONSE_USER', 'RESPONSE_SESSION', 'RESPONSE_SESSION_VERSION', 'RESPONSE_ROLES_VERSION',
  'RESPONSE_ROLE', 'RESPONSE_STATUS', 'RESPONSE_GROUP', 'RESPONSE_HEADMAN_FLAG', 'RESPONSE_HEADMAN_GROUP',
  'RESPONSE_READONLY_FLAG', 'ACCESS_USER', 'ACCESS_SESSION', 'ACCESS_SESSION_VERSION', 'ACCESS_ROLES_VERSION',
  'REPORT_RESPONSE_STRUCTURE', 'REPORT_TICKET_EXPIRED', 'REPORT_TICKET_INTERVAL', 'JWT_KEY', 'JWT_PARSE',
  'JWT_ALGORITHM', 'JWT_PAYLOAD', 'JWT_ISSUER', 'JWT_AUDIENCE', 'JWT_USE',
  'JWT_IAT_WIRE', 'JWT_EXP_WIRE', 'JWT_TIME_INTERVAL', 'JWT_IAT_FUTURE', 'JWT_EXPIRED',
  'JWT_USER', 'JWT_SESSION', 'JWT_SESSION_VERSION', 'JWT_ROLES_VERSION', 'JWT_ROLE',
  'JWT_STATUS', 'JWT_GROUP', 'JWT_HEADMAN_FLAG', 'JWT_READONLY_FLAG', 'JWT_REPORT_BINDING',
  'JWT_REPORT_EXPIRY', 'JWT_RESPONSE_EXPIRY', 'JWT_ACCESS_EXPIRY', 'FORWARD_REQUEST',
] as const;
export const GATEWAY_ADMISSION_PARSERS = [
  'NONE', 'EXPIRED', 'NOT_YET_VALID', 'SIGNATURE', 'SECURITY',
  'UNSUPPORTED', 'MALFORMED', 'OTHER_JWT',
] as const;

/** Strip only the current closed Gateway suffix; the original source/stage/status/correlation gate follows. */
export function withoutGatewayAdmissionDiagnostic(message: string, source: string): string | null {
  const split = message.indexOf(' check=');
  if (split < 0) return message;
  if (source !== 'GATEWAY_ISSUER_FILTER' || message.length > 2048) return null;
  const suffix = /^ check=([A-Z_]+) parser=([A-Z_]+) startWallMs=([^ ]+) validationNowMs=([^ ]+) issuedAtMs=([^ ]+) expiresAtMs=([^ ]+) elapsedMs=([^ ]+) wallElapsedMs=([^ ]+) correlation=UNCONFIRMED$/.exec(message.slice(split));
  if (!suffix || suffix[0] !== message.slice(split)
      || !(GATEWAY_ADMISSION_CHECKS as readonly string[]).includes(suffix[1])
      || !(GATEWAY_ADMISSION_PARSERS as readonly string[]).includes(suffix[2])) return null;
  for (let index = 3; index <= 8; index++) {
    const value = suffix[index];
    if (index >= 4 && index <= 6 && value === 'null') continue;
    if (!/^(?:0|[1-9]\d{0,18}|-[1-9]\d{0,18})$/.test(value)) return null;
    const integer = BigInt(value);
    if (integer < -9223372036854775808n || integer > 9223372036854775807n || index === 7 && integer < 0n) return null;
  }
  return message.slice(0, split);
}

/** Explain the existing parser; this function neither replaces nor drives the readiness gate. */
function classify(rows: unknown, since: number, observedAt: number): Observation {
  if (!Array.isArray(rows)) return unknown('ROWS_TYPE');
  if (rows.length === 0) return unknown('ROWS_EMPTY');
  if (rows.length > 5) return unknown('ROWS_LIMIT');
  const row = rows.at(-1);
  if (row === null) return unknown('ROW_NULL');
  if (typeof row !== 'object') return unknown('ROW_TYPE');
  if (Array.isArray(row)) return unknown('ROW_ARRAY');
  if (Object.getPrototypeOf(row) !== Object.prototype) return unknown('ROW_PROTOTYPE');
  if (Object.keys(row).sort().join(',') !== 'End,ExitCode,Output,Start') return unknown('ROW_KEYS');
  if (typeof row.ExitCode !== 'number') return unknown('EXIT_TYPE');
  if (row.ExitCode !== 0) return unknown('EXIT_NOT_ZERO');
  if (typeof row.Start !== 'string') return unknown('START_TYPE');
  if (typeof row.End !== 'string') return unknown('END_TYPE');
  if (typeof row.Output !== 'string') return unknown('OUTPUT_TYPE');
  if (Buffer.byteLength(row.Output, 'utf8') > 4096) return unknown('OUTPUT_SIZE');
  if (!Number.isFinite(since)) return unknown('SINCE_NONFINITE');
  if (!Number.isFinite(observedAt)) return unknown('OBSERVED_NONFINITE');
  const startWire = normalizeDockerHealthTimestamp(row.Start), endWire = normalizeDockerHealthTimestamp(row.End);
  const utc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;
  if (startWire === null || !utc.test(startWire) || startWire.trim() !== startWire) {
    return unknown(/[+-]\d{2}:\d{2}$/.test(row.Start) ? 'START_NON_Z' : 'START_FORMAT');
  }
  if (endWire === null || !utc.test(endWire) || endWire.trim() !== endWire) {
    return unknown(/[+-]\d{2}:\d{2}$/.test(row.End) ? 'END_NON_Z' : 'END_FORMAT');
  }
  const start = Date.parse(startWire), end = Date.parse(endWire);
  if (!Number.isFinite(start)) return unknown('START_DATE');
  if (!Number.isFinite(end)) return unknown('END_DATE');
  if (start > end) return unknown('START_AFTER_END');
  if (end > observedAt) return unknown('END_AFTER_OBSERVED');
  if (new Date(start).toISOString().slice(0, 19) !== startWire.slice(0, 19)) return unknown('START_NONCANONICAL');
  if (new Date(end).toISOString().slice(0, 19) !== endWire.slice(0, 19)) return unknown('END_NONCANONICAL');
  const nanos = (value: string) => value.replace(/(?:\.(\d{1,9}))?Z$/, (_, fraction = '') => `.${fraction.padEnd(9, '0')}Z`);
  if (nanos(startWire) > nanos(endWire)) return unknown('SUBMILLI_ORDER');
  let body;
  try { body = JSON.parse(row.Output); } catch { return unknown('BODY_JSON'); }
  if (body?.status !== 'UP') return unknown('BODY_STATUS');
  if (body?.components?.grpcChannel?.status !== 'UP') return unknown('GRPC_STATUS');
  const details = body.components.grpcChannel.details;
  if (details === null) return unknown('DETAILS_NULL');
  if (typeof details !== 'object') return unknown('DETAILS_TYPE');
  if (Array.isArray(details)) return unknown('DETAILS_ARRAY');
  if (!Object.hasOwn(details, 'schedule-service')) return unknown('SCHEDULE_MISSING');
  const state = details['schedule-service'];
  if (!states.includes(state)) return unknown('STATE_NOT_ALLOWED');
  return { reason: start < since ? 'STALE_SNAPSHOT' : `OBSERVED_${state}` as Reason,
    channelState: state, fresh: start >= since };
}

export function createHealthDiagnostic(checkpointRun?: string) {
  const empty = (): Summary => ({ observations: 0, truncated: false, firstUnknown: null, last: null, counts: {} });
  const data = { schema: 'semester-health-observation.v1', MAIN: { ATTENDANCE: empty(), ACADEMIC: empty() },
    CLEANUP: { ATTENDANCE: empty(), ACADEMIC: empty() } };
  let checkpointOwned = false, checkpoints = 0, signature = '';
  const checkpoint = (run: string, force = false, firstUnknown = false) => {
    const next = JSON.stringify([data.MAIN.ATTENDANCE.firstUnknown, data.MAIN.ATTENDANCE.last,
      data.MAIN.ACADEMIC.firstUnknown, data.MAIN.ACADEMIC.last,
      data.CLEANUP.ATTENDANCE.firstUnknown, data.CLEANUP.ATTENDANCE.last,
      data.CLEANUP.ACADEMIC.firstUnknown, data.CLEANUP.ACADEMIC.last]);
    if (!force && (next === signature || checkpoints >= 64 && !firstUnknown)) return;
    checkpoints++; signature = next;
    const directory = lstatSync(run), uid = process.getuid?.();
    if (!directory.isDirectory() || directory.isSymbolicLink() || resolve(run) !== run || realpathSync(run) !== run
      || uid !== undefined && (directory.uid !== uid || (directory.mode & 0o777) !== 0o700)) throw Error('Health checkpoint directory');
    const path = join(run, 'health-observation.safe.json');
    try {
      const info = lstatSync(path);
      if (!checkpointOwned || !info.isFile() || info.isSymbolicLink()
        || uid !== undefined && (info.uid !== uid || (info.mode & 0o777) !== 0o600)) throw Error('Health checkpoint ownership');
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
    }
    const bytes = `${JSON.stringify(data)}\n`;
    if (Buffer.byteLength(bytes, 'utf8') > 8192) throw Error('Health checkpoint byte bound');
    const pending = join(run, 'health-observation.safe.json.pending');
    let descriptor: number | undefined, created = false;
    try {
      descriptor = openSync(pending, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | (constants.O_NOFOLLOW ?? 0), 0o600);
      created = true;
      writeFileSync(descriptor, bytes); fsyncSync(descriptor); closeSync(descriptor); descriptor = undefined;
      renameSync(pending, path);
      checkpointOwned = true;
    } finally {
      if (descriptor !== undefined) closeSync(descriptor);
      if (created) { try { unlinkSync(pending); } catch { /* The atomic rename already removed it. */ } }
    }
  };
  return {
    observe(side: Side, phase: Phase, rows: unknown, since: number, observedAt: number,
      parser: (rows: unknown, since: number, observedAt: number) => RecoveryHealthRead): RecoveryHealthRead {
      // Invoke the original parser once, with the same sampled clocks, and return its exact result.
      const result = parser(rows, since, observedAt);
      try {
        let observation: Observation;
        try { observation = classify(rows, since, observedAt); } catch { observation = unknown('UNEXPECTED'); }
        if (observation.channelState !== result.channelState || observation.fresh !== result.fresh) {
          observation = unknown('RESULT_MISMATCH');
        }
        const summary = data[phase][side];
        summary.last = observation;
        const firstUnknown = observation.channelState === 'UNKNOWN' && summary.firstUnknown === null;
        if (firstUnknown) summary.firstUnknown = observation;
        if (summary.observations < 4096) {
          summary.observations++;
          summary.counts[observation.reason] = (summary.counts[observation.reason] ?? 0) + 1;
        } else summary.truncated = true;
        if (checkpointRun !== undefined) checkpoint(checkpointRun, false, firstUnknown);
      } catch {
        // A diagnostic failure must never replace the original parser or test failure.
      }
      return result;
    },
    snapshot() { return structuredClone(data); },
    write(run: string) {
      checkpoint(run, true);
    },
  };
}

const observations = new WeakMap<object, ReturnType<typeof createHealthDiagnostic>>();
function owned(owner: object, run: string) {
  let value = observations.get(owner);
  if (!value) { value = createHealthDiagnostic(run); observations.set(owner, value); }
  return value;
}
export function observeHealth(owner: object, side: Side, rows: unknown, since: number, observedAt: number,
  parser: (rows: unknown, since: number, observedAt: number) => RecoveryHealthRead, run: string): RecoveryHealthRead {
  return owned(owner, run).observe(side, 'MAIN', rows, since, observedAt, parser);
}
export function writeHealthDiagnostic(owner: object, run: string, originalReceipt: string,
  options: { mode: number; flag: 'wx' }) {
  // Keep the original v11 receipt and its write semantics; the extra file has its own strict schema.
  try {
    writeFileSync(join(run, 'reconciliation.safe.json'), originalReceipt, options);
  } finally {
    try { owned(owner, run).write(run); } catch { /* Preserve the original receipt/test outcome. */ }
  }
}
