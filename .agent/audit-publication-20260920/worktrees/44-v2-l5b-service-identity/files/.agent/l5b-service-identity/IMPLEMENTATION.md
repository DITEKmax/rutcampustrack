# L5B service identity implementation packet

## Scope

- Baseline: `13e5fd1985b798bbb61bcc85969e6a74b1fc6837`.
- Worktree: `v2-l5b-service-identity` / `codex/l5b-service-identity-20260920`.
- Rules: `.agent/orchestration-v2/RULES.md`, SHA-256
  `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Writer: assigned Luna implementation leaf only; no children, no Terra,
  no push/deploy/main merge.

## Criteria

The shared primitives validate canonical 32-byte unpadded base64url tokens,
bind separate immutable service principals, require real transport SSL for
reserved methods, classify invalid versus authenticated-forbidden calls, and
scope outgoing credentials to exact method and authority at privacy-and-
integrity transport security. Academic and Schedule adapters each protect one
reserved method and fail closed when their property is missing. Existing
unrelated RPC contracts and public close behavior remain untouched.

## Evidence gathered before checks

- Worktree started clean at the exact baseline above.
- Existing shared-security module is a plain Java library without gRPC
  autoconfiguration; app registration is explicit through the gRPC starter's
  global-interceptor bean annotation.
- Pinned gRPC version is `1.82.4`; no version upgrade or proto change was made.
- Existing Academic/Schedule client wrappers were read and left unchanged;
  no current protected stub exists to receive service credentials.

## Planned checks

Root owns the single Gradle queue. After source freeze and lease, run the
focused shared-security tests, both adapter tests, the existing student
identity test, and the Academic close regression filters in one approved batch.
The real local TLS positive, plaintext, and untrusted-peer tests are part of
that batch. No Gradle/build/runtime command was run while source was changing.

## Limitations

This is an admission/client primitive and endpoint-adapter slice. It does not
add service-token issuance, production secret or certificate provisioning,
proto declarations, domain RPC handlers, resource authorization, migrations,
events, or client changes for unrelated methods.
