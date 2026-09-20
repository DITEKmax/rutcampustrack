# Runtime evidence

Post-correction runtime is `N/A` for this source-only leaf by explicit contract:
root owns the focused Gradle test and any product runtime lease. No test or runtime
command was run after the correction.

The required historical reproduction is immutable at
`../runtime-failure-01/failure.md`: focused Gradle exit `1`, 22 tests with 6
failures, all rooted at the JJWT empty-registry callback. Its two JUnit XML SHA-256
records are copied in `evidence.md`.

The correction is limited to removing that unusable intermediate mutation. Root
must run the focused tests against post hash
`E8633DE82330BCCB8A9805E37351F5E9F439A27FD1C95524B760592B614E3FF5`.
