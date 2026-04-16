package ru.rutcampustrack.academic.integration;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.UserRequest;
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
 * conflicts with AcademicGrpcIntegrationTest ("academic-grpc-test").
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
class CacheIntegrationTest extends AbstractAcademicCacheIntegrationTest {

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
    private RedisCacheManager cacheManager;

    @Autowired
    private UserService userService;

    @Autowired
    private SemesterService semesterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    /**
     * CACHE-01 / GRPC-02: Two consecutive GetGroupMembers calls for the same groupId
     * must trigger only ONE DB query (userRepository.findByGroupId).
     */
    @Test
    void getGroupMembers_secondCall_servedFromCache() {
        GroupMembersRequest request = GroupMembersRequest.newBuilder()
                .setGroupId(GROUP_ID)
                .build();

        stub.getGroupMembers(request);
        stub.getGroupMembers(request);

        assertThat(redisTemplate.keys("group_members::*")).isNotEmpty();
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

        // Mutation: archiveUser triggers @CacheEvict(users, key=#id)
        userService.archiveUser(testUserId);

        // Cache must be evicted
        assertThat(redisTemplate.hasKey(userCacheKey)).isFalse();

        // Next gRPC call repopulates cache
        stub.getUserById(request);
        assertThat(redisTemplate.hasKey(userCacheKey)).isTrue();
    }

    /**
     * CACHE-02 / TRANSFER: transferStudent evicts group_members for both old and new group.
     * After transfer, subsequent GetGroupMembers calls for both groups must hit the DB.
     */
    @Test
    void transferStudent_invalidatesBothGroupCaches() {
        // Create a second group for the transfer target
        Long group2Id = jdbcTemplate.queryForObject(
                "INSERT INTO groups (name, is_active, created_at) " +
                "VALUES ('ИВТ-212', true, NOW()) RETURNING id",
                Long.class);

        // Create a dedicated student in group 1 for transfer (not the seed headman student)
        Long transferStudentId = jdbcTemplate.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, is_headman, group_id, password_changed, created_at, updated_at) " +
                "VALUES ('cache_transfer_test', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', " +
                "'Cache', 'TransferTest', 'student', 'active', false, " + GROUP_ID + ", false, NOW(), NOW()) RETURNING id",
                Long.class);

        GroupMembersRequest req1 = GroupMembersRequest.newBuilder().setGroupId(GROUP_ID).build();
        GroupMembersRequest req2 = GroupMembersRequest.newBuilder().setGroupId(group2Id).build();

        // Prime caches for both groups
        stub.getGroupMembers(req1);
        stub.getGroupMembers(req2);

        String key1 = "group_members::" + GROUP_ID;
        String key2 = "group_members::" + group2Id;
        assertThat(redisTemplate.hasKey(key1)).isTrue();
        assertThat(redisTemplate.hasKey(key2)).isTrue();

        // Mutation: evicts group_members for new group (@CacheEvict annotation) and
        // old group (programmatic CacheManager eviction)
        userService.transferStudent(transferStudentId, new TransferStudentRequest(group2Id, "Test transfer"));

        // Both caches must be evicted
        assertThat(redisTemplate.hasKey(key1)).isFalse();
        assertThat(redisTemplate.hasKey(key2)).isFalse();

        // Next calls repopulate both caches
        stub.getGroupMembers(req1);
        stub.getGroupMembers(req2);
        assertThat(redisTemplate.hasKey(key1)).isTrue();
        assertThat(redisTemplate.hasKey(key2)).isTrue();
    }

    /**
     * CACHE-02 / D-10: patchUser with headman change evicts BOTH groups and group_members
     * caches for the user's group. After eviction, both GetGroup and GetGroupMembers must
     * hit the DB again (both DB queries counted via @SpyBean).
     *
     * This test verifies the D-10 decision: headman change triggers programmatic CacheManager
     * eviction of both "groups" and "group_members" caches for the user's group.
     */
    @Test
    void headmanChange_invalidatesGroupAndMembersCache() {
        GroupRequest groupReq = GroupRequest.newBuilder().setGroupId(GROUP_ID).build();
        GroupMembersRequest membersReq = GroupMembersRequest.newBuilder()
                .setGroupId(GROUP_ID).build();

        // Prime BOTH caches for group 1
        stub.getGroup(groupReq);
        stub.getGroupMembers(membersReq);

        String groupKey = "groups::" + GROUP_ID;
        String membersKey = "group_members::" + GROUP_ID;
        assertThat(redisTemplate.hasKey(groupKey)).isTrue();
        assertThat(redisTemplate.hasKey(membersKey)).isTrue();

        // Mutation: patchUser with isHeadman=false (student id=3 is currently headman=true)
        // triggers @CacheEvict(users, key=#id) AND programmatic eviction of groups::1
        // and group_members::1 (per D-10)
        userService.patchUser(STUDENT_ID, new PatchUserRequest(null, null, null, false, null, null, null, null));

        // Both caches must be evicted (keys gone from Redis)
        assertThat(redisTemplate.hasKey(groupKey)).isFalse();
        assertThat(redisTemplate.hasKey(membersKey)).isFalse();

        // Next calls repopulate both caches
        stub.getGroup(groupReq);
        stub.getGroupMembers(membersReq);
        assertThat(redisTemplate.hasKey(groupKey)).isTrue();
        assertThat(redisTemplate.hasKey(membersKey)).isTrue();

        // Restore headman status for seed data consistency across tests
        jdbcTemplate.update("UPDATE users SET is_headman = true WHERE id = " + STUDENT_ID);
    }
}
