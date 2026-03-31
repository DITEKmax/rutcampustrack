---
phase: 06-rest-api-hateoas
plan: 02
subsystem: academic-service
tags: [spring-boot, hateoas, rest, user-crud, group-crud, semester-crud, bcrypt, jpa]
dependency_graph:
  requires: [06-01]
  provides: [academic-service/user-api, academic-service/group-api, academic-service/semester-api]
  affects: [academic-service/academic-app]
tech_stack:
  added: [BCryptPasswordEncoder, SecureRandom, EntityManager.flush, RepresentationModelAssembler]
  patterns: [controller-implements-contract, service-per-domain, assembler-per-domain, role-check-via-aop]
key_files:
  created:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterController.java
  modified:
    - services/academic-service/academic-api-contract/build.gradle.kts
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Semester.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/StudentGroupHistory.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRepository.java
decisions:
  - "UserService.createUser returns EntityModel<UserCreatedResponse> directly to avoid double fetch"
  - "Page<User>->Page<UserResponse> mapping done in controller before passing to PagedResourcesAssembler to match contract signature"
  - "spring-data-commons:3.4.1 added to academic-api-contract — needed for Pageable and PagedResourcesAssembler in contract interfaces"
  - "createdAt @Setter added to entities (User, Group, Semester, StudentGroupHistory) — required since no @PrePersist lifecycle"
  - "Plan 01 branch (worktree-agent-a9170a78) merged into this worktree before execution — plan correctly depends on 06-01"
metrics:
  duration_minutes: 45
  completed_date: "2026-03-30"
  tasks_completed: 2
  files_created: 9
  files_modified: 7
---

# Phase 6 Plan 02: Admin Controllers (User, Group, Semester) Summary

Admin-facing REST controllers and services for User, Group, and Semester domains with BCrypt password, auto-login generation via PostgreSQL sequences, headman cascade revoke, student transfer history, atomic semester activation, and confirmation-guarded deletion.

## What Was Built

### Task 1: User Domain

**UserAssembler** (`RepresentationModelAssembler<User, EntityModel<UserResponse>>`):
- `toModel(User)` — maps to `UserResponse` with self link via `linkTo(methodOn(UserController.class).getUser(id))`
- `toCreatedModel(User, String plainPassword)` — maps to `UserCreatedResponse` (includes `initialPassword` shown once)
- `toResponse(User)` — helper for page mapping

**UserService**:
- `createUser` — login generated with PostgreSQL sequences (`student%05d` / `teacher%05d` / `admin%05d`), 12-char random password from safe charset, BCrypt hash via `BCryptPasswordEncoder`
- `patchUser` — headman revoke cascade: `revokeAllByGroupId(groupId)` when `isHeadman` flipped to false
- `transferStudent` — closes active `StudentGroupHistory`, creates new history record, cascades headman revoke if was headman in old group
- `getMe` — reads `requestContext.getUserId()`

**UserController** (`implements UserApi`):
- `@RequireRole({ADMIN})` on all mutations + getUser/listUsers
- `@RequireRole({STUDENT})` on `/me`
- No `@RequestMapping` — inherited from `UserApi`

### Task 2: Group and Semester Domains

**GroupAssembler** / **GroupService** / **GroupController**:
- createGroup checks code uniqueness via `existsByCode`
- `listGroups(Boolean active, Pageable)` — filtered via `findByIsActive(active, pageable)` (new overload added to GroupRepository)
- `getMyGroupMembers` — fetches `requestContext.getGroupId()`, paginates via `findByGroupId(groupId, pageable)` (new overload in UserRepository)
- `@RequireRole({STUDENT})` on `/my/members`

**SemesterAssembler** / **SemesterService** / **SemesterController**:
- `activateSemester` — atomic: `deactivateAllActive()` then `entityManager.flush()` then `setActive(true)` + `saveAndFlush()` (Pitfall 5 prevention)
- `deleteSemester` — exact name match against `request.confirmation()`, throws `BadRequestException` on mismatch

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing spring-data-commons in academic-api-contract**
- **Found during:** Task 1 compilation
- **Issue:** Contract interfaces use `Pageable` and `PagedResourcesAssembler` from `spring-data-commons`, but the contract module only had `spring-hateoas` which does NOT transitively bring in `spring-data-commons`
- **Fix:** Added `api("org.springframework.data:spring-data-commons:3.4.1")` to `academic-api-contract/build.gradle.kts`
- **Files modified:** `services/academic-service/academic-api-contract/build.gradle.kts`

**2. [Rule 2 - Missing setter] createdAt fields missing @Setter on entities**
- **Found during:** Task 1 implementation
- **Issue:** `User`, `Group`, `Semester`, `StudentGroupHistory` entities have `createdAt` with `updatable = false` but no `@Setter`, making it impossible to set the value at creation time without `@PrePersist`
- **Fix:** Added `@Setter` annotation to `createdAt` in all four entities. Also added `@Setter` to `userId` and `groupId` in `StudentGroupHistory` (needed for new history record creation in `transferStudent`)
- **Files modified:** `entity/User.java`, `entity/Group.java`, `entity/Semester.java`, `entity/StudentGroupHistory.java`

**3. [Rule 2 - Missing repository method] GroupRepository and UserRepository missing pageable overloads**
- **Found during:** Task 2 implementation
- **Issue:** `GroupRepository.findByIsActive(boolean)` returns `List<Group>` but service needs `Page<Group>` for HATEOAS pagination. `UserRepository.findByGroupId(Long)` returns `List<User>` but needs `Page<User>`
- **Fix:** Added `Page<Group> findByIsActive(boolean, Pageable)` to GroupRepository; added `Page<User> findByGroupId(Long, Pageable)` to UserRepository (Spring Data derives both from method name)
- **Files modified:** `repository/GroupRepository.java`, `repository/UserRepository.java`

**4. [Rule 3 - Blocking] Plan 01 dependency not yet in worktree**
- **Found during:** Initial setup
- **Issue:** Plan 02 depends on 06-01 but the worktree branch was at `main` (pre-06-01). Plan 01 was completed by parallel agent on `worktree-agent-a9170a78`
- **Fix:** Merged `worktree-agent-a9170a78` into current branch before starting implementation
- **Impact:** None — fast-forward merge, no conflicts

## Known Stubs

None. All three controllers are fully wired to services with real JPA-backed logic.

## Self-Check

Verified files exist:
- UserController.java, UserService.java, UserAssembler.java — FOUND
- GroupController.java, GroupService.java, GroupAssembler.java — FOUND
- SemesterController.java, SemesterService.java, SemesterAssembler.java — FOUND

Verified commits exist:
- a7c3634 (Task 1: User domain)
- 5afe0c1 (Task 2: Group + Semester domains)

## Self-Check: PASSED
