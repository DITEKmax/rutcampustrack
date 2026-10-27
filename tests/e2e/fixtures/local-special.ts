import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

export type LocalSpecialLane = 'bot' | 'production-service-worker' | 'password-recovery' | 'registry-native-pdf'
  | 'semester-metadata' | 'subject-deletion' | 'assignment' | 'today' | 'semester-reconciliation'
  | 'application-recovery' | 'semester-archive';
export interface LocalSpecialContext {
  project: string; nonce: string; root: string; run: string; laneRun: string; networkName: string;
  certsDir: string; composeFile: string; recoveryProject: string; databaseProject: string; botImage: string | undefined;
}
export interface LocalSpecialService {
  id: string; image: string; configuredImage: string; labels: Record<string, string>;
  running: boolean; paused: boolean; health: string | undefined; readonly: boolean;
  mounts: { Type: string; Source: string; Destination: string; RW: boolean; Name?: string }[];
  networks: Record<string, { NetworkID: string; IPAddress: string; Aliases?: string[] }>;
}

function admitted(expected: LocalSpecialLane, service?: string): unknown {
  const phase = process.env.RCT_LOCAL_SPECIAL_PHASE;
  if (process.env.RCT_LOCAL_STAGE17_MODE !== 'special13' || process.env.RCT_LOCAL_SPECIAL_LANE !== expected || !phase)
    throw new Error('Local special lane and phase are required');
  try {
    return JSON.parse(execFileSync('python3', [resolve(__dirname, '../../../scripts/local_e2e_async.py'),
      'admit', '--expected-mode', 'special13', '--expected-special-lane', expected,
      '--expected-special-phase', phase, ...(service ? ['--special-service', service] : [])],
    { encoding: 'utf8', timeout: 60000, maxBuffer: 128 * 1024, stdio: ['ignore', 'pipe', 'pipe'] }));
  } catch {
    // Child errors and inspect/environment output never become framework diagnostic text.
    throw new Error('Local special resource admission failed');
  }
}

/** Undefined means no local identity was supplied; malformed local state always fails. */
export function localSpecialLane(expected: LocalSpecialLane): LocalSpecialContext | undefined {
  if (!process.env.RCT_LOCAL_STAGE17_MANIFEST) {
    if (process.env.RCT_LOCAL_STAGE17_NONCE || process.env.RCT_LOCAL_STAGE17_MODE || process.env.RCT_LOCAL_SPECIAL_LANE || process.env.RCT_LOCAL_SPECIAL_PHASE)
      throw new Error('Local special manifest is required');
    return undefined;
  }
  const value = admitted(expected) as LocalSpecialContext & { phase: string };
  const keys = ['phase', 'project', 'nonce', 'root', 'run', 'laneRun', 'networkName', 'certsDir', 'composeFile', 'recoveryProject', 'databaseProject', 'botImage'];
  if (!value || Object.keys(value).length !== keys.length || !keys.every(key => Object.hasOwn(value, key))
    || value.phase !== 'LOCAL_SPECIAL_ADMITTED' || !/^[a-f0-9]{24}$/.test(value.nonce)
    || value.project !== `rct-local-stage17-${value.nonce.slice(0, 12)}`
    || value.laneRun !== `${value.run}/lanes/${expected}` || value.networkName !== `${value.project}_private_net`
    || value.certsDir !== `${value.run}/certs` || value.composeFile !== `${value.run}/compose.json`
    || value.recoveryProject !== `rct-recovery-${value.nonce}`
    || ![value.project, value.recoveryProject].includes(value.databaseProject)
    || value.botImage !== null && (typeof value.botImage !== 'string' || !/^sha256:[a-f0-9]{64}$/.test(value.botImage)))
    throw new Error('Local special context was rejected');
  const { phase: _event, ...context } = value;
  return { ...context, botImage: context.botImage ?? undefined };
}

export function requireLocalSpecialLane(expected: LocalSpecialLane): LocalSpecialContext {
  const context = localSpecialLane(expected);
  if (!context) throw new Error('Local special lane is required');
  return context;
}

export function localSpecialService(context: LocalSpecialContext, service: string): LocalSpecialService {
  const lane = process.env.RCT_LOCAL_SPECIAL_LANE as LocalSpecialLane;
  const current = requireLocalSpecialLane(lane);
  if (JSON.stringify(current) !== JSON.stringify(context)) throw new Error('Local special context changed');
  const value = admitted(lane, service) as { phase: string; service: LocalSpecialService };
  const row = value?.service;
  if (value?.phase !== 'LOCAL_SPECIAL_SERVICE' || Object.keys(value).length !== 2 || !row
    || !/^[a-f0-9]{64}$/.test(row.id) || !/^sha256:[a-f0-9]{64}$/.test(row.image)
    || typeof row.configuredImage !== 'string' || typeof row.running !== 'boolean' || typeof row.paused !== 'boolean'
    || typeof row.readonly !== 'boolean' || !Array.isArray(row.mounts) || !row.networks
    || row.labels?.['rct.local.stage17'] !== context.nonce)
    throw new Error('Local special service was rejected');
  return row;
}
