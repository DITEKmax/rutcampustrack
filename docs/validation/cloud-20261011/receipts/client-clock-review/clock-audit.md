# Clock boundary audit on frozen049ab4e3

All four production main-source trees were searched for Clock declarations/calls, including aliases and field injection. Inventory contains58 source files mentioning Clock, not58 independent injections. Exact paths/calls/lines and reading dispositions are in `clock-consumer-inventory.json`. Callsites were read; large unaffected domain classes were reviewed at Clock usages and critical expiry methods, not claimed full product reviews. Existing Clock injection has no Clock qualifier. MarkingService's qualifier applies to its executor only.

The external initializer registers an unqualified primary Clock at startup. This reaches every normal Clock injection in each allowed service, not only lesson-time consumers. A differently named original Clock bean remains present but is not selected by unqualified consumers.

## Contaminated boundaries — CC01

Academic StudentProjectionScopeService43/76/193 combines educational serverDate with durable session refresh expiry. Its JDBC query reads refreshExpiresAt without a SQL NOW guard; `SessionSnapshot.isLiveAt` compares the supplied instant. Preserve current live roles/version/status/group authority and real session time; excluding the whole mixed class is not an adequate correction if teaching serverDate is needed.

Attendance StudentRequestService400/435 performs authorized student/bot attachment download expiry;633 selects due retention rows;738 rechecks expiry;797 establishes one-year storage expiry;1417 renders availability. AttendanceAttachmentService58/122/140 establishes retention and controls journal availability/downloads. HeadmanRequestService499 and TeacherAttendanceReadGrpcService587 render attachment expiry. All receive the primary Clock. A fileClock ahead of real time may classify a retained attachment as expired and the active retention scheduler can clear bytes via StudentRequestService.expireOne2190. A pinned earlier Clock can continue returning retained bytes after real expiry. These are operational retention/access boundaries; keep them real independently of teaching request deadlines. Readonly fixture mounts do not prevent these downstream writes.

MobileIdentityFilter Clock is used for error timestamps, while InternalJwtValidator uses real Instant.now for admission. MobileProblemHandler uses Clock both for problem timestamps and domain Retry-After; correction must align retryAt with domain cooldown without claiming security timestamps are isolated merely because validator bytes match.

## Time that remains independently real in the reviewed sources

- InternalJwtValidator181 uses Instant.now. PublicKeyProvider refresh uses Spring fixedRate timers, no injected Clock. Auth/gateway have no fixture mount/property; SessionAdmissionService41 creates Clock.systemUTC, ReportDownloadTicketService66 does likewise. This is source isolation, not actual service-startup proof.
- AdminAccessJdbcAuthority55 checks refresh expiry using SQL NOW and reads current grants uncached. Student projection grant/version/revoked/status/group reads remain durable; its one expiry comparison is the exception above. Other reviewed signed gRPC identity interceptors delegate time validation to InternalJwtValidator.
- Academic CacheConfig uses Redis duration TTL; GeofenceService70/94 uses Instant.now for refresh age. SemesterCacheService has no Clock-based TTL. Shared outbox/idempotency cleanup receives explicit Clock.systemUTC from all three OutboxConfig providers. JDBC ShedLock uses usingDbTime; Mongo lock provider and Spring scheduling timers are not replaced by this Clock bean.
- RPC deadlines use real gRPC TimeUnit-based deadlines. Teaching Clock does not change system wall time or JVM timers. Browser setFixedTime changes JavaScript Date only; this bounded lane does not establish security-expiry UI or attachment-expiry UI acceptance.

## Educational and crosscutting consumers

Academic homework scope/lifecycle/binding/notifications, semester activation, group educational year/promotion and dashboard activity windows receive teaching time. Schedule one-off/recurring writers, lifecycle/read/list/window logic, generation/parity/homework placement/reminders/status-transition job bodies receive it. Attendance checkin/snapshot/manual marking, late/excuse request windows, report dates/activity/audit timestamps receive it. BFF StudentQueryService uses it for student day/homework/server timestamps. ScheduleItemService currently stores an unused Clock. HomeworkStudentService and AcademicGrpcServiceImpl delegate lifecycle to an injected HomeworkLifecycle; HomeworkBindingArchiveCoordinator uses the field alias notificationClock. Exact full58-file enumeration is the inventory; broad primary injection also changes consumers unrelated to the focused geo scenario.

## Narrow correction requirements

Keep production defaults, normal entrypoints, protocols/grants, status scheduler and teaching windows intact. Route file time only to explicitly enrolled educational time consumers. Separate real security expiry and operational retention from teaching date inside mixed consumers; an explicit narrow clock seam may be required and belongs to the author's assigned scope. Do not use reflection/stack-sensitive routing or disable expiry/schedulers. External opt-in, disposable marker, four-service allowlist, no production fixture dependency and fail-on-invalid behavior remain required.

Controls must exercise an actual mixed consumer with teaching date changed while real session/attachment expiry stays unchanged; merely retaining the systemClock bean and retrieving primary Clock in a GenericContext cannot show this. Production Boot artifact scanning must inspect nested classpath jars (CC02). Replay assertions need a receipt-distinguishing counterexample (CC03). After author corrections, independently recheck stable revision before any fixture application/browser/fullstack lease. Later security/export integration may add Clock consumers, so reconcile the inventory against that actual runtime revision before launch.

No Java/Gradle/Docker/browser/application runtime was started by reviewer. No production source correction, fixture injection or domain write was performed.
