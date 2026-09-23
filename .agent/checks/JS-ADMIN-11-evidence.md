# JS-ADMIN-11 — evidence

- Base/branch: 55cb9ba2c119f79d0452e2e221b71b0fcd5a95e2 → codex/js-admin-11.
- Scope/criteria: ADMIN opens «Главная» by default in PWA and TMA; existing sections remain reachable. The dashboard reads GET /api/academic/dashboard/stats and shows active semester, ACTIVE-role user counts, and active registry groups with loading/error/refresh states. Existing response fields remain compatible; totalGroups equals activeGroups.
- Core excerpt: DashboardService.getStats() counts distinct UserRoleGrant.userId by role and ACTIVE status; both group fields use GroupRegistryReadRepository.countAll(null).activeCount(). It preserves SemesterRepository.findByIsActiveTrue().
- Aggregate fixture: GroupRegistryIT.dashboardCountsOnlyActiveRoleGrantsAndRegistryActiveGroups covers active student/teacher grants, suspended and archived grants, a user with both active roles, and active/draft/archive registry groups.

Checks:
- npm run typecheck --workspace @rct/pwa-vue (from frontends): exit 0.
- npm run typecheck --workspace @rct/tma-vue (from frontends): exit 0.
- git diff --check (implementation paths): exit 0.
- gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.group.GroupRegistryIT.dashboardCountsOnlyActiveRoleGrantsAndRegistryActiveGroups": exit 0. JUnit XML reports tests=1, skipped=0, failures=0, errors=0; the Postgres/Testcontainers integration test ran.

Inventory:
- services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardService.java
- services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRoleGrantRepository.java
- services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupRegistryIT.java
- services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/DashboardApi.java
- services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/dashboard/DashboardStatsResponse.java
- frontends/mobile-core/src/features/admin-dashboard/AdminDashboardScreen.vue
- frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-client.ts
- frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-screen.pcss
- frontends/mobile-core/src/features/admin-semester/AdminRoleNavigation.vue
- frontends/mobile-core/src/features/admin-semester/admin-role-navigation.pcss
- frontends/mobile-core/src/shared/session-owner.ts
- frontends/mobile-core/src/index.ts
- frontends/pwa-vue/src/App.vue
- frontends/pwa-vue/src/auth.ts
- frontends/tma-vue/src/App.vue
- frontends/tma-vue/src/tma-session.ts

Limits: PWA/TMA UI has typecheck evidence but no browser/runtime smoke yet. The full desktop 131 dashboard scope is not claimed; this is the compact PWA/TMA ADMIN home.
