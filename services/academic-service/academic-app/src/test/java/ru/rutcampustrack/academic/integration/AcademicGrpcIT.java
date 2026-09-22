package ru.rutcampustrack.academic.integration;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.AssistantPermissionCheckRequest;
import ru.rutcampustrack.academic.grpc.AssistantPermissionCheckResponse;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsRequest;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsResponse;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsRequest;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.academic.grpc.UserByTelegramIdRequest;
import ru.rutcampustrack.academic.grpc.UserByTelegramIdResponse;
import ru.rutcampustrack.academic.grpc.UserRequest;
import ru.rutcampustrack.academic.grpc.UserResponse;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for all 7 gRPC RPCs in AcademicGrpcServiceImpl.
 * Uses in-process gRPC server (grpc.server.in-process-name=academic-grpc-test) to avoid
 * port binding and enable parallel test execution.
 *
 * Data setup: V2 seed data provides group(id=1), semester(id=1, active=true),
 * users admin(id=1), teacher(id=2), student(id=3, is_headman=true, group_id=1),
 * campus_settings(id=1). Additional data inserted in @BeforeEach.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "grpc.server.in-process-name=academic-grpc-test",
        "grpc.server.port=-1",
        "grpc.client.inProcess.address=in-process:academic-grpc-test",
        "grpc.client.inProcess.negotiationType=plaintext"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AcademicGrpcIT extends AbstractAcademicIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    // Seed data constants — match V2__seed_test_data.sql exactly
    private static final long GROUP_ID = 1L;
    private static final long ADMIN_ID = 1L;
    private static final long TEACHER_ID = 2L;
    private static final long STUDENT_ID = 3L; // is_headman=true, group_id=1

    private static final long NONEXISTENT_ID = 99999L;

    @GrpcClient("inProcess")
    @SuppressWarnings("unused") // injected by grpc-spring-boot-starter
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InternalJwtTestFactory internalJwtTestFactory;

    private Long subjectId;
    private Long archivedUserId;
    private Long testSemesterId;
    private Long unassignedTeacherId;
    private String testSemesterName;
    private List<Long> priorActiveSemesterIds;

    @BeforeEach
    void setUpAdditionalData() {
        priorActiveSemesterIds = jdbcTemplate.query(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                (rs, rowNum) -> rs.getLong(1));
        testSemesterId = ensureCurrentSemester();
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", testSemesterId);
        ensureTeacherGrant(TEACHER_ID);
        subjectId = insertCanonicalSubject();
        unassignedTeacherId = insertUnassignedTeacher();

        // Seed an archived user without deleting a reused row or its durable grant.
        archivedUserId = ensureArchivedUser();
    }

    @AfterEach
    void restoreActiveSemester() {
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        for (Long id : priorActiveSemesterIds) {
            jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", id);
        }
    }

    private Long ensureCurrentSemester() {
        LocalDate today = LocalDate.now(MOSCOW);
        List<Long> current = jdbcTemplate.query(
                "SELECT id FROM semesters WHERE date_from <= ? AND date_to >= ? "
                        + "ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), today, today);
        if (!current.isEmpty()) {
            testSemesterName = jdbcTemplate.queryForObject(
                    "SELECT name FROM semesters WHERE id = ?", String.class, current.get(0));
            return current.get(0);
        }
        testSemesterName = "L5A grpc current " + UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                Long.class, testSemesterName, today.minusDays(5), today.plusDays(30));
    }

    private void ensureTeacherGrant(long teacherId) {
        jdbcTemplate.update(
                "INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'teacher', 'active', NULL, NOW(), NOW()) "
                        + "ON CONFLICT (user_id, role) DO UPDATE SET status = 'active', updated_at = NOW()",
                teacherId);
    }

    private long insertCanonicalSubject() {
        String name = "Algorithms " + UUID.randomUUID();
        LocalDate today = LocalDate.now(MOSCOW);
        LocalDate semesterFrom = jdbcTemplate.queryForObject(
                "SELECT date_from FROM semesters WHERE id = ?", LocalDate.class, testSemesterId);
        LocalDate validFrom = semesterFrom.isAfter(today.minusDays(1)) ? semesterFrom : today.minusDays(1);
        long id = jdbcTemplate.queryForObject(
                "WITH inserted_subject AS ("
                        + "INSERT INTO subjects (name, type, group_id) "
                        + "VALUES (?, 'lecture'::subject_type, ?) RETURNING id) "
                        + "INSERT INTO subject_lesson_types (subject_id, lesson_type) "
                        + "SELECT id, 'lecture'::subject_type FROM inserted_subject RETURNING subject_id",
                Long.class, name, GROUP_ID);
        jdbcTemplate.update(
                "INSERT INTO assignments (teacher_id, subject_id, group_id, semester_id, lesson_type, "
                        + "valid_from, valid_until_exclusive) VALUES (?, ?, ?, ?, 'lecture'::subject_type, ?, NULL)",
                TEACHER_ID, id, GROUP_ID, testSemesterId, validFrom);
        return id;
    }

    private long insertUnassignedTeacher() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        long id = jdbcTemplate.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                        + "is_headman, password_changed, created_at, updated_at) "
                        + "VALUES (?, NULL, 'L5A', 'Unassigned', 'teacher'::user_role, "
                        + "'active'::account_status, false, false, NOW(), NOW()) RETURNING id",
                Long.class, "l5a-grpc-" + suffix);
        ensureTeacherGrant(id);
        return id;
    }

    private long ensureArchivedUser() {
        List<Long> existing = jdbcTemplate.query(
                "SELECT id FROM users WHERE login = 'archived_test'",
                (rs, rowNum) -> rs.getLong(1));
        if (existing.isEmpty()) {
            return jdbcTemplate.queryForObject(
                    "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                            + "is_headman, password_changed, created_at, updated_at) "
                            + "VALUES ('archived_test', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', "
                            + "'Archived', 'User', 'student'::user_role, 'archived'::account_status, "
                            + "false, false, NOW(), NOW()) RETURNING id",
                    Long.class);
        }
        long id = existing.get(0);
        jdbcTemplate.update(
                "UPDATE users SET status = 'archived'::account_status, is_headman = false, "
                        + "group_id = NULL WHERE id = ?", id);
        return id;
    }

    // =====================================================================
    // GRPC-01: GetGroup
    // =====================================================================

    @Test
    void getGroup_validId_returnsGroupInfo() {
        GroupRequest request = GroupRequest.newBuilder()
                .setGroupId(GROUP_ID)
                .build();

        GroupResponse response = stub.getGroup(request);

        assertThat(response.getId()).isEqualTo(GROUP_ID);
        assertThat(response.getName()).isEqualTo("ИВТ-211");
        // 58-04: поле code удалено из GroupResponse (proto reserved 3).
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void getGroup_invalidId_throwsNotFound() {
        GroupRequest request = GroupRequest.newBuilder()
                .setGroupId(NONEXISTENT_ID)
                .build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.getGroup(request));
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // =====================================================================
    // GRPC-02: GetGroupMembers
    // =====================================================================

    @Test
    void getGroupMembers_returnsActiveStudentsOnly() {
        GroupMembersRequest request = GroupMembersRequest.newBuilder()
                .setGroupId(GROUP_ID)
                .build();

        GroupMembersResponse response = stub.getGroupMembers(request);

        // Should contain the student from V2 seed data
        assertThat(response.getStudentsList()).isNotEmpty();
        assertThat(response.getStudentsList())
                .anyMatch(s -> s.getUserId() == STUDENT_ID && s.getIsHeadman());

        // Archived user (if group_id matches) must NOT be in the list
        // Our archived test user has no group_id set (NULL), so verify no archived login
        // by checking IDs — the archived_test user has no group_id, so won't appear here
        // Additional assertion: all returned users should have valid IDs > 0
        response.getStudentsList().forEach(s ->
                assertThat(s.getUserId()).isGreaterThan(0L));
    }

    @Test
    void getGroupMembers_archivedUserInGroup_notReturned() {
        // Insert an archived student in group 1 and verify they are excluded
        String login = "archived-in-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, is_headman, group_id, password_changed, created_at, updated_at) " +
                "VALUES (?, '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', " +
                "'Archived', 'InGroup', 'student', 'archived', false, 1, false, NOW(), NOW())", login);
        Long archivedInGroupId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = ?", Long.class, login);

        GroupMembersRequest request = GroupMembersRequest.newBuilder()
                .setGroupId(GROUP_ID)
                .build();

        GroupMembersResponse response = stub.getGroupMembers(request);

        // The archived user must NOT appear in the members list
        assertThat(response.getStudentsList())
                .noneMatch(s -> s.getUserId() == archivedInGroupId);
    }

    // =====================================================================
    // GRPC-03: GetTeacherSubjects
    // =====================================================================

    @Test
    void getTeacherSubjects_returnsSubjectsWithGroups() {
        TeacherSubjectsRequest request = TeacherSubjectsRequest.newBuilder()
                .setTeacherId(TEACHER_ID)
                .setSemesterId(testSemesterId)
                .build();

        TeacherSubjectsResponse response = stub.getTeacherSubjects(request);

        assertThat(response.getSubjectsList()).isNotEmpty();
        assertThat(response.getSubjectsList()).anySatisfy(info -> {
            assertThat(info.getSubjectId()).isEqualTo(subjectId);
            assertThat(info.getGroupName()).isEqualTo("ИВТ-211");
            assertThat(info.getSubjectType()).isEqualTo("lecture");
            assertThat(info.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(info.getSemesterId()).isEqualTo(testSemesterId);
        });
    }

    @Test
    void getTeacherSubjects_noAssignments_returnsEmptyList() {
        // A real teacher grant with no assignment must receive an empty list.
        TeacherSubjectsRequest request = TeacherSubjectsRequest.newBuilder()
                .setTeacherId(unassignedTeacherId)
                .setSemesterId(testSemesterId)
                .build();

        TeacherSubjectsResponse response = stub.getTeacherSubjects(request);

        assertThat(response.getSubjectsList()).isEmpty();
    }

    @Test
    void getSubjectsByIds_returnsSubjectNamesAndTypes() {
        SubjectsByIdsRequest request = SubjectsByIdsRequest.newBuilder()
                .addSubjectIds(subjectId)
                .build();

        SubjectsByIdsResponse response = stub.getSubjectsByIds(request);

        assertThat(response.getSubjectsList()).hasSize(1);
        assertThat(response.getSubjectsList().get(0).getSubjectName()).startsWith("Algorithms ");
        assertThat(response.getSubjectsList().get(0).getSubjectType()).isEqualTo("lecture");
    }

    // =====================================================================
    // GRPC-04: IsHeadman
    // =====================================================================

    @Test
    void isHeadman_headmanOfGroup_returnsTrue() {
        // student (id=3) is is_headman=true and group_id=1 in seed data
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(STUDENT_ID)
                .setGroupId(GROUP_ID)
                .build();

        HeadmanCheckResponse response = stub.isHeadman(request);

        assertThat(response.getIsHeadman()).isTrue();
    }

    @Test
    void isHeadman_notHeadman_returnsFalse() {
        // teacher (id=2) is not a student/headman of this group
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(TEACHER_ID)
                .setGroupId(GROUP_ID)
                .build();

        HeadmanCheckResponse response = stub.isHeadman(request);

        assertThat(response.getIsHeadman()).isFalse();
    }

    @Test
    void isHeadman_userNotFound_returnsFalse() {
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(NONEXISTENT_ID)
                .setGroupId(GROUP_ID)
                .build();

        HeadmanCheckResponse response = stub.isHeadman(request);

        assertThat(response.getIsHeadman()).isFalse();
    }

    @Test
    void isHeadman_rateLimitExceeded_throwsResourceExhausted() {
        // M16 G7: 300 calls/min per userId (было 120 в M06 G8b — staros'а
        // с bulk-mark группы 30 студентов упирались в лимит). Используем
        // уникальный userId, чтобы не задеть bucket'ы других тестов.
        long uniqueUserId = 888_001L;
        HeadmanCheckRequest request = HeadmanCheckRequest.newBuilder()
                .setUserId(uniqueUserId)
                .setGroupId(GROUP_ID)
                .build();

        // 300 вызовов — все должны пройти (consume all tokens).
        for (int i = 0; i < 300; i++) {
            stub.isHeadman(request);
        }

        // 301-й вызов — RESOURCE_EXHAUSTED.
        assertThatThrownBy(() -> stub.isHeadman(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    Status status = ((StatusRuntimeException) ex).getStatus();
                    assertThat(status.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
                    assertThat(status.getDescription()).contains("rate limit");
                });
    }

    // =====================================================================
    // GRPC-04a: durable assistant permission authority
    // =====================================================================

    @Test
    void checkAssistantPermission_usesSignedIdentityAndFreshDurableGrant() {
        String login = "grpc-assist-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long assistantId = jdbcTemplate.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                        + "is_headman, group_id, password_changed, created_at, updated_at) "
                        + "VALUES (?, NULL, 'Grpc', 'Assistant', 'student', 'active', false, ?, true, NOW(), NOW()) "
                        + "RETURNING id",
                Long.class, login, GROUP_ID);
        jdbcTemplate.update(
                "INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'student', 'active', ?, NOW(), NOW()) "
                        + "ON CONFLICT (user_id, role) DO UPDATE SET status = 'active', group_id = EXCLUDED.group_id, updated_at = NOW()",
                assistantId, GROUP_ID);
        long assistantAssignmentId = jdbcTemplate.queryForObject(
                "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by, is_active, assigned_at) "
                        + "VALUES (?, ?, ARRAY['mark_attendance', 'manage_excuses']::varchar(64)[], ?, true, NOW()) "
                        + "RETURNING id",
                Long.class, GROUP_ID, assistantId, STUDENT_ID);
        try {
            String activeToken = internalJwtTestFactory.validToken(
                    assistantId, UUID.randomUUID(), 1L, 1L,
                    "STUDENT", "ACTIVE", GROUP_ID, false, false);

            AssistantPermissionCheckRequest markAttendance = AssistantPermissionCheckRequest.newBuilder()
                    .setGroupId(GROUP_ID)
                    .setPermission("MARK_ATTENDANCE")
                    .build();
            assertThat(withInternalToken(activeToken)
                    .checkAssistantPermission(markAttendance)
                    .getAllowed()).isTrue();

            // The same signed actor is denied a different capability and a
            // foreign target group; request payload has no actor override.
            assertThat(withInternalToken(activeToken)
                    .checkAssistantPermission(markAttendance.toBuilder()
                            .setPermission("CANCEL_LESSONS")
                            .build())
                    .getAllowed()).isFalse();
            assertThat(withInternalToken(activeToken)
                    .checkAssistantPermission(markAttendance.toBuilder()
                            .setGroupId(GROUP_ID + 10_000)
                            .build())
                    .getAllowed()).isFalse();

            // Revocation is visible on the next uncached RPC.
            jdbcTemplate.update(
                    "UPDATE headman_assistants SET is_active = false, revoked_at = NOW() WHERE id = ?",
                    assistantAssignmentId);
            assertThat(withInternalToken(activeToken)
                    .checkAssistantPermission(markAttendance)
                    .getAllowed()).isFalse();

            // A terminal/read-only signed identity cannot use a still-present
            // assistant row after account disablement.
            String disabledToken = internalJwtTestFactory.validToken(
                    assistantId, UUID.randomUUID(), 2L, 2L,
                    "STUDENT", "EXPELLED", GROUP_ID, false, true);
            assertThat(withInternalToken(disabledToken)
                    .checkAssistantPermission(markAttendance)
                    .getAllowed()).isFalse();

            assertThatThrownBy(() -> stub.checkAssistantPermission(markAttendance))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            error -> assertThat(error.getStatus().getCode())
                                    .isEqualTo(Status.Code.UNAUTHENTICATED));
        } finally {
            jdbcTemplate.update("DELETE FROM headman_assistants WHERE id = ?", assistantAssignmentId);
            jdbcTemplate.update("DELETE FROM user_role_grants WHERE user_id = ?", assistantId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", assistantId);
        }
    }

    // =====================================================================
    // GRPC-05: GetActiveSemester
    // =====================================================================

    @Test
    void getActiveSemester_activeSemesterExists_returnsSemester() {
        Empty request = Empty.newBuilder().build();

        SemesterResponse response = stub.getActiveSemester(request);

        assertThat(response.getId()).isEqualTo(testSemesterId);
        assertThat(response.getName()).isEqualTo(testSemesterName);
        assertThat(response.getDateFrom()).isNotEmpty();
        assertThat(response.getDateTo()).isNotEmpty();
        // V6 migration adds first_week_type with DEFAULT 'odd'
        assertThat(response.getFirstWeekType()).isEqualTo("odd");
    }

    @Test
    void getActiveSemester_noActiveSemester_throwsNotFound() {
        // Deactivate all semesters
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        try {
            Empty request = Empty.newBuilder().build();

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                    () -> stub.getActiveSemester(request));
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        } finally {
            // Restore active semester for subsequent tests
            jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", testSemesterId);
        }
    }

    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub withInternalToken(String token) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    // =====================================================================
    // GRPC-06: GetCampusGeofence
    // =====================================================================

    @Test
    void getCampusGeofence_returnsCampusSettings() {
        Empty request = Empty.newBuilder().build();

        GeofenceResponse response = stub.getCampusGeofence(request);

        // V2 seed: lat=55.788204, lng=37.606762, radius_m=200 (RUT MIIT, ул. Образцова 9)
        assertThat(response.getLat()).isNotZero();
        assertThat(response.getLng()).isNotZero();
        assertThat(response.getRadiusM()).isGreaterThan(0);
        assertThat(response.getLat()).isEqualTo(55.788204);
        assertThat(response.getLng()).isEqualTo(37.606762);
        assertThat(response.getRadiusM()).isEqualTo(200);
    }

    // =====================================================================
    // GRPC-07: GetUserById
    // =====================================================================

    @Test
    void getUserById_validId_returnsUserInfo() {
        UserRequest request = UserRequest.newBuilder()
                .setUserId(TEACHER_ID)
                .build();

        UserResponse response = stub.getUserById(request);

        assertThat(response.getId()).isEqualTo(TEACHER_ID);
        assertThat(response.getLogin()).isEqualTo("teacher");
        assertThat(response.getDisplayName()).isEqualTo("Преподавателев Учитель Знаниевич");
        assertThat(response.getRole()).isEqualTo("teacher");
        assertThat(response.getStatus()).isEqualTo("active");
    }

    @Test
    void getUserById_archivedUser_stillReturnsUser() {
        // The archived user must be returned (not NOT_FOUND)
        // This validates that findByIdIncludingArchived bypasses @SQLRestriction
        UserRequest request = UserRequest.newBuilder()
                .setUserId(archivedUserId)
                .build();

        UserResponse response = stub.getUserById(request);

        assertThat(response.getId()).isEqualTo(archivedUserId);
        assertThat(response.getLogin()).isEqualTo("archived_test");
        assertThat(response.getStatus()).isEqualTo("archived");
    }

    @Test
    void getUserById_invalidId_throwsNotFound() {
        UserRequest request = UserRequest.newBuilder()
                .setUserId(NONEXISTENT_ID)
                .build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.getUserById(request));
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // =====================================================================
    // GRPC-09: GetUserByTelegramId
    // =====================================================================

    @Test
    void getUserByTelegramId_found_returnsUserWithAllFields() {
        // Insert a student with telegram_id, initial_password, and group_id
        jdbcTemplate.update("DELETE FROM users WHERE login = 'bot_test_student'");
        jdbcTemplate.update(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, is_headman, group_id, " +
                "telegram_id, initial_password, password_changed, created_at, updated_at) " +
                "VALUES ('bot_test_student', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', " +
                "'Bot', 'TestStudent', 'student', 'active', false, 1, 987654321, 'initpass123', false, NOW(), NOW())");

        UserByTelegramIdRequest request = UserByTelegramIdRequest.newBuilder()
                .setTelegramId(987654321L)
                .build();

        UserByTelegramIdResponse response = stub.getUserByTelegramId(request);

        assertThat(response.getFound()).isTrue();
        assertThat(response.getLogin()).isEqualTo("bot_test_student");
        assertThat(response.getDisplayName()).isEqualTo("Bot TestStudent");
        assertThat(response.getRole()).isEqualTo("student");
        assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(response.getGroupName()).isEqualTo("ИВТ-211");
        assertThat(response.getIsHeadman()).isFalse();
        assertThat(response.getTelegramId()).isEqualTo(987654321L);
        // initial_password возвращается пока password_changed=false (для /start в боте).
        assertThat(response.getInitialPassword()).isEqualTo("initpass123");
        assertThat(response.getPasswordChanged()).isFalse();

        // Cleanup
        jdbcTemplate.update("DELETE FROM users WHERE login = 'bot_test_student'");
    }

    @Test
    void getUserByTelegramId_notFound_returnsFoundFalse() {
        UserByTelegramIdRequest request = UserByTelegramIdRequest.newBuilder()
                .setTelegramId(NONEXISTENT_ID)
                .build();

        UserByTelegramIdResponse response = stub.getUserByTelegramId(request);

        assertThat(response.getFound()).isFalse();
        assertThat(response.getLogin()).isEmpty();
        assertThat(response.getUserId()).isZero();
    }
}
