import { readFileSync, statSync } from 'node:fs';
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
