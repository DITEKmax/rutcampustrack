# Correction-02 runtime evidence

Runtime is **N/A for this bounded correction**. The request limits this leaf to
static compile-risk and diff checks and forbids Gradle, Docker, Testcontainers,
PostgreSQL and service execution. No integration-test success or cleanup claim
is made here.

The source now exercises the production Spring constructor path when the IT is
run: the context owns `SessionAdmissionService` and
`InternalSessionAdmissionController`, while seeded JWT/authority/properties
instances are preserved as singletons. Root retains the actual PostgreSQL
integration runtime gate and any subsequent independent review.
