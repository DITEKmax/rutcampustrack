# Stage2 affected integration gate failure record

## Scope

Affected auth integration group: `OtpIT`, `TmaIT`, `LogoutLifecycleIT` and
`SameSiteCookieContractIT`, covering OTP/TMA login flows, password change,
logout lifecycle and SameSite cookie behavior.

## Criteria

The selected group must complete all 23 tests with zero skipped, failures and
errors. This run completed 20/23; the three failures split into two stale test
fixtures and one real Redis WS-ticket cleanup regression.

## Evidence

Exact command:

`.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.OtpIT --tests ru.rutcampustrack.auth.integration.TmaIT --tests ru.rutcampustrack.auth.integration.LogoutLifecycleIT --tests ru.rutcampustrack.auth.integration.SameSiteCookieContractIT --no-daemon --no-parallel --max-workers=1 --console=plain`

- Session: `87633`
- Exit code: `1`
- Result: `BUILD FAILED in 53s`; `24 actionable tasks: 1 executed, 23 up-to-date`
- Test result: `23 tests completed, 3 failed`; `20` passed, `0` skipped,
  `0` errors.
- Environment: Windows PowerShell, host `DITEK-PK`, Gradle wrapper, revision
  `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Docker cleanup was requested as a separate check; no Docker result is claimed
  in this record.

Preserved raw JUnit XML under `junit/`:

| File | Tests | Skipped | Failures | Errors | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `TEST-ru.rutcampustrack.auth.integration.OtpIT.xml` | 8 | 0 | 2 | 0 | `C6061DA1810984A982E24DBD5194A7336E147AC86E31B50025A41D52B7871B57` |
| `TEST-ru.rutcampustrack.auth.integration.TmaIT.xml` | 8 | 0 | 0 | 0 | `A9398B9AE28960FF60DDD5A7F8288D8C7FD310B96B1926D05BE6DCF7E7995CEC` |
| `TEST-ru.rutcampustrack.auth.integration.LogoutLifecycleIT.xml` | 2 | 0 | 1 | 0 | `77241F0BAA6267DF7842303A00A2B695D8184A97983C4B0ADAB4753CD2A93DF1` |
| `TEST-ru.rutcampustrack.auth.integration.SameSiteCookieContractIT.xml` | 5 | 0 | 0 | 0 | `E34348B515B3DAA4F7929975057132E331D0118A55FCBBD083EE87CE444368E1` |

## Diagnoses

1. `OtpIT.changePassword_withCorrectCurrent_returns204` fails at line 154
   because the fixture sends stale `NewPass1x`. The current password policy
   correctly returns `400 BAD_REQUEST`; the new password is shorter than the
   required 12 characters and has no required special character. The fixture
   needs a valid-policy new password.
2. `OtpIT.changePassword_withWrongCurrent_returns401` fails at line 182
   because the fixture logs in with the admin bootstrap token. Bootstrap scope
   correctly cannot access the password endpoint, so the actual response is
   `403 FORBIDDEN`, while the stale test expects `401 UNAUTHORIZED`. The fixture
   should use student selected access and assert the typed wrong-current
   `400 BAD_REQUEST` response.
3. `LogoutLifecycleIT.logout_withBearer_invalidatesWsTicketsAndRevokesRefresh`
   fails at line 86. The endpoint returns `204`, clears the cookie and revokes
   the database refresh authority, but the user's legacy Redis WS tickets
   remain. This is a product cleanup regression, not a stale expectation.

`TmaIT` (`8/8`) and `SameSiteCookieContractIT` (`5/5`) are fully passing in the
preserved reports.

## Checks

| Check | Result | Exit |
| --- | --- | ---: |
| Copy four source integration XML reports to this archive | All source/destination hashes match; byte comparison `True` for all four | 0 |
| Parse archived suite counts | `8 + 8 + 2 + 5 = 23`; `20` pass and `3` fail; zero skipped/errors | 0 |

## Runtime evidence

The supplied session `87633` output is the runtime evidence for this affected
group. It confirms TMA and SameSite pass, identifies the two password fixture
contract mismatches, and reproduces the WS-ticket persistence defect after
successful HTTP logout and database refresh revocation. Docker cleanup remains
separate and unclaimed here.

## Diff

This failure archive contains only the four byte-identical XML reports and this
record. No source or test file was changed while capturing the failure.

## Limitations

The two OTP failures are not product defects and should be corrected in the
test fixture only after root accepts the bounded fixture delta. The WS-ticket
failure is a product behavior finding; no code change is authorized by this
record. The group remains blocked until root decides the WS cleanup scope and
the stale OTP expectations are corrected and rerun.
