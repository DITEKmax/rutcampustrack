// D9-owned reservations only. No timers, native calls, HTTP, retries or data access.
export type Resource = 'CLOSE' | 'PRODUCER' | 'CAPTURE' | 'ATTENDANCE_RESTART' | 'SCHEDULE_RESTART' |
  'EFFECT' | 'ARCHIVE' | 'RESTORE' | 'AUTHORITY' | 'FINAL';
export type Reservations = Partial<Record<Resource, number>>;
export type RecoverStage = 'NOT_RUN' | 'CLOSE_GET' | 'PRODUCER' | 'EFFECT' | 'CONSUMER' | 'COMPLETE';
export function createPhaseBudget(end: number, reservations: (next?: {stop?: string; event?: boolean; archive?: boolean}) => Reservations,
  cleaning: () => boolean, now: () => number = () => performance.now()) {
  const leases = new Map<Resource, number>();
  let active: Resource | undefined, observing = false;
  const witness = {
    schema: 'RCT_RECONCILIATION_BUDGET_V1',
    main: null as null | {resource: Resource | 'NONE'; requestedMs: number; availableMs: number; ownRemainingMs: number | null; otherReserveMs: number; totalRemainingMs: number},
    cleanup: null as null | {resource: Resource | 'NONE'; requestedMs: number; availableMs: number; ownRemainingMs: number | null; otherReserveMs: number; totalRemainingMs: number},
    recover: {stage: 'NOT_RUN' as RecoverStage, outcome: 'NOT_RUN' as 'NOT_RUN' | 'RUNNING' | 'PASSED' | 'FAILED',
      closeStatus: 'UNKNOWN' as 'UNKNOWN' | 'CLOSED' | 'OTHER', producerRows: 'UNKNOWN' as 'UNKNOWN' | 'ZERO' | 'ONE' | 'MANY', producerSent: null as boolean | null,
      effectRows: 'UNKNOWN' as 'UNKNOWN' | 'ZERO' | 'ONE' | 'MANY', effectGuard: null as boolean | null, consumerGuard: null as boolean | null},
  };
  const finite = (value: number) => Math.max(-1_000_000, Math.min(1_000_000, Math.floor(value)));
  function remaining(key: Resource, cap: number) {
    return Math.max(0, Math.min(cap, (leases.get(key) ?? Infinity) - now()));
  }
  function budget(cap: number, next = {}, action = false) {
    const required = reservations(next);
    let others = 0;
    for (const [key, value] of Object.entries(required) as [Resource, number][]) {
      if (key !== active) others += remaining(key, value);
    }
    const own = active && leases.has(active) ? leases.get(active)! - now() : Infinity;
    const total = end - now(), available = Math.min(total - others, observing ? Infinity : own);
    if (available < (action ? cap : 1000)) {
      witness[cleaning() ? 'cleanup' : 'main'] = {resource: active ?? 'NONE', requestedMs: finite(cap), availableMs: finite(available),
        ownRemainingMs: Number.isFinite(own) ? finite(own) : null, otherReserveMs: finite(others), totalRemainingMs: finite(total)};
      throw new Error('Reconciliation cleanup reserve exhausted');
    }
    return Math.max(1, Math.min(cap, Math.floor(available)));
  }
  function bind(key: Resource, absolute: number) {
    if (!Number.isFinite(absolute)) throw new Error('Reconciliation resource deadline required');
    leases.set(key, Math.min(leases.get(key) ?? Infinity, absolute));
  }
  function start(key: Resource) {
    if (!leases.has(key)) {
      const cap = reservations()[key];
      if (!(typeof cap === 'number' && cap > 0)) throw new Error('Reconciliation resource obligation required');
      bind(key, now() + cap);
    }
  }
  function enter(key: Resource, observation = false) {
    start(key);
    const previous = active, previousObservation = observing;
    active = key; observing = observation && leases.get(key)! <= now();
    const leave = () => { active = previous; observing = previousObservation; };
    try {
      if (!observing) budget(Math.max(0, Math.floor(leases.get(key)! - now())), {}, true);
    } catch (error) { leave(); throw error; }
    return leave;
  }
  async function run<T>(key: Resource, call: () => Promise<T> | T, observation = false): Promise<T> {
    const leave = enter(key, observation);
    try { return await call(); }
    finally { leave(); }
  }
  function assertWithin() {
    if (end < now() || (!observing && active && leases.get(active)! < now())) {
      // Reuse the same finite guard receipt; no arbitrary error content leaves the caller.
      budget(1, {}, true);
    }
  }
  return {budget, bind, run, enter, witness, assertWithin,
    deadline: (key: Resource) => leases.get(key),
    remaining: (key: Resource) => remaining(key, reservations()[key] ?? 0),
    narrow: (key: Resource, cap: number) => bind(key, now() + cap),
    recoverStage(stage: RecoverStage) {
      witness.recover.stage = stage; witness.recover.outcome = 'RUNNING';
      if (stage === 'CLOSE_GET') witness.recover.closeStatus = 'UNKNOWN';
      if (stage === 'PRODUCER') { witness.recover.producerRows = 'UNKNOWN'; witness.recover.producerSent = null; }
      if (stage === 'EFFECT') { witness.recover.effectRows = 'UNKNOWN'; witness.recover.effectGuard = null; }
      if (stage === 'CONSUMER') witness.recover.consumerGuard = null;
    },
    recoverPassed() { witness.recover.outcome = 'PASSED'; },
    recoverFailed() { witness.recover.outcome = 'FAILED'; },
  };
}
