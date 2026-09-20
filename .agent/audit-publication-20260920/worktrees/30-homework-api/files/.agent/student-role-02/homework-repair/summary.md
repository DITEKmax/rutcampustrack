# Homework API repair summary

Completed the four accepted bounded repairs in the assigned Homework BFF
scope. Overflowing IDs now become typed 400 before Academic Homework RPC;
`completed` accepts only JSON booleans for this request type; missing active
semester maps to 503 for Homework GET/PUT while generic Today/schedule mapping
is unchanged; and a high-precedence bounded filter adds `Cache-Control:
no-store` to Homework success and error responses, including pre-auth 401.

Java-first error headers were exported to the canonical OpenAPI snapshot and
mobile-core types were regenerated. The focused Homework servlet/local-gRPC
matrix passed 6/6, affected query unit tests passed, and neighboring check-in
HTTP/auth tests passed. OpenAPI update/no-update, TS drift, typecheck, lint,
contract, fixture and both backend bootJar checks all exited 0. Scoped
`git -c core.whitespace=cr-at-eol diff --check` exited 0; plain diff check
remains an expected generated OpenAPI CRLF warning and exits 1.

Stable handoff is ready for fresh independent Sol recheck. Parent still owns
integration, dependency/security scans, real PostgreSQL concurrency/runtime,
and final merge. No commit was created.
