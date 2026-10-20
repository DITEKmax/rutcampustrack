/** Browser-free whole-byte privacy controls; native table/DOM semantics remain a live-browser proof. */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { requireOrImport } = require(
  join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'),
);
const { registryRawPrivacyBoundary } = await requireOrImport(
  join(dirname(fileURLToPath(import.meta.url)), 'registry-html-boundary.ts'),
);
const input = (content) => ({ content, forbiddenValues: ['synthetic-secret'] });
assert.equal(registryRawPrivacyBoundary(input('<table title="public">A &amp; B</table>')), true);
let rejected = 0;
for (const content of [
  '<table title="public" title="synthetic&#45;secret">safe</table>',
  '<table title="public" title="synthetic&#x2d;secret">safe</table>',
  '<!-- synthetic&#45;secret -->',
  '<table data-safe="synthetic&amp;#45;secret">safe</table>',
  '<table title="&#112;assword">safe</table>',
  '<table>synthetic-secret</table>',
]) {
  assert.equal(registryRawPrivacyBoundary(input(content)), false);
  rejected++;
}
console.log(JSON.stringify({ state: 'PASS', wholeBytePrivacy: true, positive: 1, rejected,
  nativeDomOrBrowserProof: false }));
