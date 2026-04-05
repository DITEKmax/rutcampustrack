---
phase: 27-web-push-backend
plan: "02"
subsystem: api
tags: [web-push, spring-boot, mongodb, aop, security, role-check, push-subscription]

requires:
  - phase: 27-01
    provides: notification-api-contract PushApi interface, DTOs (SubscribeRequest/UnsubscribeRequest/VapidPublicKeyResponse), UserRole enum, WebPushConfig bean

provides:
  - @RequireRole AOP security infrastructure (RequireRole, RoleCheckAspect, RequestContext, UserContextFilter) in notification-service
  - PushSubscriptionDocument MongoDB document with user_id, group_id, endpoint, p256dh, auth fields
  - PushSubscriptionRepository with findAllByGroupId + deleteByUserIdAndEndpoint + deleteByEndpoint
  - PushMongoConfig with uniq_user_endpoint compound index and idx_group_id index
  - PushController implementing PushApi: getVapidPublicKey (200), subscribe (201), unsubscribe (204)
  - 14 unit tests covering role enforcement, filter header population, controller behaviour, index config

affects:
  - 27-03 (PushService uses PushSubscriptionRepository.findAllByGroupId for fanout delivery)

tech-stack:
  added: []
  patterns:
    - "@RequireRole AOP pattern mirrored from attendance-service — copies 4 files with package rename and UserRole import change"
    - "RequestContext.ScopedProxyMode.TARGET_CLASS mandatory pattern for request-scoped beans injected into singletons"
    - "userId from Gateway X-User-Id header (not request body) — T-27-01 spoofing mitigation"
    - "deleteByUserIdAndEndpoint scopes unsubscribe to current user — T-27-07 information disclosure mitigation"

key-files:
  created:
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/RequireRole.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/RoleCheckAspect.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/UserContextFilter.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/RequestContext.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/exception/AccessDeniedException.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushSubscriptionDocument.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushSubscriptionRepository.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushController.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/PushMongoConfig.java
    - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/security/SecurityInfrastructureTest.java
    - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushControllerTest.java
    - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushSubscriptionRepositoryTest.java
  modified: []

key-decisions:
  - "AccessDeniedException placed in exception package (not security) — mirrors attendance-service package layout"
  - "PushSubscriptionRepositoryTest uses structural/reflection tests for index config rather than live MongoDB — consistent with unit-test-only approach for this service"

patterns-established:
  - "@RequireRole + RoleCheckAspect: same 4-file pattern as attendance-service, copy with package rename for each new service"
  - "UserContextFilter reads X-User-Id/X-User-Role/X-Group-Id/X-Is-Headman — gateway header contract"

requirements-completed:
  - PUSH-02
  - PUSH-03

duration: 7min
completed: "2026-04-05"
---

# Phase 27 Plan 02: Push Subscription CRUD and Security AOP Summary

**@RequireRole AOP security with RequestContext (4 files), PushController implementing PushApi with MongoDB subscription persistence (subscribe/unsubscribe/VAPID), 14 unit tests, all threats T-27-01/T-27-05/T-27-06/T-27-07 mitigated**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-05T20:13:11Z
- **Completed:** 2026-04-05T20:20:31Z
- **Tasks:** 2
- **Files modified:** 12 (all new)

## Accomplishments

- Created complete @RequireRole AOP infrastructure (RequireRole annotation, RoleCheckAspect, RequestContext with ScopedProxyMode.TARGET_CLASS, UserContextFilter) by adapting attendance-service pattern with notification package imports
- Implemented PushController implementing PushApi contract: getVapidPublicKey returns VAPID public key, subscribe stores to MongoDB with userId/groupId from Gateway headers, unsubscribe removes by userId+endpoint
- Created PushSubscriptionDocument @Document with 7 fields, PushSubscriptionRepository with 3 query methods, PushMongoConfig with compound unique index and group_id index

## Task Commits

Each task was committed atomically:

1. **Task 1: Security infrastructure @RequireRole AOP** - `818fa55` (feat)
2. **Task 2: PushSubscription MongoDB + PushController** - `4015d60` (feat)

## Files Created/Modified

- `services/notification-service/notification-app/.../security/RequireRole.java` - @interface RequireRole with UserRole[] value()
- `services/notification-service/notification-app/.../security/RoleCheckAspect.java` - @Aspect intercepting @RequireRole, throws AccessDeniedException on mismatch
- `services/notification-service/notification-app/.../security/RequestContext.java` - Request-scoped bean with ScopedProxyMode.TARGET_CLASS, userId/role/groupId/headman
- `services/notification-service/notification-app/.../security/UserContextFilter.java` - OncePerRequestFilter reading X-User-Id/X-User-Role/X-Group-Id/X-Is-Headman
- `services/notification-service/notification-app/.../exception/AccessDeniedException.java` - RuntimeException thrown by RoleCheckAspect
- `services/notification-service/notification-app/.../push/PushSubscriptionDocument.java` - @Document(push_subscriptions) with 7 snake_case @Field mappings
- `services/notification-service/notification-app/.../push/PushSubscriptionRepository.java` - MongoRepository with findAllByGroupId, deleteByUserIdAndEndpoint, deleteByEndpoint
- `services/notification-service/notification-app/.../push/PushController.java` - Implements PushApi, @RequireRole(STUDENT) on all 3 methods, userId from RequestContext
- `services/notification-service/notification-app/.../config/PushMongoConfig.java` - @PostConstruct creates uniq_user_endpoint + idx_group_id indexes
- `services/notification-service/notification-app/.../security/SecurityInfrastructureTest.java` - 4 tests for RoleCheckAspect and UserContextFilter
- `services/notification-service/notification-app/.../push/PushControllerTest.java` - 8 tests: VAPID key, subscribe (201, userId, groupId), unsubscribe (204), @RequireRole on all methods
- `services/notification-service/notification-app/.../push/PushSubscriptionRepositoryTest.java` - 2 structural tests for PushMongoConfig @PostConstruct

## Decisions Made

- **AccessDeniedException location**: Placed in `exception` package (not `security`) to mirror the attendance-service package layout where it also lives in the exception package.
- **Structural tests for MongoDB indexes**: PushSubscriptionRepositoryTest uses reflection to verify `@PostConstruct` is present on `initIndexes()` since unit tests cannot run a live MongoDB. Actual index creation verified at integration/live test time.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no additional configuration beyond what Plan 01 already established (VAPID env vars in docker-compose).

## Next Phase Readiness

- Plan 03 can now implement push delivery: `PushSubscriptionRepository.findAllByGroupId(groupId)` retrieves all subscribers for fanout, `webPushService` bean (from Plan 01) is ready for `PushService.send()`
- All 3 PushApi endpoints are live at `/push/vapid-public-key`, `POST /push/subscribe`, `DELETE /push/subscribe`
- STUDENT role enforcement is working — TEACHER/ADMIN will receive 403 from RoleCheckAspect

---

## Self-Check: PASSED

All created files verified to exist on disk. Commits 818fa55 and 4015d60 in git log. All 14 tests pass (BUILD SUCCESSFUL). PushController implements PushApi, contains @RequireRole on all 3 methods, uses requestContext.getUserId() and requestContext.getGroupId(). PushSubscriptionDocument has @Document(collection = "push_subscriptions") and all required @Field annotations. PushMongoConfig contains "uniq_user_endpoint" and "idx_group_id" index names.

---
*Phase: 27-web-push-backend*
*Completed: 2026-04-05*
