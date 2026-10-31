package ru.rutcampustrack.academic.assistant;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

/** Real HTTP + PostgreSQL locks prove conditional rights changes never resurrect revoked rights. */
class AssistantPermissionsCasIT extends AbstractAcademicIntegrationTest {
    private static final String A = "MARK_ATTENDANCE";
    private static final String B = "MANAGE_HOMEWORK";
    private static final String C = "MANAGE_EXCUSES";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;
    private SelectedActor headman;
    private long group;
    private long helper;
    private long assistant;

    @BeforeEach
    void fixture() throws Exception {
        var canonical = identityFixture.canonical();
        var admin = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        group = canonical.groupId();
        helper =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "AssistantCas",
                                        "Fixture",
                                        null,
                                        UserRole.STUDENT,
                                        group,
                                        null,
                                        740000000001L),
                                admin)
                        .userId();
        headman = identityFixture.select(canonical.studentId(), AuthRole.HEADMAN);
        assistant = assign();
    }

    @Test
    void concurrentRevocationWinsAndStaleAdditionRollsBackWithoutNotification() throws Exception {
        long facts = factCount();
        String revoke =
                mapper.writeValueAsString(
                        Map.of("permissions", List.of(A), "expectedPermissions", List.of(B, A)));
        String staleAdd =
                mapper.writeValueAsString(
                        Map.of(
                                "permissions",
                                List.of(A, B, C),
                                "expectedPermissions",
                                List.of(A, B)));
        try (var executor = Executors.newFixedThreadPool(2)) {
            try (Connection lock = dataSource.getConnection()) {
                lock.setAutoCommit(false);
                try (var statement =
                        lock.prepareStatement(
                                "SELECT id FROM headman_assistants WHERE id=? FOR UPDATE")) {
                    statement.setLong(1, assistant);
                    try (var ignored = statement.executeQuery()) {
                        var revocation = executor.submit(() -> patch(headman, revoke));
                        awaitLockWaiters(1);
                        var addition = executor.submit(() -> patch(headman, staleAdd));
                        awaitLockWaiters(2);
                        lock.commit();
                        assertThat(revocation.get(10, TimeUnit.SECONDS).getStatusCode())
                                .isEqualTo(HttpStatus.OK);
                        assertConflict(addition.get(10, TimeUnit.SECONDS));
                    }
                } finally {
                    lock.rollback();
                }
            }
        }
        assertThat(storedPermissions()).containsExactly("mark_attendance");
        assertThat(active()).isTrue();
        assertThat(factCount()).isEqualTo(facts + 1);
        var fact = latestFact();
        assertThat(fact.path("change").asText()).isEqualTo("PERMISSIONS_CHANGED");
        assertThat(fact.path("after_permissions")).hasSize(1);
        assertThat(fact.path("after_permissions").get(0).asText()).isEqualTo("mark_attendance");
    }

    @Test
    void conditionalLastRightRevokeRejectsStaleSnapshotAndPreservesLegacyLifecycle()
            throws Exception {
        // Legacy replacement remains accepted; legacy empty replacement remains invalid.
        assertThat(patch(headman, "{\"permissions\":[\"MARK_ATTENDANCE\"]}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        long beforeInvalid = factCount();
        assertThat(patch(headman, "{\"permissions\":[]}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(factCount()).isEqualTo(beforeInvalid);
        assertThat(
                        patch(
                                        headman,
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "permissions",
                                                        List.of(A, B),
                                                        "expectedPermissions",
                                                        List.of(A))))
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        long beforeConflict = factCount();
        assertConflict(
                patch(
                        headman,
                        mapper.writeValueAsString(
                                Map.of(
                                        "permissions",
                                        List.of(),
                                        "expectedPermissions",
                                        List.of(A)))));
        assertThat(active()).isTrue();
        assertThat(storedPermissions()).containsExactly("mark_attendance", "manage_homework");
        assertThat(factCount()).isEqualTo(beforeConflict);

        var revoked =
                patch(
                        headman,
                        mapper.writeValueAsString(
                                Map.of(
                                        "permissions",
                                        List.of(),
                                        "expectedPermissions",
                                        List.of(B, A))));
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        var response = mapper.readTree(revoked.getBody());
        assertThat(response.path("active").asBoolean()).isFalse();
        assertThat(response.path("revokedAt").isTextual()).isTrue();
        assertThat(response.path("permissions")).hasSize(2);
        assertThat(active()).isFalse();
        assertThat(storedPermissions()).containsExactly("mark_attendance", "manage_homework");
        assertThat(factCount()).isEqualTo(beforeConflict + 1);
        var fact = latestFact();
        assertThat(fact.path("change").asText()).isEqualTo("REVOKED");
        assertThat(fact.path("before_active").asBoolean()).isTrue();
        assertThat(fact.path("after_active").asBoolean()).isFalse();
        assertThat(fact.path("after_permissions")).isEmpty();
        assertThat(
                        patch(
                                        headman,
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "permissions",
                                                        List.of(A),
                                                        "expectedPermissions",
                                                        List.of(A, B))))
                                .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        var list = exchange(headman, HttpMethod.GET, "/academic/assistants?groupId=" + group, null);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).doesNotContain("AssistantCas");
        // Legacy DELETE still revokes a newly assigned active record and remains idempotent.
        assistant = assign();
        long beforeDelete = factCount();
        assertThat(exchange(headman, HttpMethod.DELETE, path(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(active()).isFalse();
        assertThat(exchange(headman, HttpMethod.DELETE, path(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(factCount()).isEqualTo(beforeDelete + 1);
    }

    @Test
    void unknownOrNullEnumElementsAndNonHeadmanCannotChangePermissions() throws Exception {
        long before = factCount();
        for (String body :
                List.of(
                        "{\"permissions\":null}",
                        "{\"permissions\":[null]}",
                        "{\"permissions\":[\"UNKNOWN\"]}",
                        "{\"permissions\":[\"MARK_ATTENDANCE\"],\"expectedPermissions\":[null]}",
                        "{\"permissions\":[\"MARK_ATTENDANCE\"],\"expectedPermissions\":[\"UNKNOWN\"]}")) {
            assertThat(patch(headman, body).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var student = identityFixture.select(helper, AuthRole.STUDENT);
        assertThat(
                        patch(
                                        student,
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "permissions",
                                                        List.of(C),
                                                        "expectedPermissions",
                                                        List.of(A, B))))
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(storedPermissions()).containsExactly("mark_attendance", "manage_homework");
        assertThat(factCount()).isEqualTo(before);
    }

    private long assign() throws Exception {
        var response =
                exchange(
                        headman,
                        HttpMethod.POST,
                        "/academic/assistants",
                        mapper.writeValueAsString(
                                Map.of(
                                        "studentId",
                                        helper,
                                        "groupId",
                                        group,
                                        "permissions",
                                        List.of(A, B))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("id").asLong();
    }

    private ResponseEntity<String> patch(SelectedActor actor, String body) {
        return exchange(actor, HttpMethod.PATCH, path() + "/permissions", body);
    }

    private String path() {
        return "/academic/assistants/" + assistant;
    }

    private ResponseEntity<String> exchange(
            SelectedActor actor, HttpMethod method, String path, String body) {
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", actor.internalToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private void assertConflict(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.readTree(response.getBody()).path("extras").path("code").asText())
                .isEqualTo("ASSISTANT_PERMISSIONS_CONFLICT");
    }

    private boolean active() {
        return jdbc.queryForObject(
                "SELECT is_active FROM headman_assistants WHERE id=?", Boolean.class, assistant);
    }

    private List<String> storedPermissions() {
        return jdbc.queryForList(
                "SELECT unnest(permissions) FROM headman_assistants WHERE id=?",
                String.class,
                assistant);
    }

    private long factCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM academic_outbox WHERE event_type='assistant.access.changed' AND (payload->'payload'->>'user_id')::bigint=?",
                Long.class,
                helper);
    }

    private JsonNode latestFact() throws Exception {
        return mapper.readTree(
                        jdbc.queryForObject(
                                "SELECT payload::text FROM academic_outbox WHERE event_type='assistant.access.changed' AND (payload->'payload'->>'user_id')::bigint=? ORDER BY id DESC LIMIT 1",
                                String.class,
                                helper))
                .path("payload");
    }

    private void awaitLockWaiters(int expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            long waiting =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND (query LIKE '%headman_assistants%' OR query LIKE '%groups%')",
                            Long.class);
            if (waiting >= expected) return;
            Thread.sleep(10);
        }
        fail("Expected %s real HTTP mutations waiting on database locks", expected);
    }
}
