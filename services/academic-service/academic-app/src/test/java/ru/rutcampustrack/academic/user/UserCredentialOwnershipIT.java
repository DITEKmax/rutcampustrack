package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.auth.session.model.AuthRole;

class UserCredentialOwnershipIT extends AbstractAcademicIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    @Test
    void noConflictWhenNoExistingValues() throws Exception {
        var actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", actor.internalToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response =
                http.exchange(
                        "/academic/users",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                java.util.Map.of(
                                        "lastName",
                                        "Credential",
                                        "firstName",
                                        "Owner",
                                        "role",
                                        "STUDENT",
                                        "groupId",
                                        identityFixture.canonical().groupId()),
                                headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = mapper.readTree(response.getBody());
        long id = body.path("id").asLong();
        String initial = body.path("initialPassword").asText();
        assertThat(initial.length()).isEqualTo(12);
        var stored =
                identityFixture
                        .authJdbc()
                        .queryForMap(
                                "SELECT password_hash,initial_password,password_changed FROM"
                                    + " auth_identities WHERE id=?",
                                id);
        assertThat(initial.equals(stored.get("initial_password"))).isTrue();
        assertThat(
                        new BCryptPasswordEncoder()
                                .matches(initial, (String) stored.get("password_hash")))
                .isTrue();
        assertThat(((String) stored.get("password_hash")).startsWith("$2a$10$")).isTrue();
        assertThat(stored.get("password_changed")).isEqualTo(false);
        assertThat(
                        jdbc.queryForList(
                                "SELECT column_name FROM information_schema.columns WHERE"
                                    + " table_schema=current_schema() AND"
                                    + " table_name='academic_profiles'",
                                String.class))
                .doesNotContain("password_hash", "initial_password", "password_changed");
        var local = users.findByIdIncludingArchived(id).orElseThrow();
        assertThat(local.getPasswordHash()).isNull();
        assertThat(local.getInitialPassword()).isNull();
        assertThat(body.has("passwordHash")).isFalse();
    }
}
