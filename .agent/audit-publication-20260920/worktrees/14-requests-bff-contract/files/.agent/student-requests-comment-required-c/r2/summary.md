# FINAL / RELEASE

Scope complete for the bounded S3 correction in requests-bff-contract.

Changed exactly one product file by restoring the accepted P1
NotificationResolution(long groupId, long studentId, String studentName,
RequestDetail detail) record and its canonical Javadoc after RequestDetail. The
current ReasonOption(..., boolean commentRequired) producer declaration was
preserved.

Exact hashes:

- before target: A6356840FAF4D34EF3B0E1022DC3CEACBD82AE2CEB92943FDD4198A578EA5AA9
- accepted P1 source: 142609485B22E52C9A6AA2506D332FD42E8F37CFEC178FCC22910777C9CB6D10
- after target: C5FF83BB1ABA886BA89DA94CD0A2832D7F88E566DBD37B3E2E820A2AD4C76EDD

Static exact-transform, shape, whitespace and frozen-hash checks passed with
exit 0. The source comparison had expected exit 1 because only the intentional
P1 ReasonOption arity difference remains. Runtime is NOT RUN (N/A) by contract;
root must rerun the frozen attendance-app command and capture runtime evidence
before integration/review.

No product, contract or scope decision delta was needed. No Terra escalation,
commit, reset, cleanup or foreign-work overwrite occurred.

RELEASE: sole-writer work is released to root with the r2 evidence bundle.