package ru.rutcampustrack.academic.group;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.HeadmanAssignmentResponse;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.GroupRegistryReadRepository;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL proof for the canonical ADMIN headman transaction. The assertions
 * cover the durable grant source used by the registry, legacy PATCH convergence
 * and a real two-transaction candidate race.
 */
@AutoConfigureMockMvc
class GroupHeadmanAssignmentIT extends AbstractAcademicIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(20);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupHeadmanAssignmentService assignmentService;
    @Autowired private UserService userService;
    @Autowired private GroupRegistryReadRepository registryReadRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private ru.rutcampustrack.academic.grpc.HeadmanGroupCompositionReadService compositionReadService;

    @Test
    void currentHeadmanCompositionUsesFreshGrantRosterAndGroupRoles() {
        Fixture fixture = fixture("composition");
        assignmentService.assign(fixture.groupId(), new AssignHeadmanRequest(fixture.firstId(), null));
        jdbc.update("INSERT INTO headman_assistants "
                + "(group_id, student_id, permissions, assigned_by, is_active, assigned_at) "
                + "VALUES (?, ?, ARRAY['view_stats']::varchar[], ?, true, NOW())",
                fixture.groupId(), fixture.thirdId(), adminId());
        var actor = compositionActor(fixture.firstId(), fixture.groupId(), "HEADMAN", true);
        var snapshot = compositionReadService.read(actor, fixture.groupId());
        assertThat(snapshot.getGroupName()).isEqualTo(fixture.name());
        assertThat(snapshot.getMembersList()).extracting(member -> member.getUserId())
                .containsExactly(fixture.secondId(), fixture.firstId(), fixture.thirdId());
        assertThat(snapshot.getMembersList()).extracting(member -> member.getGroupRole())
                .containsExactly("STUDENT", "HEADMAN", "ASSISTANT");
        assertThat(snapshot.getMembersList()).allSatisfy(member -> {
            assertThat(member.getDisplayName()).startsWith("Тестов ");
            assertThat(member.getLogin()).isNotBlank();
        });
        // A VIEW_STATS assistant cannot reuse the personal roster projection.
        assertThatThrownBy(() -> compositionReadService.read(
                compositionActor(fixture.thirdId(), fixture.groupId(), "STUDENT", false), fixture.groupId()))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
        assertThatThrownBy(() -> compositionReadService.read(actor, fixture.groupId() + 1))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);

        jdbc.update("UPDATE headman_assistants SET is_active = false, revoked_at = NOW() "
                + "WHERE group_id = ?", fixture.groupId());
        String changedLogin = "roster_" + Long.toUnsignedString(System.nanoTime(), 36);
        jdbc.update("UPDATE users SET login = ? WHERE id = ?", changedLogin, fixture.thirdId());
        Long added = student("composition_new", "Александр", fixture.groupId());
        var fresh = compositionReadService.read(actor, fixture.groupId());
        assertThat(fresh.getMembersList()).extracting(member -> member.getUserId())
                .containsExactly(added, fixture.secondId(), fixture.firstId(), fixture.thirdId());
        assertThat(fresh.getMembersList().getLast().getLogin()).isEqualTo(changedLogin);
        assertThat(fresh.getMembersList().getLast().getGroupRole()).isEqualTo("STUDENT");
        // Durable transfer invalidates the old signed HEADMAN identity on the next read.
        assignmentService.assign(fixture.groupId(), new AssignHeadmanRequest(fixture.secondId(), fixture.firstId()));
        assertThatThrownBy(() -> compositionReadService.read(actor, fixture.groupId()))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
    }

    private static ru.rutcampustrack.shared.security.InternalJwtClaims compositionActor(
            long userId, long groupId, String role, boolean headman) {
        return new ru.rutcampustrack.shared.security.InternalJwtClaims(userId,
                java.util.UUID.randomUUID(), 1, 1, role, "ACTIVE", groupId, headman, false);
    }

    @Test
    void assignmentChangesDurableHeadmanRevokesAssistantsAndReloadsRegistry() throws Exception {
        Fixture fixture = fixture("switch");
        Long adminId = adminId();

        mockMvc.perform(put("/academic/groups/{id}/headman", fixture.groupId())
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false")
                        .contentType(APPLICATION_JSON)
                        .content("{\"studentId\":" + fixture.firstId()
                                + ",\"expectedHeadmanId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headmanId", equalTo(fixture.firstId().intValue())))
                .andExpect(jsonPath("$.activatesDraft", equalTo(true)));

        jdbc.update("INSERT INTO headman_assistants "
                        + "(group_id, student_id, permissions, assigned_by, is_active, assigned_at) "
                        + "VALUES (?, ?, ARRAY['manage_homework']::varchar[], ?, true, NOW())",
                fixture.groupId(), fixture.thirdId(), adminId);

        var preview = assignmentService.preview(
                fixture.groupId(), fixture.secondId());
        assertThat(preview.assistantsToRevoke()).isEqualTo(1);
        assertThat(preview.currentHeadmanId()).isEqualTo(fixture.firstId());

        HeadmanAssignmentResponse changed = assignmentService.assign(
                fixture.groupId(), new AssignHeadmanRequest(fixture.secondId(), fixture.firstId()));
        assertThat(changed.changed()).isTrue();
        assertThat(changed.assistantsRevoked()).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_role_grants "
                        + "WHERE group_id = ? AND role = 'headman' AND status = 'active'",
                Long.class, fixture.groupId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM user_role_grants WHERE user_id = ? AND role = 'headman'",
                String.class, fixture.firstId())).isEqualTo("suspended");
        assertThat(jdbc.queryForObject(
                "SELECT is_headman FROM users WHERE id = ?",
                Boolean.class, fixture.firstId())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT is_headman FROM users WHERE id = ?",
                Boolean.class, fixture.secondId())).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT is_active FROM headman_assistants WHERE group_id = ? AND student_id = ?",
                Boolean.class, fixture.groupId(), fixture.thirdId())).isFalse();

        HeadmanAssignmentResponse repeat = assignmentService.assign(
                fixture.groupId(), new AssignHeadmanRequest(fixture.secondId(), fixture.secondId()));
        assertThat(repeat.changed()).isFalse();
        assertThat(repeat.assistantsRevoked()).isZero();

        var page = registryReadRepository.find(
                AdminGroupStatus.ACTIVE, fixture.name(), org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).headmanFio()).isEqualTo("Тестов Второй");
    }

    @Test
    void activeStudentGrantKeepsMultiRoleHeadmanEligibleDuringProfileSync() {
        Fixture fixture = fixture("multi_role");
        jdbc.update("UPDATE users SET role = 'teacher', status = 'suspended', employee_number = ?, group_id = NULL, "
                        + "updated_at = NOW() WHERE id = ?",
                "EMP-" + fixture.firstId(), fixture.firstId());
        jdbc.update("INSERT INTO user_role_grants "
                        + "(user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'teacher', 'suspended', NULL, NOW(), NOW())",
                fixture.firstId());

        HeadmanAssignmentResponse assigned = assignmentService.assign(
                fixture.groupId(), new AssignHeadmanRequest(fixture.firstId(), null));
        assertThat(assigned.changed()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_role_grants "
                        + "WHERE user_id = ? AND role = 'headman' AND status = 'active' AND group_id = ?",
                Long.class, fixture.firstId(), fixture.groupId())).isEqualTo(1L);

        userService.patchUser(fixture.firstId(),
                new PatchUserRequest(null, "Обновлён", null, null, null, null, null, null));

        assertThat(jdbc.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, fixture.firstId()))
                .isEqualTo("teacher");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_role_grants "
                        + "WHERE user_id = ? AND role = 'headman' AND status = 'active' AND group_id = ?",
                Long.class, fixture.firstId(), fixture.groupId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM user_role_grants WHERE user_id = ? AND role = 'teacher'",
                String.class, fixture.firstId())).isEqualTo("suspended");

        userService.updateRoleGrant(fixture.firstId(), "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM user_role_grants WHERE user_id = ? AND role = 'student'",
                String.class, fixture.firstId())).isEqualTo("suspended");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM user_role_grants WHERE user_id = ? AND role = 'headman'",
                String.class, fixture.firstId())).isEqualTo("suspended");
    }

    @Test
    void legacyPatchConvergesThroughCanonicalPathAndKeepsOneActiveGrant() {
        Fixture fixture = fixture("legacy");

        userService.patchUser(fixture.firstId(),
                new PatchUserRequest(null, null, null, true, null, null, null, null));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_role_grants "
                        + "WHERE group_id = ? AND role = 'headman' AND status = 'active'",
                Long.class, fixture.groupId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT is_headman FROM users WHERE id = ?",
                Boolean.class, fixture.firstId())).isTrue();

        userService.patchUser(fixture.firstId(),
                new PatchUserRequest(null, null, null, false, null, null, null, null));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_role_grants "
                        + "WHERE group_id = ? AND role = 'headman' AND status = 'active'",
                Long.class, fixture.groupId())).isZero();
    }

    @Test
    void concurrentCandidatesHaveOneWinnerAndOneCasConflict() throws Exception {
        Fixture fixture = fixture("race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<HeadmanAssignmentResponse> first = CompletableFuture.supplyAsync(
                    () -> assignmentService.assign(
                            fixture.groupId(), new AssignHeadmanRequest(fixture.firstId(), null)), executor);
            CompletableFuture<HeadmanAssignmentResponse> second = CompletableFuture.supplyAsync(
                    () -> assignmentService.assign(
                            fixture.groupId(), new AssignHeadmanRequest(fixture.secondId(), null)), executor);
            CompletableFuture.allOf(first, second).handle((ignored, failure) -> null).join();

            long successes = 0;
            long conflicts = 0;
            for (CompletableFuture<HeadmanAssignmentResponse> result : new CompletableFuture[]{first, second}) {
                try {
                    result.join();
                    successes++;
                } catch (CompletionException error) {
                    if (error.getCause() instanceof ru.rutcampustrack.academic.exception.ConflictException) {
                        conflicts++;
                    } else {
                        throw error;
                    }
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM user_role_grants "
                            + "WHERE group_id = ? AND role = 'headman' AND status = 'active'",
                    Long.class, fixture.groupId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void archivedGroupAndNonMemberAreRejected() {
        Fixture source = fixture("invalid_source");
        Fixture other = fixture("invalid_other");

        assertThatThrownBy(() -> assignmentService.assign(
                source.groupId(), new AssignHeadmanRequest(other.firstId(), null)))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);

        jdbc.update("UPDATE groups SET is_active = false WHERE id = ?", source.groupId());
        assertThatThrownBy(() -> assignmentService.assign(
                source.groupId(), new AssignHeadmanRequest(source.firstId(), null)))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
    }

    private Fixture fixture(String label) {
        long entropy = System.nanoTime() + SEQUENCE.getAndIncrement();
        char[] letters = {'А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ж', 'З', 'К', 'Л', 'М', 'Н', 'О', 'П', 'Р', 'С'};
        String alphabetic = "Т" + letters[Math.floorMod(entropy, letters.length)]
                + letters[Math.floorMod(entropy / letters.length, letters.length)];
        String numeric = String.format("%03d", 100 + Math.floorMod(entropy, 899));
        String name = alphabetic + "-" + numeric;
        Long groupId = jdbc.queryForObject(
                "INSERT INTO groups (name, alphabetic_code, numeric_code, current_course, "
                        + "training_duration_years, duration_status, is_active, created_at) "
                        + "VALUES (?, ?, ?, ?, 9, 'KNOWN', true, NOW()) RETURNING id",
                Long.class, name, alphabetic, numeric, Integer.parseInt(numeric.substring(0, 1)));
        Long first = student(label + "_first", "Первый", groupId);
        Long second = student(label + "_second", "Второй", groupId);
        Long third = student(label + "_third", "Третий", groupId);
        return new Fixture(groupId, name, first, second, third);
    }

    private Long student(String login, String firstName, Long groupId) {
        String boundedLogin = login.substring(0, Math.min(login.length(), 12))
                + "_" + Long.toUnsignedString(System.nanoTime(), 36);
        Long id = jdbc.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                        + "is_headman, group_id, password_changed, created_at, updated_at) "
                        + "VALUES (?, 'x', 'Тестов', ?, 'student', 'active', false, ?, false, NOW(), NOW()) "
                        + "RETURNING id",
                Long.class, boundedLogin, firstName, groupId);
        jdbc.update("INSERT INTO user_role_grants "
                        + "(user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'student', 'active', ?, NOW(), NOW())",
                id, groupId);
        return id;
    }

    private Long adminId() {
        return jdbc.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
    }

    private record Fixture(Long groupId, String name, Long firstId, Long secondId, Long thirdId) {}
}
