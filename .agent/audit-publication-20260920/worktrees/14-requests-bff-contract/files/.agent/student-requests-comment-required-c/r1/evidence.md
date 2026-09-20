# Evidence r1

## Baseline and dependency import

- Frozen contract: comment-required-producer-chain-2026-09-09.md, SHA-256 51A1760E1EE4A14BC9622BE5A8351F1BFD1268DB6F70056BAE8D0C8070372A08.
- Full adjacent manifest SHA-256 A190D4C62F763E60E8BF0326BB7444D3EC161ACBFDB01198FDEB29724D7EC1F6.
- Target detached revision before work: d3c31acb8cce53791a4981e5858a37d44fdc9a0e.
- Eight implementation baselines matched exactly before implementation: proto 4108E3F9…B08F8, models DEFCA9A5…B25C2, service target pre 7AD59234…DEBC, mapper E7737B9C…0244F, BFF API 651B83E0…8286, facade D15D5EC9…B968, DomainIT 968F4A49…0DE6, authorization target pre 1C0E6A2C…4644.
- All three required-new paths were absent before write.
- Source GO was granted. Source service pre hash B962498813AD3CC4E77EDBFE81E5E82E3013CFE1CF7B0490762880D23FCD3960; source authorization test pre hash D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22.
- Exact two-file import completed. Target post-import hashes matched source: service B962498813AD3CC4E77EDBFE81E5E82E3013CFE1CF7B0490762880D23FCD3960; authorization test D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22. Authorization test stayed byte-exact after implementation.

## Behavioral evidence in source

- Domain ReasonOption now carries primitive commentRequired; the StudentRequestService.commentRequired(ExcuseType) helper is used by both options construction and OTHER submission validation.
- Proto StudentRequestReasonOption has exactly bool comment_required = 3; no other proto field was added.
- Attendance mapper sets .setCommentRequired(item.commentRequired()).
- BFF DTO declares commentRequired in requiredProperties and as a primitive boolean.
- Facade copies item.getCommentRequired() directly.
- Domain test covers OTHER=true, all current non-OTHER reasons false, null/empty/whitespace rejection, and accepted nonblank OTHER.
- New mapper, facade and Jackson tests cover true and false values.

## Immutable prior evidence and supersession

Accepted P1 final manifest SHA-256 6D822BB002AA0AF2FE768855FA8EA2CA2986DE379A1D3424029B18662EFD9DEE and accepted P2 exact-five manifest SHA-256 61C14D95E6E8EA61BCE791EC648218C39FE9771E62E355605A2BAAF313722B97 remain immutable. This r1 manifest explicitly supersedes only the affected path hashes listed in changed-path-manifest.json; historical files were not edited. The UI R4 review SHA remains 3C4D553047A877114393EEABD32E15D35DEBAD1D66F2C22075ADB8384B3F679F.

## Runtime evidence and limitations

No runtime evidence is claimed. The global heavy lease was B SQL7 active, so Gradle, generateProto, compilation, unit/integration tests, Docker/Testcontainers and live HTTP/gRPC were not run. The three exact contract commands are recorded as NOT RUN in checks.md for root to execute after a separate runtime GO. OpenAPI, generated Java, generated TypeScript and client union integration remain deferred to their single integration writer. Fresh independent Sol/high review remains pending after stable runtime.

Foreign pre-existing changes, including notification-bot files, were preserved. No Terra escalation was requested; no defect or complexity gate exists.
