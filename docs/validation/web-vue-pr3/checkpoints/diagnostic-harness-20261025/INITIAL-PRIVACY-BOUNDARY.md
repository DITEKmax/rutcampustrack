# Diagnostic harness — initial independent boundary review

Read-only preparatory review for owner comment6396083761 and DIAGNOSTIC-HARNESS-CONTRACT.md. Baseline2b remains preserved. Implementation is WIP; no final code findings or readiness verdict yet. No product/harness/test source edits, no commands that start Docker/Auth/DB/browser/E2E/build/CI/owner-local gates, no child agents.

Confirmed old hazard: onTestEnd accepts worker annotations with file/line/text-derived identity. Replacing the old source pins with a caseId field without changing that authority chain would still allow worker-supplied known-case data to be adopted. The agreed onBegin preregistration snapshot + branded actual failure/observation + private per-run worker/reporter envelope addresses the relevant boundary.

Trusted checkout and dynamic per-run source manifest are the trust base; this is not protection against arbitrary malicious repository code. Essential finite negative controls for final review:

1. Mutated/overwritten onTestEnd annotations cannot replace the onBegin registration. An unknown ID and a known catalog ID belonging to a different registered test are both refused.
2. A valid envelope cannot be reused for a foreign test/project/retry/run or a different actual throwable that happens to be sanitized to the same public generic message. No raw exception/stack, key, nonce carrier or signature is serialized into public reports/attachments/stdout.
3. A plain Error carrying known machine wording, forged JSON fields or a copied safe annotation cannot become a branded authoritative event. Only actual producer failure/observation is eligible.
4. Exact five public event fields and finite allowed case/stage/code/status combinations, ≤8 events; unknown/private/extra fields, oversized data, bool-as-number status and malformed JSON are rejected. Preserve original failure/nonzero independently of diagnostic availability.
5. Mongo/gRPC exception ambiguity, bounded log/time/byte parsing and privacy projection remain. Human text stays a reported diagnostic, not outcome authority.
6. CI admission remains real hosted context; local admission uses its own owner nonce/generated Compose/current run directory and does not impersonate hosted. Dynamic source/config/CA/Compose/build/artifact manifests, pre-operation expected digests and mutation/restoration checks retain their concrete integrity role.
7. Ubuntu/CI use the same shared implementation. Relocation/comment/blank-line controls do not repin source or introduce replacement string/anchor snapshots.

These points were sent to Root and both existing scoped authors without blocking implementation for style or proposing a new framework. Author product evidence and finite controls will be reviewed once the coherent source diff is frozen; no intermediate WIP source is declared accepted or defective merely because migration is incomplete.
