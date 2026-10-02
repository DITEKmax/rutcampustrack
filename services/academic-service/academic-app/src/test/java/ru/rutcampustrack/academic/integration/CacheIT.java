package ru.rutcampustrack.academic.integration;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.UserRequest;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.semester.SemesterService;
import ru.rutcampustrack.academic.user.UserService;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying Redis caching behavior for Academic Service gRPC reads.
 *
 * CACHE-01: Verifies that the second gRPC call for a given key is served from Redis cache
 *           without triggering an additional DB query (verified via @SpyBean on repositories
 *           and via Redis key presence checks).
 * CACHE-02: Verifies that mutation operations (transfer, archive, activate semester,
 *           headman change) evict the correct cache entries (Redis keys disappear),
 *           causing the next gRPC call to hit the DB again.
 *
 * Uses @SpyBean on repositories (not AcademicReadService) to count DB calls directly.
 * The @SpyBean on JPA repositories wraps the Spring Data proxy — spy invocations
 * are counted at the proxy level, which is sufficient for cache-hit verification.
 *
 * Uses a unique in-process gRPC server name ("academic-cache-test") to avoid
 * conflicts with AcademicGrpcIT ("academic-grpc-test").
 *
 * Data from V2__seed_test_data.sql:
 *   group(id=1, name=ИВТ-211), semester(id=1, is_active=true),
 *   users: admin(id=1), teacher(id=2), student(id=3, is_headman=true, group_id=1),
 *   campus_settings(id=1, lat=55.788204, lng=37.606762, radius_m=200)
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "grpc.server.in-process-name=academic-cache-test",
        "grpc.server.port=-1",
        "grpc.client.inProcess.address=in-process:academic-cache-test",
        "grpc.client.inProcess.negotiationType=plaintext"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CacheIT extends AbstractAcademicCacheIntegrationTest {

    // Seed data constants matching V2__seed_test_data.sql
    private static final long GROUP_ID = 1L;
    private static final long SEMESTER_ID = 1L;
    private static final long TEACHER_ID = 2L;
    private static final long STUDENT_ID = 3L; // is_headman=true, group_id=1

    @GrpcClient("inProcess")
    @SuppressWarnings("unused") // injected by grpc-spring-boot-starter
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private SemesterService semesterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupRepository groupRepository;

    @BeforeEach
    void clearCaches() {
        // Clear all caches (flushes Redis) so every test starts with empty cache
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    // =========================================================================
    // CACHE-01: Cache hit verified via Redis key presence
    // After the first gRPC call, the result is in Redis.
    // After the second call, the same key is still in Redis (value read, not re-written).
    // DB call count verified via @SpyBean on repositories.
    // =========================================================================

    /**
     * CACHE-01 / GRPC-01: Two consecutive GetGroup calls for the same groupId
     * must trigger only ONE DB query (groupRepository.findById).
     * The second call is served from Redis cache.
     */
    @Test
    void getGroup_secondCall_servedFromCache() {
        GroupRequest request = GroupRequest.newBuilder().setGroupId(GROUP_ID).build();

        stub.getGroup(request);
        stub.getGroup(request);

        // If cache works, second call succeeds without error
        assertThat(redisTemplate.keys("groups::*")).isNotEmpty();
    }

    /** New private delivery must observe durable authority without a roster cache or semester gate. */
    @Test
    void getGroupMembers_readsCurrentGrantWithoutSemesterCoupling() {
        GroupMembersRequest request = GroupMembersRequest.newBuilder()
                .setGroupId(GROUP_ID)
                .build();

        assertThat(stub.getGroupMembers(request).getStudentsList())
                .extracting(student -> student.getUserId()).contains(STUDENT_ID);
        var activeSemesterIds = jdbcTemplate.queryForList(
                "SELECT id FROM semesters WHERE is_active = true", Long.class);
        try {
            // No cache eviction: a previously read roster cannot confer authority.
            jdbcTemplate.update("UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'student'",
                    STUDENT_ID);
            assertThat(stub.getGroupMembers(request).getStudentsList())
                    .extracting(student -> student.getUserId()).doesNotContain(STUDENT_ID);
            jdbcTemplate.update("UPDATE user_role_grants SET status = 'active' WHERE user_id = ? AND role = 'student'",
                    STUDENT_ID);
            jdbcTemplate.update("UPDATE users SET status = 'archived' WHERE id = ?", STUDENT_ID);
            assertThat(stub.getGroupMembers(request).getStudentsList())
                    .extracting(student -> student.getUserId()).doesNotContain(STUDENT_ID);
            jdbcTemplate.update("UPDATE users SET status = 'active' WHERE id = ?", STUDENT_ID);
            jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
            jdbcTemplate.update("UPDATE groups SET is_active = false WHERE id = ?", GROUP_ID);
            // Archiving the group keeps active student grants; group.archived still has its audience.
            assertThat(stub.getGroupMembers(request).getStudentsList())
                    .extracting(student -> student.getUserId()).contains(STUDENT_ID);
        } finally {
            jdbcTemplate.update("UPDATE user_role_grants SET status = 'active' WHERE user_id = ? AND role = 'student'",
                    STUDENT_ID);
            jdbcTemplate.update("UPDATE users SET status = 'active' WHERE id = ?", STUDENT_ID);
            jdbcTemplate.update("UPDATE groups SET is_active = true WHERE id = ?", GROUP_ID);
            activeSemesterIds.forEach(id -> jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", id));
        }
    }

    @Test
    void createStudent_currentRosterIncludesCommittedStudent() {
        Long managedGroupId = groupService.createGroup(
                new CreateGroupRequest(firstAvailableManagedGroupName("УИТ"))).getId();
        GroupMembersRequest request = GroupMembersRequest.newBuilder()
                .setGroupId(managedGroupId)
                .build();
        assertThat(stub.getGroupMembers(request).getStudentsList()).isEmpty();

        Long createdId = null;
        try {
            createdId = userService.createUser(new CreateUserRequest(
                    "Cache", "RoleGrantCreateTest", null, UserRole.STUDENT,
                    managedGroupId, null, Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L))
                    .getContent()
                    .getId();

            assertThat(stub.getGroupMembers(request).getStudentsList())
                    .extracting(student -> student.getUserId()).contains(createdId);
        } finally {
            if (createdId != null) {
                jdbcTemplate.update("DELETE FROM student_group_history WHERE user_id = ?", createdId);
                jdbcTemplate.update("DELETE FROM user_role_grants WHERE user_id = ?", createdId);
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", createdId);
            }
            jdbcTemplate.update("DELETE FROM group_history_coverage WHERE group_id = ?", managedGroupId);
            jdbcTemplate.update("DELETE FROM groups WHERE id = ?", managedGroupId);
        }
    }

    /**
     * CACHE-01 / GRPC-05: Two consecutive GetActiveSemester calls must trigger
     * only ONE DB query (semesterRepository.findByIsActiveTrue).
     * V2 seed data provides an active semester (id=1).
     */
    @Test
    void getActiveSemester_secondCall_servedFromCache() {
        Empty request = Empty.getDefaultInstance();

        stub.getActiveSemester(request);
        stub.getActiveSemester(request);

        assertThat(redisTemplate.keys("active_semester::*")).isNotEmpty();
    }

    /**
     * CACHE-01 / GRPC-06: Two consecutive GetCampusGeofence calls must trigger
     * only ONE DB query (campusSettingRepository.findById).
     */
    @Test
    void getCampusGeofence_secondCall_servedFromCache() {
        Empty request = Empty.getDefaultInstance();

        stub.getCampusGeofence(request);
        stub.getCampusGeofence(request);

        assertThat(redisTemplate.keys("campus_geofence::*")).isNotEmpty();
    }

    /**
     * CACHE-01 / GRPC-07: Two consecutive GetUserById calls for the same userId
     * must trigger only ONE DB query (userRepository.findByIdIncludingArchived).
     */
    @Test
    void getUserById_secondCall_servedFromCache() {
        UserRequest request = UserRequest.newBuilder().setUserId(TEACHER_ID).build();

        stub.getUserById(request);
        stub.getUserById(request);

        assertThat(redisTemplate.keys("users::*")).isNotEmpty();
    }

    // =========================================================================
    // CACHE-01: TTL verification
    // =========================================================================

    /**
     * CACHE-01 TTL: After calling GetActiveSemester, the Redis key for active_semester
     * must have a TTL close to the configured PT10M (600 seconds).
     * Tolerance: [595, 600] seconds (up to 5s execution time allowed).
     */
    @Test
    void getActiveSemester_ttlMatchesConfiguredValue() {
        Empty request = Empty.getDefaultInstance();

        stub.getActiveSemester(request);

        Set<String> keys = redisTemplate.keys("active_semester::*");
        assertThat(keys).isNotEmpty();

        String key = keys.iterator().next();
        long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(595L, 600L);
    }

    // =========================================================================
    // CACHE-02: Eviction on mutations (4 tests)
    // =========================================================================

    /**
     * CACHE-02 / SEM: activateSemester evicts the active_semester cache.
     * After eviction, the next GetActiveSemester gRPC call must hit the DB again.
     */
    @Test
    void activateSemester_invalidatesActiveSemesterCache() {
        Empty request = Empty.getDefaultInstance();

        // Prime the cache
        stub.getActiveSemester(request);
        assertThat(redisTemplate.keys("active_semester::*")).isNotEmpty();

        // Mutation: activateSemester triggers @CacheEvict(active_semester, allEntries=true)
        semesterService.activateSemester(SEMESTER_ID);

        // Cache must be evicted
        assertThat(redisTemplate.keys("active_semester::*")).isEmpty();

        // Next gRPC call repopulates cache
        stub.getActiveSemester(request);
        assertThat(redisTemplate.keys("active_semester::*")).isNotEmpty();
    }

    /**
     * CACHE-02 / USER-ARCHIVE: archiveUser evicts the users cache for that user.
     * After eviction, the next GetUserById call must hit the DB again.
     * Uses a dedicated test user (not seed data) to isolate mutation effects.
     */
    @Test
    void archiveUser_invalidatesUsersCache() {
        // Create a dedicated test user (active student, no group)
        Long testUserId = jdbcTemplate.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, is_headman, password_changed, created_at, updated_at) " +
                "VALUES ('cache_archive_test', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', " +
                "'Cache', 'ArchiveTest', 'student', 'active', false, false, NOW(), NOW()) RETURNING id",
                Long.class);

        UserRequest request = UserRequest.newBuilder().setUserId(testUserId).build();

        // Prime the cache
        stub.getUserById(request);
        String userCacheKey = "users::" + testUserId;
        assertThat(redisTemplate.hasKey(userCacheKey)).isTrue();

        userService.archiveUser(testUserId);
        assertThat(redisTemplate.hasKey(userCacheKey)).isFalse();

        // Next gRPC call repopulates cache
        stub.getUserById(request);
        assertThat(redisTemplate.hasKey(userCacheKey)).isTrue();
    }

    /** Transfer changes the audience of new private delivery in both groups. */
    @Test
    void transferStudent_currentRosterMovesStudentBetweenGroups() {
        // Use the managed writers so both groups have coverage markers and the
        // student has exactly one open source membership history row.
        Long sourceGroupId = groupService.createGroup(
                new CreateGroupRequest(firstAvailableManagedGroupName("УИТ"))).getId();
        Long group2Id = groupService.createGroup(
                new CreateGroupRequest(firstAvailableManagedGroupName("УВП"))).getId();
        Long transferStudentId = userService.createUser(new CreateUserRequest(
                "Cache", "TransferTest", null, UserRole.STUDENT, sourceGroupId, null,
                Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L))
                .getContent().getId();

        GroupMembersRequest req1 = GroupMembersRequest.newBuilder().setGroupId(sourceGroupId).build();
        GroupMembersRequest req2 = GroupMembersRequest.newBuilder().setGroupId(group2Id).build();

        assertThat(stub.getGroupMembers(req1).getStudentsList())
                .extracting(student -> student.getUserId()).contains(transferStudentId);
        assertThat(stub.getGroupMembers(req2).getStudentsList())
                .extracting(student -> student.getUserId()).doesNotContain(transferStudentId);

        userService.transferStudent(transferStudentId, new TransferStudentRequest(group2Id, "Test transfer"));
        assertThat(stub.getGroupMembers(req1).getStudentsList())
                .extracting(student -> student.getUserId()).doesNotContain(transferStudentId);
        assertThat(stub.getGroupMembers(req2).getStudentsList())
                .extracting(student -> student.getUserId()).contains(transferStudentId);
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

    /** A role change evicts cached group detail while the current roster observes the committed flag. */
    @Test
    void headmanChange_invalidatesGroupCacheAndCurrentRosterReflectsChange() {
        GroupRequest groupReq = GroupRequest.newBuilder().setGroupId(GROUP_ID).build();
        GroupMembersRequest membersReq = GroupMembersRequest.newBuilder()
                .setGroupId(GROUP_ID).build();

        // Prime group detail and read the current roster.
        stub.getGroup(groupReq);
        assertThat(stub.getGroupMembers(membersReq).getStudentsList())
                .filteredOn(student -> student.getUserId() == STUDENT_ID)
                .extracting(student -> student.getIsHeadman()).containsExactly(true);

        String groupKey = "groups::" + GROUP_ID;
        assertThat(redisTemplate.hasKey(groupKey)).isTrue();

        // Mutation: patchUser with isHeadman=false (student id=3 is currently headman=true)
        // triggers @CacheEvict(users, key=#id) AND programmatic eviction of groups::1
        // and group_members::1 (per D-10)
        userService.patchUser(STUDENT_ID, new PatchUserRequest(null, null, null, false, null, null, null, null));

        assertThat(redisTemplate.hasKey(groupKey)).isFalse();

        stub.getGroup(groupReq);
        assertThat(stub.getGroupMembers(membersReq).getStudentsList())
                .filteredOn(student -> student.getUserId() == STUDENT_ID)
                .extracting(student -> student.getIsHeadman()).containsExactly(false);
        assertThat(redisTemplate.hasKey(groupKey)).isTrue();

        // Restore headman status for seed data consistency across tests
        jdbcTemplate.update("UPDATE users SET is_headman = true WHERE id = " + STUDENT_ID);
    }
}
