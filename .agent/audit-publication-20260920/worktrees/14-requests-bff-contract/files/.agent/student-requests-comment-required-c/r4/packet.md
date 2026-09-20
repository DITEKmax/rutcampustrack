# R4 final evidence packet

Goal: freeze the final producer-chain bytes and attach exact runtime evidence.
Context/evidence: R1 implementation, R2 NotificationResolution dependency correction, R3 BSON ObjectId test-fixture correction.
Relevant scope: 11 exact producer/test paths plus R4 evidence only.
Required behavior: server-owned OTHER=true and all other reasons=false survive domain, protobuf mapper, BFF facade and required JSON boolean.
Constraints: preserve P1 attachments, P2 error behavior and authorization bytes; no generated OpenAPI/TypeScript write.
Existing patterns: Java annotation API, protobuf generation during Gradle, focused XML evidence.
Acceptance criteria: exact source hashes, all three command groups green, 67/67 tests, independent Sol/high review.
Verification: final-manifest.json, runtime-evidence.json and checks.md.
Do not: rerun green tests, mutate product/config/lockfiles, or claim live HTTP/gRPC runtime.
