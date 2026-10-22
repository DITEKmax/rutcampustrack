/** Browser-free controls of the actual lifecycle assertions, with ordered locator boundaries. */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect as primitiveExpect, errors as playwrightErrors } from '@playwright/test';
const require = createRequire(import.meta.url);
const { requireOrImport } = require(
  join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'),
);
const { terminal, assertOwnedSemesterUi } = await requireOrImport(
  join(dirname(fileURLToPath(import.meta.url)), 'semester-lifecycle-observers.ts'),
);

const operation = {
  operationId: '00000000-0000-4000-8000-000000000001',
  semesterId: 7,
  action: 'ARCHIVE',
  operationState: 'COMPLETED',
  academic: 'READY',
  schedule: 'READY',
  attendance: 'READY',
  stateVersion: 4,
};
const status = {
  semesterId: 7,
  operation,
  active: false,
  transition: 'NONE',
  releasePending: false,
  archived: true,
  writeBlocked: true,
};
const semester = { id: 7, active: false, activationScheduled: false, archived: true };
async function checkTerminal(changes = {}) {
  const calls = [];
  const admin = {
    async call(method, path, body, accepted, headers) {
      calls.push([method, path, headers]);
      if (method === 'POST') return { ...operation, ...changes.replay };
      if (path.endsWith('/archive/status'))
        return { ...status, ...changes.status, operation: { ...operation, ...changes.operation } };
      return { ...semester, ...changes.semester };
    },
  };
  const result = await terminal(admin, 7, 'ARCHIVE', 'owned-key', {}, 1, operation);
  assert.equal(result.semesterId, 7);
  assert.deepEqual(calls.at(-1), [
    'POST',
    '/api/academic/semesters/7/archive',
    { 'Idempotency-Key': 'owned-key' },
  ]);
}

async function checkUi(changes = {}) {
  const state = {
    statusCount: 1,
    ownCount: 1,
    icon: 'Архивный',
    action: 'Восстановить',
    enabled: true,
    visible: true,
    ...changes,
  };
  let observed;
  const observedVisible = new Promise((resolve) => (observed = resolve));
  let resume;
  const completion = new Promise((resolve) => (resume = resolve));
  const actions = [];
  const continueCheck = {
    async isVisible() {
      const visible = state.visible;
      observed();
      await completion;
      return visible;
    },
    async count() {
      return state.statusCount;
    },
    async click() {
      actions.push('continue');
      if (state.clickError) throw new Error('Controlled click failure');
      if (state.statusCount === 0 || state.clickTimeout)
        throw new playwrightErrors.TimeoutError('Controlled vanished continuation');
    },
  };
  const statusCard = {
    kind: 'status',
    filter: () => statusCard,
    getByRole: () => continueCheck,
  };
  const ownCard = {
    kind: 'owned',
    getByRole: (role, options) => ({ kind: role, name: options.name }),
  };
  const expect = (locator) => ({
    async toHaveCount(count) {
      primitiveExpect(locator.kind === 'status' ? state.statusCount : state.ownCount).toBe(count);
      actions.push('owned-count');
    },
    async toBeVisible() {
      primitiveExpect(state.ownCount).toBe(1);
      primitiveExpect(state.icon).toBe(locator.name);
      actions.push('owned-icon');
    },
    async toBeEnabled() {
      primitiveExpect(state.ownCount).toBe(1);
      primitiveExpect(state.action).toBe(locator.name);
      primitiveExpect(state.enabled).toBe(true);
      actions.push('owned-action');
    },
  });
  const checking = assertOwnedSemesterUi(
    { locator: () => statusCard }, { getByRole: () => ({}) },
    'Owned synthetic semester', ownCard, changes.restore ? 'RESTORE' : 'ARCHIVE', () => 1, expect,
  );
  await observedVisible;
  // Deterministic ordering: visibility was read; permitted UI completion now
  // removes the transient card before the caller resumes. No clocks or sleeps.
  if (changes.remove !== false) state.statusCount = 0;
  resume();
  await checking;
}

await checkTerminal();
let terminalRejected = 0;
for (const changes of [
  { operation: { operationId: 'foreign-operation' } },
  { operation: { semesterId: 8 } },
  { operation: { action: 'RESTORE' } },
  { operation: { operationState: 'PENDING' } },
  { operation: { academic: 'PENDING' } },
  { status: { semesterId: 8 } },
  { status: { active: true } },
  { status: { transition: 'ARCHIVING' } },
  { status: { releasePending: true } },
  { status: { archived: false } },
  { status: { writeBlocked: false } },
  { semester: { id: 8 } },
  { semester: { activationScheduled: true } },
  { replay: { operationId: 'foreign-operation' } },
  { replay: { stateVersion: 5 } },
]) {
  await assert.rejects(() => checkTerminal(changes));
  terminalRejected += 1;
}
await checkUi();
await checkUi({ visible: false });
await checkUi({ remove: false });
await checkUi({ restore: true, icon: 'Неактивный', action: 'Архивировать' });
let uiRejected = 0;
for (const changes of [
  { ownCount: 0 },
  { ownCount: 2 },
  { icon: 'Неактивный' },
  { action: 'Архивировать' },
  { enabled: false },
  { clickError: true },
  { clickTimeout: true, remove: false },
  { restore: true, icon: 'Архивный', action: 'Восстановить' },
]) {
  await assert.rejects(() => checkUi(changes));
  uiRejected += 1;
}
console.log(JSON.stringify({
  state: 'PASS', terminalAccepted: 1, terminalRejected, uiAccepted: 4, uiRejected,
  ordering: 'OBSERVED_VISIBLE_THEN_COMPLETED_REMOVAL', sharedFixtureAssertions: true,
  browserOrMountedDomProof: false,
}));
