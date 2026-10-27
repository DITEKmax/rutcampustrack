import { localSpecialLane, type LocalSpecialLane } from './local-special';
import { createHash, randomUUID } from 'node:crypto';
import {
  closeSync,
  constants,
  fchmodSync,
  fstatSync,
  fsyncSync,
  lstatSync,
  openSync,
  readFileSync,
  realpathSync,
  readSync,
  renameSync,
  statSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import { isAbsolute, join } from 'node:path';

/** This file is the same read-only mount used by four isolated educational services. */
export function readTeachingInstant(directory: string | undefined): Date {
  if (process.env.RCT_E2E_SYNTHETIC_SEED !== 'true' || !directory || !isAbsolute(directory)) {
    throw new Error(
      'Teaching-clock lane requires explicit synthetic seed and absolute fixture directory',
    );
  }
  const marker = join(directory, 'disposable-target');
  const file = join(directory, 'current-instant');
  if (
    statSync(marker).size > 64 ||
    readFileSync(marker, 'utf8').trim() !== 'RCT_E2E_DISPOSABLE' ||
    statSync(file).size > 128
  ) {
    throw new Error('Teaching-clock lane requires a bounded disposable fixture');
  }
  const value = readFileSync(file, 'utf8').trim();
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(value)) {
    throw new Error('Teaching-clock fixture must contain an ISO UTC instant');
  }
  const instant = new Date(value);
  if (!Number.isFinite(instant.getTime())) throw new Error('Teaching-clock instant is invalid');
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Moscow',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(instant);
  const part = (name: string) => parts.find((item) => item.type === name)?.value;
  // One-off lessons support all calendar days; only the fixed teaching time is required.
  if (part('hour') !== '09' || part('minute') !== '00') {
    // Preserve the existing closed reporter's classification key for this time guard.
    throw new Error('Positive-geo lane requires an ordinary weekday at 09:00 Europe/Moscow');
  }
  return instant;
}

