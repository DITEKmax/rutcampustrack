# H78 summary

Status: PASS_TARGETED / NATIVE_PENDING_ROOT.

The bounded correction is in the requests harness only. Get-MongoSnapshot now
converts persisted attachment BSON Long sizes to JSON numbers only after a safe
exact BigInt round-trip, and applies the same strict boundary to sizes inside
already-JSON outbox payloads. Wrong types, unsafe values, invalid Long text,
fractions and negative sizes fail closed. The existing strict PowerShell JSON
integer assertion and complete API/Mongo/outbox descriptor comparison remain
in force.

The R8 pure check passes with exit 0 and covers the current generated query,
native-Long-shaped Mongo descriptors, JSON-number outbox descriptors, all
descriptor/envelope fields, API PENDING plus ACTIVE mapping, lowercase
persisted submitted status, and negative boundary/alias/expiry/content cases.
The runner parser, probe syntax and scoped diff check also exit 0.

Root supplied and preserved the actual native BSON sample: pinned Mongo 7
mongosh --nodb, no network, exit 0, Long(10485760) exact safe value and unsafe
9007199254740993 control. Root owns the single H80 native proof against the
frozen current source. Product Docker/full runtime is N/A for this
harness-only change.

Current frozen hashes:

- runner.ps1: DB4BE98017B14840A30E4CEBEDF3992B58561B7827ABB6B7EAD95A4385BE4BFD
- r8-mongo-projection-check.ps1:
  46AB5ECC5BAC08B1293B0EDCF23BE627DAB486E9929C9B9B58B30A56E9D34C23
- probe.mjs:
  1093B32778D8A7AF7CBC3E8F691073597257EB3091AC6A51DF8B12DE8E8731D6

Foreign dirty work in the shared worktree was preserved. No commit, push,
deploy, product edit, Terra escalation or child agent was used.
