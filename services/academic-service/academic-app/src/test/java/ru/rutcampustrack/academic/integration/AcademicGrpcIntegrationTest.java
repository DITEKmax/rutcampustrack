package ru.rutcampustrack.academic.integration;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.TeacherSubjectGroup;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.academic.grpc.HeadmanCheckRequest;
import ru.rutcampustrack.academic.grpc.HeadmanCheckResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsRequest;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.academic.grpc.UserRequest;
import ru.rutcampustrack.academic.grpc.UserResponse;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;

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
public class AcademicGrpcIntegrationTest extends AbstractAcademicIntegrationTest {

    // Seed data constants — match V2__seed_test_data.sql exactly
    private static final long GROUP_ID = 1L;
    private static final long SEMESTER_ID = 1L;
    private static final long ADMIN_ID = 1L;
    private static final long TEACHER_ID = 2L;
    private static final long STUDENT_ID = 3L; // is_headman=true, group_id=1

    private static final long NONEXISTENT_ID = 99999L;

    @GrpcClient("inProcess")
    @SuppressWarnings("unused") // injected by grpc-spring-boot-starter
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private TeacherSubjectGroupRepository teacherSubjectGroupRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long subjectId;
    private Long archivedUserId;

    @BeforeEach
    void setUpAdditionalData() {
        // Insert subject for GRPC-03 tests (not in V2 seed data)
        // Clean up first to avoid duplicate key on repeated test runs
        teacherSubjectGroupRepository.deleteAll();
        subjectRepository.deleteAll();

        Subject subject = new Subject();
        subject.setName("Algorithms");
        subject.setType(SubjectType.LECTURE);
        subject = subjectRepository.save(subject);
        subjectId = subject.getId();

        // Insert teacher-subject-group assignment for GRPC-03
        TeacherSubjectGroup assignment = new TeacherSubjectGroup(
                TEACHER_ID, subjectId, GROUP_ID, SEMESTER_ID);
        teacherSubjectGroupRepository.save(assignment);

        // Insert archived user for GRPC-07 test via JdbcTemplate
        // (cannot use JPA save because @SQLRestriction blocks archived users)
        jdbcTemplate.update(
                "DELETE FROM users WHERE login = 'archived_test'");
        jdbcTemplate.update(
                "INSERT INTO users (login, password_hash, display_name, role, status, is_headman, password_changed, created_at, updated_at) " +
                "VALUES ('archived_test', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', " +
                "'Archived User', 'student', 'archived', false, false, NOW(), NOW())");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'archived_test'", Long.class);
        archivedUserId = id;
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
        assertThat(response.getName()).isEqualTo("IVT-21-1");
        assertThat(response.getCode()).isEqualTo("ivt-21-1");
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
        jdbcTemplate.update(
                "DELETE FROM users WHERE login = 'archived_in_group'");
        jdbcTemplate.update(
                "INSERT INTO users (login, password_hash, display_name, role, status, is_headman, group_id, password_changed, created_at, updated_at) " +
                "VALUES ('archived_in_group', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', " +
                "'Archived In Group', 'student', 'archived', false, 1, false, NOW(), NOW())");
        Long archivedInGroupId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'archived_in_group'", Long.class);

        GroupMembersRequest request = GroupMembersRequest.newBuilder()
                .setGroupId(GROUP_ID)
                .build();

        GroupMembersResponse response = stub.getGroupMembers(request);

        // The archived user must NOT appear in the members list
        assertThat(response.getStudentsList())
                .noneMatch(s -> s.getUserId() == archivedInGroupId);

        // Cleanup
        jdbcTemplate.update("DELETE FROM users WHERE login = 'archived_in_group'");
    }

    // =====================================================================
    // GRPC-03: GetTeacherSubjects
    // =====================================================================

    @Test
    void getTeacherSubjects_returnsSubjectsWithGroups() {
        TeacherSubjectsRequest request = TeacherSubjectsRequest.newBuilder()
                .setTeacherId(TEACHER_ID)
                .setSemesterId(SEMESTER_ID)
                .build();

        TeacherSubjectsResponse response = stub.getTeacherSubjects(request);

        assertThat(response.getSubjectsList()).isNotEmpty();
        assertThat(response.getSubjectsList().get(0).getSubjectName()).isEqualTo("Algorithms");
        assertThat(response.getSubjectsList().get(0).getGroupName()).isEqualTo("IVT-21-1");
        assertThat(response.getSubjectsList().get(0).getSubjectType()).isEqualTo("lecture");
        assertThat(response.getSubjectsList().get(0).getGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    void getTeacherSubjects_noAssignments_returnsEmptyList() {
        // ADMIN_ID (id=1) has no teacher_subject_groups assignments
        TeacherSubjectsRequest request = TeacherSubjectsRequest.newBuilder()
                .setTeacherId(ADMIN_ID)
                .setSemesterId(SEMESTER_ID)
                .build();

        TeacherSubjectsResponse response = stub.getTeacherSubjects(request);

        assertThat(response.getSubjectsList()).isEmpty();
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

    // =====================================================================
    // GRPC-05: GetActiveSemester
    // =====================================================================

    @Test
    void getActiveSemester_activeSemesterExists_returnsSemester() {
        Empty request = Empty.newBuilder().build();

        SemesterResponse response = stub.getActiveSemester(request);

        assertThat(response.getId()).isEqualTo(SEMESTER_ID);
        assertThat(response.getName()).isEqualTo("Spring 2026");
        assertThat(response.getDateFrom()).isNotEmpty();
        assertThat(response.getDateTo()).isNotEmpty();
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
            jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = " + SEMESTER_ID);
        }
    }

    // =====================================================================
    // GRPC-06: GetCampusGeofence
    // =====================================================================

    @Test
    void getCampusGeofence_returnsCampusSettings() {
        Empty request = Empty.newBuilder().build();

        GeofenceResponse response = stub.getCampusGeofence(request);

        // V2 seed: lat=55.7699, lng=37.7039, radius_m=200
        assertThat(response.getLat()).isNotZero();
        assertThat(response.getLng()).isNotZero();
        assertThat(response.getRadiusM()).isGreaterThan(0);
        assertThat(response.getLat()).isEqualTo(55.7699);
        assertThat(response.getLng()).isEqualTo(37.7039);
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
        assertThat(response.getDisplayName()).isEqualTo("Test Teacher");
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
}
