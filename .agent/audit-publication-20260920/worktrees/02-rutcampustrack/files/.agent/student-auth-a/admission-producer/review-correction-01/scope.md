# Review correction 01 scope

Status: `SOURCE_READY_FOR_ROOT_AUDIT` / `RELEASE`.

Risk is S3 because this slice controls session liveness, authorization state,
and internal-token issuance. The bounded repair owns exactly these five source
paths for audit; the first three were changed by this leaf:

- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionAdmissionService.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionAdmissionServiceTest.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java`
- `services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java`

New evidence is limited to this `review-correction-01/` directory. Existing
producer, Auth13, shared-security, profile, and other agent changes remain in
the dirty checkout and were not reverted, staged, committed, or reformatted.
The baseline revision is `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

The root decision for this repair is to apply the review finding within the
frozen producer contract. No product, API, or scope redesign was needed; any
decision beyond this time-source correction remains a root decision.
