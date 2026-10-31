import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { test, type TestInfo } from '@playwright/test';
import type { Suite, TestCase, TestError, TestResult } from '@playwright/test/reporter';

// One shared public catalog; error text and source coordinates are never case identities.
const catalog = JSON.parse(readFileSync(resolve(__dirname, 'http-diagnostic-cases.json'), 'utf8'));
export const caseAnnotation = 'rct-diagnostic-case.v1';
const proofField = '__rctDiagnosticProof';
const keyEnvironment = 'RCT_PRIVATE_DIAGNOSTIC_RUN_KEY';
export interface DiagnosticEvent {
  schema: 'rct-diagnostic-event.v1';
  caseId: string;
  stageId: string;
  eventCode: string;
  httpStatus: number;
}
interface Binding {
  caseId: string;
  testId: string;
  project: string;
  retry: number;
}
interface Proof {
  schema: 'rct-diagnostic-proof.v1';
  binding: Binding;
  event: DiagnosticEvent;
  before: string;
  after: string;
  signature: string;
}
interface Context {
  binding: Binding;
  native: WeakMap<object, DiagnosticEvent>;
  serialized: WeakMap<object, DiagnosticEvent>;
  nativeCount: number;
  observation?: DiagnosticEvent;
  captured: WeakSet<object>;
}
const contexts = new WeakMap<TestInfo, Context>();
const own = (v: unknown, k: string): unknown =>
  v && typeof v === 'object' ? Object.getOwnPropertyDescriptor(v, k)?.value : undefined;
const exact = (v: unknown, keys: string[]) =>
  !!v &&
  typeof v === 'object' &&
  !Array.isArray(v) &&
  Object.getPrototypeOf(v) === Object.prototype &&
  Object.values(Object.getOwnPropertyDescriptors(v)).every((d) => Object.hasOwn(d, 'value')) &&
  Object.keys(v).sort().join(',') === [...keys].sort().join(',');
if (
  !exact(catalog, ['schema', 'cases']) ||
  catalog.schema !== 'rct-diagnostic-catalog.v1' ||
  !catalog.cases ||
  typeof catalog.cases !== 'object' ||
  Array.isArray(catalog.cases)
)
  throw new Error('Invalid diagnostic catalog');
function validBinding(value: unknown): value is Binding {
  if (!exact(value, ['caseId', 'testId', 'project', 'retry'])) return false;
  const binding = value as Binding;
  return (
    typeof binding.caseId === 'string' &&
    Object.hasOwn(catalog.cases, binding.caseId) &&
    typeof binding.testId === 'string' &&
    binding.testId.length > 0 &&
    binding.testId.length <= 1024 &&
    typeof binding.project === 'string' &&
    binding.project.length <= 128 &&
    Number.isInteger(binding.retry) &&
    binding.retry >= 0 &&
    binding.retry <= 1000
  );
}
const digest = (v: unknown) => createHash('sha256').update(JSON.stringify(v)).digest('hex');
const errorDigest = (error: unknown) => {
  const message = own(error, 'message'),
    stack = own(error, 'stack');
  return typeof message === 'string' &&
    message.length <= 65536 &&
    typeof stack === 'string' &&
    stack.length <= 65536
    ? digest({ message, stack })
    : null;
};
function registered(annotations: readonly { type: string; description?: string }[]): string | null {
  const rows = annotations.filter((a) => a.type === caseAnnotation);
  if (!rows.length) return null;
  if (
    rows.length !== 1 ||
    typeof rows[0].description !== 'string' ||
    !Object.hasOwn(catalog.cases, rows[0].description)
  )
    throw new Error('Invalid diagnostic case registration');
  return rows[0].description;
}
export function diagnosticCase(caseId: string) {
  if (!Object.hasOwn(catalog.cases, caseId))
    throw new Error('Unknown diagnostic case registration');
  return { type: caseAnnotation, description: caseId };
}
function allowed(event: unknown): event is DiagnosticEvent {
  if (!exact(event, ['schema', 'caseId', 'stageId', 'eventCode', 'httpStatus'])) return false;
  const e = event as DiagnosticEvent;
  return (
    e.schema === 'rct-diagnostic-event.v1' &&
    typeof e.caseId === 'string' &&
    Object.hasOwn(catalog.cases, e.caseId) &&
    catalog.cases[e.caseId].events.some(
      (rule: any) =>
        rule.stageId === e.stageId &&
        rule.eventCode === e.eventCode &&
        rule.httpStatus === e.httpStatus,
    )
  );
}
function context(info: TestInfo): Context | null {
  const existing = contexts.get(info);
  if (existing) return existing;
  const caseId = registered(info.annotations);
  if (!caseId) return null;
  const value: Context = {
    binding: { caseId, testId: info.testId, project: info.project.name, retry: info.retry },
    native: new WeakMap(),
    serialized: new WeakMap(),
    nativeCount: 0,
    captured: new WeakSet(),
  };
  if (!validBinding(value.binding)) throw new Error('Invalid diagnostic execution context');
  // Associate the actual raw object at the existing framework serialization boundary.
  // Equal message/stack bytes on a different Error never acquire its native brand.
  const boundary = info as TestInfo & { _failWithError?: (error: unknown) => unknown };
  const original = boundary._failWithError;
  if (typeof original !== 'function') throw new Error('Diagnostic failure boundary unavailable');
  Object.defineProperty(info, '_failWithError', {
    configurable: true,
    writable: true,
    value: function (this: TestInfo, raw: unknown) {
      const before = info.errors.length;
      const event = raw && typeof raw === 'object' ? value.native.get(raw) : undefined;
      const result = Reflect.apply(original, this, [raw]);
      if (this === info && event && info.errors.length === before + 1) {
        const serialized = info.errors[before];
        if (serialized && typeof serialized === 'object') value.serialized.set(serialized, event);
      }
      return result;
    },
  });
  contexts.set(info, value);
  return value;
}
export function beginDiagnosticCase(info: TestInfo): void {
  context(info);
}
export function currentDiagnosticCase(): string | null {
  try {
    return context(test.info())?.binding.caseId ?? null;
  } catch {
    return null;
  }
}
export type LogoutControlObservation =
  | 'LOGOUT_CONTROL_MISSING'
  | 'LOGOUT_CONTROL_MULTIPLE'
  | 'LOGOUT_CONTROL_HIDDEN'
  | 'LOGOUT_CONTROL_DISABLED'
  | 'LOGOUT_CONTROL_OUTSIDE_VIEWPORT'
  | 'LOGOUT_CONTROL_POINTER_INTERCEPTED'
  | 'LOGOUT_CLICK_UNKNOWN';

