'use strict';

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const probePath = path.join(root, 'probe.mjs');
const preloadPath = path.join(here, 'probe-secrets-process-prefixed-preload.cjs');
const evidencePath = path.join(here, 'h68-probe-secrets-prefixed-evidence.json');
const sentinelValues = [
  'abcXYZ-ACCESS-SENTINEL',
  'abcCOOKIE-SENTINEL',
  'escaped-double-suffix-SENTINEL',
  'escaped-single-suffix-SENTINEL'
];
const overlapSuffixValues = ['XYZ-ACCESS-SENTINEL', 'COOKIE-SENTINEL'];
const commandArgs = [
  '--require', preloadPath,
  probePath,
  '--origin', 'https://synthetic.invalid',
  '--ca', probePath,
  '--login', 'synthetic-login',
  '--password', 'abc',
  '--i2-only'
];

function sha256(filePath) {
  return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
}

function redactEvidence(text) {
  let safe = String(text);
  for (const sentinel of sentinelValues) safe = safe.split(sentinel).join('<synthetic-secret>');
  return safe;
}

function run(mode) {
  const result = spawnSync(process.execPath, commandArgs, {
    cwd: root,
    encoding: 'utf8',
    env: { ...process.env, H68_PROCESS_SECRET_MODE: mode }
  });
  const stdout = String(result.stdout ?? '');
  const parsed = (() => { try { return JSON.parse(stdout); } catch { return null; } })();
  const leaked = Object.fromEntries(sentinelValues.map((sentinel) => [sentinel, stdout.includes(sentinel)]));
  return {
    mode,
    exitCode: result.status,
    parsed,
    parseFailed: parsed === null,
    leaked,
    overlapSuffixLeak: Object.fromEntries(overlapSuffixValues.map((suffix) => [suffix, stdout.includes(suffix)])),
    escapedDoubleSuffixLeak: stdout.includes('escaped-double-suffix-SENTINEL'),
    escapedSingleSuffixLeak: stdout.includes('escaped-single-suffix-SENTINEL'),
    outputRedacted: redactEvidence(stdout),
    stderrRedacted: redactEvidence(result.stderr)
  };
}

const success = run('success');
const forcedCatch = run('catch');
const evidence = {
  schema: 'rct.student-requests-h68-probe-secrets-prefixed.v1',
  probePath,
  probeSha256: sha256(probePath),
  preloadPath,
  preloadSha256: sha256(preloadPath),
  command: 'node --require probe-secrets-process-prefixed-preload.cjs probe.mjs --origin https://synthetic.invalid --ca probe.mjs --login synthetic-login --password abc --i2-only',
  controlledRuntime: 'Synthetic https.Agent/request; no network, ports, Docker, credentials or keys.',
  success,
  forcedTopCatch: forcedCatch,
  actualDefects: [
    'Current pre-fix structured string sanitizer stops a quoted sensitive value at an escaped quote, leaving the suffix visible.',
    'Current pre-fix registered replacement is insertion ordered: password abc replaces the prefix of token abcXYZ-ACCESS-SENTINEL and cookie abcCOOKIE-SENTINEL, leaving suffixes visible.'
  ],
  limitations: ['This evidence is immutable after this run and records redacted output only.']
};
if (fs.existsSync(evidencePath)) {
  throw new Error(`refusing to overwrite existing pre-fix evidence; use a new explicitly named evidence path: ${evidencePath}`);
}
fs.writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');

if (success.parseFailed || forcedCatch.parseFailed ||
    !success.escapedDoubleSuffixLeak || !success.escapedSingleSuffixLeak ||
    !forcedCatch.escapedDoubleSuffixLeak || !forcedCatch.escapedSingleSuffixLeak ||
    !Object.values(success.overlapSuffixLeak).some(Boolean) ||
    !Object.values(forcedCatch.overlapSuffixLeak).some(Boolean)) {
  throw new Error(`expected pre-fix probe leaks were not reproduced; evidence=${evidencePath}`);
}
process.stdout.write(`H68 probe secret prefixed: PASS (JSON remained parseable while suffix leaks reproduced; evidence ${evidencePath})\n`);
