/** Final read-only retained-copy observation. No queue mutation or payload is exposed. */
type CountClass = 'ZERO' | 'ONE' | 'MANY' | 'UNKNOWN';
type Counts = { messages_ready: number; messages_unacknowledged: number };
type Reason = 'NOT_RUN' | 'MATCH' | 'DEADLINE' | 'TRANSPORT' | 'HTTP' | 'BODY' | 'JSON' | 'SHAPE' | 'BOUND';
export type DlqWitness = {
  schema: 'RCT_RECONCILIATION_DLQ_V1'; stage: 'CLEANUP_SEMESTER_DLQ';
  first: { ready: CountClass; unacked: CountClass }; last: { ready: CountClass; unacked: CountClass };
  reason: Reason; httpStatus: number | null; boundMs: number; elapsedMs: number;
};
export function createDlqWitness(): DlqWitness {
  return { schema: 'RCT_RECONCILIATION_DLQ_V1', stage: 'CLEANUP_SEMESTER_DLQ',
    first: { ready: 'UNKNOWN', unacked: 'UNKNOWN' }, last: { ready: 'UNKNOWN', unacked: 'UNKNOWN' },
    reason: 'NOT_RUN', httpStatus: null, boundMs: 0, elapsedMs: 0 };
}
class ObservationFailure extends Error {
  constructor(readonly reason: Reason, readonly httpStatus: number | null = null) { super('Retained DLQ observation failed'); }
}
const fail = (reason: Reason, httpStatus: number | null = null): never => { throw new ObservationFailure(reason, httpStatus); };
const now = () => performance.now();
const countClass = (value: number): CountClass => value === 0 ? 'ZERO' : value === 1 ? 'ONE' : 'MANY';
function counts(value: unknown): Counts {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return fail('SHAPE');
  const row = value as Record<string, unknown>;
  for (const key of ['messages_ready', 'messages_unacknowledged']) {
    if (typeof row[key] !== 'number' || !Number.isSafeInteger(row[key]) || (row[key] as number) < 0) return fail('SHAPE');
  }
  return { messages_ready: row.messages_ready as number, messages_unacknowledged: row.messages_unacknowledged as number };
}
async function within<T>(operation: () => Promise<T>, deadline: number, clock: () => number, expired?: () => void): Promise<T> {
  const left = Math.floor(deadline - clock());
  if (left <= 0) return fail('DEADLINE');
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    const result = await Promise.race([operation(), new Promise<never>((_, reject) => {
      timer = setTimeout(() => { expired?.(); reject(new ObservationFailure('DEADLINE')); }, left);
    })]);
    if (clock() >= deadline) return fail('DEADLINE');
    return result;
  } finally { if (timer !== undefined) clearTimeout(timer); }
}
export async function readDlqCounters(fetcher: typeof fetch, url: string, headers: Record<string, string>,
  deadline: number, clock: () => number = now): Promise<Counts> {
  // One request deadline is shared by headers and every body chunk; never reset per read.
  const readEnd = Math.min(deadline, clock() + 4000);
  const controller = new AbortController();
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
  try {
    let response: globalThis.Response;
    try {
      response = await within(() => fetcher(url, { method: 'GET', redirect: 'error', headers,
        signal: controller.signal }), readEnd, clock, () => controller.abort());
    } catch (error) { if (error instanceof ObservationFailure) throw error; return fail('TRANSPORT'); }
    if (!response.ok) return fail('HTTP', Number.isInteger(response.status) && response.status >= 100 && response.status <= 599 ? response.status : null);
    if (!response.body) return fail('BODY');
    reader = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let length = 0;
    for (;;) {
      let part: ReadableStreamReadResult<Uint8Array>;
      try { part = await within(() => reader!.read(), readEnd, clock, () => controller.abort()); }
      catch (error) { if (error instanceof ObservationFailure) throw error; return fail('BODY'); }
      if (part.done) break;
      length += part.value.length;
      if (length > 262144) return fail('BOUND');
      chunks.push(part.value);
    }
    let value: unknown;
    try { value = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return fail('JSON'); }
    return counts(value);
  } finally {
    controller.abort();
    // Cancellation is read-only and must not hold the last5s disposal reserve hostage.
    if (reader) void reader.cancel().catch(() => {});
  }
}
export async function observeRetainedDlq(read: (deadline: number) => Promise<unknown>, deadline: number,
  witness: DlqWitness, clock: () => number = now,
  wait: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms))): Promise<void> {
  const started = clock();
  const end = Math.min(deadline, started + 10000);
  witness.boundMs = Math.max(0, Math.min(10000, Math.floor(end - started)));
  let first = true;
  try {
    for (;;) {
      const row = counts(await within(() => read(end), end, clock));
      witness.last = { ready: countClass(row.messages_ready), unacked: countClass(row.messages_unacknowledged) };
      if (first) { witness.first = { ...witness.last }; first = false; }
      if (row.messages_ready === 1 && row.messages_unacknowledged === 0) { witness.reason = 'MATCH'; return; }
      // Only a valid count mismatch is retried. Read/HTTP/shape failures escape immediately.
      const remaining = Math.floor(end - clock());
      if (remaining <= 1) return fail('DEADLINE');
      await within(() => wait(Math.min(250, remaining - 1)), end, clock);
    }
  } catch (error) {
    witness.reason = error instanceof ObservationFailure ? error.reason : 'TRANSPORT';
    witness.httpStatus = error instanceof ObservationFailure ? error.httpStatus : null;
    throw new Error('Original DLQ copy must remain retained');
  } finally { witness.elapsedMs = Math.max(0, Math.min(10000, Math.trunc(clock() - started))); }
}
