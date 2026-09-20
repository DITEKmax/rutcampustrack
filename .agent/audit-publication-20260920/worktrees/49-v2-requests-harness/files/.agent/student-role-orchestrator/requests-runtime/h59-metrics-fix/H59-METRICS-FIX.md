# H59 metrics configuration fix — author evidence

## Scope

- Risk: S3 bounded runtime-harness correction.
- Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness`.
- Base revision: `73fd5f27ceb429ad0692b073189f8a527c550830`.
- Sole writer scope: `requests-runtime/runner.ps1` and this evidence directory only.
- Assigned implementation: fresh Luna max, no child agents; Terra is prohibited.
- Project rules SHA-256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Contract SHA-256: `2714D1C64A8DBEE24BB7B3CD3228E0377AF727CCB9750A8016E8AD2785038771`.

## Criteria

The disposable runtime must retain `MANAGEMENT_ENDPOINT_METRICS_ACCESS = 'unrestricted'` and `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = 'health,info,metrics'`, while removing only the obsolete `MANAGEMENT_ENDPOINT_METRICS_ENABLED = 'true'` assignment that makes current Spring Boot startup fail. Product sources, production settings, security behavior, I1/I2 assertions, accepted WIP, and shared status remain untouched.

## Root evidence

The root-owned H59 run `20260919-183652649-mnkcr-fz` failed during Academic startup because Spring Boot reported `management.endpoint.metrics.access` and `management.endpoint.metrics.enabled` as mutually exclusive. The filtered failure is recorded in:

- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/h59-academic-filtered.log`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/h59-report.json`

The same report records root runtime `FAIL`, Academic phase `FAIL`, cleanup `PASS`, zero cleanup errors, and all owned resources verified absent. This leaf did not rerun runtime, Docker, Gradle, build, or network proof.

## Baseline and patch

- Runner before snapshot SHA-256: `67F6D0E471FCE5DC7835023D32BB5BD3D26EEB00426AD9AD7A7C2C62409FCA3D`.
- Runner before snapshot size: `160634` bytes, saved as `runner-before.ps1` in this directory before editing.
- Runner after SHA-256: `2B47DA9105461EB3C9D11FAE7CA92B319A7B8695725F665D96D8F03930CCD9A0`.
- Runner after size: `160581` bytes.
- Functional diff: one removed assignment at the original `Start-BackendServices` metrics block; current access assignment is line `1619`.

```diff
         MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = 'health,info,metrics'
-        MANAGEMENT_ENDPOINT_METRICS_ENABLED = 'true'
         MANAGEMENT_ENDPOINT_METRICS_ACCESS = 'unrestricted'
```

The byte-level bounded-patch check passed: the after file equals the before snapshot with exactly that one line removed. The pre-existing dirty Git inventory also matched before and after, excluding this intended runner change and this new evidence directory.

## Checks

All checks ran in PowerShell `7.6.5` on `Microsoft Windows NT 10.0.26200.0` at the worktree above. See `checks.json` for command, exit code, expected result, and evidence references.

- Rules and contract hashes: PASS, exit `0`.
- Baseline revision and runner hash: PASS, exit `0`.
- Before snapshot copy and hash: PASS, exit `0`.
- PowerShell `Parser::ParseFile` plus `HashtableAst` assertions: PASS, exit `0`.
- Exact byte-level bounded replacement: PASS, exit `0`.
- `git diff --no-index` snapshot comparison: PASS with expected diff exit `1`; one deletion and no additions.
- Pre-existing Git status inventory preservation: PASS, exit `0`.

## Runtime status and release

Runtime startup after this correction remains **UNVERIFIED** until root performs a new authorized H59 run. The previous root run's cleanup evidence is retained and was not edited. This leaf started no process and owns no active process or runtime resource; author resources are released. The repaired harness is ready for the fresh independent Sol high review with the contract, stable diff, checks, and critical root evidence above.

## Limitations

- No claim is made that Academic startup, I1, I2, security, cleanup, or full runtime now pass; they require the root rerun.
- No Docker, Gradle, dependency, product-source, build-artifact, shared orchestration, `source-validation.json`, or old run-folder change was made.
- Existing accepted WIP in this worktree remains dirty by design and is preserved for root/reviewer inspection.
