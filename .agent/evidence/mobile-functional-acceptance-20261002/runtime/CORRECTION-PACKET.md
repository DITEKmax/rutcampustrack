# Next bounded correction packet

Goal: identify the actual dependency failure behind the observed real PWA bootstrap error and restore bootstrap before accepting the three new UI flows.
Context/evidence: R1 reached READY/TLS browser success but root observed Academic Service unavailable after one synthetic student login. Raw HTTP/gRPC status remains unknown. Source d174, manifest67ccdaf; runtime reportFAIL/cleanupPASS.
Relevant scope: existing MobileAcademicClient.call/StudentQueryService bootstrap and runtime-only ability to observe its existing authorized Academic reads; a new writer must be assigned by root. Existing input/outputs are preserved; no broad Auth or event audit.
Required behavior: link one controlled bootstrap request to exact GetUserById, optional GetGroup and GetActiveSemester results/status. Preserve TLS, service secret/internal JWT, existing deadlines and current binding/session semantics. Fix only a demonstrated cause.
Constraints: no new framework/broker/tunnel campaign, auth bypass, secret dumps, full suite, source fix inferred from generic503, or runtime repetition without root GO/new evidence.
Existing patterns: MobileGrpcAuth attaches internal JWT plus service secret; prod TLS config is already packaged and mounted. Existing generated RPC clients/source contracts are available; old broad probe must not run.
Acceptance: raw cause is demonstrated, minimal correction reviewed if code/config changes, bounded bootstrap success then the three root-driven UI flows. HTTP health/READY is insufficient.
Verification: source masking at MobileAcademicClient:130–149, exact bootstrap reads StudentQueryService:63–65, startup/config evidence in bff-safe-config-r1.json and diagnostic files. New targeted raw-status reproduction only after assigned scope/resources.
Do not: label this a timeout/TLS/Auth defect without new evidence, change deadline/auth to make it pass, create missing fixture before bootstrap is healthy, or claim provider/TMA production readiness.
