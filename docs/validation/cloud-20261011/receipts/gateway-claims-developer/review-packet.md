# GW01 independent review packet

Stable local author commit `2003948d83b2603967aa29f6434217c2a29453e1` on codex/rct-validation-gateway-claims-20261011, frozen root `232f0210ab65a6750b120e811d81f0e110b9e107`, accepted baseline `3908fad9624f97aec8891fc79338ceb6b0616bf3`. Worktree `/workspace/rct-worktrees/gateway-claims` is clean. Writer changed exactly one existing test file, +53/-3; no product/config/schema/API/proto edits or pushes.

## Source and required oracle
Owner TASK-CONTRACT security section, P07 claims rejection and P17 safe errors; explicit packet-gateway-exp-diagnostic.md preserves whole-second original JSON NumericDate policy, issuer/audience/purpose/signature and current grants. Previous PRCI38175673119 / gateway114577427225, e093 head, actual merge93ca323572a05b713aa30cdf395edf3feb24278b failed only existing non-whole expiration fixture with NPE. Root safe source receipt is docs/validation/cloud-20261011/receipts/e093-gateway-invalid-exp-ci.json. Actual merge object is unavailable locally; no equivalent merge checkout claim.

## Cause and bounded classification
GW01 TESTFIXTURE DEFECT, corrected; production admission DEFECT RULED_OUT for this counterexample. The legacy `BigDecimal.valueOf(epochMilli / 1000.0)` can produce integral JSON at millisecond000. Production JwtAuthenticationFilter#isIntegralNumericDate correctly accepts that valid whole-second JSON. The original unstubbed mock GatewayFilterChain returned null, causing `.block()` NPE when token was correctly forwarded. Default wallclock execution is probabilistic; unchanged selected35 checks passed locally, CI failure NOT_REPRODUCED by that run alone.

Actual controlled Java signed-payload structural red91703 resolved the cause: iat/exp at000 serialize NUMBER integral=true/fractional=false and meaningful status assertion fails401 vs null (two failures);001/999 serialize fractional and are rejected401 (four pass). No JWT/payload body was printed. This is an actual serializer/filter counterexample, not a production guard mutation.

## Correction and tests
The existing two invalid fixtures now derive from captured Date wholeSecond plus exact decimal0.125. Shared negative helper uses acceptingChain returning Mono.empty, then retains status401 and never-forward assertions, so unexpected forwarding yields a meaningful failure. Six iat/exp×000/001/999 source-wall boundaries ensure the corrected fixture remains nonzero fractional, inspect signed raw JSON and require refusal. Two explicit legacy000 positive controls force the actual integral serialization form (nonzero units digit), assert raw integral JSON and forwarding; their accepting-chain response has no assigned status. All35 original strict negative cases retained.

Production filter bytes match frozen root and baseline, SHA2563348509976bc3e0a5da0500c646fbefe9ab10452e586377876e7c051d15d2c3f. Tested WIP equals committed test SHA2561775a6fd5b88fa75515c5a8b2517110c8d0ccfedcff06d2cd899ce441e148be4.

## Actual execution and sensitivity
Three bounded invocations, no containers/apps/external providers. Exact commands/options/hashes are baseline-command.json, structural-command.json, green-command.json.
- Baseline57001 exit0: selected35 PASS, skips0,1.526s test time. Local unchanged wallclock NPE not reproduced.
- Structural91703 exit1: six cases, two fixture boundary failures000/four pass001/999, skips0,1.235s. Before XML and safe node metadata in structural-boundary-red.xml/structural-result.json.
- Final: BUILD SUCCESSFUL in34s,43 PASS/0fail/0error/0skip,2.460s. Existing35 plus six exactfraction boundaries plus two valid whole-second controls. final-selected-green.xml and safe copied log. Last tool-result session/exit metadata lost at compaction/truncated output, honestly UNKNOWN; launch argv/WIP hash were persisted before execution, no duplicate run solely for history. The wrapper propagates Gradle return code; raw log success is observed, shell tool exit code is not claimed.

Existing local filter tests use mocked live authority; these prove fixture/production raw claim gate only, not end-to-end session authority/current private delivery. No full150 suite or current remote CI claimed. Normal git commit exit0. Common .git/hooks/pre-commit is absent; no hook execution claimed or bypassed. Root owns common hook installation/configuration and final repository gates. Scoped git diff --check PASS and artifacts scanned.

## Review focus and next step
Independently open current production guard and modified tests. Confirm fixture math cannot become whole, accepting-chain status+never-forward preserve meaningful negatives, positive whole NumericDate stays valid, safe artifacts and tested committed hash match. Reviewer/root own fresh review and integration/remote CI; author remains available for bounded findings. GATEWAY1 released, no live own Java/processes/resources; foreign EXPORTS1 untouched. Notification/provider six new light prep files remain separately uncommitted/NOT_RUN and paused, no overlapping writes.

## Reproduction delta (test-only, safe)
The structural red used the same signed raw token/filter/refusal oracle as the final six-case test, but its `value` construction was `BigDecimal.valueOf((second * 1_000L + millis) / 1000.0)` instead of `fractionalNumericDate(Date.from(Instant.ofEpochMilli(second * 1_000L + millis)))`. This reproduces the legacy fixture at controlled000/001/999 wall offsets; real signed raw node metadata, not a decoded-token dump, is in preserved XML. Red test WIP SHA82fc4fc477c4f2008f8a8d973b2c30c0b85204b0ba56017b8530fde6bb28202c. Parent/root scope prohibits product mutation; no guard edits were performed.
