# H71 diagnosis before source edit

Recorded before changing `runner.ps1` or `r8-mongo-projection-check.ps1`.

- Revision: `73fd5f27ceb429ad0692b073189f8a527c550830` (`codex/v2-requests-harness` worktree).
- Accepted runner hash from H71 context: `05C76B696BF987370C0597C3AF76C9DB94FB588AA3AC3087D6907110EBBB9716`.
- Rules hash: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Original H71 report: `.agent/orchestration-v2/evidence/h71-report.json`, run `20260920-085506935-kjaskyqr`, union `426a15b6b42e816deaa3ca5c50437e0964aaf85e`.
- Source-derived model: `ExcuseTicket.status` defaults to `ExcuseTicketStatus.SUBMITTED`; registered `MongoConvertersConfig.ExcuseTicketStatusWriter` returns `source.name().toLowerCase()`.
- Harness projection: `Get-MongoSnapshot` selects raw `x.status`; `Assert-I1MongoDelta` compared it strictly to uppercase `SUBMITTED`.

## Reproduction

Command: in-memory PowerShell AST extraction of the actual `Get-MongoSnapshot` and `Assert-I1MongoDelta` definitions, followed by Node execution of the emitted Mongo JavaScript against collection/ObjectId stubs. The ticket fixture applied the registered writer model (`SUBMITTED` -> `submitted`) before the emitted snapshot was passed into the production assertion. No Docker, service, network, source edit, or product runtime was used.

Exit code: `0` for the diagnostic command; the expected assertion failure was captured as data.

Observed result:

```json
{"observedDatabase":"attendance_db","observedRawStatus":"submitted","statusProjection":true,"registeredWriterModel":"ExcuseTicketStatusWriter: source.name().toLowerCase()","assertionState":"FAIL_EXPECTED","assertionMessage":"I1 persisted request ticket must retain the SUBMITTED database state (API maps it to PENDING)","requestId":"abcdef0123456789abcdef01"}
```

The first exact mismatch is therefore `rawx.status = "submitted"` versus the harness literal `"SUBMITTED"`. This is a harness expectation defect, not a demonstrated product persistence defect: the report did not save the post-I1 snapshot value, while the source converter and emitted projection establish the storage representation.

## Minimal repair gate

Change only the H71 raw Mongo assertion and its model-correct pure regression fixture to expect strict lowercase `submitted`. Keep `PENDING` as the public API mapping and `SUBMITTED` as the domain enum. Keep the negative persisted `approved` state rejected. Do not normalize, case-fold, or change `RequestAttachmentDocument.state`; `AttachmentState` has no registered converter in `MongoConvertersConfig` and is outside this mismatch.
