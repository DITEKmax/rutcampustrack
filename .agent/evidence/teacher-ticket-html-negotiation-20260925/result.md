# Result — teacher ticket HTML negotiation

## Outcome
PASS for the bounded Gateway regression. The root-owned live run `20260925-201050455-qacvkez_` established the original negotiation defect: the same authenticated teacher stats export returned 406 with `Accept: text/html` and 200/2152 bytes with `Accept: */*`; ticket redemption returned 503. HEADMAN redemption still returned 200 with matching bytes. Runtime evidence remains in the frozen harness run's `accepted-artifacts/api-acceptance.json`.

The Gateway now asks the mobile BFF for any representation on fixed dynamic export routes. It still checks the returned media type against the stored format before forwarding. No selector, route, authz, TTL, rate limit, byte limit, or security setting changed.

## Checks
- `:services:api-gateway:test --tests "ru.rutcampustrack.gateway.security.ReportDownloadTicketDownloadFilterTest"` with Gradle 8.12, `--no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true` — exit 0; `BUILD SUCCESSFUL`, 18 actionable tasks (3 executed, 15 up-to-date). Includes valid HTML passthrough with `Accept: */*` and rejection of a 2xx JSON response for an HTML ticket.
- `git diff --check` — exit 0 after source, tests, and evidence were added.
- An initial Gradle invocation using unquoted `-Dorg.gradle.java.compile-classpath-packaging=true` exited 1 before test execution because Gradle parsed the property suffix as a task. This was command-argument formatting only; corrected invocation above passed.

## Scope and limits
- Base: `c1b4bb210300fdbf3477507a6231cd114e0b3269`; branch: `codex/teacher-ticket-html-negotiation-20260925`.
- Product diff: `ReportDownloadTicketDownloadFilter.java`; focused regression: `ReportDownloadTicketDownloadFilterTest.java`.
- Test uses WireMock for Gateway behavior. No new end-to-end runtime was run by this leaf; root owns live follow-up after integration.
