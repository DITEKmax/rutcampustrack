# Source evidence

## Review and correction

The immutable review in shared-review-verdict.md failed the prior shared-security
subset for four findings. This repair addresses only the root-accepted algorithm,
header, duplicate-key, and test-coverage portions. The review's record accessor and
zero-skew proposals are recorded as rejected root deltas in owner-adjudication.md.

The current source visibly contains:

- parser allowlist: Jwts.SIG.RS256;
- raw header allowlist: alg, kid, optional typ;
- exact plain-string checks and nonblank kid check;
- duplicate-object-key rejection in the bounded raw JSON parser;
- signed RS384/RS512/PS256 negative cases;
- signed duplicate JOSE-header and duplicate-payload rejection cases;
- full InternalJwtClaims equality at the filter boundary.

## Current owned source manifest

| Path | Bytes | SHA-256 |
|---|---:|---|
| services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java | 23291 | 4696BD3886F4BFC145B92009D3DA5EC4E5BB1C7EAF5E6BA9C90CE6E405C483FF |
| services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java | 14470 | 4D3C3DD677B902F875F655584F530BA43FBDAE0DE687426EF122EAA42E190B26 |
| services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/DualModeUserContextFilterTest.java | 7934 | 2CCB149F9FD57627BE6C18CF4B30A6DD49A234F146595A03663428C5B8EB87CB |
| services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java | 3355 | DA186908CBC27F752195EBBBF8A874004E183F507EF928C78B32D24910CBCF52 |

No bearer or internal JWT value is written in this evidence.
