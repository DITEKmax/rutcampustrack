// The original D9 poll owns every predicate and operation. This is only its protected window.
export const INITIAL_READINESS_MS = 75_000;
export type InitialReadinessSnapshot = {
  firstFailed: string;
  mainReady: number | null; mainUnacked: number | null; mainConsumers: number | null;
  deadReady: number | null; deadUnacked: number | null;
  retryReady: number | null; retryUnacked: number | null;
  scheduleUnproven: number | null; scheduleLegacy: number | null; scheduleDue: number | null;
  initialExpectedApplied: boolean; initialNoPendingWork: boolean; initialProducerSent: boolean;
};
export function createInitialReadiness(now: () => number = () => performance.now()) {
  let started = 0, end = 0;
  const witness = {
    schema: 'RCT_RECONCILIATION_READINESS_V1', requestedBoundMs: INITIAL_READINESS_MS,
    admittedBoundMs: 0, elapsedMs: 0,
    status: 'NOT_RUN' as 'NOT_RUN' | 'POLLING' | 'PASSED' | 'FAILED' | 'BUDGET_REJECTED',
    firstFailingCondition: 'UNOBSERVED', lastFailingCondition: 'UNOBSERVED',
    lastCompletedPoll: null as InitialReadinessSnapshot | null,
  };
  const elapsed = () => Math.max(0, Math.min(420_000, Math.floor(now() - started)));
  return {
    witness,
    admit(budget: (cap: number) => number) {
      started = now(); witness.status = 'BUDGET_REJECTED';
      const admitted = budget(INITIAL_READINESS_MS);
      if (admitted !== INITIAL_READINESS_MS) throw new Error('Full initial readiness cleanup-protected budget required');
      witness.admittedBoundMs = admitted; end = started + admitted; witness.status = 'POLLING';
      return end;
    },
    remaining() { return Math.max(1, Math.floor(end - now())); },
    observe(snapshot: InitialReadinessSnapshot, passed: boolean) {
      witness.lastCompletedPoll = { ...snapshot };
      if (!passed) {
        if (witness.firstFailingCondition === 'UNOBSERVED') witness.firstFailingCondition = snapshot.firstFailed;
        witness.lastFailingCondition = snapshot.firstFailed;
      }
    },
    finish(passed: boolean) {
      if (witness.status === 'POLLING') {
        witness.status = passed ? 'PASSED' : 'FAILED';
        witness.elapsedMs = elapsed();
      } else if (witness.status === 'BUDGET_REJECTED') witness.elapsedMs = elapsed();
    },
  };
}
