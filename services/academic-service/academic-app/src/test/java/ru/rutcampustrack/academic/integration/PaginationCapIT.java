package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.util.HashSet;
import java.util.Set;

class PaginationCapIT extends AbstractAcademicIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;

    @Test
    void listUsers_sizeExceedsMax_truncatedToShared100() throws Exception {
        var canonical = identityFixture.canonical();
        var actor = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        var expected =
                new HashSet<Long>(
                        Set.of(canonical.adminId(), canonical.teacherId(), canonical.studentId()));
        for (int index = 0; index < 102; index++) {
            expected.add(
                    identityFixture
                            .seedUser(
                                    new CreateUserRequest(
                                            "Cap" + index,
                                            "Synthetic",
                                            null,
                                            UserRole.STUDENT,
                                            canonical.groupId(),
                                            null,
                                            610000005000L + index),
                                    actor)
                            .userId());
        }
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", actor.internalToken());
        Set<Long> previous = Set.of();
        var seen = new HashSet<Long>();
        for (int page = 0; page < 2; page++) {
            var response =
                    http.exchange(
                            "/academic/users?size=1000000&page=" + page + "&sort=id,asc",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var json = mapper.readTree(response.getBody());
            assertThat(json.path("page").path("size").asInt()).isEqualTo(100);
            assertThat(json.path("page").path("totalElements").asLong()).isEqualTo(expected.size());
            assertThat(json.path("page").path("number").asInt()).isEqualTo(page);
            var ids = new HashSet<Long>();
            var rows = json.path("_embedded").path("userResponseList");
            rows.forEach(row -> ids.add(row.path("id").asLong()));
            assertThat(rows.size()).isEqualTo(page == 0 ? 100 : expected.size() - 100);
            assertThat(ids).hasSize(rows.size()).doesNotContainAnyElementsOf(previous);
            assertThat(expected).containsAll(ids);
            seen.addAll(ids);
            previous = ids;
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
    }
}
