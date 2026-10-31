'use strict';
const fs = require('node:fs'), assert = require('node:assert/strict');
function admitPreparedPrivate(directory) {
  const info = fs.lstatSync(directory);
  assert(info.isDirectory() && !info.isSymbolicLink(), 'Expected prepared private directory');
  assert.equal(fs.realpathSync(directory), directory, 'Private path must remain exact');
  assert.equal(info.mode & 0o777, 0o700, 'Prepared private permissions changed');
  assert.equal(info.uid, process.getuid(), 'Prepared private owner changed');
}
module.exports = { admitPreparedPrivate };
