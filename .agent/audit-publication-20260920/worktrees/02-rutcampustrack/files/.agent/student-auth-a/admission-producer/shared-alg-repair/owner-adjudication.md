# Root adjudication

The root decision for this fresh repair is recorded as follows:

- Accept the RS256 algorithm allowlist correction.
- Accept strict raw JOSE header validation for producer wire {alg,kid}, with optional
  typ=JWT and required nonblank kid.
- Accept duplicate-key and focused coverage corrections, including full filter tuple
  equality.
- Reject the proposed InternalJwtClaims component/accessor rename; preserve
  isHeadman() in this slice and do not add a compatibility alias.
- Reject the proposed zero-skew change; preserve configurable clock-skew semantics.
- Keep Auth admission, issuer, Gateway, downstream consumers, contracts, and runtime
  checks in their existing owners/scopes.
