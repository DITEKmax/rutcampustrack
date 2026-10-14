# Clock Boot registration — independent review

Verdict: **APPROVED for this four-file source/control correction; blocking findings: 0.** Genuine FIRST3 headman61/helper142 browser acceptance and current ordinary CI remain required. This review does not close Q01 or claim a production deployment.

Reviewer: `/root/clock_boot_registration_review`; requested Sol6.1/high, actual metadata UNKNOWN. Read-only review; no Gradle/JVM/Docker/browser/network execution, children, product edits or shared-document writes. Only this assigned review artifact was created. Scope/risk: external E2E initializer enrollment and separation of educational versus security time.

Reviewed worktree: `/workspace/rct-worktrees/first3-spec-frame-20261013`, baseline/HEAD `e9a5f99b4f36518549b0c677ccc618339e5494c0`. Final observed inventory is exactly three modified tracked files plus the one new resource below; optional `ReportServiceEducationalTimeTest.java` is restored to baseline. `git diff --check` passed. No product/auth/API/Angular/workflow/deployment or accepted-assertion changes are present in this package.

| File, relative to reviewed worktree | Final SHA256 |
|---|---|
| `build.gradle.kts` | `7fa3f6eee0be78380348f74c68f1ab7e76f537b30ca982d5e315fec2b9332c19` |
| `tests/e2e/backend-clock/src/control/java/ru/rutcampustrack/e2e/ClockFixtureControl.java` | `44e0b383a1ae6a920d0e1183f4b3a067f388c081af2fb4f16bf88c7f966e85bc` |
| `tests/e2e/infra/docker-compose.clock-fixture.yml` | `ef73aee6aade53fdccf41fb9678ff3dc01454319425c000ed491e9531a6e3085` |
| `tests/e2e/backend-clock/src/main/resources/META-INF/spring.factories` (new) | `0750a7630d4271f2288e8101981ee74094b06efe8431c029764cb2f49871a2bc` |

## Independent critical-source inspection

- Read full governing TASK-CONTRACT, CURRENT, decisions, questions, resources, project/test AGENTS and orchestration RULES. Inspected actual initializer, FileClock, EducationalTime, default Clock configurations, ReportService constructor, mixed Academic session/Attendance attachment/BFF security consumers, production Docker entrypoint, explicit compose override and ordinary CI preparation path.
- Cached actual Spring Boot JAR is version `3.5.16`, SHA256 `2d5b16632402b840b0425ed52610071890bf8cb24c6c49af343bbde574123265`. Its class/resource contents lack `DelegatingApplicationContextInitializer` and any class reference to `context.initializer.classes`; `SpringApplication` contains the ApplicationContextInitializer/SpringFactoriesLoader discovery mechanism. The ignored legacy argument therefore could not enroll this fixture.
- The new one-line `META-INF/spring.factories` uses the correct ApplicationContextInitializer key and existing initializer class. Root build adds resources only to the separate `e2eClock` source set; its JAR packages that output. The Boot dependency is only on `e2eClockControlImplementation`; production source sets, bootJar dependencies and base images/entrypoints are unchanged.
- Compose removes only four obsolete initializer arguments. The external classpath, read-only fixture directory mount, explicit enabled/file properties, genuine production main classes and four-service allowlist remain intact. Auth and Gateway have no fixture mount or classpath enrollment; the unchanged initializer rejects their service identities.
- The initializer registers primary **EducationalTime**, a record composing Clock, under its separate fixture bean name. It never registers a Clock bean. The normal default EducationalTime and real Clock beans can coexist; the fixture wins only EducationalTime lookup. Production defaults wrap real Clock. ReportService uses the selected EducationalTime provider. Academic session validity, Attendance attachment retention and BFF security diagnostics retain ordinary real Clock; shared JWT validation retains real Instant.now. No security/token-time injection was introduced.
- The new control calls `SpringApplication.run()` with Boot-discovered initializers and existing competing default educational/real-clock beans. It checks FileClock selection, exact fixture instant and unchanged ordinary Clock identity/type. It does not manually add or call the fixture initializer in that discovery block. Existing direct controls still cover guards, excluded services, allowed identities and atomic clock replacement.

## Evidence accepted and independently checked

Author evidence: `/workspace/scratch/rct-validation/first3_editability_current_diagnosis/outcome.safe.json`.

- Two early failures stopped before compilation because the checking caller selected an existing JRE without JAVA_COMPILER. They remain recorded as precompile fixture failures, not causal RED.
- Actual RED used the existing full JDK21 and old registration metadata: compile/control execution completed, native exit 1 at `require(educational instanceof FileClock)`, 14.56 seconds. This targets the missing Boot enrollment.
- Corrected GREEN: scoped `spotlessJavaApply spotlessJavaCheck e2eClockFixtureJar e2eClockFixtureControl`, offline/JDK21, native exit 0, Boot control and PASS message observed, 30.5 seconds. Author terminal evidence reports all owned processes reaped, no remaining members, and private TMP absent. Reviewer did not replay these checks.
- Reviewer directly inspected the final built external JAR: SHA256 `af2734c3d93cf78a9e855b34417c01125a71bdda969603e5f02e986c8e24ff81`, 4362 bytes; exact SPI line present, ClockInitializer/FileClock classes present, no ClockFixtureControl class or dependencies packaged. It matches the author's packaged-artifact evidence.

No significant source or security-boundary defect found. This establishes the narrow registration fix and local discovery control. Actual Docker service startup and genuine journal editability still need the unchanged current browser/backend acceptance; no whole scenario/family promotion is justified by this review.
