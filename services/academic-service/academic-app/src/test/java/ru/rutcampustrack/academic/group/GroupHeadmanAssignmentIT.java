package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PostgreSQL proof for the canonical ADMIN headman transaction. The assertions cover the durable
 * grant source used by the registry, legacy PATCH convergence and a real two-transaction candidate
 * race.
 */
@AutoConfigureMockMvc
class GroupHeadmanAssignmentIT extends AbstractAcademicIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(20);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupHeadmanAssignmentService assignmentService;
    @Autowired private UserService userService;
    @Autowired private GroupRegistryReadRepository registryReadRepository;
    @Autowired private MockMvc mockMvc;

    @Autowired
    private ru.rutcampustrack.academic.grpc.HeadmanGroupCompositionReadService
            compositionReadService;

    @Test
    void currentHeadmanCompositionUsesFreshGrantRosterAndGroupRoles() {
        Fixture fixture = fixture("composition");
        assign(fixture.groupId(), new AssignHeadmanRequest(fixture.firstId(), null));
        jdbc.update(
                "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by,"
                    + " is_active, assigned_at) VALUES (?, ?, ARRAY['view_stats']::varchar[], ?,"
                    + " true, NOW())",
                fixture.groupId(),
                fixture.thirdId(),
                adminId());
        var actor = identityFixture.select(fixture.firstId(), AuthRole.HEADMAN).claims();
        var snapshot = compositionReadService.read(actor, fixture.groupId());
        assertThat(snapshot.getGroupName()).isEqualTo(fixture.name());
        assertThat(snapshot.getMembersList())
                .extracting(member -> member.getUserId())
                .containsExactly(fixture.secondId(), fixture.firstId(), fixture.thirdId());
        assertThat(snapshot.getMembersList())
                .extracting(member -> member.getGroupRole())
                .containsExactly("STUDENT", "HEADMAN", "ASSISTANT");
        assertThat(snapshot.getMembersList())
                .allSatisfy(
                        member -> {
                            assertThat(member.getDisplayName()).startsWith("Тестов ");
                            assertThat(member.getLogin()).isNotBlank();
                        });
        // A VIEW_STATS assistant cannot reuse the personal roster projection.
        assertThatThrownBy(
                        () ->
                                compositionReadService.read(
                                        identityFixture
                                                .select(fixture.thirdId(), AuthRole.STUDENT)
                                                .claims(),
                                        fixture.groupId()))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
        assertThatThrownBy(() -> compositionReadService.read(actor, fixture.groupId() + 1))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);

        jdbc.update(
                "UPDATE headman_assistants SET is_active = false, revoked_at = NOW() "
                        + "WHERE group_id = ?",
                fixture.groupId());
        String generatedLogin =
                identityFixture
                        .authJdbc()
                        .queryForObject(
                                "SELECT login FROM auth_identities WHERE id=?",
                                String.class,
                                fixture.thirdId());
        assertThat(snapshot.getMembersList().getLast().getLogin()).isEqualTo(generatedLogin);
        patchUser(
                fixture.thirdId(),
                new PatchUserRequest(null, null, "Обновлённый", null, null, null, null, null));
        Long added = student("composition_new", "Александр", fixture.groupId());
        var fresh = compositionReadService.read(actor, fixture.groupId());
        assertThat(fresh.getMembersList())
                .extracting(member -> member.getUserId())
                .containsExactly(added, fixture.secondId(), fixture.firstId(), fixture.thirdId());
        assertThat(fresh.getMembersList().getLast().getDisplayName())
                .isEqualTo("Тестов Третий Обновлённый")
                .isNotEqualTo(snapshot.getMembersList().getLast().getDisplayName());
        assertThat(fresh.getMembersList().getLast().getLogin()).isEqualTo(generatedLogin);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT login FROM auth_identities WHERE id=?",
                                        String.class,
                                        fixture.thirdId()))
                .isEqualTo(generatedLogin);
        assertThat(fresh.getMembersList().getFirst().getLogin())
                .isEqualTo(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT login FROM auth_identities WHERE id=?",
                                        String.class,
                                        added));
        assertThat(fresh.getMembersList().getLast().getGroupRole()).isEqualTo("STUDENT");
        // Durable transfer invalidates the old signed HEADMAN identity on the next read.
        assign(fixture.groupId(), new AssignHeadmanRequest(fixture.secondId(), fixture.firstId()));
        assertThatThrownBy(() -> compositionReadService.read(actor, fixture.groupId()))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
    }

    @Test
    void assignmentChangesDurableHeadmanRevokesAssistantsAndReloadsRegistry() throws Exception {
        Fixture fixture = fixture("switch");
        Long adminId = adminId();

        mockMvc.perform(
                        put("/academic/groups/{id}/headman", fixture.groupId())
                                .header("X-User-Id", adminId)
                                .header("X-User-Role", "ADMIN")
                                .with(signedAdmin())
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"studentId\":"
                                                + fixture.firstId()
                                                + ",\"expectedHeadmanId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headmanId", equalTo(fixture.firstId().intValue())))
                .andExpect(jsonPath("$.activatesDraft", equalTo(true)));

        jdbc.update(
                "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by,"
                    + " is_active, assigned_at) VALUES (?, ?, ARRAY['manage_homework']::varchar[],"
                    + " ?, true, NOW())",
                fixture.groupId(),
                fixture.thirdId(),
                adminId);

        var preview = assignmentService.preview(fixture.groupId(), fixture.secondId());
        assertThat(preview.assistantsToRevoke()).isEqualTo(1);
        assertThat(preview.currentHeadmanId()).isEqualTo(fixture.firstId());

        HeadmanAssignmentResponse changed =
                assign(
                        fixture.groupId(),
                        new AssignHeadmanRequest(fixture.secondId(), fixture.firstId()));
        assertThat(changed.changed()).isTrue();
        assertThat(changed.assistantsRevoked()).isEqualTo(1);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_role_directory WHERE group_id = ?"
                                        + " AND role = 'headman' AND status = 'active'",
                                Long.class,
                                fixture.groupId()))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM academic_role_directory WHERE user_id = ? AND"
                                        + " role = 'headman'",
                                String.class,
                                fixture.firstId()))
                .isEqualTo("suspended");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_headman FROM academic_user_directory WHERE id = ?",
                                Boolean.class,
                                fixture.firstId()))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_headman FROM academic_user_directory WHERE id = ?",
                                Boolean.class,
                                fixture.secondId()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM headman_assistants WHERE group_id = ? AND"
                                        + " student_id = ?",
                                Boolean.class,
                                fixture.groupId(),
                                fixture.thirdId()))
                .isFalse();

        HeadmanAssignmentResponse repeat =
                assign(
                        fixture.groupId(),
                        new AssignHeadmanRequest(fixture.secondId(), fixture.secondId()));
        assertThat(repeat.changed()).isFalse();
        assertThat(repeat.assistantsRevoked()).isZero();

        var page =
                registryReadRepository.find(
                        AdminGroupStatus.ACTIVE,
                        fixture.name(),
                        org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).headmanFio()).isEqualTo("Тестов Второй");
    }

    @Test
    void activeStudentGrantKeepsMultiRoleHeadmanEligibleDuringProfileSync() {
        Fixture fixture = fixture("multi_role", true);
        HeadmanAssignmentResponse assigned =
                assign(fixture.groupId(), new AssignHeadmanRequest(fixture.firstId(), null));
        assertThat(assigned.changed()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_role_directory WHERE user_id = ? AND"
                                    + " role = 'headman' AND status = 'active' AND group_id = ?",
                                Long.class,
                                fixture.firstId(),
                                fixture.groupId()))
                .isEqualTo(1L);

        patchUser(
                fixture.firstId(),
                new PatchUserRequest(null, "Обновлён", null, null, null, null, null, null));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT role FROM academic_user_directory WHERE id = ?",
                                String.class,
                                fixture.firstId()))
                .isEqualTo("teacher");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_role_directory WHERE user_id = ? AND"
                                    + " role = 'headman' AND status = 'active' AND group_id = ?",
                                Long.class,
                                fixture.firstId(),
                                fixture.groupId()))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM academic_role_directory WHERE user_id = ? AND"
                                        + " role = 'teacher'",
                                String.class,
                                fixture.firstId()))
                .isEqualTo("suspended");

        updateRoleGrant(
                fixture.firstId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM academic_role_directory WHERE user_id = ? AND"
                                        + " role = 'student'",
                                String.class,
                                fixture.firstId()))
                .isEqualTo("suspended");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM academic_role_directory WHERE user_id = ? AND"
                                        + " role = 'headman'",
                                String.class,
                                fixture.firstId()))
                .isEqualTo("suspended");
    }

    @Test
    void legacyPatchRejectsStandaloneRemovalButAllowsReplacementAndNonHeadmanFalse()
            throws Exception {
        Fixture fixture = fixture("legacy");

        patchUser(
                fixture.firstId(),
                new PatchUserRequest(null, null, null, true, null, null, null, null));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_role_directory WHERE group_id = ?"
                                        + " AND role = 'headman' AND status = 'active'",
                                Long.class,
                                fixture.groupId()))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_headman FROM academic_user_directory WHERE id = ?",
                                Boolean.class,
                                fixture.firstId()))
                .isTrue();

        jdbc.update(
                "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by,"
                    + " is_active, assigned_at) VALUES (?, ?, ARRAY['view_stats']::varchar[], ?,"
                    + " true, NOW())",
                fixture.groupId(),
                fixture.thirdId(),
                adminId());
        // The real create operation already committed its managed enrollment history.
        var formerHeadman = identityFixture.select(fixture.firstId(), AuthRole.HEADMAN).claims();
        var before = headmanMutationSnapshot(fixture);

        patchHeadman(fixture.firstId(), "{\"isHeadman\":false,\"lastName\":\"Отказ\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field", equalTo("isHeadman")));
        assertThat(headmanMutationSnapshot(fixture)).isEqualTo(before);

        patchHeadman(fixture.secondId(), "{\"isHeadman\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headman", equalTo(true)));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_role_directory WHERE group_id = ?"
                                        + " AND role = 'headman' AND status = 'active'",
                                Long.class,
                                fixture.groupId()))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM academic_role_directory WHERE user_id = ? AND"
                                        + " role = 'headman'",
                                String.class,
                                fixture.firstId()))
                .isEqualTo("suspended");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM headman_assistants WHERE group_id = ? AND"
                                        + " student_id = ?",
                                Boolean.class,
                                fixture.groupId(),
                                fixture.thirdId()))
                .isFalse();
        assertThatThrownBy(() -> compositionReadService.read(formerHeadman, fixture.groupId()))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);

        // A false flag on an ordinary student must not remove the other current headman.
        patchHeadman(fixture.firstId(), "{\"isHeadman\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headman", equalTo(false)));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT user_id FROM academic_role_directory WHERE group_id = ? AND"
                                        + " role = 'headman' AND status = 'active'",
                                Long.class,
                                fixture.groupId()))
                .isEqualTo(fixture.secondId());
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM student_group_history WHERE group_id = ? ORDER BY"
                                        + " id",
                                fixture.groupId()))
                .isEqualTo(before.get("history"));
    }

    private org.springframework.test.web.servlet.ResultActions patchHeadman(
            long userId, String body) throws Exception {
        return mockMvc.perform(
                patch("/academic/users/{id}", userId)
                        .header("X-User-Id", adminId())
                        .header("X-User-Role", "ADMIN")
                        .with(signedAdmin())
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false")
                        .contentType(APPLICATION_JSON)
                        .content(body));
    }

    private Map<String, Object> headmanMutationSnapshot(Fixture fixture) {
        return Map.of(
                "users",
                        jdbc.queryForList(
                                "SELECT id, last_name, first_name, group_id, status, is_headman,"
                                        + " roles_version, updated_at FROM academic_user_directory"
                                        + " WHERE id IN (?, ?, ?) ORDER BY id",
                                fixture.firstId(),
                                fixture.secondId(),
                                fixture.thirdId()),
                "grants",
                        jdbc.queryForList(
                                "SELECT * FROM academic_role_directory WHERE group_id = ? ORDER BY"
                                        + " id",
                                fixture.groupId()),
                "assistants",
                        jdbc.queryForList(
                                "SELECT to_jsonb(h)::text AS state FROM headman_assistants h WHERE"
                                        + " group_id = ? ORDER BY id",
                                fixture.groupId()),
                "history",
                        jdbc.queryForList(
                                "SELECT * FROM student_group_history WHERE group_id = ? ORDER BY"
                                        + " id",
                                fixture.groupId()),
                "outbox",
                        jdbc.queryForList(
                                "SELECT id, event_type FROM academic_outbox ORDER BY id"));
    }

    @Test
    void concurrentCandidatesHaveOneWinnerAndOneCasConflict() throws Exception {
        Fixture fixture = fixture("race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> first =
                    CompletableFuture.supplyAsync(
                            () -> assignStatus(fixture.groupId(), fixture.firstId(), null),
                            executor);
            CompletableFuture<Integer> second =
                    CompletableFuture.supplyAsync(
                            () -> assignStatus(fixture.groupId(), fixture.secondId(), null),
                            executor);
            CompletableFuture.allOf(first, second).join();
            long successes = 0;
            long conflicts = 0;
            for (CompletableFuture<Integer> result : List.of(first, second)) {
                int status = result.join();
                if (status == 200) successes++;
                else {
                    assertThat(status)
                            .withFailMessage(
                                    () ->
                                            "HEADMAN_RACE_HTTP_DIAGNOSTIC_V1 category="
                                                    + switch (status) {
                                                        case 200 -> "HTTP200";
                                                        case 202 -> "HTTP202";
                                                        case 401 -> "HTTP401";
                                                        case 403 -> "HTTP403";
                                                        case 409 -> "HTTP409";
                                                        case 429 -> "HTTP429";
                                                        case 503 -> "HTTP503";
                                                        default -> "OTHER";
                                                    })
                            .isEqualTo(409);
                    conflicts++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM academic_role_directory WHERE group_id ="
                                            + " ? AND role = 'headman' AND status = 'active'",
                                    Long.class,
                                    fixture.groupId()))
                    .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void archivedGroupAndNonMemberAreRejected() throws Exception {
        Fixture source = fixture("invalid_source");
        Fixture other = fixture("invalid_other");

        assertThat(assignStatus(source.groupId(), other.firstId(), null)).isEqualTo(409);
        jdbc.update("UPDATE groups SET is_active = false WHERE id = ?", source.groupId());
        assertThat(assignStatus(source.groupId(), source.firstId(), null)).isEqualTo(409);
    }

    private HeadmanAssignmentResponse assign(long groupId, AssignHeadmanRequest request) {
        try (var ignored = identityFixture.request(admin())) {
            return assignmentService.assign(groupId, request);
        }
    }

    private void patchUser(long userId, PatchUserRequest request) {
        try (var ignored = identityFixture.request(admin())) {
            userService.patchUser(userId, request);
        }
    }

    private void updateRoleGrant(long userId, String role, RoleGrantUpdateRequest request) {
        try (var ignored = identityFixture.request(admin())) {
            userService.updateRoleGrant(userId, role, request);
        }
    }

    private int assignStatus(long groupId, long candidate, Long expected) {
        try {
            return mockMvc.perform(
                            put("/academic/groups/{id}/headman", groupId)
                                    .with(identityFixture.signed(admin(), UUID.randomUUID()))
                                    .contentType(APPLICATION_JSON)
                                    .content(
                                            "{\"studentId\":"
                                                    + candidate
                                                    + ",\"expectedHeadmanId\":"
                                                    + expected
                                                    + "}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception failure) {
            throw new AssertionError("Actual headman HTTP request failed", failure);
        }
    }

    private SelectedActor admin() {
        return identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
    }

    private Fixture fixture(String label) {
        return fixture(label, false);
    }

    private Fixture fixture(String label, boolean teacherFirst) {
        long entropy = System.nanoTime() + SEQUENCE.getAndIncrement();
        char[] letters = {
            'А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ж', 'З', 'К', 'Л', 'М', 'Н', 'О', 'П', 'Р', 'С'
        };
        String alphabetic =
                "Т"
                        + letters[Math.floorMod(entropy, letters.length)]
                        + letters[Math.floorMod(entropy / letters.length, letters.length)];
        String numeric = String.format("%03d", 100 + Math.floorMod(entropy, 899));
        String name = alphabetic + "-" + numeric;
        Long groupId =
                jdbc.queryForObject(
                        "INSERT INTO groups (name, alphabetic_code, numeric_code, current_course,"
                            + " training_duration_years, duration_status, is_active, created_at)"
                            + " VALUES (?, ?, ?, ?, 9, 'KNOWN', true, NOW()) RETURNING id",
                        Long.class,
                        name,
                        alphabetic,
                        numeric,
                        Integer.parseInt(numeric.substring(0, 1)));
        jdbc.update(
                "INSERT INTO group_history_coverage(group_id,coverage_from,writer_version) "
                        + "SELECT ?,date_from,? FROM semesters WHERE id=?",
                groupId,
                ru.rutcampustrack.academic.history.HistoricalMembershipService.WRITER_VERSION,
                identityFixture.canonical().semesterId());
        Long first;
        if (teacherFirst) {
            String employee = "EMP-HEADMAN-" + SEQUENCE.incrementAndGet();
            first =
                    identityFixture
                            .seedUser(
                                    new CreateUserRequest(
                                            "Тестов",
                                            "Первый",
                                            null,
                                            UserRole.TEACHER,
                                            null,
                                            employee,
                                            null),
                                    admin())
                            .userId();
            updateRoleGrant(
                    first,
                    "STUDENT",
                    new RoleGrantUpdateRequest(
                            RoleGrantStatus.ACTIVE,
                            groupId,
                            null,
                            610000007000L + SEQUENCE.incrementAndGet()));
            updateRoleGrant(
                    first,
                    "TEACHER",
                    new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, employee, null));
        } else first = student(label + "_first", "Первый", groupId);
        Long second = student(label + "_second", "Второй", groupId);
        Long third = student(label + "_third", "Третий", groupId);
        return new Fixture(groupId, name, first, second, third);
    }

    private Long student(String login, String firstName, Long groupId) {
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                "Тестов",
                                firstName,
                                null,
                                UserRole.STUDENT,
                                groupId,
                                null,
                                610000007000L + SEQUENCE.incrementAndGet()),
                        admin())
                .userId();
    }

    private Long adminId() {
        return identityFixture.canonical().adminId();
    }

    private record Fixture(Long groupId, String name, Long firstId, Long secondId, Long thirdId) {}
}
