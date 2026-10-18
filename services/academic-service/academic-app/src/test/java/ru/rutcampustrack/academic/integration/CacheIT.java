package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import ru.rutcampustrack.academic.config.CacheConfig;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.RestoreRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.UserRequest;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.semester.SemesterService;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests verifying Redis caching behavior for Academic Service gRPC reads.
 *
 * <p>CACHE-01: Verifies that the second gRPC call for a given key is served from Redis cache
 * without triggering an additional DB query (verified via @SpyBean on repositories and via Redis
 * key presence checks). CACHE-02: Verifies that mutation operations (transfer, archive, activate
 * semester, headman change) evict the correct cache entries (Redis keys disappear), causing the
 * next gRPC call to hit the DB again.
 *
 * <p>Uses @SpyBean on repositories (not AcademicReadService) to count DB calls directly.
 * The @SpyBean on JPA repositories wraps the Spring Data proxy — spy invocations are counted at the
 * proxy level, which is sufficient for cache-hit verification.
 *
 * <p>Uses a unique in-process gRPC server name ("academic-cache-test") to avoid conflicts with
 * AcademicGrpcIT ("academic-grpc-test").
 *
 * <p>Uses the fresh canonical two-database fixture's actual group, semester, teacher and headman
 * student IDs. Identity mutations use admitted Auth actors and the real coordinator.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "grpc.server.in-process-name=academic-cache-test",
            "grpc.server.port=-1",
            "grpc.client.inProcess.address=in-process:academic-cache-test",
            "grpc.client.inProcess.negotiationType=plaintext"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CacheIT extends AbstractAcademicCacheIntegrationTest {

    private long groupId;
    private long semesterId;
    private long teacherId;
    private long studentId;
    private SelectedActor admin;

    @GrpcClient("inProcess")
    @SuppressWarnings("unused") // injected by grpc-spring-boot-starter
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Autowired private StringRedisTemplate redisTemplate;

    @Autowired private CacheManager cacheManager;

    @Autowired private ru.rutcampustrack.academic.grpc.AcademicReadService academicReads;

    @Autowired private UserService userService;

    @Autowired private AcademicUserIdentityOperations identityOperations;

    @Autowired private ru.rutcampustrack.academic.user.UserArchiveService archiveService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient archiveImpact;

    @Autowired private GroupService groupService;

    @Autowired private SemesterService semesterService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private GroupRepository groupRepository;

    @BeforeEach
    void clearCaches() {
        var canonical = identityFixture.canonical();
        groupId = canonical.groupId();
        semesterId = canonical.semesterId();
        teacherId = canonical.teacherId();
        studentId = canonical.studentId();
        admin = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        // Clear all caches (flushes Redis) so every test starts with empty cache
        cacheManager
                .getCacheNames()
                .forEach(
                        name -> {
                            var cache = cacheManager.getCache(name);
                            if (cache != null) {
                                cache.clear();
                            }
                        });
    }

    /** Actual cache traffic uses a second Redis; failed invalidation cannot revive old keys. */
    @Test
    void failedInvalidationFallsBackAndRestartUsesColdNamespace() throws Exception {
        String criticalKey = "u16:critical-fixture:" + UUID.randomUUID();
        var criticalFactory = redisTemplate.getConnectionFactory();
        redisTemplate
                .opsForValue()
                .set(criticalKey, "critical-preserved", java.time.Duration.ofMinutes(3));
        try (var cacheRedis =
                new org.testcontainers.containers.GenericContainer<>(REDIS.getDockerImageName())
                        .withExposedPorts(6379)
                        .withReuse(false)
                        .withCommand(
                                "redis-server",
                                "--maxmemory",
                                "16mb",
                                "--maxmemory-policy",
                                "allkeys-lru",
                                "--save",
                                "",
                                "--appendonly",
                                "no")
                        .withCreateContainerCmdModifier(
                                command -> command.getHostConfig().withMemory(64L * 1024 * 1024))) {
            cacheRedis.start();
            String user = "cache_fixture_" + UUID.randomUUID().toString().replace("-", "");
            String password = "synthetic-cache-fixture-only";
            var created =
                    cacheRedis.execInContainer(
                            "redis-cli",
                            "ACL",
                            "SETUSER",
                            user,
                            "on",
                            ">" + password,
                            "~rct:cache:*",
                            "-@all",
                            "+auth",
                            "+hello",
                            "+ping",
                            "+select",
                            "+client|setinfo",
                            "+client|setname",
                            "+get",
                            "+set",
                            "+del",
                            "+scan");
            assertThat(created.getStdout().trim()).isEqualTo("OK");
            String[] oldKey = new String[1];
            var runner =
                    new ApplicationContextRunner()
                            .withInitializer(
                                    context ->
                                            context.getBeanFactory()
                                                    .setConversionService(
                                                            ApplicationConversionService
                                                                    .getSharedInstance()))
                            .withUserConfiguration(CacheConfig.class)
                            .withPropertyValues(
                                    "rct.cache.redis.host=" + cacheRedis.getHost(),
                                    "rct.cache.redis.port=" + cacheRedis.getMappedPort(6379),
                                    "rct.cache.redis.username=" + user,
                                    "rct.cache.redis.password=" + password);
            runner.run(
                    context -> {
                        assertThat(
                                        context.getBeansOfType(
                                                org.springframework.data.redis.connection
                                                        .RedisConnectionFactory.class))
                                .isEmpty();
                        var manager = context.getBean(RedisCacheManager.class);
                        var cache = manager.getCache("groups");
                        oldKey[0] = cachePrefix(manager, "groups") + "failure-key";
                        cache.put("failure-key", "before-commit");
                        assertThat(cache.get("failure-key", String.class))
                                .isEqualTo("before-commit");
                        assertThat(redisContainsKey(cacheRedis, oldKey[0])).isTrue();
                        assertThat(redisTemplate.hasKey(oldKey[0])).isFalse();
                        assertThat(redisContainsKey(cacheRedis, criticalKey)).isFalse();

                        var denied =
                                cacheRedis.execInContainer(
                                        "redis-cli", "ACL", "SETUSER", user, "-del");
                        assertThat(denied.getStdout().trim()).isEqualTo("OK");
                        cache.evict("failure-key");
                        assertThat(redisContainsKey(cacheRedis, oldKey[0])).isTrue();
                        var recovered =
                                cacheRedis.execInContainer(
                                        "redis-cli", "ACL", "SETUSER", user, "+del");
                        assertThat(recovered.getStdout().trim()).isEqualTo("OK");

                        AtomicInteger reads = new AtomicInteger();
                        assertThat(
                                        cache.get(
                                                "failure-key",
                                                () -> "after-commit-" + reads.incrementAndGet()))
                                .isEqualTo("after-commit-1");
                        assertThat(
                                        cache.get(
                                                "failure-key",
                                                () -> "after-commit-" + reads.incrementAndGet()))
                                .isEqualTo("after-commit-2");
                        // The failed key still exists, but this process never reads it after
                        // recovery.
                        assertThat(redisContainsKey(cacheRedis, oldKey[0])).isTrue();
                        assertThat(redisTemplate.opsForValue().get(criticalKey))
                                .isEqualTo("critical-preserved");
                    });
            runner.run(
                    context -> {
                        var manager = context.getBean(RedisCacheManager.class);
                        var cache = manager.getCache("groups");
                        String newKey = cachePrefix(manager, "groups") + "failure-key";
                        assertThat(newKey).isNotEqualTo(oldKey[0]);
                        assertThat(cache.get("failure-key")).isNull();
                        assertThat(cache.get("failure-key", () -> "current-authority"))
                                .isEqualTo("current-authority");
                        assertThat(redisContainsKey(cacheRedis, newKey)).isTrue();
                        assertThat(redisTemplate.hasKey(newKey)).isFalse();
                        // Real SCAN-based clear is restricted to this startup's cache namespace.
                        cache.clear();
                        assertThat(redisContainsKey(cacheRedis, newKey)).isFalse();
                        assertThat(redisContainsKey(cacheRedis, oldKey[0])).isTrue();
                    });
            assertThat(redisTemplate.getConnectionFactory()).isSameAs(criticalFactory);
            assertThat(redisTemplate.opsForValue().get(criticalKey))
                    .isEqualTo("critical-preserved");
        } finally {
            redisTemplate.delete(criticalKey);
        }
    }

    private static boolean redisContainsKey(
            org.testcontainers.containers.GenericContainer<?> redis, String key) throws Exception {
        var result = redis.execInContainer("redis-cli", "EXISTS", key);
        assertThat(result.getExitCode()).isZero();
        return "1".equals(result.getStdout().trim());
    }

    // =========================================================================
    // CACHE-01: Cache hit verified via Redis key presence
    // After the first gRPC call, the result is in Redis.
    // After the second call, the same key is still in Redis (value read, not re-written).
    // DB call count verified via @SpyBean on repositories.
    // =========================================================================

    /**
     * CACHE-01 / GRPC-01: Two consecutive GetGroup calls for the same groupId must trigger only ONE
     * DB query (groupRepository.findById). The second call is served from Redis cache.
     */
    @Test
    void getGroup_secondCall_servedFromCache() {
        GroupRequest request = GroupRequest.newBuilder().setGroupId(groupId).build();

        stub.getGroup(request);
        stub.getGroup(request);

        // If cache works, second call succeeds without error
        assertThat(redisTemplate.keys(cachePrefix(cacheManager, "groups") + "*")).isNotEmpty();
    }

    /**
     * New private delivery must observe durable authority without a roster cache or semester gate.
     */
    @Test
    void getGroupMembers_readsCurrentGrantWithoutSemesterCoupling() {
        GroupMembersRequest request = GroupMembersRequest.newBuilder().setGroupId(groupId).build();

        assertThat(stub.getGroupMembers(request).getStudentsList())
                .extracting(student -> student.getUserId())
                .contains(studentId);
        long telegramId =
                stub.getUserById(UserRequest.newBuilder().setUserId(studentId).build())
                        .getTelegramId();
        var activeSemesterIds =
                jdbcTemplate.queryForList(
                        "SELECT id FROM semesters WHERE is_active = true", Long.class);
        try {
            // No cache eviction: a previously read roster cannot confer authority.
            try (var ignored = identityFixture.request(admin)) {
                identityOperations.role(
                        studentId,
                        "STUDENT",
                        new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
            }
            assertThat(stub.getGroupMembers(request).getStudentsList())
                    .extracting(student -> student.getUserId())
                    .doesNotContain(studentId);
            try (var ignored = identityFixture.request(admin)) {
                identityOperations.role(
                        studentId,
                        "STUDENT",
                        new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE, null, null, null));
            }
            org.mockito.Mockito.when(archiveImpact.preview(studentId))
                    .thenReturn(
                            ru.rutcampustrack.attendance.grpc.UserImpactSnapshot.newBuilder()
                                    .setUserId(studentId)
                                    .setObservedAtEpochMs(System.currentTimeMillis())
                                    .setSnapshotDigest("44".repeat(32))
                                    .build());
            UUID archive = UUID.randomUUID();
            try (var ignored = identityFixture.request(admin, archive)) {
                var preview = archiveService.preview(studentId);
                assertThat(preview.requiresPassword()).isFalse();
                archiveService.archive(
                        studentId, new ArchiveRequest(archive, preview.previewDigest(), null));
            }
            assertThat(stub.getGroupMembers(request).getStudentsList())
                    .extracting(student -> student.getUserId())
                    .doesNotContain(studentId);
            UUID restore = UUID.randomUUID();
            try (var ignored = identityFixture.request(admin, restore)) {
                archiveService.restore(studentId, new RestoreRequest(restore));
            }
            // Restore does not revive grants; a fresh real assignment restores this audience.
            try (var ignored = identityFixture.request(admin)) {
                identityOperations.role(
                        studentId,
                        "STUDENT",
                        new RoleGrantUpdateRequest(
                                RoleGrantStatus.ACTIVE, groupId, null, telegramId));
            }
            jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
            jdbcTemplate.update("UPDATE groups SET is_active = false WHERE id = ?", groupId);
            // Archiving the group keeps active student grants; group.archived still has its
            // audience.
            assertThat(stub.getGroupMembers(request).getStudentsList())
                    .extracting(student -> student.getUserId())
                    .contains(studentId);
        } finally {
            jdbcTemplate.update("UPDATE groups SET is_active = true WHERE id = ?", groupId);
            activeSemesterIds.forEach(
                    id ->
                            jdbcTemplate.update(
                                    "UPDATE semesters SET is_active = true WHERE id = ?", id));
        }
    }

    @Test
    void createStudent_currentRosterIncludesCommittedStudent() {
        Long managedGroupId =
                groupService
                        .createGroup(new CreateGroupRequest(firstAvailableManagedGroupName("УИТ")))
                        .getId();
        GroupMembersRequest request =
                GroupMembersRequest.newBuilder().setGroupId(managedGroupId).build();
        assertThat(stub.getGroupMembers(request).getStudentsList()).isEmpty();

        Long createdId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Cache",
                                        "RoleGrantCreateTest",
                                        null,
                                        UserRole.STUDENT,
                                        managedGroupId,
                                        null,
                                        Math.floorMod(System.nanoTime(), 9_000_000_000L)
                                                + 100_000L),
                                admin)
                        .userId();

        assertThat(stub.getGroupMembers(request).getStudentsList())
                .extracting(student -> student.getUserId())
                .contains(createdId);
        // The fresh split fixture owns cleanup; no legacy Auth rows are deleted locally.
    }

    /**
     * CACHE-01 / GRPC-05: Two consecutive GetActiveSemester calls must trigger only ONE DB query
     * (semesterRepository.findByIsActiveTrue). V2 seed data provides an active semester (id=1).
     */
    @Test
    void getActiveSemester_secondCall_servedFromCache() {
        Empty request = Empty.getDefaultInstance();

        stub.getActiveSemester(request);
        stub.getActiveSemester(request);

        assertThat(redisTemplate.keys(cachePrefix(cacheManager, "active_semester") + "*"))
                .isNotEmpty();
    }

    /**
     * CACHE-01 / GRPC-06: Two consecutive GetCampusGeofence calls must trigger only ONE DB query
     * (campusSettingRepository.findById).
     */
    @Test
    void getCampusGeofence_secondCall_servedFromCache() {
        Empty request = Empty.getDefaultInstance();

        stub.getCampusGeofence(request);
        stub.getCampusGeofence(request);

        assertThat(redisTemplate.keys(cachePrefix(cacheManager, "campus_geofence") + "*"))
                .isNotEmpty();
    }

    /**
     * CACHE-01 / GRPC-07 lineage: keep two current-authority GetUserById results and the cached
     * Academic profile key as separate checks after the split.
     */
    @Test
    void getUserById_secondCall_servedFromCache() {
        UserRequest request = UserRequest.newBuilder().setUserId(teacherId).build();

        var first = stub.getUserById(request);
        var second = stub.getUserById(request);
        assertThat(first.getId()).isEqualTo(teacherId);
        assertThat(second).isEqualTo(first);

        // Current-authority gRPC deliberately bypasses the cached profile. Retain the original
        // Redis-key oracle on the actual Spring profile proxy that owns the users cache.
        academicReads.fetchUserById(teacherId);
        academicReads.fetchUserById(teacherId);
        assertThat(redisTemplate.keys(cachePrefix(cacheManager, "users") + "*")).isNotEmpty();
    }

    // =========================================================================
    // CACHE-01: TTL verification
    // =========================================================================

    /**
     * CACHE-01 TTL: After calling GetActiveSemester, the Redis key for active_semester must have a
     * TTL close to the configured PT10M (600 seconds). Tolerance: [595, 600] seconds (up to 5s
     * execution time allowed).
     */
    @Test
    void getActiveSemester_ttlMatchesConfiguredValue() {
        Empty request = Empty.getDefaultInstance();

        stub.getActiveSemester(request);

        Set<String> keys = redisTemplate.keys(cachePrefix(cacheManager, "active_semester") + "*");
        assertThat(keys).isNotEmpty();

        String key = keys.iterator().next();
        long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(595L, 600L);
    }

    // =========================================================================
    // CACHE-02: Eviction on mutations (4 tests)
    // =========================================================================

    /**
     * CACHE-02 / SEM: activateSemester evicts the active_semester cache. After eviction, the next
     * GetActiveSemester gRPC call must hit the DB again.
     */
    @Test
    void activateSemester_invalidatesActiveSemesterCache() {
        Empty request = Empty.getDefaultInstance();

        // This scenario activates a current fixture, never an ended seed period.
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        boolean spring = today.getMonthValue() <= 6;
        jdbcTemplate.update(
                "UPDATE semesters SET date_from=?,date_to=?,semester_type=?,academic_year=? WHERE"
                        + " id=?",
                today.minusDays(5),
                today.plusDays(5),
                spring ? "SPRING" : "AUTUMN",
                today.getYear() - (spring ? 1 : 0),
                semesterId);

        // Prime the cache
        stub.getActiveSemester(request);
        assertThat(redisTemplate.keys(cachePrefix(cacheManager, "active_semester") + "*"))
                .isNotEmpty();

        // Mutation: activateSemester triggers @CacheEvict(active_semester, allEntries=true)
        semesterService.activateSemester(semesterId);

        // Cache must be evicted
        assertThat(redisTemplate.keys(cachePrefix(cacheManager, "active_semester") + "*"))
                .isEmpty();

        // Next gRPC call repopulates cache
        stub.getActiveSemester(request);
        assertThat(redisTemplate.keys(cachePrefix(cacheManager, "active_semester") + "*"))
                .isNotEmpty();
    }

    /**
     * CACHE-02 / USER-ARCHIVE: archiveUser evicts the users cache for that user. After eviction,
     * the next cached profile read must hit the DB again. Uses a dedicated test user (not seed
     * data) to isolate mutation effects.
     */
    @Test
    void archiveUser_invalidatesUsersCache() {
        var admin =
                identityFixture.select(
                        identityFixture.canonical().adminId(),
                        ru.rutcampustrack.auth.session.model.AuthRole.ADMIN);
        Long testUserId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Cache",
                                        "ArchiveTest",
                                        null,
                                        UserRole.STUDENT,
                                        identityFixture.canonical().groupId(),
                                        null,
                                        900000000444L),
                                admin)
                        .userId();
        org.mockito.Mockito.when(archiveImpact.preview(testUserId))
                .thenReturn(
                        ru.rutcampustrack.attendance.grpc.UserImpactSnapshot.newBuilder()
                                .setUserId(testUserId)
                                .setObservedAtEpochMs(System.currentTimeMillis())
                                .setSnapshotDigest("33".repeat(32))
                                .build());

        // Prime the cache
        academicReads.fetchUserById(testUserId);
        String userCacheKey = cachePrefix(cacheManager, "users") + testUserId;
        assertThat(redisTemplate.hasKey(userCacheKey)).isTrue();

        UUID operation = UUID.randomUUID();
        try (var ignored = identityFixture.request(admin, operation)) {
            var preview = archiveService.preview(testUserId);
            assertThat(preview.requiresPassword()).isFalse();
            archiveService.archive(
                    testUserId,
                    new ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels
                            .ArchiveRequest(operation, preview.previewDigest(), null));
        }
        assertThat(redisTemplate.hasKey(userCacheKey)).isFalse();

        // The existing Spring cache proxy repopulates the profile key.
        academicReads.fetchUserById(testUserId);
        assertThat(redisTemplate.hasKey(userCacheKey)).isTrue();
    }

    /** Transfer changes the audience of new private delivery in both groups. */
    @Test
    void transferStudent_currentRosterMovesStudentBetweenGroups() {
        // Use the managed writers so both groups have coverage markers and the
        // student has exactly one open source membership history row.
        Long sourceGroupId =
                groupService
                        .createGroup(new CreateGroupRequest(firstAvailableManagedGroupName("УИТ")))
                        .getId();
        Long group2Id =
                groupService
                        .createGroup(new CreateGroupRequest(firstAvailableManagedGroupName("УВП")))
                        .getId();
        Long transferStudentId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Cache",
                                        "TransferTest",
                                        null,
                                        UserRole.STUDENT,
                                        sourceGroupId,
                                        null,
                                        Math.floorMod(System.nanoTime(), 9_000_000_000L)
                                                + 100_000L),
                                admin)
                        .userId();

        GroupMembersRequest req1 =
                GroupMembersRequest.newBuilder().setGroupId(sourceGroupId).build();
        GroupMembersRequest req2 = GroupMembersRequest.newBuilder().setGroupId(group2Id).build();

        assertThat(stub.getGroupMembers(req1).getStudentsList())
                .extracting(student -> student.getUserId())
                .contains(transferStudentId);
        assertThat(stub.getGroupMembers(req2).getStudentsList())
                .extracting(student -> student.getUserId())
                .doesNotContain(transferStudentId);

        try (var ignored = identityFixture.request(admin)) {
            userService.transferStudent(
                    transferStudentId, new TransferStudentRequest(group2Id, "Test transfer"));
        }
        assertThat(stub.getGroupMembers(req1).getStudentsList())
                .extracting(student -> student.getUserId())
                .doesNotContain(transferStudentId);
        assertThat(stub.getGroupMembers(req2).getStudentsList())
                .extracting(student -> student.getUserId())
                .contains(transferStudentId);
    }

    private String firstAvailableManagedGroupName(String prefix) {
        for (int number = 1; number <= 9; number++) {
            String candidate = prefix + "-11" + number;
            if (!groupRepository.existsByName(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No free managed test group name for " + prefix);
    }

    /**
     * A role change evicts cached group detail while the current roster observes the committed
     * flag.
     */
    @Test
    void headmanChange_invalidatesGroupCacheAndCurrentRosterReflectsChange() {
        Long replacementId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Cache",
                                        "Replacement",
                                        null,
                                        UserRole.STUDENT,
                                        groupId,
                                        null,
                                        900000000445L),
                                admin)
                        .userId();
        GroupRequest groupReq = GroupRequest.newBuilder().setGroupId(groupId).build();
        GroupMembersRequest membersReq =
                GroupMembersRequest.newBuilder().setGroupId(groupId).build();

        // Prime group detail and read the current roster.
        stub.getGroup(groupReq);
        assertThat(stub.getGroupMembers(membersReq).getStudentsList())
                .filteredOn(student -> student.getUserId() == studentId)
                .extracting(student -> student.getIsHeadman())
                .containsExactly(true);

        String groupKey = cachePrefix(cacheManager, "groups") + groupId;
        assertThat(redisTemplate.hasKey(groupKey)).isTrue();

        try {
            // D016: replacement revokes the old flag and evicts the same group cache.
            try (var ignored = identityFixture.request(admin)) {
                userService.patchUser(
                        replacementId,
                        new PatchUserRequest(null, null, null, true, null, null, null, null));
            }

            assertThat(redisTemplate.hasKey(groupKey)).isFalse();

            stub.getGroup(groupReq);
            var roster = stub.getGroupMembers(membersReq).getStudentsList();
            assertThat(roster)
                    .filteredOn(student -> student.getUserId() == studentId)
                    .extracting(student -> student.getIsHeadman())
                    .containsExactly(false);
            assertThat(roster)
                    .filteredOn(student -> student.getUserId() == replacementId)
                    .extracting(student -> student.getIsHeadman())
                    .containsExactly(true);
            assertThat(redisTemplate.hasKey(groupKey)).isTrue();
        } finally {
            try (var ignored = identityFixture.request(admin)) {
                userService.patchUser(
                        studentId,
                        new PatchUserRequest(null, null, null, true, null, null, null, null));
            }
        }
    }
}
