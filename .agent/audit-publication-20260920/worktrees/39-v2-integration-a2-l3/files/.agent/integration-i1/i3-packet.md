# I3 — isolate Compose dynamic allocation from the trusted Nginx edge

## Goal

Repair the single HIGH finding from the accepted I2 full-union review. Keep
the trusted proxy address fixed while making Docker's dynamic allocation range
explicit and disjoint. This lease is S3 source/config plus pure verification;
it does not claim a production startup.

## Context/evidence

The frozen I2 product union contained 34 paths with canonical manifest hash
`32031090D593BBC8B53869590F14D90386A97B1AD4786CDC7B6C16586C8CC266`. The
immutable review record is `.agent/orchestration-v2/SLOTS.md:128`: the full
private network range overlapped static Nginx `172.30.0.10`, the config gap was
confirmed by root, and an actual full Compose startup was not run. Root's
critical-read references are `docker-compose.prod.yml:1009-1014` and
`:902`, plus `docker-compose.e2e.yml:525-530` and `:520`.

The I2 OpenAPI snapshot and generated TypeScript remain historical accepted
artifacts and are preserved byte-for-byte. R3 owns the separate H32 runtime
allocation proof; this packet records no copied or author-owned allocation
runtime evidence.

## Relevant scope

Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-integration-a2-l3`,
branch `codex/v2-integration-a2-l3`, base/head before I3
`b8220ac92125a8afa37598b270aa4fab7aa1f470`.

Product scope is limited to `docker-compose.prod.yml`,
`docker-compose.e2e.yml`, `.env.prod.example`, nonsecret network keys in
`tests/e2e/.env.ci`, and `scripts/validate-env-prod.sh`. The bounded metadata
and pure-check logs are under `.agent/integration-i1/`; provenance is recorded
in `docs/sources/manifest.yaml`. Source and accepted sibling worktrees remain
read-only.

## Required behavior

Both Compose `private_net` IPAM blocks must set `subnet`, `gateway`, and
`ip_range` through the required inputs `GATEWAY_PRIVATE_SUBNET`,
`GATEWAY_NETWORK_GATEWAY`, and `GATEWAY_DYNAMIC_IP_RANGE`. The static edge
continues to use `GATEWAY_NGINX_IPV4` as its explicit service address.

The documented values are parent `172.30.0.0/24`, gateway `172.30.0.1`, edge
`172.30.0.10`, and dynamic range `172.30.0.128/25`. The validator must parse
dot-env values without shell evaluation; require canonical aligned IPv4 CIDRs,
an RFC1918 parent, full dynamic-range containment, usable gateway and edge
inside the parent, distinct fixed hosts, and both fixed hosts outside the
dynamic interval. Configurable private parents remain supported.

## Constraints

This is one-writer work in the assigned worktree. The frozen RULES checksum is
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`; the
assigned implementation model is Luna max. No children, Terra, source/E
edits, push, deploy, real production env, secret reads or service/network
startup are allowed in this source phase. R3's H32 proof has separate ownership
and runtime resources.

## Existing patterns

Reuse the existing strict `ipv4_to_int` and aligned `cidr_bounds` helpers and
the required-variable validator. Keep Compose callers explicit about their
env-file. Do not add `aux_addresses`, wildcard trust, or a second allocation
mechanism.

## Acceptance criteria

The two rendered IPAM definitions reserve the same explicit gateway and a
dynamic range that excludes the trusted edge. The template and tracked CI
input agree on all four nonsecret values, and all current variable references
are inventoried. Pure cases include default and alternative RFC1918 parents,
missing inputs, malformed/trailing-dot values, nonaligned and outside ranges,
overlap with fixed hosts, gateway=edge, and injection text. The expanded 35-row
union manifest reproduces its path hashes while preserving the accepted
OpenAPI/JWT artifacts.

## Verification

Run only Git/hash/diff checks, Git Bash syntax, static text checks, and synthetic
validator cases in this lease. Record exit codes and evidence in
`i3-checks.json`, `i3-source-freeze.md`, and the bounded logs. Docker Compose
config-only checks are a separately authorized follow-up gate; no config check
starts a service or creates a network. Full clean-start allocation evidence and
fresh independent review remain external gates.

## Do not

Do not regenerate OpenAPI or frontend types, alter schemas/auth, read real
secrets, copy source evidence, claim R3 runtime proof, weaken validation,
reserve the edge with `aux_addresses`, or run a whole stack. Keep I2's original
finding distinguishable from this source correction and from later runtime
evidence.
