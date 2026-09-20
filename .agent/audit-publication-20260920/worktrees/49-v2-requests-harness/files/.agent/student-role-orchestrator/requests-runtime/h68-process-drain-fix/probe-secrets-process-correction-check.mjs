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
const evidencePath = path.join(here, 'h68-probe-secrets-correction-evidence.json');
const sentinelValues = [
  'abcXYZ-ACCESS-SENTINEL',
  'abcCOOKIE-SENTINEL',
  'cookie-abc-SUFFIX-SENTINEL',
  'XYZ-ACCESS-SENTINEL',
  'COOKIE-SENTINEL',
  'escaped-double-suffix-SENTINEL',
  'escaped-single-suffix-SENTINEL'
];
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

function run(mode) {
  const result = spawnSync(process.execPath, commandArgs, {
    cwd: root,
    encoding: 'utf8',
    env: { ...process.env, H68_PROCESS_SECRET_MODE: mode }
  });
  const stdout = String(result.stdout ?? '');
  let parsed = null;
  let parseError = null;
  try { parsed = JSON.parse(stdout); } catch (error) { parseError = error.message; }
  const leaked = Object.fromEntries(sentinelValues.map((sentinel) => [sentinel, stdout.includes(sentinel)]));
  return { mode, exitCode: result.status, parsed, parseError, leaked, stderr: String(result.stderr ?? '') };
}

const success = run('success');
const forcedCatch = run('catch');
const successCacheControl = success.parsed?.i2?.fixed?.cacheControl ?? '';
const catchError = forcedCatch.parsed?.error ?? '';
const successNoLeak = Object.values(success.leaked).every((value) => !value);
const catchNoLeak = Object.values(forcedCatch.leaked).every((value) => !value);
const escapedTextSafe = successCacheControl.includes('password=<redacted>') &&
  successCacheControl.includes('cookie=<redacted>') &&
  catchError.includes('password=<redacted>') &&
  catchError.includes('cookie=<redacted>');
const probeText = fs.readFileSync(probePath, 'utf8');
const sourceGuards = probeText.includes('registeredPattern') &&
  probeText.includes("sort((left, right) => right.length - left.length)") &&
  probeText.includes('/\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b/g') &&
  probeText.includes('(?:\\\\[\\s\\S][^"\\\\]*)*');
const equalityAndSuffixFixture = fs.readFileSync(preloadPath, 'utf8').includes('TOKEN=${secrets.accessToken}') &&
  fs.readFileSync(preloadPath, 'utf8').includes('cookieSuffix') &&
  fs.readFileSync(preloadPath, 'utf8').includes('SUFFIX=${secrets.cookieSuffix}');

const evidence = {
  schema: 'rct.student-requests-h68-probe-secrets-correction.v1',
  probePath,
  probeSha256: sha256(probePath),
  preloadPath,
  preloadSha256: sha256(preloadPath),
  command: 'node --require probe-secrets-process-prefixed-preload.cjs probe.mjs --origin https://synthetic.invalid --ca probe.mjs --login synthetic-login --password abc --i2-only',
  controlledRuntime: 'Synthetic https.Agent/request; no network, ports, Docker, credentials or keys.',
  success: { exitCode: success.exitCode, parsed: success.parsed, parseError: success.parseError, leaked: success.leaked },
  forcedTopCatch: { exitCode: forcedCatch.exitCode, parsed: forcedCatch.parsed, parseError: forcedCatch.parseError, leaked: forcedCatch.leaked },
  checks: {
    successExit0: success.exitCode === 0,
    successJson: success.parsed?.status === 'PASS' && success.parseError === null,
    successNoOpaqueOrOverlapLeak: successNoLeak,
    successEscapedQuotedValuesSafe: escapedTextSafe,
    catchExit1: forcedCatch.exitCode === 1,
    catchJson: forcedCatch.parsed?.status === 'FAIL' && forcedCatch.parseError === null,
    catchNoOpaqueOrOverlapLeak: catchNoLeak,
    sourceCollisionAndEscapedGuards: sourceGuards,
    equalityAndSuffixFixture
  },
  limitations: ['Synthetic runtime proves actual success and top-level catch emitters; it does not claim a full Docker runtime.']
};
fs.writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');

if (!evidence.checks.successExit0 || !evidence.checks.successJson || !evidence.checks.successNoOpaqueOrOverlapLeak ||
    !evidence.checks.successEscapedQuotedValuesSafe || !evidence.checks.catchExit1 || !evidence.checks.catchJson ||
    !evidence.checks.catchNoOpaqueOrOverlapLeak || !evidence.checks.sourceCollisionAndEscapedGuards || !evidence.checks.equalityAndSuffixFixture) {
  throw new Error(`H68 probe secret correction failed; evidence=${evidencePath}`);
}
process.stdout.write(`H68 probe secret correction: PASS (escaped quoted values and overlapping registry secrets; evidence ${evidencePath})\n`);
