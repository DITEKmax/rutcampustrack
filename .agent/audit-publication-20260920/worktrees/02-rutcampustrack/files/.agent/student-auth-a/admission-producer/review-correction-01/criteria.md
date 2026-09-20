# Review correction 01 criteria

1. `initialNow` is read for and used only by the single
   `SessionStatePort.SnapshotCommand`.
2. After the authoritative snapshot is returned, `freshNow` is read once and
   `snapshot.isLiveAt(freshNow)` is the liveness gate used before signing.
3. `iat` is `freshNow` truncated to whole seconds, `exp` is the minimum of the
   original access expiration and `freshNow + TTL`, also truncated to whole
   seconds, and signing is rejected unless `exp` is strictly after both
   `freshNow` and `iat`.
4. The same accepted snapshot supplies the signer input and response identity;
   there is one snapshot call and no cache or extension. After signing, a
   `returnNow` guard rejects a session that expired during signing and rejects
   an internal or original access expiration that is no longer strictly future.
5. Deterministic tests cover delayed success, access expiry during the snapshot,
   session expiry during the snapshot, no signer call on denial, the typed
   failure matrix, identity mismatches, and repeated authority/signing calls.
6. `JwtTokenPurposeTest` removes each mandatory session-access claim from a
   raw signed token and expects rejection, accepts a raw signed token without
   optional `group_id`, and rejects the required semantic identity mismatches.
7. The existing internal-wire and Spring/MockMvc matrix tests remain present in
   the other two audited paths without unrelated expansion.
8. The five-path ownership boundary and the evidence-only directory boundary
   remain intact.
