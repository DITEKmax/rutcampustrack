# Конфликты источников и dependent blockers

| Status | Sources | Resolution / effect |
|---|---|---|
| Resolved | latest design job stories vs repository-history `docs/product/job-stories.md` | Registry keeps both texts separately. Latest design is the accepted-source candidate; history is not copied or silently overwritten. |
| Closed source policy | all 31 `Р-01…Р-31` owner decisions; 30 affected backend request rows | `product_decision_status=closed`; 20 revised rows have no inferred action and await scoped code comparison, while 7 accepted rows carry their cited closed-decision ID. A request-row count is not a count of decisions. No owner reapproval is required. |
| Closed source retirement policy | 3 superseded backend rows, including `JS-HEADMAN-09/10` mass-cancel | `retire-request` preserves historical evidence. It does not authorize removal of an existing server capability; consumer audit and any delete diff are next-scope work. |
| Open source request/question | 145 backend rows | `product_decision_status=open`; static code/API cells are not runtime evidence and a technical contract gate does not promote them into product decisions. |
| Technical gate | spec-first vs Java-first contract | No canonical artifact/owner/revision selected. FE/BE parallel implementation cannot start; this does not change source policy status. |
| Resolved trace map | story registry vs backend delta | One generated map accepts only same-row stable-story citations or explicitly cited R-25 decision links. Adjacent prose, role and service no longer create links. |
| Blocked | Figma packet for auth/session → Today | Node links/states/assets are absent from this preparation; stage 09 reader must obtain them. |
| Watch | 001a/001b standalone wireframe links | Source files were not provided; `001-login.md` is evidence, not a substitute decision. |

## Source drift, 06.09.2026

The previous transfer verification is retained: 2,230 manifest entries and zero hash
mismatches at its recorded snapshot. This preparation read live source for semantic
reconciliation but did not run bulk transfer or overwrite source bytes. Any future hash
drift in the four roots must be recorded as a new source-drift event; only dependents of
the changed source are blocked, and the previous manifest entry is retained for history.

Read-only recheck on 06.09.2026 PASS: 2,205 target records, 34 exclusions and 109
duplicates resolved through `retained_address` to their canonical target (with cycle
checking). Missing targets, missing sources, source drift and target-hash failures are
all zero. No source or target was overwritten and no bulk transfer was rerun.