/** Local post-seed config only: accept the exact successful recovery clock, never an hour override. */
export function readTeachingInstantForConfig(directory: string | undefined): Date {
  const lane = process.env.RCT_LOCAL_SPECIAL_LANE;
  const phase = process.env.RCT_LOCAL_SPECIAL_PHASE;
  function reject(): never { throw new Error('Local recovery config clock admission failed'); }
  if (process.env.RCT_LOCAL_STAGE17_MANIFEST &&
      (lane === 'application-recovery' && !['seed', 'pending', 'admit', 'attachment'].includes(phase ?? '') ||
       lane === 'semester-archive' && phase !== 'main')) reject();
  if (process.env.RCT_LOCAL_SPECIAL_DISCOVERY === 'true' && process.env.RCT_LOCAL_STAGE17_MANIFEST) {
    const first = lane === 'application-recovery' ? 'seed' : lane === 'assignment' ? 'prepare' : 'main';
    if (!lane || phase !== first || process.env.RCT_LOCAL_SPECIAL_PREPARATION === 'true') reject();
    const discovery = localSpecialLane(lane as LocalSpecialLane);
    if (!discovery || directory !== join(discovery.run, 'clock')) reject();
    return readTeachingInstant(directory);
  }
  const postSeed = lane === 'application-recovery' && ['pending', 'admit', 'attachment'].includes(phase ?? '');
  const archive = lane === 'semester-archive' && phase === 'main';
  if (!postSeed && !archive) return readTeachingInstant(directory);
  try {
    const local = localSpecialLane(archive ? 'semester-archive' : 'application-recovery');
    if (!local) return readTeachingInstant(directory);
    const uid = process.getuid?.();
    const recovery = join(local.run, 'lanes/application-recovery');
    if (uid === undefined || process.platform !== 'linux' || process.env.RCT_E2E_SYNTHETIC_SEED !== 'true' ||
        process.env.RCT_LOCAL_SPECIAL_DISCOVERY === 'true' || process.env.RCT_LOCAL_SPECIAL_PREPARATION === 'true' ||
        directory !== join(local.run, 'clock') || process.env.RCT_APP_RECOVERY_RUN !== recovery ||
        process.env.RCT_APP_RECOVERY_PROJECT !== local.recoveryProject ||
        (archive ? process.env.RCT_APP_RECOVERY_PHASE !== undefined && process.env.RCT_APP_RECOVERY_PHASE !== ''
          : process.env.RCT_APP_RECOVERY_PHASE !== phase) ||
        local.databaseProject !== (phase === 'pending' ? local.project : local.recoveryProject)) reject();
    const directoryOwned = (path: string, mode: number) => {
      const info = lstatSync(path);
      if (!info.isDirectory() || info.isSymbolicLink() || info.uid !== uid ||
          (info.mode & 0o777) !== mode || realpathSync(path) !== path) reject();
    };
    directoryOwned(local.run, 0o700);
    directoryOwned(join(local.run, 'lanes'), 0o700);
    directoryOwned(recovery, 0o700);
    directoryOwned(directory!, 0o755);
    const ownedRead = (path: string, mode: number, maximum: number): Buffer => {
      if (realpathSync(path) !== path || lstatSync(path).isSymbolicLink()) reject();
      const descriptor = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW);
      try {
        const info = fstatSync(descriptor);
        if (!info.isFile() || info.uid !== uid || info.nlink !== 1 || (info.mode & 0o777) !== mode ||
            info.size <= 0 || info.size > maximum) reject();
        const buffer = Buffer.alloc(maximum + 1);
        const count = readSync(descriptor, buffer, 0, buffer.length, 0);
        const after = fstatSync(descriptor);
        if (count !== info.size || after.dev !== info.dev || after.ino !== info.ino || after.size !== info.size ||
            after.mtimeMs !== info.mtimeMs || after.ctimeMs !== info.ctimeMs) reject();
        return buffer.subarray(0, count);
      } finally { closeSync(descriptor); }
    };
    const privateJson = (path: string, bound: number) => JSON.parse(ownedRead(path, 0o600, bound).toString('utf8'));
    const owner = privateJson(join(local.run, 'owner.json'), 8 * 1024 ** 2);
    if (!owner || owner.root !== local.root || owner.run !== local.run || owner.nonce !== local.nonce || owner.project !== local.project ||
        owner.uid !== uid || owner.mode !== 'special13' || owner.special_cohort !== 'lifecycle' ||
        process.env.RCT_LOCAL_STAGE17_MANIFEST !== join(local.run, 'owner.json') ||
        typeof owner.source_sha !== 'string' || !/^[a-f0-9]{40}$/.test(owner.source_sha)) reject();
    const passed = (selected: string) => {
      const receipt = privateJson(join(recovery, `${selected}.result.safe.json`), 4096);
      if (!receipt || Object.keys(receipt).length !== 7 ||
          !['lane', 'phase', 'status', 'expected', 'passed', 'failed', 'notRun'].every(key => Object.hasOwn(receipt, key)) ||
          receipt.lane !== 'application-recovery' || receipt.phase !== selected || receipt.status !== 'PASS' ||
          receipt.expected !== 1 || receipt.passed !== 1 || receipt.failed !== 0 || receipt.notRun !== 0) reject();
    };
    passed('seed');
    if (archive) {
      for (const selected of ['pending', 'admit', 'attachment']) passed(selected);
      const verified = privateJson(join(recovery, 'verified.private.json'), 65536);
      if (!verified || verified.result !== 'PASS' || verified.target_project !== local.recoveryProject) reject();
    }
    if (!ownedRead(join(recovery, 'disposable-target'), 0o600, 64).equals(Buffer.from('RCT_APP_RECOVERY_DISPOSABLE\n'))) reject();
    const input = privateJson(join(recovery, 'lifecycle-input.private.json'), 4096);
    if (!input || Object.keys(input).length !== 4 ||
        !['lessonId', 'semesterId', 'expectedInstant', 'clockSha256'].every(key => Object.hasOwn(input, key)) ||
        !Number.isSafeInteger(input.lessonId) || input.lessonId <= 0 || !Number.isSafeInteger(input.semesterId) || input.semesterId <= 0 ||
        typeof input.expectedInstant !== 'string' || !/^\d{4}-\d{2}-\d{2}T09:00:00\.000Z$/.test(input.expectedInstant) ||
        typeof input.clockSha256 !== 'string' || !/^[a-f0-9]{64}$/.test(input.clockSha256)) reject();
    const instant = new Date(input.expectedInstant);
    if (!Number.isFinite(instant.getTime()) || instant.toISOString() !== input.expectedInstant) reject();
    const seed = privateJson(join(recovery, 'seed.json'), 65536);
    if (!seed || seed.historyLessonId !== input.lessonId ||
        seed.historyUrl !== `/api/v1/student/attendance?semesterId=${input.semesterId}`) reject();
    if (!ownedRead(join(directory!, 'disposable-target'), 0o644, 64).equals(Buffer.from('RCT_E2E_DISPOSABLE\n'))) reject();
    const clock = ownedRead(join(directory!, 'current-instant'), 0o644, 128);
    if (!clock.equals(Buffer.from(`${input.expectedInstant}\n`)) ||
        createHash('sha256').update(clock).digest('hex') !== input.clockSha256) reject();
    return instant;
  } catch { return reject(); }
}

