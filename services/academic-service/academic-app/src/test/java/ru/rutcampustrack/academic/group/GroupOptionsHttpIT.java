package ru.rutcampustrack.academic.group;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

/**
 * Names-only registry lookup stays paged and permission-bound when archive discovery is enabled.
 */
class GroupOptionsHttpIT extends AbstractAcademicIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private SelectedActor permitted;
    private SelectedActor denied;

    @BeforeEach
    void fixture() {
        var full = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        permitted = admin("Permitted", List.of("USERS_VIEW"), full);
        denied = admin("Denied", List.of("DASHBOARD_VIEW"), full);
        jdbc.update(
                "INSERT INTO groups(name,is_active,archived_at) VALUES ('Lookup-A',true,NULL),('Lookup-B',false,NULL),('Lookup-C',false,NOW())");
    }

    @Test
    void usersViewListsOnlySafeNamesAndExplicitArchiveFilterMatchesCountsAndPages()
            throws Exception {
        for (int page = 0; page < 2; page++) {
            var result = get(permitted, "search=lookup&page=" + page + "&size=1");
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            var json = mapper.readTree(result.getBody());
            assertThat(json.path("totalElements").asLong()).isEqualTo(2);
            assertThat(json.path("totalPages").asInt()).isEqualTo(2);
            assertThat(json.path("number").asInt()).isEqualTo(page);
            assertThat(json.path("items")).hasSize(1);
            var item = json.path("items").get(0);
            assertThat(item.path("name").asText()).isEqualTo(page == 0 ? "Lookup-A" : "Lookup-B");
            assertThat(item.path("archived").asBoolean()).isFalse();
            assertSafeItem(item);
        }
        for (int page = 0; page < 3; page++) {
            var result =
                    get(permitted, "search=lookup&includeArchived=true&page=" + page + "&size=1");
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            var json = mapper.readTree(result.getBody());
            assertThat(json.path("totalElements").asLong()).isEqualTo(3);
            assertThat(json.path("totalPages").asInt()).isEqualTo(3);
            assertThat(json.path("items")).hasSize(1);
            var item = json.path("items").get(0);
            assertThat(item.path("name").asText()).isEqualTo("Lookup-" + (char) ('A' + page));
            assertThat(item.path("archived").asBoolean()).isEqualTo(page == 2);
            assertSafeItem(item);
        }
    }

    @Test
    void absentPermissionAndOtherRoleCannotBrowseArchiveAndInvalidPagingRemainsRejected() {
        assertThat(get(denied, "includeArchived=true").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        var student =
                identityFixture.select(identityFixture.canonical().studentId(), AuthRole.STUDENT);
        assertThat(get(student, "includeArchived=true").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(permitted, "size=101&includeArchived=true").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private SelectedActor admin(String name, List<String> permissions, SelectedActor full) {
        long id =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Lookup",
                                        name,
                                        null,
                                        UserRole.ADMIN,
                                        null,
                                        null,
                                        null,
                                        new AdminAccessInput("LIMITED", permissions)),
                                full)
                        .userId();
        return identityFixture.select(id, AuthRole.ADMIN);
    }

    private ResponseEntity<String> get(SelectedActor actor, String query) {
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", actor.internalToken());
        return http.exchange(
                "/academic/groups/options?" + query,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private void assertSafeItem(JsonNode item) {
        var fields = new HashSet<String>();
        item.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name", "archived");
    }
}
