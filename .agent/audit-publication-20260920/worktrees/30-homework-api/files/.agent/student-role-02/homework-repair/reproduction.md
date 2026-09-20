# Homework API repair reproduction

Date: 2026-09-07. Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Environment: Windows, Java 21.0.10, Gradle 8.12, local servlet with an
in-process Academic gRPC fake. The focused test file was extended with the
accepted four-defect matrix before changing production code.

## Pre-fix HTTP matrix

Command:

```text
.\gradlew.bat --no-daemon --no-problems-report :services:mobile-bff:mobile-bff-app:integrationTest --tests "*StudentHomeworkHttpGrpcIT"
```

Exit code: `1`.

Evidence: 6 tests completed, 5 failed. The error-response assertions showed no
`Cache-Control: no-store` on Homework 401, 403, 404 and 503 responses. The
missing-active-semester case returned `403 FORBIDDEN` instead of the required
`503 SERVICE_UNAVAILABLE`; the gRPC fake confirmed the Homework read/mutation
RPC was not reached after the preflight failure.

## Pre-fix input reproduction

Command:

```text
.\gradlew.bat --no-daemon --no-problems-report :services:mobile-bff:mobile-bff-app:integrationTest --tests "*StudentHomeworkHttpGrpcIT.invalidHomeworkInputsUseTypedProblemDetails"
```

Exit code: `1`.

For this diagnostic run only, the test temporarily expected the known old
overflow result and temporarily skipped the new header assertion so later
cases could execute. The overflow path
`PUT /api/v1/student/homework/9223372036854775808/completion` returned
`500 INTERNAL_SERVER_ERROR` (the temporary `500` assertion passed). The next
case, body `{"completed":1}`, returned `200 OK` instead of `400`; the run
stopped at that first scalar coercion. The review artifact
`.agent/student-role-02/homework-api-review-1.md` independently records the
same behavior for numeric and string scalar values. The test expectations were
restored to the required `400`/no-store contract immediately after capture.

No production source was changed during either reproduction run.
