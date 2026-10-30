/** Preserve exact shortcut target/authority query predicates without product or browser execution. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';

const source = readFileSync(new URL('../specs/mobile-real-server.spec.ts', import.meta.url), 'utf8');
const start = source.indexOf('const shortcutQueryMatches = ');
const end = source.indexOf('\n      };', start);
assert.ok(start > 0 && end > start, 'Use the current exact query predicate');
const declaration = stripTypeScriptTypes(source.slice(start, end + 9), { mode: 'transform' });
const facts = {};
const match = new Function('dataset', 'shortcutSubjectId', 'shortcutFacts', `${declaration}\nreturn shortcutQueryMatches;`)(
  { semester: { id: 17 }, group: { id: 23 } }, 31, facts,
);
const query = { semesterId: '17', groupId: '23', subjectId: '31', lessonType: 'LECTURE', pastOnly: 'true', page: '0', pageSize: '100' };
const makeUrl = values => new URL(`https://control.invalid/api/v1/teacher/journal?${new URLSearchParams(values)}`);
assert.equal(match(makeUrl(query)), true);
assert.equal(match(makeUrl({ ...query, lessonType: 'lecture' })), true, 'Existing case-insensitive lesson type contract');
let negatives = 0;
for (const [key, field] of Object.entries({
  semesterId: 'semesterMatches', groupId: 'groupMatches', subjectId: 'subjectMatches',
  lessonType: 'lessonTypeMatches', pastOnly: 'pastOnlyMatches', page: 'pageMatches', pageSize: 'pageSizeMatches',
})) {
  assert.equal(match(makeUrl({ ...query, [key]: 'wrong' })), false);
  assert.equal(facts[field], false);
  const absent = { ...query }; delete absent[key];
  assert.equal(match(makeUrl(absent)), false);
  assert.equal(facts[field], false);
  negatives += 2;
}
const duplicate = makeUrl(query);
duplicate.searchParams.append('lessonType', 'LECTURE');
assert.equal(match(duplicate), false);
assert.equal(Object.values(facts).every(value => typeof value === 'boolean'), true);
console.log(JSON.stringify({ state: 'PASS', equivalentValidQueries: 2, negativesRejected: negatives + 1, exactTargetAndQueryRetained: true, browserServerNetwork: false, historicalCause: 'UNKNOWN' }));
