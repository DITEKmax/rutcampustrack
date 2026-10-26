import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

/** Prove the launcher's live 17-container cohort before any local seed change. */
export function requireLocalStage17(): boolean {
  return requireLocalLane('full') !== undefined;
}

/** Only the Python admission boundary may supply a local Compose identity. */
export function requireLocalLane(mode: 'full' | 'client' | 'lesson-lifecycle' | 'ui-batch'):
  { project: string; nonce: string } | undefined {
  if (!process.env.RCT_LOCAL_STAGE17_MANIFEST) {
    if (process.env.RCT_LOCAL_STAGE17_NONCE || process.env.RCT_LOCAL_STAGE17_MODE)
      throw new Error('Local disposable cohort manifest is required');
    return undefined;
  }
  const batch = process.env.RCT_LOCAL_STAGE17_MODE === 'ui-batch';
  const admittedMode = batch && mode === 'client' ? 'ui-batch' : mode;
  const lane = process.env.RCT_LOCAL_UI_BATCH_LANE;
  if (batch && (!lane || (mode !== 'client' && mode !== 'ui-batch')))
    throw new Error('Local UI batch requires its explicit lane admission');
  try {
    const output = execFileSync(
      'python3',
      [resolve(__dirname, '../../../scripts/local_e2e_async.py'), 'admit', '--expected-mode', admittedMode, ...(batch ? ['--expected-batch-lane', lane!] : [])],
      { encoding: 'utf8', timeout: 30000, maxBuffer: 4096, stdio: ['ignore', 'pipe', 'pipe'] },
    );
    const value = JSON.parse(output);
    if (
      value.phase === 'LOCAL_STAGE17_ADMITTED' && value.mode === admittedMode &&
      typeof value.nonce === 'string' && /^[0-9a-f]{24}$/.test(value.nonce) &&
      value.project === `rct-local-stage17-${value.nonce.slice(0, 12)}` &&
      Object.keys(value).length === (batch ? 5 : 4) && (!batch || value.batchLane === lane)
    ) return { project: value.project, nonce: value.nonce };
  } catch {
    // Raw subprocess errors may contain paths and environment; keep the boundary closed.
  }
  throw new Error('Local disposable cohort lane admission failed');
}
