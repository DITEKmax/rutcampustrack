Goal: safely identify the Academic call and gRPC status behind the observed PWA bootstrap503 on the next exact request; this diagnostic patch does not claim to have resolved its underlying cause.

Context/evidence: root observed first student login on run20261002-203457516-sgjgvbyb returning Academic dependency503. Existing error mapper discards underlying code. StudentQueryService.session63–65 invokes user→group→activeSemester in order. Critical MobileAcademicClient/StudentQueryService content at author HEAD d560540e matched product freeze d1745515 (scoped diff empty); no explicit source defect identified. Canonical main RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA read/verified. Foreign WT rules/packet/WIP preserved.

Relevant scope / inventory: changed only services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAcademicClient.java in reused pwa-install-delivery-20260923 worktree. Author product commit3ec63094; root sole main integrator.

Required behavior: every existing call has a static operation literal. StatusRuntimeException logs only operation and Status.Code enum. Generic Exception logs operation and fixed NON_GRPC marker. No exception description/message/class/cause/stack trace, identifiers, tokens, JWT, request or user payload.

Constraints: existing HTTP status/ProblemCode/message branches unchanged, including NOT_FOUND override arguments and MobileBffException pass-through. Auth attachment,3-second deadlines, endpoints/stubs and payload construction unchanged. No external diagnostic service or client response details.

Existing patterns: SLF4J Logger/LoggerFactory and parameterized warn used by existing service clients. Local helper carries literal operation into both overloads; labels user/group/activeSemester distinguish bootstrap stages, other existing callers carry separate literals.

Acceptance criteria: source review confirms ten public outbound call paths carry static labels and logged values are only literals/status enum. Existing user-facing error contract preserved. Actual next-request log must be accepted by root; no runtime result yet.

Verification: scoped critical baseline diff empty exit0; scoped product diff check exit0 (a799d8); exact index one file and product commit exit0 (c7919b). No Gradle/tests/runtime launched; root plans bounded mobile-bff bootJar on shared holder and reuse of other artifacts. No logger wiring tests needed. source.diff is exact product commit diff.

Do not: children/Terra/main edits/runtime/deploy/push/auth weakening/deadline changes/full suite/generation/new framework, foreign reset/stash/clean. Scope expansion only after concrete evidence and root approval.
