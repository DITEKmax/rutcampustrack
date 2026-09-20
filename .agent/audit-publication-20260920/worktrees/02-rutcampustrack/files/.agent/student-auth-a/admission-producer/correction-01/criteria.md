# Correction-01 criteria

1. `internalTokenCarriesInternalPurposeAndCannotBeParsedAsAccess` and
   `sessionAccessTokenCarriesFrozenWireAndLegacyAccessIsNotAdmitted` derive
   `issuedAt` from `Instant.now().truncatedTo(ChronoUnit.SECONDS)` and keep the
   60-second positive-token window.
2. All negative purpose/legacy assertions in `JwtTokenPurposeTest` remain
   present and unchanged in intent.
3. The default `SessionAdmissionServiceTest` claims include
   `group_id` as the string `"10"`, matching the ACTIVE snapshot's group 10.
4. `terminalGrantIsAdmittedReadOnly` explicitly overrides `group_id` to null
   while retaining terminal status `EXPELLED` and `readOnly=true` assertions.
5. The source-17 guard reports exactly two expected fixture drifts, thirteen
   other existing entries unchanged, and the two historical deleted entries
   absent.
6. The bounded diff contains only the two owned test files plus evidence under
   `correction-01/`; no product behavior is changed.
