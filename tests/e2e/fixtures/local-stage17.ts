import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

/** Prove the launcher's live 16-container cohort before any local seed change. */
export function requireLocalStage17(): boolean {
  if (!process.env.RCT_LOCAL_STAGE17_MANIFEST) return false;
  try {
    const output = execFileSync(
      'python3',
      [resolve(__dirname, '../../../scripts/local_e2e_async.py'), 'admit'],
      { encoding: 'utf8', timeout: 30000, maxBuffer: 4096, stdio: ['ignore', 'pipe', 'pipe'] },
    );
    const value = JSON.parse(output);
    if (value.phase === 'LOCAL_STAGE17_ADMITTED' && Object.keys(value).length === 1) return true;
  } catch {
    // Raw subprocess errors may contain paths and environment; keep the boundary closed.
  }
  throw new Error('Local stage17 disposable cohort admission failed');
}
