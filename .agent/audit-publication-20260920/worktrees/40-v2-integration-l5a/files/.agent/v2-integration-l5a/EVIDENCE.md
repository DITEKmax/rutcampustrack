# Static evidence

- `HEAD` at freeze: `426a15b6b42e816deaa3ca5c50437e0964aaf85e`.
- Rules pointer: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA-256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Frozen contract SHA-256: `9522C3830530590FC36DBFD906BCEEAA22FF48F481BBD1B9D8528CA98A89BE8F`.
- Frozen inventory SHA-256: `15B218A3B4C6B344E6389D633C73B0EA91B6102DA444317A1D3B47C5CD2A7042`.
- Source manifest: [source-manifest.json](source-manifest.json). It records all 44 expected source blobs and their actual worktree blobs.
- Manifest result: `source_only_count=44`, `source_exact_count=44`, `source_mismatch_count=0`, `allowlist_count=46`, `changed_product_count=46`, `missing_allowed=[]`, `extra_product=[]`.
- Composite blobs at freeze: Gradle `c0fafd3106cffe7294b21a405a847ac1ae670903`; Academic gRPC `05dc4d4748ef71df970a8b8a0d73dd7adf49e03e`.
- Static structural evidence confirms one `@Autowired`, all seven union dependency markers, assignment authority lookup, `getAssignmentsByIds`, signed identity checks and bounded `65_536` streaming.
- No changed path is under `proto/` or Academic migrations; target-only preservation is established by the exact 46-path scope assertion.