function diagnosticFailure(
  error: Error,
  eventCode: string,
  httpStatus: number,
  stageId: 'DATASET_HTTP' | 'UI_ACTION',
): Error {
  try {
    const state = context(test.info());
    if (!state) return error;
    const event: DiagnosticEvent = {
      schema: 'rct-diagnostic-event.v1',
      caseId: state.binding.caseId,
      stageId,
      eventCode,
      httpStatus,
    };
    if (allowed(event) && !state.native.has(error) && state.nativeCount < 8) {
      state.native.set(error, event);
      state.nativeCount++;
    }
  } catch {
    /* Optional diagnostics must preserve the original failure. */
  }
  return error;
}
export function diagnosticHttpFailure(error: Error, eventCode: string, httpStatus: number): Error {
  return diagnosticFailure(error, eventCode, httpStatus, 'DATASET_HTTP');
}
/** Native original-error branding; the catalog admits these observations only for D002. */
export function diagnosticUiFailure(error: Error, eventCode: LogoutControlObservation): Error {
  return diagnosticFailure(error, eventCode, 0, 'UI_ACTION');
}
export function retainDiagnosticObservation(): void {
  try {
    const state = context(test.info());
    if (!state) return;
    const event: DiagnosticEvent = {
      schema: 'rct-diagnostic-event.v1',
      caseId: state.binding.caseId,
      stageId: 'D002_RECONNECT',
      eventCode: 'OBSERVATION',
      httpStatus: 0,
    };
    if (allowed(event)) state.observation = event;
  } catch {
    /* No replacement error. */
  }
}
function sign(unsigned: Omit<Proof, 'signature'>, key: Buffer): Proof {
  return {
    ...unsigned,
    signature: createHmac('sha256', key).update(JSON.stringify(unsigned)).digest('hex'),
  };
}
/** Capture the actual serialized failure before privacy rewriting, and seal its final projection. */
export function captureDiagnosticFailure(
  info: TestInfo,
  error: TestError,
  rewrite: () => void,
): void {
  let state: Context | null;
  try {
    state = context(info);
  } catch {
    rewrite();
    return;
  }
  if (!state) {
    rewrite();
    return;
  }
  if (state.captured.has(error)) {
    const prior = own(error, proofField);
    const intact =
      Array.isArray(prior) && prior.every((proof) => own(proof, 'after') === errorDigest(error));
    // A later hook may add fields. Always reapply privacy, preserving only an intact seal.
    rewrite();
    if (intact)
      Object.defineProperty(error, proofField, {
        value: prior,
        enumerable: true,
        configurable: true,
      });
    return;
  }
  state.captured.add(error);
  const before = errorDigest(error);
  const event = state.serialized.get(error) || {
    schema: 'rct-diagnostic-event.v1' as const,
    caseId: state.binding.caseId,
    stageId: 'FRAMEWORK',
    eventCode: 'REPORTED_FRAMEWORK_ERROR',
    httpStatus: 0,
  };
  rewrite();
  const after = errorDigest(error);
  const keyText = process.env[keyEnvironment];
  if (!before || !after || !keyText || !/^[a-f0-9]{64}$/.test(keyText)) return;
  const key = Buffer.from(keyText, 'hex');
  const events = [event, ...(state.observation ? [state.observation] : [])];
  Object.defineProperty(error, proofField, {
    value: events.map((value) =>
      sign(
        { schema: 'rct-diagnostic-proof.v1', binding: state.binding, event: value, before, after },
        key,
      ),
    ),
    enumerable: true,
    configurable: true,
  });
  key.fill(0);
}
/** Reporter-private per-run verifier. Nothing from the proof enters the public row. */
export class DiagnosticRun {
  private key = randomBytes(32);
  private registry = new WeakMap<TestCase, string>();
  invalid = false;
  begin(suite: Suite): void {
    process.env[keyEnvironment] = this.key.toString('hex');
    const seen = new Set<string>();
    for (const item of suite.allTests()) {
      try {
        const id = registered(item.annotations);
        if (!id) continue;
        const key = JSON.stringify([item.parent.project()?.name, id]);
        if (seen.has(key)) {
          this.invalid = true;
          continue;
        }
        seen.add(key);
        this.registry.set(item, id);
      } catch {
        this.invalid = true;
      }
    }
  }
  caseId(item: TestCase): string | undefined {
    return this.registry.get(item);
  }
  read(item: TestCase, result: TestResult): DiagnosticEvent[] {
    const caseId = this.registry.get(item),
      events: DiagnosticEvent[] = [],
      seen = new Set<string>(),
      seenProofs = new Set<string>();
    const proofsPresent = result.errors.some((error) => own(error, proofField) !== undefined);
    if (!caseId) {
      if (proofsPresent) this.invalid = true;
      return events;
    }
    const expected = {
      caseId,
      testId: item.id,
      project: item.parent.project()?.name ?? '',
      retry: result.retry,
    };
    for (const error of result.errors) {
      const proofs = own(error, proofField);
      if (!Array.isArray(proofs) || !proofs.length || proofs.length > 2) {
        this.invalid = true;
        continue;
      }
      for (const proof of proofs) {
        if (
          !exact(proof, ['schema', 'binding', 'event', 'before', 'after', 'signature']) ||
          proof.schema !== 'rct-diagnostic-proof.v1' ||
          !validBinding(proof.binding) ||
          JSON.stringify(proof.binding) !== JSON.stringify(expected) ||
          !allowed(proof.event) ||
          proof.event.caseId !== caseId ||
          typeof proof.before !== 'string' ||
          !/^[a-f0-9]{64}$/.test(proof.before) ||
          proof.after !== errorDigest(error) ||
          typeof proof.signature !== 'string' ||
          !/^[a-f0-9]{64}$/.test(proof.signature)
        ) {
          this.invalid = true;
          continue;
        }
        const { signature, ...unsigned } = proof;
        const expectedSignature = sign(unsigned, this.key).signature;
        if (
          !timingSafeEqual(Buffer.from(signature, 'hex'), Buffer.from(expectedSignature, 'hex'))
        ) {
          this.invalid = true;
          continue;
        }
        const proofIdentity = JSON.stringify(proof);
        if (seenProofs.has(proofIdentity)) {
          this.invalid = true;
          continue;
        }
        seenProofs.add(proofIdentity);
        const identity = JSON.stringify(proof.event);
        if (seen.has(identity)) continue;
        if (events.length >= 8) {
          this.invalid = true;
          continue;
        }
        seen.add(identity);
        events.push(proof.event);
      }
    }
    return events;
  }
  close(): void {
    delete process.env[keyEnvironment];
    this.key.fill(0);
  }
}
