import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { closeSync, constants, lstatSync, openSync, readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { TextDecoder } from 'node:util';
import { dirname, resolve } from 'node:path';

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

const admissionChildKinds = ['AdmissionError', 'FileNotFoundError', 'PermissionError', 'TimeoutExpired', 'CalledProcessError',
  'JSONDecodeError', 'ValueError', 'KeyError', 'TypeError', 'OSError', 'RuntimeError', 'INTERRUPTED'] as const;
function admissionOwn(value: unknown, key: string): unknown {
  if (!value || typeof value !== 'object') return undefined;
  try { return Object.getOwnPropertyDescriptor(value, key)?.value; } catch { return undefined; }
}

/** Read only the bounded closed Python failure envelope; never copy raw child output. */
function closedAdmissionChild(error: unknown): { childKind: string; codeDigest: string | null; nativeStatus: number | null; signal: string | null } {
  const status = admissionOwn(error, 'status'); const signal = admissionOwn(error, 'signal');
  const result = { childKind: 'UNKNOWN', codeDigest: null as string | null,
    nativeStatus: typeof status === 'number' && Number.isInteger(status) && status >= 0 && status <= 255 ? status : null,
    signal: typeof signal === 'string' ? ['SIGKILL', 'SIGTERM', 'SIGINT', 'SIGABRT', 'SIGSEGV'].includes(signal) ? signal : 'OTHER' : null };
  try {
    const output = admissionOwn(error, 'stdout');
    let text: string;
    if (typeof output === 'string') {
      if (output.length > 4096 || Buffer.byteLength(output) > 4096) return result;
      text = output;
    } else if (ArrayBuffer.isView(output) && Buffer.isBuffer(output)) {
      const byteLength = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(Uint8Array.prototype), 'byteLength')?.get;
      const size = byteLength?.call(output);
      if (!Number.isSafeInteger(size) || size < 0 || size > 4096) return result;
      text = new TextDecoder('utf-8', { fatal: true }).decode(output);
    } else return result;
    const value = JSON.parse(text);
    if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== 3 ||
        !['phase', 'error_kind', 'admission_code'].every(key => Object.hasOwn(value, key)) || value.phase !== 'LOCAL_RUN_FAILED' ||
        !admissionChildKinds.some(kind => kind === value.error_kind)) return result;
    const code = value.admission_code;
    if (code !== null && (typeof code !== 'string' || !/^[A-Z][A-Z0-9_]{0,127}$/.test(code))) return result;
    return { ...result, childKind: value.error_kind,
      codeDigest: code === null ? null : createHash('sha256').update(code, 'ascii').digest('hex') };
  } catch { return result; }
}

/** First failure wins in an already prepared stable namespace; diagnostic refusal never changes admission. */
function retainAdmissionChild(expected: LocalSpecialLane, phase: string, error: unknown): void {
  let descriptor: number | undefined;
  try {
    if (process.platform !== 'linux') return;
    const root = resolve(__dirname, '../../..');
    const nonce = process.env.RCT_LOCAL_STAGE17_NONCE;
    const discovery = process.env.RCT_LOCAL_SPECIAL_DISCOVERY ?? '';
    const lanes = ['bot', 'production-service-worker', 'password-recovery', 'registry-native-pdf', 'semester-metadata',
      'subject-deletion', 'assignment', 'today', 'semester-reconciliation', 'application-recovery', 'semester-archive'];
    if (!nonce || !/^[a-f0-9]{24}$/.test(nonce) || !lanes.includes(expected) ||
        !['main', 'prepare', 'pending', 'resume', 'seed', 'admit', 'attachment'].includes(phase) ||
        process.env.RCT_LOCAL_STAGE17_MODE !== 'special13' || process.env.RCT_LOCAL_SPECIAL_LANE !== expected ||
        process.env.RCT_LOCAL_SPECIAL_PHASE !== phase || !['', 'true'].includes(discovery)) return;
    const run = resolve(root, '.agent/evidence/local-runs', `rct-local-stage17-${nonce.slice(0, 12)}`);
    const manifest = resolve(run, 'owner.json');
    if (process.env.RCT_LOCAL_STAGE17_MANIFEST !== manifest) return;
    const directory = resolve(run, 'framework-private', expected, phase);
    for (let path = directory; path !== dirname(dirname(run)); path = dirname(path)) {
      const info = lstatSync(path);
      if (!info.isDirectory() || info.isSymbolicLink() || info.uid !== process.getuid?.() ||
          (info.mode & 0o777) !== 0o700 || realpathSync(path) !== path) return;
    }
    const info = lstatSync(manifest);
    if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1 || info.uid !== process.getuid?.() ||
        (info.mode & 0o777) !== 0o600 || info.size <= 0 || info.size > 8 * 1024 ** 2) return;
    const owner = JSON.parse(readFileSync(manifest, 'utf8'));
    const revision = admissionOwn(owner, 'source_sha');
    if (admissionOwn(owner, 'root') !== root || admissionOwn(owner, 'run') !== run || admissionOwn(owner, 'nonce') !== nonce ||
        admissionOwn(owner, 'project') !== `rct-local-stage17-${nonce.slice(0, 12)}` || admissionOwn(owner, 'uid') !== process.getuid?.() ||
        admissionOwn(owner, 'mode') !== 'special13' || typeof revision !== 'string' || !/^[a-f0-9]{40}$/.test(revision)) return;
    const source = resolve(__dirname, 'local-special.ts');
    const sourceInfo = lstatSync(source);
    const digest = admissionOwn(admissionOwn(owner, 'sources'), 'tests/e2e/fixtures/local-special.ts');
    if (!sourceInfo.isFile() || sourceInfo.isSymbolicLink() || sourceInfo.nlink !== 1 || sourceInfo.size > 1024 ** 2 ||
        realpathSync(source) !== source || typeof digest !== 'string' || !/^[a-f0-9]{64}$/.test(digest) ||
        createHash('sha256').update(readFileSync(source)).digest('hex') !== digest) return;
    const receipt = { schema: 'RCT_LOCAL_ADMISSION_FAILURE_V1', lane: expected, phase, sourceRevision: revision, ...closedAdmissionChild(error) };
    descriptor = openSync(resolve(directory, `admission.${discovery === 'true' ? 'discovery' : 'execution'}.safe.json`),
      constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
    writeFileSync(descriptor, JSON.stringify(receipt, null, 2));
  } catch { /* A missing/unsafe namespace, prior receipt or failed write never masks the original failure. */ }
  finally { if (descriptor !== undefined) { try { closeSync(descriptor); } catch { /* Never replace the admission failure. */ } } }
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
  } catch (error) {
    // Preserve only a small closed child reason; the public error and all admission guards stay unchanged.
    retainAdmissionChild(expected, phase, error);
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
