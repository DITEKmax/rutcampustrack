'use strict';

import fs from 'node:fs';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const mode = process.argv[2] ?? 'both';
const size = Number(process.argv[3] ?? '1048576');
const markerPath = process.argv[4];
const descendantMarkerPath = process.argv[5];
const stdoutByte = mode === 'stderr' ? 0x53 : 0x4f;
const stderrByte = mode === 'stdout' ? 0x54 : 0x45;

if (mode === 'timeout') {
  if (markerPath) fs.writeFileSync(markerPath, String(process.pid), 'utf8');
  process.stdout.write('timeout-child-started');
  process.stderr.write('timeout-child-diagnostic');
  setInterval(() => {}, 1000);
} else if (mode === 'timeout-tree') {
  if (markerPath) fs.writeFileSync(markerPath, String(process.pid), 'utf8');
  const descendant = spawn(process.execPath, [fileURLToPath(import.meta.url), 'grandchild', '0', descendantMarkerPath], { stdio: 'inherit', windowsHide: true });
  if (descendantMarkerPath) fs.writeFileSync(descendantMarkerPath, String(descendant.pid), 'utf8');
  process.stdout.write('timeout-tree-parent-started');
  process.stderr.write('timeout-tree-parent-diagnostic');
  setInterval(() => {}, 1000);
} else if (mode === 'grandchild') {
  if (markerPath) fs.writeFileSync(markerPath, String(process.pid), 'utf8');
  setInterval(() => {}, 1000);
} else {
  if (mode !== 'stderr') process.stdout.write(Buffer.alloc(size, stdoutByte));
  if (mode !== 'stdout') process.stderr.write(Buffer.alloc(size, stderrByte));
  process.exitCode = mode === 'nonzero' ? 7 : 0;
}
