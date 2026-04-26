# Phase 54: Headman Web Cabinet — Group Management + Subjects - Discussion Log (Assumptions Mode)

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in CONTEXT.md — this log preserves the analysis.

**Date:** 2026-04-09
**Phase:** 54-headman-web-cabinet-group-management-subjects
**Mode:** assumptions
**Areas analyzed:** WPAN-13 Backend Fix, Angular Route Registration, HeadmanApiService, Component Architecture, Testing

## Assumptions Presented

### WPAN-13 Backend Fix
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| `RoleCheckAspect` is the 403 source — it rejects STUDENT role even when `isHeadman=true` | Confident | `RoleCheckAspect.java`: `Arrays.asList(required).contains(actual)` — no headman special-case; `AssistantController` uses `@RequireRole({UserRole.STUDENT})` which is semantically correct but AOP blocks before service runs |
| Fix requires adding `requestContext.isHeadman()` bypass in AOP only — no enum change | Confident | `RequestContext.java` already has `isHeadman()` field populated by gateway headers; `AssistantService.requireHeadman()` provides second-layer check |
| Gateway already forwards `X-Is-Headman` — no Angular interceptor change needed | Confident | `JwtAuthenticationFilter.java` line with `X-Is-Headman` header injection confirmed |

### Angular Route Registration
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Replace placeholder, add 2 new routes under existing `/headman` block | Confident | `app.routes.ts` shows `HeadmanPlaceholderComponent` at `/headman/dashboard`; 2 new routes needed per phase goal |
| Routes use `headmanGuard` (already imported and wired) | Confident | `app.routes.ts`: headman block already has `canActivate: [headmanGuard]` |

### HeadmanApiService
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| New service in `features/headman/shared/` following `StudentApiService` pattern | Confident | `StudentApiService` is the established pattern for feature-level API services; no shared headman service exists yet |
| Dashboard data assembled via `forkJoin` (no dedicated dashboard endpoint likely) | Likely | No `/api/academic/headman/dashboard` endpoint found in `DashboardApi.java` (admin-only); research should verify |

### Component Architecture
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Reuse `StatCardComponent` for dashboard stats | Confident | UI-SPEC §1 explicitly states "reuse StatCardComponent as-is" |
| Sidebar `NavItem` needs `isHeadman` filter awareness | Likely | `UI-SPEC §0` states sidebar `filteredNavItems` must show headman items only for headmen; current `NavItem` interface unknown — research must check |

### Testing
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Extend `RoleCheckAspect` test with headman pass case | Confident | Standard practice; `RoleCheckAspect.java` is testable via Mockito mock of `RequestContext` |
| No guard spec changes needed | Confident | UI-SPEC §0 explicitly: "No guard code change required for HEAD-WEB-01 — the guard is correct as implemented" |

## Corrections Made

No corrections — all assumptions confirmed (assumptions mode; no user interaction step taken given rich UI-SPEC already present).

## External Research

No external research performed — codebase provided sufficient evidence for all decisions.
Backend APIs and Angular patterns are fully established from prior phases.
