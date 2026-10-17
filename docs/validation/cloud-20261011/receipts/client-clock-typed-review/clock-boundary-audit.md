# Typed Clock boundary and oracle audit

Frozen source: `6e2376c39fa733a0e645d5d929f132a6448a953d`; previous root input `2f5f6e327f346c0656eb8ce63901c55bb65cce1f`. Requested reviewer model/effort Sol6.1/xhigh; actual metadata UNKNOWN. Source/evidence read only, no runtime.

The four service main trees still have 58 files mentioning Clock, not 58 independent injections. The current inventory records source lines and SHA256 for each; eight classes explicitly select ObjectProvider<EducationalTime>. Other ordinary educational consumers retain real Clock. This narrows the former global behavior: homework lifecycle, semester/group jobs, reminder/report/teacher views and non-enrolled schedule writers are outside this finite lane. No attempt was made to promote them to shifted time or full coverage.

## Concrete partition

| Consumer | Educational time | Real time / disposition |
|---|---|---|
| Academic StudentProjectionScopeService | serverDate/rank-date calculation | durable session refreshExpiresAt admission; JDBC session query has no independent SQL NOW guard, so the separate real field matters |
| Attendance StudentCheckinService | eligibility window, acceptedAt, cooldown, receipt serverNow | geofence refresh remains separate Instant.now; JWT/authority admission unchanged |
| Attendance StudentAttendanceSnapshotService | snapshot serverNow, allowed window, retryAt | no operational attachment expiry rewritten here |
| Attendance MarkingService | manual marking lesson-date/window and pair timestamps | attachment replace/download/availability delegated to separate unchanged real AttendanceAttachmentService |
| Attendance StudentRequestService | request windows, budgets, history, domain lock/write timestamps | owner/bot attachment download; retention selection and transactional recheck; cleanup lock timestamps, expiredAt and byte clearing; uploadedAt + UTC one-year expiresAt; dynamic attachment descriptor availability |
| Schedule LessonStatusTransitionJob | actual scheduler body activation/closure from Moscow educational instant | Spring timer and SQL/ShedLock infrastructure stay real; publication/archive fences unchanged |
| BFF StudentQueryService | student day/serverNow and educational projections | signed claims/role guards and reviewed enum mapping methods identical to imported pretyped security source |
| BFF MobileProblemHandler | domain Retry-After from retryAt | problem.timestamp and MobileIdentityFilter diagnostic timestamps remain real |

All four existing Clock factory bodies are independently byte-equal to pretyped b3b0f319. New default wrapper beans use those clocks. Typed initializer registers no Clock bean. The ObjectProvider fallback wraps the already injected real/default Clock in focused Spring contexts with no EducationalTime bean; existing direct Clock constructors retain their old behavior. No reflection, stack-sensitive routing, security expiry disable or production fixture dependency was found.

Critical old originals were reopened via Git: 049ab primary Clock registration, real session consumer, old shallow scanner and fixed-time-only replay. Critical current source was read at declarations, constructors and clock/expiry call sites. Source inventory and hashes support the partition; they are not a claim to have semantically reviewed every unrelated method of all 58 files.

## Prepared controls

Session tests exercise actual Spring annotated constructor selection with one real Clock and two typed wrappers, current authority snapshots and actual resolver. Live/expired real time crosses fixed refresh expiry, teaching time has past/future values. CTR01 invalidates one positive past-date history fixture; the repair must keep it a positive authority/history case. Future live and past expired are relevant wrong-clock counterexamples after repair.

Retention tests use the actual StudentRequestService with controlled authority/storage/transaction ports. Retained bytes remain downloadable despite future educational time; real-expired bytes produce GONE even under earlier teaching time. Retention query cutoff is checked against real time, and actual cleanup changes state, expiredAt and clears bytes only for real-expired rows. These are meaningful source oracles, not a Mongo/DB transaction acceptance claim. Source also confirms both excuse and journal cleanup branches, request upload timestamps and descriptor logic use retentionClock. Their consumer runtime remains NOT_RUN.

BFF test checks 45-second Retry-After calculated from educational now, actual real diagnostic timestamp and unchanged domain retryAt. It would reject use of the wrong clock in either mixed field. It is NOT_RUN.

## Browser Date and reachable finite lane

Actual PWA auth.ts/auth-client.ts, shared session-owner.ts and TMA tma-session.ts were searched for Date/exp/JWT decoding/expiry heuristics and relevant token/refresh paths read. There is no Date-based local token expiry comparison in these four paths. expiresIn is adapted/returned metadata; refresh/re-admission uses server response and generation checks. Source hashes independently match the author's four-path audit.

setFixedTime is applied only in the dedicated spec before login and leaves timers running. Ordinary config has teachingInstant undefined, causing the dataset's default real Date to remain active. Typed server date/window and browser Date align at Moscow weekday 09:00. Dataset creates lesson number 1 (canonical 08:30–09:50), through actual publication/adoption/one-off APIs; scheduler polls actual ACTIVE. OneOffLessonCoordinator creation validates semester/assignment date, not a conflicting unshifted current-hour guard. Non-enrolled creator clocks govern ordinary timestamp fields; the enrolled status job supplies the activation instant. No source counterexample making the fresh allowed browser action unreachable was found; actual startup/admission still requires runtime proof.

Fresh positive geo does not require countdown progression. TodayScreen countdown uses Date.now and therefore this frozen-Date lane must not be generalized to cooldown-progress or expiry-sensitive UI. Attachment previews/download UI, password recovery reset countdown, archive/report proof, notification throttle and assistant cache Date consumers are outside the claimed finite geo scenario. Real authentication age and ordinary real-mobile expiry checks must remain enabled.

The dedicated config preserves safe reporter, trace/video/screenshots off and a single synthetic dataset owner. The inherited dataset checks artifact/privacy and explicit synthetic loopback target before request-context/login/seed creation. Existing ordinary config/privacy paths are preserved. No private credentials/logs, browser or app were used by reviewer.

## Evidence limits

Existing six ZIP controls and their old-scanner red demonstrate scanner sensitivity only. Older external Java/GenericContext/dry-run graph receipts are 049ab. Current wrapper and three new consumer tests were only syntax-parsed by author; NOT_COMPILED/NOT_RUN. Corrected source introduces compileOnly shared-web dependency for the external fixture and control runtime dependency, with no reverse production dependency or source-set output inclusion. Actual current ordinary build graph, four Boot JARs, new external JAR, application injection and browser remain pending.

Shared security/retention critical files and StudentQueryService methods from session() to end were independently compared with imported reviewed security bytes. Reviewed source-equivalence does not repeat or replace the separate security package review or expand grants/API/proto.
