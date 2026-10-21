package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class GroupRosterSnapshotIT extends AbstractAcademicIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired UserService users;
    @MockitoSpyBean AcademicIdentityReadFence fences;

    @Test
    void guardRosterAndLocalRoleLabelsResolveBeforeSameRepeatableReadSnapshotEnds()
            throws Exception {
        var canonical = identityFixture.canonical();
        var admin = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        long target =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Snapshot",
                                        "Member",
                                        null,
                                        UserRole.STUDENT,
                                        canonical.groupId(),
                                        null,
                                        610000004002L),
                                admin)
                        .userId();
        var student = identityFixture.select(canonical.studentId(), AuthRole.STUDENT);
        var once = new AtomicBoolean(true);
        var writer = Executors.newSingleThreadExecutor();
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            if (once.compareAndSet(true, false)) {
                                writer.submit(
                                                () -> {
                                                    try (var ignored =
                                                            identityFixture.request(admin)) {
                                                        users.updateRoleGrant(
                                                                target,
                                                                "STUDENT",
                                                                new RoleGrantUpdateRequest(
                                                                        RoleGrantStatus.SUSPENDED,
                                                                        null,
                                                                        null,
                                                                        null));
                                                    }
                                                })
                                        .get(20, TimeUnit.SECONDS);
                            }
                            return null;
                        })
                .when(fences)
                .requireGroupReadable(canonical.groupId());
        try {
            var first = roster(student.internalToken());
            var firstIds = new java.util.HashSet<Long>();
            first.forEach(row -> firstIds.add(row.path("id").asLong()));
            assertThat(firstIds).containsExactlyInAnyOrder(canonical.studentId(), target);
            first.forEach(
                    row -> {
                        assertThat(row.path("groupId").asLong()).isEqualTo(canonical.groupId());
                        assertThat(row.path("roles").findValuesAsText("status"))
                                .contains("ACTIVE")
                                .doesNotContain("SUSPENDED");
                    });
            var later = roster(student.internalToken());
            assertThat(later.size()).isEqualTo(1);
            assertThat(later.get(0).path("id").asLong()).isEqualTo(canonical.studentId());
        } finally {
            writer.shutdownNow();
            assertThat(writer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private JsonNode roster(String token) throws Exception {
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        var response =
                http.exchange(
                        "/academic/groups/my/members?size=100&sort=id,asc",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody()).path("_embedded").path("userResponseList");
    }
}
