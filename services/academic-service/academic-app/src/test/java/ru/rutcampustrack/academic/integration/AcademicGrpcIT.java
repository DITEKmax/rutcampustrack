package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.academic.assistant.AssistantService;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.AssistantPermissionCheckRequest;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupRequest;
import ru.rutcampustrack.academic.grpc.GroupResponse;
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
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.academic.user.UserArchiveService;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Real in-process Academic gRPC calls backed by the canonical independent Auth/Academic fixture.
 * Auth identities, grants, selected sessions and archival use the existing two-database protocol.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "grpc.server.in-process-name=academic-grpc-test",
            "grpc.server.port=-1",
            "grpc.client.inProcess.address=in-process:academic-grpc-test",
            "grpc.client.inProcess.negotiationType=plaintext"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AcademicGrpcIT extends AbstractAcademicIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private long groupId;
    private long adminId;
    private long teacherId;
    private long studentId;
    private String teacherLogin;
    private String archivedLogin;
    private AcademicIdentityIntegrationFixture.SelectedActor adminActor;

    private static final long NONEXISTENT_ID = 99999L;

    @GrpcClient("inProcess")
    @SuppressWarnings("unused") // injected by grpc-spring-boot-starter
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private UserRepository users;

    @Autowired private UserService userService;

    @Autowired private UserArchiveService archives;

    @Autowired private AssistantService assistants;

    @Autowired private AcademicLiveSessionClient liveSessions;

    // Archive setup needs only the external impact snapshot; identity authority remains real.
    @MockitoBean private AttendanceUserImpactGrpcClient attendance;

    @Autowired private TestRestTemplate http;

    private Long subjectId;
    private Long archivedUserId;
    private Long testSemesterId;
    private Long unassignedTeacherId;
    private String testSemesterName;
    private List<Long> priorActiveSemesterIds;

    @BeforeEach
    void setUpAdditionalData() {
        var canonical = identityFixture.canonical();
        groupId = canonical.groupId();
        adminId = canonical.adminId();
        teacherId = canonical.teacherId();
        studentId = canonical.studentId();
        adminActor = identityFixture.select(adminId, AuthRole.ADMIN);
        teacherLogin = users.findById(teacherId).orElseThrow().getLogin();
        priorActiveSemesterIds =
                jdbcTemplate.query(
                        "SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                        (rs, rowNum) -> rs.getLong(1));
        testSemesterId = ensureCurrentSemester();
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", testSemesterId);
        subjectId = insertCanonicalSubject();
        unassignedTeacherId = insertUnassignedTeacher();

        var archived = seedStudent("Archived", "User", 610000000101L);
        archivedUserId = archived.userId();
        archivedLogin = archived.login();
        archiveUser(archivedUserId);
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
        List<Long> current =
                jdbcTemplate.query(
                        "SELECT id FROM semesters WHERE date_from <= ? AND date_to >= ? "
                                + "ORDER BY id DESC LIMIT 1",
                        (rs, rowNum) -> rs.getLong(1),
                        today,
                        today);
        if (!current.isEmpty()) {
            testSemesterName =
                    jdbcTemplate.queryForObject(
                            "SELECT name FROM semesters WHERE id = ?",
                            String.class,
                            current.get(0));
            return current.get(0);
        }
        testSemesterName = "L5A grpc current " + UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                Long.class,
                testSemesterName,
                today.minusDays(5),
                today.plusDays(30));
    }

    private long insertCanonicalSubject() {
        String name = "Algorithms " + UUID.randomUUID();
        LocalDate today = LocalDate.now(MOSCOW);
        LocalDate semesterFrom =
                jdbcTemplate.queryForObject(
                        "SELECT date_from FROM semesters WHERE id = ?",
                        LocalDate.class,
                        testSemesterId);
        LocalDate validFrom =
                semesterFrom.isAfter(today.minusDays(1)) ? semesterFrom : today.minusDays(1);
        long id =
                jdbcTemplate.queryForObject(
                        "WITH inserted_subject AS (INSERT INTO subjects (name, type, group_id)"
                            + " VALUES (?, 'lecture'::subject_type, ?) RETURNING id) INSERT INTO"
                            + " subject_lesson_types (subject_id, lesson_type) SELECT id,"
                            + " 'lecture'::subject_type FROM inserted_subject RETURNING subject_id",
                        Long.class,
                        name,
                        groupId);
        jdbcTemplate.update(
                "INSERT INTO assignments (teacher_id, subject_id, group_id, semester_id,"
                        + " lesson_type, valid_from, valid_until_exclusive) VALUES (?, ?, ?, ?,"
                        + " 'lecture'::subject_type, ?, NULL)",
                teacherId,
                id,
                groupId,
                testSemesterId,
                validFrom);
        return id;
    }

    private long insertUnassignedTeacher() {
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                "L5A",
                                "Unassigned",
                                null,
                                UserRole.TEACHER,
                                null,
                                "GRPC-" + UUID.randomUUID().toString().substring(0, 20),
                                null),
                        adminActor)
                .userId();
    }

    private AcademicIdentityIntegrationFixture.SeededUser seedStudent(
            String lastName, String firstName, long telegramId) {
        return identityFixture.seedUser(
                new CreateUserRequest(
                        lastName, firstName, null, UserRole.STUDENT, groupId, null, telegramId),
                adminActor);
    }

    private void archiveUser(long userId) {
        when(attendance.preview(userId))
                .thenReturn(
                        UserImpactSnapshot.newBuilder()
                                .setUserId(userId)
                                .setObservedAtEpochMs(Instant.now().toEpochMilli())
                                .setSnapshotDigest("b".repeat(64))
                                .build());
        ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview preview;
        try (var ignored = identityFixture.request(adminActor)) {
            preview = archives.preview(userId);
        }
        var request =
                new ArchiveRequest(
                        UUID.randomUUID(),
                        preview.previewDigest(),
                        preview.requiresPassword()
                                ? AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD
                                : null);
        try (var ignored = identityFixture.request(adminActor, request.operationId())) {
            archives.archive(userId, request);
        }
        identityFixture.catchUpCatalog();
    }

    // =====================================================================
    // GRPC-01: GetGroup
    // =====================================================================

    @Test
    void getGroup_validId_returnsGroupInfo() {
        GroupRequest request = GroupRequest.newBuilder().setGroupId(groupId).build();

        GroupResponse response = stub.getGroup(request);

        assertThat(response.getId()).isEqualTo(groupId);
        assertThat(response.getName()).isEqualTo("ИВТ-211");
        // 58-04: поле code удалено из GroupResponse (proto reserved 3).
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void getGroup_invalidId_throwsNotFound() {
        GroupRequest request = GroupRequest.newBuilder().setGroupId(NONEXISTENT_ID).build();

        StatusRuntimeException ex =
                assertThrows(StatusRuntimeException.class, () -> stub.getGroup(request));
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // =====================================================================
    // GRPC-02: GetGroupMembers
    // =====================================================================

    @Test
    void getGroupMembers_returnsActiveStudentsOnly() {
        GroupMembersRequest request = GroupMembersRequest.newBuilder().setGroupId(groupId).build();

        GroupMembersResponse response = stub.getGroupMembers(request);

        // The canonical selected headman remains an active member.
        assertThat(response.getStudentsList()).isNotEmpty();
        assertThat(response.getStudentsList())
                .anyMatch(s -> s.getUserId() == studentId && s.getIsHeadman());

        assertThat(response.getStudentsList()).noneMatch(s -> s.getUserId() == archivedUserId);
        response.getStudentsList().forEach(s -> assertThat(s.getUserId()).isGreaterThan(0L));
    }

    @Test
    void getGroupMembers_archivedUserInGroup_notReturned() {
        long archivedInGroupId = seedStudent("Archived", "InGroup", 610000000102L).userId();
        assertThat(
                        stub.getGroupMembers(
                                        GroupMembersRequest.newBuilder()
                                                .setGroupId(groupId)
                                                .build())
                                .getStudentsList())
                .anyMatch(s -> s.getUserId() == archivedInGroupId);
        archiveUser(archivedInGroupId);

        GroupMembersRequest request = GroupMembersRequest.newBuilder().setGroupId(groupId).build();

        GroupMembersResponse response = stub.getGroupMembers(request);

        // The archived user must NOT appear in the members list
        assertThat(response.getStudentsList()).noneMatch(s -> s.getUserId() == archivedInGroupId);
    }

    // =====================================================================
    // GRPC-03: GetTeacherSubjects
    // =====================================================================

    @Test
    void getTeacherSubjects_returnsSubjectsWithGroups() {
        TeacherSubjectsRequest request =
                TeacherSubjectsRequest.newBuilder()
                        .setTeacherId(teacherId)
                        .setSemesterId(testSemesterId)
                        .build();

        TeacherSubjectsResponse response = stub.getTeacherSubjects(request);

        assertThat(response.getSubjectsList()).isNotEmpty();
        assertThat(response.getSubjectsList())
                .anySatisfy(
                        info -> {
                            assertThat(info.getSubjectId()).isEqualTo(subjectId);
                            assertThat(info.getGroupName()).isEqualTo("ИВТ-211");
                            assertThat(info.getSubjectType()).isEqualTo("lecture");
                            assertThat(info.getGroupId()).isEqualTo(groupId);
                            assertThat(info.getSemesterId()).isEqualTo(testSemesterId);
                        });
    }

    @Test
    void getTeacherSubjects_noAssignments_returnsEmptyList() {
        // A real teacher grant with no assignment must receive an empty list.
        TeacherSubjectsRequest request =
                TeacherSubjectsRequest.newBuilder()
                        .setTeacherId(unassignedTeacherId)
                        .setSemesterId(testSemesterId)
                        .build();

        TeacherSubjectsResponse response = stub.getTeacherSubjects(request);

        assertThat(response.getSubjectsList()).isEmpty();
    }

    @Test
    void getSubjectsByIds_returnsSubjectNamesAndTypes() {
        jdbcTemplate.update(
                "INSERT INTO subject_lesson_types(subject_id,lesson_type) VALUES (?,'practice') ON"
                        + " CONFLICT DO NOTHING",
                subjectId);
        SubjectsByIdsRequest request =
                SubjectsByIdsRequest.newBuilder().addSubjectIds(subjectId).build();

        SubjectsByIdsResponse response = stub.getSubjectsByIds(request);

        assertThat(response.getSubjectsList()).hasSize(1);
        assertThat(response.getSubjectsList().get(0).getSubjectName()).startsWith("Algorithms ");
        assertThat(response.getSubjectsList().get(0).getSubjectType()).isEqualTo("lecture");
        assertThat(response.getSubjectsList().get(0).getGroupId()).isEqualTo(groupId);
        assertThat(response.getSubjectsList().get(0).getLessonTypesList())
                .containsExactly("lecture", "practice");
    }

    // =====================================================================
    // GRPC-04: IsHeadman
    // =====================================================================

    @Test
    void isHeadman_headmanOfGroup_returnsTrue() {
        // The canonical student has a genuinely assigned HEADMAN grant.
        HeadmanCheckRequest request =
                HeadmanCheckRequest.newBuilder().setUserId(studentId).setGroupId(groupId).build();

        HeadmanCheckResponse response = stub.isHeadman(request);

        assertThat(response.getIsHeadman()).isTrue();
    }

    @Test
    void isHeadman_notHeadman_returnsFalse() {
        // The canonical teacher is not a student/headman of this group.
        HeadmanCheckRequest request =
                HeadmanCheckRequest.newBuilder().setUserId(teacherId).setGroupId(groupId).build();

        HeadmanCheckResponse response = stub.isHeadman(request);

        assertThat(response.getIsHeadman()).isFalse();
    }

    @Test
    void isHeadman_userNotFound_returnsFalse() {
        HeadmanCheckRequest request =
                HeadmanCheckRequest.newBuilder()
                        .setUserId(NONEXISTENT_ID)
                        .setGroupId(groupId)
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
        HeadmanCheckRequest request =
                HeadmanCheckRequest.newBuilder()
                        .setUserId(uniqueUserId)
                        .setGroupId(groupId)
                        .build();

        // 300 вызовов — все должны пройти (consume all tokens).
        for (int i = 0; i < 300; i++) {
            stub.isHeadman(request);
        }

        // 301-й вызов — RESOURCE_EXHAUSTED.
        assertThatThrownBy(() -> stub.isHeadman(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        ex -> {
                            Status status = ((StatusRuntimeException) ex).getStatus();
                            assertThat(status.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
                            assertThat(status.getDescription()).contains("rate limit");
                        });
    }

    @Test
    @ResourceLock("java.util.Locale.default")
    void enumDatabaseAndHttpBoundaryRemainCanonicalUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var admin = users.findById(adminId).orElseThrow();
            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            String token = adminActor.internalToken();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", token);
            SoftAssertions localeEquality = new SoftAssertions();
            assertFilteredUsersEqualUnderLocales(
                    headers,
                    "/academic/users?status=active&page=0&size=100&sort=id,asc",
                    "u.status = 'active'",
                    localeEquality);
            assertFilteredUsersEqualUnderLocales(
                    headers,
                    "/academic/users?status=active&role=ADMIN&page=0&size=100&sort=id,asc",
                    "u.status = 'active' AND EXISTS (SELECT 1 FROM academic_role_directory g "
                            + "WHERE g.user_id = u.id AND g.role = 'admin')",
                    localeEquality);
            assertFilteredUsersEqualUnderLocales(
                    headers,
                    "/academic/users?role=admin&roleStatus=ACTIVE&page=0&size=100&sort=id,asc",
                    "u.status <> 'archived' AND EXISTS (SELECT 1 FROM academic_role_directory g"
                        + " WHERE g.user_id = u.id AND g.role = 'admin' AND g.status = 'active')",
                    localeEquality);
            var unknown =
                    http.exchange(
                            "/academic/users?status=unknown",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class);
            assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            localeEquality.assertAll();
        } finally {
            Locale.setDefault(previous);
        }
    }

    private void assertFilteredUsersEqualUnderLocales(
            HttpHeaders headers, String uri, String predicate, SoftAssertions localeEquality) {
        // Only closed test predicates enter this SQL; no registry body or credentials are retained.
        List<FilteredUserRow> expected =
                jdbcTemplate.query(
                        "SELECT u.id, u.status::text FROM academic_user_directory u WHERE "
                                + predicate
                                + " ORDER BY u.id",
                        (rs, rowNum) ->
                                new FilteredUserRow(
                                        rs.getLong(1), rs.getString(2).toUpperCase(Locale.ROOT)));
        assertThat(expected).as("%s nonempty SQL fixture", uri).isNotEmpty();
        assertThat(expected).extracting(FilteredUserRow::id).contains(adminId);
        assertThat(expected).extracting(FilteredUserRow::id).doesNotContain(archivedUserId);
        assertThat(expected.size())
                .as("%s fits the explicit first page", uri)
                .isLessThanOrEqualTo(100);

        Locale.setDefault(Locale.ROOT);
        FilteredUsers canonical = filteredUsers(headers, uri);
        assertThat(canonical.rows())
                .as("%s ROOT rows match SQL", uri)
                .containsExactlyElementsOf(expected);
        assertThat(canonical.totalElements()).as("%s ROOT total", uri).isEqualTo(expected.size());
        assertThat(canonical.returnedCount())
                .as("%s ROOT returned count", uri)
                .isEqualTo(expected.size());

        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        FilteredUsers turkish = filteredUsers(headers, uri);
        // Aggregate the two independent grant-normalization witnesses in this same selected test.
        localeEquality
                .assertThat(turkish)
                .as("ENUM_ROW_EQUALITY tr-TR %s", uri)
                .isEqualTo(canonical);
    }

    private FilteredUsers filteredUsers(HttpHeaders headers, String uri) {
        var response =
                http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(response.getStatusCode()).as("%s HTTP status", uri).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body != null).as("%s JSON body present", uri).isTrue();
        JsonNode rows = body.path("_embedded").path("userResponseList");
        // A legitimate empty HATEOAS page can omit _embedded; the nonempty oracle still rejects it.
        assertThat(rows.isMissingNode() || rows.isArray())
                .as("%s row collection shape", uri)
                .isTrue();
        List<FilteredUserRow> projected = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode id = row.path("id");
            JsonNode status = row.path("status");
            assertThat(id.isIntegralNumber() && id.canConvertToLong())
                    .as("%s row id shape", uri)
                    .isTrue();
            assertThat(status.isTextual()).as("%s row status shape", uri).isTrue();
            projected.add(new FilteredUserRow(id.longValue(), status.textValue()));
        }
        JsonNode total = body.path("page").path("totalElements");
        assertThat(total.isIntegralNumber() && total.canConvertToLong())
                .as("%s total shape", uri)
                .isTrue();
        return new FilteredUsers(List.copyOf(projected), total.longValue(), projected.size());
    }

    private record FilteredUserRow(long id, String status) {}

    private record FilteredUsers(
            List<FilteredUserRow> rows, long totalElements, int returnedCount) {}

    // =====================================================================
    // GRPC-04a: durable assistant permission authority
    // =====================================================================

    @Test
    void checkAssistantPermission_usesSignedIdentityAndFreshDurableGrant() {
        long assistantId = seedStudent("Grpc", "Assistant", 610000000103L).userId();
        var headman = identityFixture.select(studentId, AuthRole.HEADMAN);
        long assistantAssignmentId;
        try (var ignored = identityFixture.request(headman)) {
            assistantAssignmentId =
                    assistants
                            .assignAssistant(
                                    new AssignAssistantRequest(
                                            assistantId,
                                            groupId,
                                            List.of(
                                                    AssistantPermission.MARK_ATTENDANCE,
                                                    AssistantPermission.MANAGE_EXCUSES,
                                                    AssistantPermission.MANAGE_HOMEWORK)))
                            .getId();
        }
        var activeActor = identityFixture.select(assistantId, AuthRole.STUDENT);
        String activeToken = activeActor.internalToken();

        AssistantPermissionCheckRequest markAttendance =
                AssistantPermissionCheckRequest.newBuilder()
                        .setGroupId(groupId)
                        .setPermission("MARK_ATTENDANCE")
                        .build();
        assertThat(
                        withInternalToken(activeToken)
                                .checkAssistantPermission(markAttendance)
                                .getAllowed())
                .isTrue();
        AssistantPermissionCheckRequest homework =
                markAttendance.toBuilder().setPermission("MANAGE_HOMEWORK").build();
        assertThat(withInternalToken(activeToken).checkAssistantPermission(homework).getAllowed())
                .isTrue();
        assertThat(
                        withInternalToken(activeToken)
                                .checkAssistantPermission(
                                        homework.toBuilder().setGroupId(groupId + 10_000).build())
                                .getAllowed())
                .isFalse();
        try (var ignored = identityFixture.request(headman)) {
            assistants.updatePermissions(
                    assistantAssignmentId,
                    new UpdateAssistantPermissionsRequest(
                            List.of(
                                    AssistantPermission.MARK_ATTENDANCE,
                                    AssistantPermission.MANAGE_EXCUSES)));
        }
        assertThat(withInternalToken(activeToken).checkAssistantPermission(homework).getAllowed())
                .isFalse();

        // The same signed actor is denied a different capability and a
        // foreign target group; request payload has no actor override.
        assertThat(
                        withInternalToken(activeToken)
                                .checkAssistantPermission(
                                        markAttendance.toBuilder()
                                                .setPermission("CANCEL_LESSONS")
                                                .build())
                                .getAllowed())
                .isFalse();
        assertThat(
                        withInternalToken(activeToken)
                                .checkAssistantPermission(
                                        markAttendance.toBuilder()
                                                .setGroupId(groupId + 10_000)
                                                .build())
                                .getAllowed())
                .isFalse();

        // Revocation is visible on the next uncached RPC.
        try (var ignored = identityFixture.request(headman)) {
            assistants.revokeAssistant(assistantAssignmentId);
        }
        assertThat(
                        withInternalToken(activeToken)
                                .checkAssistantPermission(markAttendance)
                                .getAllowed())
                .isFalse();

        // Restore a real active helper before changing its Auth/domain role status.
        try (var ignored = identityFixture.request(headman)) {
            assistantAssignmentId =
                    assistants
                            .assignAssistant(
                                    new AssignAssistantRequest(
                                            assistantId,
                                            groupId,
                                            List.of(AssistantPermission.MARK_ATTENDANCE)))
                            .getId();
        }
        assertThat(
                        withInternalToken(activeToken)
                                .checkAssistantPermission(markAttendance)
                                .getAllowed())
                .isTrue();
        try (var ignored = identityFixture.request(adminActor)) {
            userService.updateRoleGrant(
                    assistantId,
                    "STUDENT",
                    new RoleGrantUpdateRequest(RoleGrantStatus.EXPELLED, null, null, null));
        }
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT is_active FROM headman_assistants WHERE id = ?",
                                Boolean.class,
                                assistantAssignmentId))
                .isTrue();
        // The former active session cannot override the current durable role status.
        assertThat(
                        withInternalToken(activeToken)
                                .checkAssistantPermission(markAttendance)
                                .getAllowed())
                .isFalse();
        assertThat(users.findById(assistantId).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.EXPELLED);
        // A terminal grant cannot produce a fresh selected identity. The real Auth boundary
        // also refuses the formerly selected session; no terminal token is manufactured.
        assertThatThrownBy(() -> liveSessions.admitProjection(activeActor.claims()))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
        assertThatThrownBy(() -> identityFixture.select(assistantId, AuthRole.STUDENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Actual Auth role selection rejected");

        assertThatThrownBy(() -> stub.checkAssistantPermission(markAttendance))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAUTHENTICATED));
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

            StatusRuntimeException ex =
                    assertThrows(
                            StatusRuntimeException.class, () -> stub.getActiveSemester(request));
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        } finally {
            // Restore active semester for subsequent tests
            jdbcTemplate.update(
                    "UPDATE semesters SET is_active = true WHERE id = ?", testSemesterId);
        }
    }

    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub withInternalToken(
            String token) {
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

        // Canonical campus: lat=55.788204, lng=37.606762, radius_m=200 (RUT MIIT, ул. Образцова 9)
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
        UserRequest request = UserRequest.newBuilder().setUserId(teacherId).build();

        UserResponse response = stub.getUserById(request);

        assertThat(response.getId()).isEqualTo(teacherId);
        assertThat(response.getLogin()).isEqualTo(teacherLogin);
        assertThat(response.getDisplayName()).isEqualTo("Преподавателев Учитель Знаниевич");
        assertThat(response.getRole()).isEqualTo("teacher");
        assertThat(response.getStatus()).isEqualTo("active");
    }

    @Test
    void getUserById_archivedUser_stillReturnsUser() {
        // The archived user must be returned (not NOT_FOUND)
        // Auth metadata and the Academic archived profile must agree.
        UserRequest request = UserRequest.newBuilder().setUserId(archivedUserId).build();

        UserResponse response = stub.getUserById(request);

        assertThat(response.getId()).isEqualTo(archivedUserId);
        assertThat(response.getLogin()).isEqualTo(archivedLogin);
        assertThat(response.getStatus()).isEqualTo("archived");
    }

    @Test
    void getUserById_invalidId_throwsNotFound() {
        UserRequest request = UserRequest.newBuilder().setUserId(NONEXISTENT_ID).build();

        StatusRuntimeException ex =
                assertThrows(StatusRuntimeException.class, () -> stub.getUserById(request));
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // =====================================================================
    // GRPC-09: GetUserByTelegramId
    // =====================================================================

    @Test
    void getUserByTelegramId_found_returnsProfileWithoutCredentials() {
        var botStudent = seedStudent("Bot", "TestStudent", 987654321L);
        identityFixture.catchUpCatalog();

        UserByTelegramIdRequest request =
                UserByTelegramIdRequest.newBuilder().setTelegramId(987654321L).build();

        UserByTelegramIdResponse response = stub.getUserByTelegramId(request);

        assertThat(response.getFound()).isTrue();
        assertThat(response.getUserId()).isEqualTo(botStudent.userId());
        assertThat(response.getLogin()).isEqualTo(botStudent.login());
        assertThat(response.getDisplayName()).isEqualTo("Bot TestStudent");
        assertThat(response.getRole()).isEqualTo("student");
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.getGroupName()).isEqualTo("ИВТ-211");
        assertThat(response.getIsHeadman()).isFalse();
        assertThat(response.getTelegramId()).isEqualTo(987654321L);
        // A generic bot lookup has no capability bound to credential access.
        assertThat(response.getInitialPassword()).isEmpty();
        assertThat(response.getPasswordChanged()).isFalse();
    }

    @Test
    void getUserByTelegramId_notFound_returnsFoundFalse() {
        UserByTelegramIdRequest request =
                UserByTelegramIdRequest.newBuilder().setTelegramId(NONEXISTENT_ID).build();

        UserByTelegramIdResponse response = stub.getUserByTelegramId(request);

        assertThat(response.getFound()).isFalse();
        assertThat(response.getLogin()).isEmpty();
        assertThat(response.getUserId()).isZero();
    }
}