/** Last educational lane only: retain the advanced clock until the owned services stop. */
export function advanceStudentQuickRequestClock(
  directory: string | undefined,
  admittedInstant: Date,
): Date {
  let temporary: string | undefined;
  let temporaryIdentity: { dev: number; ino: number } | undefined;
  function reject(): never {
    throw new Error('Student quick-request clock admission failed');
  }
  try {
    const local = localSpecialLane('today');
    const runner = process.env.RUNNER_TEMP;
    const run = process.env.GITHUB_RUN_ID;
    const attempt = process.env.GITHUB_RUN_ATTEMPT;
    const uid = process.getuid?.();
    if (
      (!local && process.env.CI !== 'true') ||
      process.env.RCT_E2E_SYNTHETIC_SEED !== 'true' ||
      (!local && (!runner || !isAbsolute(runner) || !run || !/^[1-9][0-9]*$/.test(run) || !attempt || !/^[1-9][0-9]*$/.test(attempt))) ||
      uid === undefined ||
      !directory ||
      directory !== (local ? join(local.run, 'clock') : join(runner!, `rct-first3-clock-${run}-${attempt}`))
    )
      reject();
    const ownedDirectory = lstatSync(directory);
    if (
      !ownedDirectory.isDirectory() ||
      ownedDirectory.isSymbolicLink() ||
      ownedDirectory.uid !== uid ||
      (ownedDirectory.mode & 0o777) !== 0o755
    )
      reject();
    const readOwned = (file: string, limit: number) => {
      const descriptor = openSync(file, constants.O_RDONLY | constants.O_NOFOLLOW);
      try {
        const info = fstatSync(descriptor);
        if (
          !info.isFile() ||
          info.uid !== uid ||
          (info.mode & 0o777) !== 0o644 ||
          info.size <= 0 ||
          info.size > limit
        )
          reject();
        const buffer = Buffer.alloc(limit + 1);
        const count = readSync(descriptor, buffer, 0, buffer.length, 0);
        if (count !== info.size) reject();
        return { bytes: buffer.subarray(0, count), dev: info.dev, ino: info.ino };
      } finally {
        closeSync(descriptor);
      }
    };
    const marker = readOwned(join(directory, 'disposable-target'), 64);
    if (!marker.bytes.equals(Buffer.from('RCT_E2E_DISPOSABLE\n'))) reject();
    const clock = join(directory, 'current-instant');
    const original = readOwned(clock, 128);
    const admitted = admittedInstant.toISOString();
    if (!/^\d{4}-\d{2}-\d{2}T06:00:00\.000Z$/.test(admitted)) reject();
    if (!original.bytes.equals(Buffer.from(`${admitted}\n`))) reject();
    const advanced = new Date(admittedInstant.getTime() + 3600000);
    const moscowDay = (instant: Date) =>
      new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Moscow' }).format(instant);
    if (
      advanced.getTime() <= admittedInstant.getTime() ||
      moscowDay(advanced) !== moscowDay(admittedInstant) ||
      !advanced.toISOString().endsWith('T07:00:00.000Z')
    )
      reject();
    const updated = Buffer.from(`${advanced.toISOString()}\n`);
    temporary = join(directory, `student-quick-${randomUUID()}`);
    const descriptor = openSync(
      temporary,
      constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW,
      0o644,
    );
    try {
      const info = fstatSync(descriptor);
      temporaryIdentity = { dev: info.dev, ino: info.ino };
      fchmodSync(descriptor, 0o644);
      writeFileSync(descriptor, updated);
      fsyncSync(descriptor);
    } finally {
      closeSync(descriptor);
    }
    const publishedTemporary = readOwned(temporary, 128);
    if (
      publishedTemporary.dev !== temporaryIdentity.dev ||
      publishedTemporary.ino !== temporaryIdentity.ino ||
      !publishedTemporary.bytes.equals(updated)
    )
      reject();
    const currentDirectory = lstatSync(directory);
    const current = readOwned(clock, 128);
    const currentMarker = readOwned(join(directory, 'disposable-target'), 64);
    if (
      !currentDirectory.isDirectory() ||
      currentDirectory.isSymbolicLink() ||
      currentDirectory.uid !== uid ||
      (currentDirectory.mode & 0o777) !== 0o755 ||
      currentDirectory.dev !== ownedDirectory.dev ||
      currentDirectory.ino !== ownedDirectory.ino ||
      !currentMarker.bytes.equals(marker.bytes) ||
      currentMarker.dev !== marker.dev ||
      currentMarker.ino !== marker.ino ||
      !current.bytes.equals(original.bytes) ||
      current.dev !== original.dev ||
      current.ino !== original.ino
    )
      reject();
    renameSync(temporary, clock);
    const published = readOwned(clock, 128);
    if (
      published.dev !== temporaryIdentity.dev ||
      published.ino !== temporaryIdentity.ino ||
      !published.bytes.equals(updated)
    )
      reject();
    return advanced;
  } catch {
    return reject();
  } finally {
    if (temporary && temporaryIdentity) {
      try {
        const info = lstatSync(temporary);
        if (info.dev === temporaryIdentity.dev && info.ino === temporaryIdentity.ino)
          unlinkSync(temporary);
      } catch {
        // The successful atomic rename already removed the owned temporary path.
      }
    }
  }
}
