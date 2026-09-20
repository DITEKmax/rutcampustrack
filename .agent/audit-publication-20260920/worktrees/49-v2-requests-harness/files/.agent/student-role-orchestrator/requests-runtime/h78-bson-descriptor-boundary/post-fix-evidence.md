# H78 post-fix evidence

## Correction

The emitted Mongo JavaScript now has a local exact-integer boundary. It accepts
a safe JavaScript integer, or a native BSON Long whose decimal text and
toNumber() value round-trip exactly through BigInt. It rejects non-Long objects,
strings, null, fractions, negative sizes, unsafe numbers, invalid Long text and
unsafe Long values. The helper is used independently for stored Mongo
request_attachments sizes and for sizes in the already-JSON outbox payload.

The complete emitted descriptor mapping preserves:

- string ID and request_id;
- name, content type and lowercase SHA-256 comparison;
- ACTIVE attachment state and lowercase persisted submitted ticket status;
- uploaded/expires UTC timestamps and nullable expired_at;
- exact non-negative size.

The existing PowerShell assertion remains strict and compares API camelCase,
Mongo snake_case and outbox JSON shapes separately. No global numeric
normalization or string acceptance was introduced.

## Targeted evidence

The existing R8 pure check now executes the current emitted query against a
native-Long-shaped companion fixture, keeps outbox values as JSON numbers,
checks every descriptor/envelope field, feeds the current Node API self-test
fixture into Assert-I1MongoDelta, and verifies wrong/unsafe/invalid values and
shape aliases are rejected. It exits 0.

The root-owned native proof
.agent/orchestration-v2/evidence/h80-native-boundary.ps1 is the required
one-time mongosh check using actual NumberLong values and the current emitted
query. It is intentionally not run by this leaf. Product Docker/runtime remains
N/A for this harness-only boundary correction.

## Honest status

Source-derived product mappings and root's native sample establish that BSON
Long is expected persistence behavior. Local generated-query and assertion
evidence is PASS_TARGETED; the final native mongosh evidence and acceptance
decision remain root-owned and pending.
