# Evidence

## Immutable defect record

Reference: `../runtime-failure-01/failure.md`.

- Focused command exited `1`; Gradle compiled main, fixtures and tests.
- Result: 22 tests, 16 passed, 6 failed, 0 errors, 0 skipped.
- Cause recorded there: JJWT `.sig().clear()` callback rejects the intermediate
  empty registry at `InternalJwtValidator.java:64`.
- XML references are immutable: `TEST-ru.rutcampustrack.shared.security.InternalJwtValidatorTest.xml`
  SHA-256 `FC34268487605D3ADDC96D65B3D1CF21B174AB369211B67681A930F94B6D44D0`,
  28791 bytes; `TEST-ru.rutcampustrack.shared.security.DualModeUserContextFilterTest.xml`
  SHA-256 `7858D65E547A9D50D50384E3016ADC319AE470AD0AD821C91306FAF48A62A924`,
  4684 bytes.

## Target pre/post manifest

| Path | Pre-correction bytes | Pre-correction SHA-256 | Post-correction bytes | Post-correction SHA-256 |
|---|---:|---|---:|---|
| `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java` | 23291 | `4696BD3886F4BFC145B92009D3DA5EC4E5BB1C7EAF5E6BA9C90CE6E405C483FF` | 23230 | `E8633DE82330BCCB8A9805E37351F5E9F439A27FD1C95524B760592B614E3FF5` |

The 61-byte delta is the single removed builder mutation, including indentation
and line ending.

## Unchanged repair-slice files

Current hashes match the pre-correction manifest for the existing tests/fixture:

| Path | Bytes | SHA-256 |
|---|---:|---|
| `services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java` | 14470 | `4D3C3DD677B902F875F655584F530BA43FBDAE0DE687426EF122EAA42E190B26` |
| `services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/DualModeUserContextFilterTest.java` | 7934 | `2CCB149F9FD57627BE6C18CF4B30A6DD49A234F146595A03663428C5B8EB87CB` |
| `services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java` | 3355 | `DA186908CBC27F752195EBBBF8A874004E183F507EF928C78B32D24910CBCF52` |

## Static observations

- Raw parse invocation: line 59.
- `requireJoseHeader(raw.header())`: line 60.
- JJWT `.verifyWith(publicKeyProvider.getPublicKey())`: line 63.
- Exact raw `RS256` check: line 110.
- Removed mutation pattern has no remaining match.

Evidence содержит только paths, hashes, counts and error metadata; JWT values and
secrets не записывались.
