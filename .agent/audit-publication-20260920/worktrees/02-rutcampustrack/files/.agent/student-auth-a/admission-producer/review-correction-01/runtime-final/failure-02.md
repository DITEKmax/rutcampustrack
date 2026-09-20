# Runtime failure 02

Status: `FAIL / HEAVY RELEASE`.

After removing the ACL-blocked generated root problems report, the scoped retry
ran `:services:shared:shared-security:clean` followed by the same exact two test
selectors. It exited `1` with `BUILD FAILED in 23s` before tests.

The dependent module rebuilt, but its `compileJava` could not resolve
`BusinessMetrics` from `shared-observability`; Gradle had reported the upstream
`shared-observability:compileJava` task `UP-TO-DATE`. The corresponding upstream
source, class file, and jar entry exist. This narrows the environmental fault to
stale upstream generated/incremental outputs or the resulting project classpath,
rather than the new JWT assertions.

No Auth unit or PostgreSQL IT command ran. No source or test file changed. A
bounded next diagnosis may clean and rebuild the upstream generated output and
the dependent shared-security output together before retrying the same selectors.
