# Diff r1

Dependency import:
- Replaced target StudentRequestService.java with the exact accepted P1 source bytes (7AD59234…DEBC → B9624988…3960 before feature edits); this preserves P1 attachment reconciliation.
- Replaced target StudentRequestServiceAuthorizationTest.java with exact accepted P1 source bytes (1C0E6A2C…4644 → D1DDCDE6…0B22); no subsequent edit.

Producer chain:
- proto/attendance.proto:316-320 adds only bool comment_required = 3 to StudentRequestReasonOption.
- StudentRequestModels.java:157 adds primitive commentRequired to domain ReasonOption.
- StudentRequestService.java:979 emits the domain boolean; :1563 uses the shared helper for validation; :1619 defines the helper as OTHER.
- StudentRequestGrpcMapper.java:36-38 explicitly sets the proto boolean.
- StudentRequestApiModels.java:127-128 marks commentRequired required and exposes the primitive field.
- StudentRequestFacade.java:217 maps getCommentRequired() directly.

Tests:
- StudentRequestDomainIT.java:617 proves domain true/false and strict OTHER comment validation/acceptance.
- New StudentRequestGrpcMapperTest.java proves both proto values.
- New StudentRequestFacadeOptionsTest.java proves direct BFF facade mapping.
- New StudentRequestOptionsJsonTest.java proves required boolean JSON serialization.

No generated Java, OpenAPI, TypeScript, client, UI, config, lockfile, authz, enum, bot or historical manifest was edited by this leaf. Shared checkout had foreign pre-existing changes; this manifest only claims the listed owned paths. No commit or merge was created.
