# Phase 8: Redis Caching - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-31
**Phase:** 08-redis-caching
**Areas discussed:** Caching scope, TTL strategy, Eviction cascades, Testing approach

---

## Caching Scope

### Q1: Which gRPC methods should be cached?

| Option | Description | Selected |
|--------|-------------|----------|
| All reads except 2 | Cache GetGroup, GetGroupMembers, GetActiveSemester, GetCampusGeofence, GetUserById. Skip GetTeacherSubjects (complex joins) and IsHeadman (cheap lookup). Matches '5 cache keys' from roadmap. | ✓ |
| All 7 RPCs | Cache every gRPC method including GetTeacherSubjects and IsHeadman. Maximum DB offload but more eviction complexity. | |
| Only static-like data | Cache only GetActiveSemester and GetCampusGeofence (rarely change). Minimal eviction complexity but limited benefit. | |

**User's choice:** All reads except 2 (Recommended)
**Notes:** Matches roadmap's "5 cache keys" specification.

### Q2: Should REST service layer methods also get @Cacheable?

| Option | Description | Selected |
|--------|-------------|----------|
| gRPC only | Phase scope says 'read-heavy gRPC paths'. REST endpoints are user-facing and less frequent. Keep it focused. | ✓ |
| Both gRPC and REST | Cache at repository/service level so both gRPC and REST benefit. More eviction points to manage. | |
| You decide | Claude picks the best approach based on codebase patterns. | |

**User's choice:** gRPC only (Recommended)
**Notes:** None.

---

## TTL Strategy

### Q3: Per-cache or global TTL?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-cache TTLs | Different TTLs per cache: GetCampusGeofence (hours), GetActiveSemester (minutes), others (5 min). Configurable in application.yml. | ✓ |
| Single global TTL | One TTL for all caches (e.g., 10 minutes). Simpler config but GetCampusGeofence doesn't need frequent refresh. | |
| You decide | Claude picks sensible defaults per cache key. | |

**User's choice:** Per-cache TTLs (Recommended)
**Notes:** None.

### Q4: TTL range for most volatile cache (GetGroupMembers)?

| Option | Description | Selected |
|--------|-------------|----------|
| 5 minutes | Short enough that transfers reflect quickly, long enough to absorb repeated calls during a class session. | ✓ |
| 1 minute | Near real-time consistency. Higher Redis traffic. | |
| 15 minutes | Longer cache, fewer DB hits. Relies more on active eviction after mutations. | |

**User's choice:** 5 minutes (Recommended)
**Notes:** None.

---

## Eviction Cascades

### Q5: Where should @CacheEvict annotations live?

| Option | Description | Selected |
|--------|-------------|----------|
| On REST service methods | Put @CacheEvict on UserService.transferStudent(), GroupService.update(), SemesterService.activate(), etc. Mutations happen here, so eviction is co-located with the change. | ✓ |
| Separate CacheEvictionService | Dedicated service called by REST services after mutations. Centralizes eviction logic but adds indirection. | |
| You decide | Claude picks the cleanest approach. | |

**User's choice:** On REST service methods (Recommended)
**Notes:** None.

### Q6: Student transfer dual eviction strategy?

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit dual evict | @CacheEvict for old group + @CacheEvict for new group, both by group ID. Requires @Caching annotation to combine multiple evictions on one method. | ✓ |
| Evict all group_members | Use allEntries=true on the group_members cache. Simpler but nukes the entire cache, not just affected groups. | |
| You decide | Claude picks the best eviction strategy for transfers. | |

**User's choice:** Explicit dual evict (Recommended)
**Notes:** None.

---

## Testing Approach

### Q7: Test infrastructure for caching?

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers Redis | Real Redis via Testcontainers alongside existing PostgreSQL container. Can verify TTL with RedisTemplate commands. Tests prove actual caching behavior. | ✓ |
| Embedded Redis (Coredis) | Embedded Redis for tests — lighter but less reliable, limited TTL inspection capabilities. | |
| You decide | Claude picks the best test infrastructure. | |

**User's choice:** Testcontainers Redis (Recommended)
**Notes:** None.

### Q8: How to verify 'exactly one DB query'?

| Option | Description | Selected |
|--------|-------------|----------|
| DataSource proxy + query counter | Use datasource-proxy or a simple CountingDataSource wrapper to assert exact query count between calls. Precise and deterministic. | ✓ |
| Hibernate statistics | Enable Hibernate statistics, reset before test, check query count after. Built-in but counts all queries globally. | |
| You decide | Claude picks the verification approach. | |

**User's choice:** DataSource proxy + query counter (Recommended)
**Notes:** None.

---

## Claude's Discretion

- Cache key naming convention
- RedisCacheConfiguration bean structure and serializer choice
- Exact @Caching annotation structure for multi-evict methods

## Deferred Ideas

None — discussion stayed within phase scope.
