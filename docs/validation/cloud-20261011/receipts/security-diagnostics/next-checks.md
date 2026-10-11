# Minimal red-before packet (commands proposed, NOT_RUN)
Base 3908fad9624f97aec8891fc79338ceb6b0616bf3; no runtime lease used. Commands below need assigned isolated developer worktree and root heavy lease.

## L03 smallest reproducible path
Actual public domain endpoints POST /auth/qr/status and /auth/qr/exchange; gateway /api/auth/qr/status and /api/auth/qr/exchange strips /api. No auth necessary. Send JSON purpose LOGIN, issuerId 00000000-0000-0000-0000-000000000001, challengeId 00000000-0000-0000-0000-000000000002, issuerSecret RCT-QR-SENSITIVE-MARKER (invalid 23-char proof). @Pattern fails before QrLoginService lookup; expected 400 application/problem+json, fieldErrors identifies issuerSecret but value omitted/null, no marker in response/logs, no-store. Current advice predicts marker echo. API response on actual route must be captured with synthetic data only.

Proposed new risk regression: auth controller QrLoginValidationExceptionHandlerTest.invalidBrowserProofAndApprovalValuesAreRedacted; reuse existing PasswordResetValidationExceptionHandlerTest standalone MockMvc, real QrLoginController and shared/auth/reset advices. This exercises actual interface @Valid selection; mocked service is deliberately not invoked because validation occurs first. Test /status,/exchange with issuerSecret, /preview,/decision with approvalToken. Include invalid string enum/malformed JSON: generic error, no accidental secret echo. Existing QR IT add a single genuine anonymous malformed-proof request if needed to prove security filter/advice dispatch (distinct risk from standalone validation).

Minimum focused new regression command after creating it:
./gradlew :services:auth-service:auth-app:test --tests 'ru.rutcampustrack.auth.controller.QrLoginValidationExceptionHandlerTest' --tests 'ru.rutcampustrack.auth.controller.PasswordResetValidationExceptionHandlerTest'
Existing real HTTP regression after correction:
./gradlew :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.integration.QrLoginFlowIT'
No real endpoint/port assumed until root assigns disposable runtime. Use synthetic UUIDs above, do not reuse real users or secrets.

## L04 smallest red-before path
Proposed new academic and auth config LowercaseEnumConverterTest. Set Locale default tr-TR in try/finally, restore it even on assertion failure. Direct autoApply converters: UserRole ADMIN must write admin, AccountStatus ACTIVE must read active -> ACTIVE and write active; same cases under ROOT. Null -> null; unknown database code must not grant a known role (existing IllegalArgumentException permitted as internal boundary, actual admission must fail closed). Assert expected fixed wire codes, not Enum.name copied from implementation. For academic WebConfig actual configured ConversionService status=active must convert ACTIVE under tr-TR; status=not-a-status follows existing 400. A test class sharing default locale must run isolated/nonparallel with restoration; preserve test process default afterward.

Minimum proposed regression commands:
./gradlew :services:academic-service:academic-app:test --tests 'ru.rutcampustrack.academic.config.LowercaseEnumConverterTest' --tests 'ru.rutcampustrack.academic.config.WebConfigEnumConversionTest'
./gradlew :services:auth-service:auth-app:test --tests 'ru.rutcampustrack.auth.config.LowercaseEnumConverterTest'
Small actual boundary follow-up under controlled locale: GET /academic/users?status=active with live FULL or LIMITED users-view grant returns same filtered rows as default locale; unknown status 400, no writes. JPA load synthetic active ADMIN user in both domains; write is either canonical code or correctly rejected (never persisted admın). Do not inject unsupported enum DB rows bypassing constraints and call them a normal user scenario.

## Minimal compile/check set for correction ownership
If product edits touch auth API/advice and academic converter/WebConfig: compileJava and compileTestJava of auth-app and academic-app (API contracts only if changed); then focused tests, then sufficient chosen real HTTP/DB tests. shared-web change adds shared-web compile/test and affected advice consumers. Root assigns shared-web/config/contracts sole writer explicitly before patch. Do not regenarate unchanged proto or run unrelated baseline suites.

## Sensitivity checks
Red-before on frozen buggy source is the equivalent known-defect revision for new redaction and locale tests. Record failure at missing redaction/canonical locale assertion, not compilation/infra. Green-after restore source and final SHA, run focused suite. For existing 500/public-error test, preserve safe non-sensitive rejected fields rather than suppress all field errors mechanically.

## TMA follow-up added by root steering
Current route /api/auth/tma -> /auth/tma -> AuthController.tmaAuth -> TmaService.authenticateWithInitData. Existing integration fixture TmaIT.buildValidInitDataWithAuthDate uses synthetic BOT_TOKEN; adapt helper to Map<String,String> before signing sorted dataCheckString. Never read environment bot token. Cases: omit auth_date; auth_date=not-a-number; overflow >Long.MAX_VALUE; date=now+86400; duplicate auth_date with last value signed; exact replay within current window. Missing/non-numeric/overflow must be 401 safe error with no new session. Future guard policy/tolerance needs accepted rule. Duplicate parsing cannot be diagnosed as signature bypass without concrete alternate consumer disagreement; preserve as candidate. Current replay success is intentional test contract pending source resolution, not runtime FAIL.
Small focused service test candidate TmaServiceTest.signedMalformedAuthDateIsRejectedWithoutSessionIssue uses mocks and fixture TmaProperties token; real minimal acceptance add cases to existing TmaIT and run:
./gradlew :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.integration.TmaIT'
No current valid signed fixture causes Long.parseLong failure before user DB lookup; cheapest red-before service/MVC test does not require DB. Freshness test must control clock after minimal injected Clock seam if code correction needs it, not depend on live second rounding. Keep actual client session path as separate real TMA acceptance.
