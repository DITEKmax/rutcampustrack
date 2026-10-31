'use strict';
// Filesystem-only prepared-directory/WX control. No runtime/browser/API import.
const fs = require('node:fs'), path = require('node:path'), os = require('node:os'), assert = require('node:assert/strict');
const source = path.join(__dirname, 'runtime');
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'pr3-prepared-private-'));
try {
  fs.writeFileSync(path.join(temporary, 'native-cohort.cjs'), fs.readFileSync(path.join(source, 'native-cohort.cjs')));
  fs.writeFileSync(path.join(temporary, 'settings.json'), JSON.stringify({ repo: temporary }));
  fs.writeFileSync(path.join(temporary, 'PACKAGE-SHA256'), '0'.repeat(64));
  const directory = path.join(temporary, 'private'); fs.mkdirSync(directory, { mode: 0o700 });
  const { admitPreparedPrivate } = require(path.join(source, 'prepared-private.cjs'));
  const { durableWrite } = require(path.join(temporary, 'native-cohort.cjs'));
  admitPreparedPrivate(directory);
  const intent = path.join(directory, 'intent.json'); durableWrite(intent, { state: 'ORIGINAL' }, true);
  assert.throws(() => durableWrite(intent, { state: 'RETRY' }, true), { code: 'EEXIST' });
  assert.equal(JSON.parse(fs.readFileSync(intent)).state, 'ORIGINAL');
  const alias = path.join(temporary, 'alias'); fs.symlinkSync(directory, alias, 'dir');
  assert.throws(() => admitPreparedPrivate(alias));
  fs.chmodSync(directory, 0o755); assert.throws(() => admitPreparedPrivate(directory)); fs.chmodSync(directory, 0o700);
  console.log('prepared-directory/exclusive-original-intent/symlink/permissions controls PASS; runtime actions0');
} finally { fs.rmSync(temporary, { recursive: true, force: true }); }
